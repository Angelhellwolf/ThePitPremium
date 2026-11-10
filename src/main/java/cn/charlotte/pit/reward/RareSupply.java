package cn.charlotte.pit.reward;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.config.NewConfiguration;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.enchantment.menu.button.EnchantButton;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.enchantment.rarity.EnchantmentRarity;
import cn.charlotte.pit.item.IMythicItem;
import cn.charlotte.pit.item.MythicColor;
import cn.charlotte.pit.junxu.JunxuManager;
import cn.charlotte.pit.junxu.JunxuRoom;
import cn.charlotte.pit.util.chat.CC;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import cn.charlotte.pit.menu.trade.TradeManager;
import cn.charlotte.pit.util.inventory.InventoryUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.io.File;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.ThreadLocalRandom;

/** Single-backend, main-thread supply accounting. No changes to the paid entitlement ledger. */
public final class RareSupply {
    private static final UUID SC_SHENGCHENG_UUID = UUID.fromString("e4bc7a15-8174-35ea-b23f-4c50080db6e9");
    private static final UUID SEVENTY_SEVEN_HZ_UUID = UUID.fromString("4c4e66e3-9114-3ab4-a33f-3a1127d7e500");
    private static final String BYPASS_PERMISSION = "pit.rare.bypass";
    private static RareSupplyLedger ledger;
    private static BukkitTask task;
    private static boolean healthy;
    private RareSupply() { }

    public static void start() {
        ThePit plugin = ThePit.getInstance();
        try {
            if (plugin.getPitConfig().isRedisEnable()) throw new IllegalStateException("Shared backends require a shared supply ledger");
            File file = new File(plugin.getDataFolder(), "rare-supply.yml");
            if (!file.exists()) plugin.saveResource("rare-supply.yml", false);
            YamlConfiguration config = new YamlConfiguration(); config.load(file);
            if (!config.getBoolean("enabled", true)) throw new IllegalStateException("Rare supply must be enabled");
            EnumMap<RareSupplyLedger.Pool, RareSupplyLedger.Limits> limits = new EnumMap<>(RareSupplyLedger.Pool.class);
            limits.put(RareSupplyLedger.Pool.FRESH, read(config, "fresh", 24, 160, 4, 16, 8, 40));
            limits.put(RareSupplyLedger.Pool.RARE, read(config, "rare", 8, 48, 2, 6, 4, 12));
            ledger = new RareSupplyLedger(plugin.getDataFolder().toPath().resolve("rare-supply.dat"), limits,
                    () -> ThreadLocalRandom.current().nextDouble(), cn.charlotte.pit.supporter.NationalDaySupporterRates::active);
            ledger.save();
            healthy = true;
            Bukkit.getPluginManager().registerEvents(new OwnershipListener(), plugin);
            task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!healthy) return;
                try { ledger.save(); plugin.getLogger().info(diagnostics()); }
                catch (Exception error) { fail(error); }
            }, 1200, 1200);
            plugin.getLogger().info("RARE_SUPPLY_READY " + diagnostics());
        } catch (Exception error) { fail(error); }
    }

    private static RareSupplyLedger.Limits read(YamlConfiguration config, String root, int sh, int sd, int ph, int pd, int gh, int gd) {
        return new RareSupplyLedger.Limits(config.getInt(root + ".server-hour", sh), config.getInt(root + ".server-24h", sd),
                config.getInt(root + ".player-hour", ph), config.getInt(root + ".player-24h", pd),
                config.getInt(root + ".room-hour", gh), config.getInt(root + ".room-24h", gd));
    }

    private static String group(Player player) {
        JunxuManager manager = ThePit.getInstance().getJunxuManager();
        JunxuRoom room = manager == null ? null : manager.at(player.getLocation());
        return room == null ? "" : "room:" + room.owner;
    }

    public static boolean preflight(Player player) {
        return true;
    }

    public static boolean preflight(Player player, ItemStack item) {
        return true;
    }

    public static boolean drop(double probability, Player player) {
        return attempt(RareSupplyLedger.Pool.FRESH, probability, player);
    }

    public static boolean enchant(Player player, MythicColor color) {
        if (player == null || !eligible(player)) return false;
        return directChance(NewConfiguration.INSTANCE.getChance(player, color));
    }

    /** Shared with the scoped getChance replacement in the live Kotlin class. */
    public static double baseEnchantChance(NewConfiguration config, Player player, MythicColor color) {
        if (player.hasMetadata("lucky")) return config.getLuckGem();
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        double base = config.getSupporterRareChance(profile.getActiveSupporterTier());
        return cn.charlotte.pit.map.kingsquests.CherryLuck.rareChance(profile, base, System.currentTimeMillis());
    }

    public static boolean rare(double probability, Player player) {
        return attempt(RareSupplyLedger.Pool.RARE, probability, player);
    }

    public static boolean rareFromKill(double probability, Player player, Entity target) {
        return rare(probability * combatMultiplier(target, 1), player);
    }

    public static double combatMultiplier(Entity target, double rewardMultiplier) {
        return CombatDropPolicy.multiplier(connectedHuman(target), syntheticTarget(target), rewardMultiplier);
    }

    public static double cherryChance(double rewardMultiplier, Entity target) {
        return NewConfiguration.INSTANCE.getCherryDropChance() * combatMultiplier(target, rewardMultiplier);
    }

    private static boolean connectedHuman(Entity target) {
        return target instanceof Player && ((Player) target).getAddress() != null;
    }

    public static boolean syntheticTarget(Entity target) {
        if (target == null || connectedHuman(target)) return false;
        if (target.hasMetadata("NPC")) return true;
        ThePit plugin = ThePit.getInstance();
        return plugin != null && plugin.getPitBotManager() != null
                && plugin.getPitBotManager().getPlayer(target.getUniqueId()) == target;
    }

    /** Bytecode bridge used by the scoped release to preserve unrelated live class contents. */
    public static double enchantChance(NewConfiguration ignored, Player player, MythicColor color) {
        return enchant(player, color) ? 1 : 0;
    }

    public static boolean book(Player player, ItemStack item, IMythicItem mythic) {
        MythicColor color = mythic.getColor();
        boolean standard = color == MythicColor.RED || color == MythicColor.ORANGE || color == MythicColor.BLUE
                || color == MythicColor.GREEN || color == MythicColor.YELLOW;
        if (!standard || !EnchantButton.getPlayerMythicBook(player, item)
                || mythic.getTier() == 2 && mythic.getEnchantments().size() >= 3) return true;
        guaranteed(RareSupplyLedger.Pool.RARE, player);
        return true;
    }

    public static boolean freshClaim(Player player) {
        // Guaranteed sources remain available and contribute to later probability pressure.
        guaranteed(RareSupplyLedger.Pool.FRESH, player);
        return true;
    }

    public static boolean claim(Player player, ItemStack item) {
        IMythicItem mythic = cn.charlotte.pit.util.Utils.getMythicItem(item);
        if (mythic != null) freshClaim(player);
        return true;
    }

    public static Object takePackage(java.util.Map<Integer, ItemStack> items, Object key, Player player) {
        return claim(player, items.get(key)) ? items.remove(key) : null;
    }

    public static boolean consumeVile(Player player, String name, Integer amount) {
        boolean consumed = cn.charlotte.pit.util.inventory.InventoryUtil.removeItem(player, name, amount);
        if (consumed) freshClaim(player);
        return consumed;
    }

    public static boolean consumeVile(Player player, String name, int amount) {
        return consumeVile(player, name, Integer.valueOf(amount));
    }

    public static boolean rngClaim(PlayerProfile profile, String name, double weight, Player player) {
        boolean claimed = CombatEconomy.claimRecent(profile, name, weight);
        if (claimed) guaranteed(RareSupplyLedger.Pool.FRESH, player);
        return claimed;
    }

    public static boolean rngClaim(PlayerProfile profile, String name, int weight, Player player) {
        return rngClaim(profile, name, (double) weight, player);
    }

    public static boolean auctionExpired(boolean expired, cn.charlotte.pit.events.impl.AuctionEvent auction) {
        if (expired && auction.getHighestBidHistory() != null) {
            auction(auction.getHighestBidHistory().getUuid(), auction.getLots().getContents());
        }
        return expired;
    }

    public static AbstractEnchantment chooseEnchantment(List<AbstractEnchantment> candidates, Player player) {
        return chooseEnchantment(candidates, player, 1);
    }

    public static AbstractEnchantment chooseEnchantmentFromKill(List<AbstractEnchantment> candidates, Player player, Entity target) {
        return chooseEnchantment(candidates, player, combatMultiplier(target, 1));
    }

    private static AbstractEnchantment chooseEnchantment(List<AbstractEnchantment> candidates, Player player, double multiplier) {
        List<AbstractEnchantment> rare = candidates.stream().filter(RareSupply::isRare).collect(Collectors.toList());
        List<AbstractEnchantment> normal = candidates.stream().filter(e -> !isRare(e)).collect(Collectors.toList());
        boolean success = !rare.isEmpty() && (normal.isEmpty()
                ? guaranteed(RareSupplyLedger.Pool.RARE, player) : rare((double) rare.size() / candidates.size() * multiplier, player));
        List<AbstractEnchantment> chosen = success ? rare : normal;
        return chosen.isEmpty() ? null : chosen.get(ThreadLocalRandom.current().nextInt(chosen.size()));
    }

    private static boolean isRare(AbstractEnchantment enchantment) {
        return enchantment.getRarity().getParentType() == EnchantmentRarity.RarityType.RARE;
    }

    private static boolean rareItem(ItemStack item) {
        if (item == null || item.getAmount() <= 0 || item.getType() == org.bukkit.Material.AIR) return false;
        IMythicItem mythic = cn.charlotte.pit.util.Utils.getMythicItem(item);
        return mythic != null && (mythic.getMaxLive() >= 100 || mythic.getEnchantments().keySet().stream().anyMatch(RareSupply::isRare));
    }

    private static String itemKey(ItemStack item) throws Exception {
        IMythicItem mythic = cn.charlotte.pit.util.Utils.getMythicItem(item);
        if (mythic.uuid != null && !mythic.uuid.equals(IMythicItem.getDefUUID())) return mythic.uuid.toString();
        ItemStack single = item.clone(); single.setAmount(1);
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(
                InventoryUtil.serializeItemStack(single).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder key = new StringBuilder("legacy:");
        for (byte value : digest) key.append(String.format("%02x", value & 255));
        return key.toString();
    }

    private static List<String> rareKeys(Collection<ItemStack> items) throws Exception {
        List<String> keys = new ArrayList<>();
        for (ItemStack item : items) if (rareItem(item)) {
            String key = itemKey(item);
            for (int i = 0; i < item.getAmount(); i++) keys.add(key + ":" + i);
        }
        return keys;
    }

    private static int stock(ItemStack... items) {
        int amount = 0;
        if (items != null) for (ItemStack item : items) if (rareItem(item)) {
            amount += item.getAmount();
            if (amount > 1) return 2;
        }
        return amount;
    }

    private static int rareStock(Player player) throws Exception {
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        int count = stock(player.getInventory().getContents()) + stock(player.getInventory().getArmorContents())
                + stock(player.getItemOnCursor());
        if (count > 1) return 2;
        count += stock(player.getEnderChest().getContents());
        if (profile.getEnderChest() != null && profile.getEnderChest().getInventory() != player.getEnderChest())
            count += stock(profile.getEnderChest().getInventory().getContents());
        if (count > 1) return 2;
        count += stock(InventoryUtil.deserializeItemStack(profile.getEnchantingItem()),
                InventoryUtil.deserializeItemStack(profile.getEnchantingScience()));
        TradeManager trade = TradeManager.trades.get(player.getUniqueId());
        if (trade != null) count += stock((trade.getPlayerA().equals(player) ? trade.getAItems() : trade.getBItems()).toArray(new ItemStack[0]));
        if (count > 1) return 2;
        if (profile.getMailData() != null) for (cn.charlotte.pit.data.mail.Mail mail : profile.getMailData().getMails()) {
            if (mail.isClaimed() || mail.getItem() == null
                    || mail.getExpireTime() > 0 && mail.getExpireTime() <= System.currentTimeMillis()) continue;
            count += stock(mail.getItem().getContents()) + stock(mail.getItem().getArmorContents());
            if (count > 1) return 2;
        }
        return count;
    }

    public static void tradeCompleted(TradeManager trade) {
        if (!healthy) return;
        try {
            long now = System.currentTimeMillis();
            ledger.transfer(trade.getPlayerA().getUniqueId(), trade.getPlayerB().getUniqueId(), rareKeys(trade.getAItems()), now);
            ledger.transfer(trade.getPlayerB().getUniqueId(), trade.getPlayerA().getUniqueId(), rareKeys(trade.getBItems()), now);
        } catch (Exception error) { fail(error); }
    }

    public static final class OwnershipListener implements Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void dropped(PlayerDropItemEvent event) {
            if (!healthy) return;
            try {
                ledger.groundDrop(event.getItemDrop().getUniqueId(), event.getPlayer().getUniqueId(),
                        rareKeys(java.util.Collections.singletonList(event.getItemDrop().getItemStack())), System.currentTimeMillis());
            } catch (Exception error) { fail(error); }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void pickedUp(PlayerPickupItemEvent event) {
            if (!healthy) return;
            try {
                ItemStack item = event.getItem().getItemStack().clone();
                if (!rareItem(item)) return;
                Player player = event.getPlayer();
                UUID entity = event.getItem().getUniqueId();
                String key = itemKey(item);
                int before = heldAmount(player, key);
                Bukkit.getScheduler().runTask(ThePit.getInstance(), () -> {
                    if (!healthy || !player.isOnline()) return;
                    try {
                        int delivered = Math.min(item.getAmount(), Math.max(0, heldAmount(player, key) - before));
                        ledger.groundPickup(entity, player.getUniqueId(), delivered, System.currentTimeMillis());
                    } catch (Exception error) { fail(error); }
                });
            } catch (Exception error) { fail(error); }
        }
    }

    private static int heldAmount(Player player, String key) throws Exception {
        int amount = 0;
        for (ItemStack item : player.getInventory().getContents())
            if (rareItem(item) && key.equals(itemKey(item))) amount += item.getAmount();
        return amount;
    }

    /** Record the advertised auction result, including offline winners, without cancelling settlement. */
    public static boolean auction(UUID winner, ItemStack[] contents) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Rare supply must run on the server thread");
        int units = 0;
        try {
            if (contents != null) for (ItemStack item : contents) {
                IMythicItem mythic = cn.charlotte.pit.util.Utils.getMythicItem(item);
                if (mythic == null) continue;
                int perItem = (int) mythic.getEnchantments().keySet().stream().filter(RareSupply::isRare).count();
                if (mythic.getMaxLive() >= 100) perItem++;
                units += perItem * item.getAmount();
            }
            if (units == 0) return true;
            if (!healthy) return true;
            Player online = Bukkit.getPlayer(winner);
            ledger.record(RareSupplyLedger.Pool.RARE, winner, online == null ? "" : group(online),
                    units, System.currentTimeMillis());
        } catch (Exception error) { fail(error); }
        return true;
    }

    public static boolean cactus(Player player, ItemStack result) {
        if (cn.charlotte.pit.util.inventory.InventoryUtil.isInvFull(player)) {
            player.sendMessage(CC.translate("&c你的背包已满！"));
            return false;
        }
        if (!"sacrosanct_cactus".equals(cn.charlotte.pit.util.item.ItemUtil.getInternalName(player.getItemInHand()))) {
            player.sendMessage(CC.translate("&c请手持神圣仙人掌后重试！"));
            return false;
        }
        auction(player.getUniqueId(), new ItemStack[] { result });
        cn.charlotte.pit.util.PlayerUtil.takeOneItemInHand(player);
        return true;
    }

    public static boolean guaranteed(RareSupplyLedger.Pool pool, Player player) {
        if (healthy && eligible(player)) {
            try { ledger.record(pool, player.getUniqueId(), group(player), 1, System.currentTimeMillis()); }
            catch (Exception error) { fail(error); }
        }
        return true;
    }

    private static boolean attempt(RareSupplyLedger.Pool pool, double probability, Player player) {
        if (player == null || !eligible(player)) return false;
        return directChance(probability);
    }

    private static boolean directChance(double probability) {
        if (!Double.isFinite(probability) || probability <= 0) return false;
        if (probability >= 1) return true;
        return ThreadLocalRandom.current().nextDouble() < probability;
    }

    private static boolean bypass(Player player) {
        return player.isOp() || player.hasPermission(BYPASS_PERMISSION)
                || player.hasPermission("pit.admin") || player.hasPermission("pit.staff.default")
                || player.getUniqueId().equals(SC_SHENGCHENG_UUID)
                || player.getUniqueId().equals(SEVENTY_SEVEN_HZ_UUID)
                || player.hasPermission("pit.rare.sc_shengcheng");
    }

    private static boolean eligible(Player player) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Rare supply must run on the server thread");
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(player.getUniqueId());
        return profile.isLoaded() && !syntheticTarget(player);
    }

    public static String diagnostics() {
        return "RARE_SUPPLY healthy=" + healthy + " mode=member-rates-only-random "
                + "policy=direct-chance globalPressure=off botTargetMultiplier=1.0 humanTargetMultiplier=1.0 "
                + "sc=member-tier hz77=member-tier fence=off lowStock=off boost=off compensationPeriod="
                + (cn.charlotte.pit.supporter.NationalDaySupporterRates.active(System.currentTimeMillis()) ? "1d" : "disabled")
                + " transfer=3h/6d:0.25,6h/12d:0.10 "
                + (ledger == null ? "unavailable" : ledger.diagnostics(System.currentTimeMillis()));
    }

    private static void fail(Exception error) {
        healthy = false;
        ThePit.getInstance().getLogger().log(java.util.logging.Level.SEVERE,
                "RARE_SUPPLY unavailable; refusing to reset quotas or emit controlled drops", error);
    }

    public static void shutdown() {
        if (task != null) { task.cancel(); task = null; }
        if (ledger == null) return;
        try { if (healthy) ledger.save(); ledger.close(); }
        catch (Exception error) { fail(error); }
        finally { ledger = null; healthy = false; }
    }
}
