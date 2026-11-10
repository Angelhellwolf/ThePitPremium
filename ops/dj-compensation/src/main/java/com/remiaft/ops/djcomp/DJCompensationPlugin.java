package com.remiaft.ops.djcomp;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerMailData;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.data.PlayerInvBackup;
import cn.charlotte.pit.data.TradeData;
import cn.charlotte.pit.data.mail.Mail;
import cn.charlotte.pit.data.sub.EnchantmentRecord;
import cn.charlotte.pit.data.sub.OfferData;
import cn.charlotte.pit.data.sub.PlayerInv;
import cn.charlotte.pit.enchantment.AbstractEnchantment;
import cn.charlotte.pit.event.PitPlayerEnchantEvent;
import cn.charlotte.pit.item.IMythicItem;
import cn.charlotte.pit.item.DyeColor;
import cn.charlotte.pit.item.MythicColor;
import cn.charlotte.pit.item.type.mythic.MythicLeggingsItem;
import cn.charlotte.pit.util.Utils;
import cn.charlotte.pit.util.inventory.InventoryUtil;
import cn.charlotte.pit.util.item.ItemUtil;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import net.minecraft.server.v1_8_R3.NBTTagCompound;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A deliberately narrow production-operations plugin for one compensation.
 *
 * <p>The pending result is bound to both the recipient UUID and the UUID of the
 * freshly-issued pants. It cannot affect another player or another mythic item.
 * The state is persisted before the command returns and is consumed after one
 * successful enchant event.</p>
 */
public final class DJCompensationPlugin extends JavaPlugin implements Listener {
    private static final String INTERNAL = "mythic_leggings";
    private static final String SWIFT = "swift_sprint_enchant";
    private static final String PEROXIDE = "Peroxide";
    private static final String NEW_YEAR = "happy_new_year";
    private static final String DJ_TWO = "jerry_genesis_s2";
    private static final String DJ_SIX = "jerry_genesis_s6";
    private static final String CRIT = "critically_funky_enchant";
    private static final UUID SEVENTY_SEVEN_HZ = UUID.fromString("4c4e66e3-9114-3ab4-a33f-3a1127d7e500");

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("DJ compensation helper enabled; armed=" + isArmed());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("thepit.djcomp.admin")) {
            message(sender, "&c没有权限。");
            return true;
        }
        if (args.length == 0) {
            message(sender, "&e/djcomp scan/scanoffline/traceoffline <玩家> | apply/applyoffline/issueoffline <来源> <接收者> | retune <玩家> <物品UUID> | swap77 inspect/apply <物品UUID> <旧DJ附魔ID> | status | verify | disarm");
            return true;
        }

        try {
            if ("swap77".equalsIgnoreCase(args[0])) {
                swap77Command(sender, args);
                return true;
            }
            if ("scan".equalsIgnoreCase(args[0])) {
                if (args.length != 2) {
                    message(sender, "&c用法: /djcomp scan <玩家>");
                    return true;
                }
                scanCommand(sender, args[1]);
                return true;
            }
            if ("apply".equalsIgnoreCase(args[0])) {
                if (args.length != 3) {
                    message(sender, "&c用法: /djcomp apply <来源玩家> <接收玩家>");
                    return true;
                }
                applyCommand(sender, args[1], args[2]);
                return true;
            }
            if ("scanoffline".equalsIgnoreCase(args[0])) {
                if (args.length != 2) {
                    message(sender, "&c用法: /djcomp scanoffline <玩家>");
                    return true;
                }
                scanOfflineCommand(sender, args[1]);
                return true;
            }
            if ("applyoffline".equalsIgnoreCase(args[0])) {
                if (args.length != 3) {
                    message(sender, "&c用法: /djcomp applyoffline <来源玩家> <接收玩家>");
                    return true;
                }
                applyOfflineCommand(sender, args[1], args[2]);
                return true;
            }
            if ("traceoffline".equalsIgnoreCase(args[0])) {
                if (args.length != 2) {
                    message(sender, "&c用法: /djcomp traceoffline <玩家>");
                    return true;
                }
                traceOfflineCommand(sender, args[1]);
                return true;
            }
            if ("issueoffline".equalsIgnoreCase(args[0])) {
                if (args.length != 3) {
                    message(sender, "&c用法: /djcomp issueoffline <来源玩家> <接收玩家>");
                    return true;
                }
                issueOfflineCommand(sender, args[1], args[2]);
                return true;
            }
            if ("retune".equalsIgnoreCase(args[0])) {
                if (args.length != 3) {
                    message(sender, "&c用法: /djcomp retune <玩家> <物品UUID>");
                    return true;
                }
                retuneCommand(sender, args[1], args[2]);
                return true;
            }
            if ("status".equalsIgnoreCase(args[0])) {
                statusCommand(sender);
                return true;
            }
            if ("verify".equalsIgnoreCase(args[0])) {
                verifyCommand(sender);
                return true;
            }
            if ("disarm".equalsIgnoreCase(args[0])) {
                clearPending("manual disarm by " + sender.getName());
                message(sender, "&a已取消待执行的指定附魔。");
                return true;
            }
        } catch (Throwable throwable) {
            getLogger().severe("Command failed: " + throwable.getMessage());
            throwable.printStackTrace();
            message(sender, "&c执行失败；未确认完成。请查看服务端日志。");
            return true;
        }

        message(sender, "&c未知子命令。");
        return true;
    }

    private void scanCommand(CommandSender sender, String playerName) {
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null || !player.isOnline()) {
            message(sender, "&c玩家不在线: " + playerName);
            return;
        }
        PlayerProfile profile = loadedProfile(player);
        if (profile == null) {
            message(sender, "&c玩家档案尚未加载: " + playerName);
            return;
        }
        player.closeInventory();
        List<ItemRef> djItems = findDjItems(player, profile);
        message(sender, "&7DJ #6 扫描: &f" + player.getName() + " &7共 &e" + djItems.size() + " &7条");
        for (ItemRef ref : djItems) {
            message(sender, "&7- &f" + ref.location + " &8| &f" + describe(ref.get()));
        }
        long exact = djItems.stream().filter(ref -> isPicturePants(ref.get())).count();
        message(sender, "&7与图片四附魔签名一致: &a" + exact);
    }

    private void retuneCommand(CommandSender sender, String playerName, String itemUuid) {
        Player player = Bukkit.getPlayerExact(playerName);
        if (player != null && player.isOnline()) {
            PlayerProfile profile = loadedProfile(player);
            if (profile == null) {
                message(sender, "&c玩家档案尚未加载: " + playerName);
                return;
            }
            if (profile.isTempInvUsing()) {
                message(sender, "&c玩家正在使用活动临时背包；本次未改动。");
                return;
            }
            player.closeInventory();
            retuneProfile(sender, profile, player, itemUuid);
            return;
        }

        message(sender, "&7玩家不在线，正在异步读取离线档案: " + playerName);
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            PlayerProfile profile;
            try {
                profile = loadOfflineProfile(playerName);
            } catch (Throwable throwable) {
                Bukkit.getScheduler().runTask(this,
                        () -> message(sender, "&c读取离线档案失败: " + throwable.getMessage()));
                return;
            }
            Bukkit.getScheduler().runTask(this, () -> {
                if (Bukkit.getPlayerExact(playerName) != null) {
                    message(sender, "&c玩家在读取期间上线；本次未改动，请重新执行命令。");
                    return;
                }
                if (profile == null) {
                    message(sender, "&c未找到玩家档案: " + playerName);
                    return;
                }
                retuneProfile(sender, profile, null, itemUuid);
            });
        });
    }

    private void swap77Command(CommandSender sender, String[] args) {
        if (!(sender instanceof ConsoleCommandSender) || args.length < 2) {
            message(sender, "&c仅控制台可执行: /djcomp swap77 inspect | apply <物品UUID> <旧DJ附魔ID>");
            return;
        }
        Player player = Bukkit.getPlayer(SEVENTY_SEVEN_HZ);
        if (player == null || !player.isOnline()) {
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    PlayerProfile stored = ThePit.getInstance().getMongoDB().getProfileCollection()
                            .find(Filters.eq("uuid", SEVENTY_SEVEN_HZ.toString())).first();
                    if (stored != null) PlayerProfile.loadMail(stored, SEVENTY_SEVEN_HZ);
                    Bukkit.getScheduler().runTask(this, () -> {
                        if (Bukkit.getPlayer(SEVENTY_SEVEN_HZ) != null) {
                            message(sender, "&c读取期间 77hz 上线；请重新执行命令。");
                            return;
                        }
                        swap77Profile(sender, args, stored, null);
                    });
                } catch (Throwable error) {
                    getLogger().log(java.util.logging.Level.SEVERE, "swap77 offline load failed", error);
                }
            });
            return;
        }
        PlayerProfile profile = loadedProfile(player);
        swap77Profile(sender, args, profile, player);
    }

    private void swap77Profile(CommandSender sender, String[] args, PlayerProfile profile, Player player) {
        if (profile == null || !SEVENTY_SEVEN_HZ.equals(profile.getPlayerUuid())
                || profile.isTempInvUsing() || !"77hz".equalsIgnoreCase(profile.getPlayerName())) {
            message(sender, "&c77hz 档案未加载、身份不匹配或正在使用临时背包；未修改。");
            return;
        }
        List<ItemRef> owned = player == null ? allStoredItems(profile) : allOwnedItems(player, profile);
        if ("inspect".equalsIgnoreCase(args[1]) && args.length == 2) {
            int count = 0;
            for (ItemRef ref : owned) {
                IMythicItem mythic = Utils.getMythicItem(ref.get());
                if (!(mythic instanceof MythicLeggingsItem)) continue;
                for (AbstractEnchantment enchant : mythic.getEnchantments().keySet()) {
                    if (enchant != null && isDj(enchant.getNbtName())) {
                        count++;
                        message(sender, "&7swap77: " + ref.location + " " + describe(ref.get())
                                + " old=" + enchant.getNbtName() + ":" + mythic.getEnchantments().get(enchant));
                    }
                }
            }
            message(sender, "&7swap77 DJ 附魔数量: " + count);
            return;
        }
        if (!"apply".equalsIgnoreCase(args[1]) || args.length != 4 || !isDj(args[3])) {
            message(sender, "&c用法: /djcomp swap77 apply <物品UUID> <旧DJ附魔ID>");
            return;
        }
        if (player != null) player.closeInventory();
        List<ItemRef> refs = new ArrayList<>();
        for (ItemRef candidate : owned) {
            if (args[2].equals(ItemUtil.getUUID(candidate.get()))) refs.add(candidate);
        }
        if (refs.size() != 1) {
            message(sender, "&c目标物品 UUID 匹配数量为 " + refs.size() + "，要求恰好 1；未修改。");
            return;
        }
        ItemRef ref = refs.get(0);
        ItemStack original = ref.get();
        IMythicItem mythic = Utils.getMythicItem(original);
        if (!(mythic instanceof MythicLeggingsItem) || Utils.getEnchantLevel(original, SWIFT) > 0
                || Utils.getEnchantLevel(original, args[3]) != 1) {
            message(sender, "&c原物品不是单级 DJ 神话裤，或已有疾走；未修改。" + describe(original));
            return;
        }
        AbstractEnchantment old = null;
        int djCount = 0;
        for (AbstractEnchantment enchant : mythic.getEnchantments().keySet()) {
            if (enchant != null && isDj(enchant.getNbtName())) {
                djCount++;
                old = enchant;
            }
        }
        AbstractEnchantment swift = ThePit.getInstance().getEnchantmentFactor().getEnchantmentMap().get(SWIFT);
        if (djCount != 1 || old == null || !args[3].equals(old.getNbtName()) || swift == null) {
            message(sender, "&cDJ 数量或原附魔 ID 不匹配；未修改。");
            return;
        }
        LinkedHashMap<AbstractEnchantment, Integer> changed = new LinkedHashMap<>(mythic.getEnchantments());
        changed.remove(old);
        changed.put(swift, 3);
        mythic.setEnchantments(changed);
        String hidden = mythic.getViewHiddenEnchants();
        mythic.setViewHiddenEnchants(hidden == null || hidden.trim().isEmpty()
                ? SWIFT : hidden + "," + SWIFT);
        ItemStack generated = mythic.toItemStack();

        // Preserve unrelated item-specific NBT while replacing only the visual display and enchant payload.
        net.minecraft.server.v1_8_R3.ItemStack nms = cn.charlotte.pit.UtilKt.reflectGetNmsItem(original);
        NBTTagCompound tag = nms.getTag();
        NBTTagCompound generatedTag = cn.charlotte.pit.UtilKt.reflectGetNmsItem(generated).getTag();
        if (tag == null || generatedTag == null || !tag.hasKey("extra") || !generatedTag.hasKey("extra")) {
            message(sender, "&c物品 NBT 不完整；未修改。");
            return;
        }
        NBTTagCompound extra = tag.getCompound("extra");
        NBTTagCompound newExtra = generatedTag.getCompound("extra");
        extra.set("ench", newExtra.getList("ench", 8));
        extra.setString("view_hidden_enchants", mythic.getViewHiddenEnchants());
        tag.set("extra", extra);
        tag.set("display", generatedTag.getCompound("display"));
        nms.setTag(tag);
        ItemStack updated = CraftItemStack.asBukkitCopy(nms);
        if (!args[2].equals(ItemUtil.getUUID(updated)) || Utils.getEnchantLevel(updated, args[3]) > 0
                || Utils.getEnchantLevel(updated, SWIFT) != 3) {
            message(sender, "&c生成物品未通过 UUID/附魔验证；未修改。");
            return;
        }
        if (player == null && ref.mail) {
            message(sender, "&c离线目标位于邮件；不覆盖独立邮件集合。未修改。");
            return;
        }
        long lastLogout = profile.getLastLogoutTime();
        ref.set(updated);
        if (player != null) {
            profile.save(player);
            if (ref.mail) profile.getMailData().save();
            player.updateInventory();
        } else {
            if (Bukkit.getPlayer(SEVENTY_SEVEN_HZ) != null || profile.isLogin()) {
                message(sender, "&c玩家已上线或档案标记在线；未写入。");
                return;
            }
            long modified = ThePit.getInstance().getMongoDB().getProfileCollection().replaceOne(
                    Filters.and(Filters.eq("uuid", SEVENTY_SEVEN_HZ.toString()),
                            Filters.eq("lastLogoutTime", lastLogout), Filters.eq("login", false)),
                    profile, new ReplaceOptions().upsert(false)).getModifiedCount();
            if (modified != 1) {
                message(sender, "&c离线档案条件写入失败；未确认替换，请重新 inspect。");
                return;
            }
        }
        getLogger().info("swap77 applied item=" + args[2] + " location=" + ref.location
                + " old=" + args[3] + " new=" + SWIFT + ":3");
        message(sender, "&a77hz 的目标物品已替换 DJ 为疾走 III，并设置外人 /view 隐藏；UUID=" + args[2]);
        if (player == null) {
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                PlayerProfile persisted = PlayerProfile.loadPlayerProfileByUuid(SEVENTY_SEVEN_HZ);
                List<ItemRef> matches = persisted == null ? new ArrayList<>() : findByUuidOffline(persisted, args[2]);
                String persistedHidden = matches.size() == 1
                        ? ItemUtil.getItemStringData(matches.get(0).get(), "view_hidden_enchants") : null;
                boolean verified = matches.size() == 1 && Utils.getEnchantLevel(matches.get(0).get(), SWIFT) == 3
                        && Utils.getEnchantLevel(matches.get(0).get(), args[3]) <= 0
                        && persistedHidden != null && persistedHidden.contains(SWIFT);
                getLogger().info("swap77 persistence verified=" + verified + " copies=" + matches.size());
            });
        }
    }

    private static boolean isDj(String id) {
        return id != null && (id.startsWith("jerry_genesis_s") || "dj_pvz_bundle".equals(id));
    }

    private void retuneProfile(CommandSender sender, PlayerProfile profile, Player player, String itemUuid) {
        List<ItemRef> refs = player == null
                ? findByUuidOffline(profile, itemUuid)
                : findByUuid(player, profile, itemUuid);
        if (refs.size() != 1) {
            message(sender, "&c要求按 UUID 精确找到 1 条裤子，但找到 " + refs.size() + " 条；本次未改动。");
            return;
        }

        ItemRef ref = refs.get(0);
        ItemStack current = ref.get();
        if (isRetunedPants(current)) {
            message(sender, "&a该裤子已经是目标属性；未重复改动。" + describe(current));
            verifyRetunePersisted(sender, profile.getPlayerUuid(), itemUuid);
            return;
        }
        if (!isPicturePants(current)) {
            message(sender, "&c目标 UUID 存在，但当前属性不再是原图片裤签名；本次未改动。" + describe(current));
            return;
        }

        Map<String, AbstractEnchantment> registry = ThePit.getInstance()
                .getEnchantmentFactor().getEnchantmentMap();
        AbstractEnchantment peroxide = registry.get(PEROXIDE);
        AbstractEnchantment newYear = registry.get(NEW_YEAR);
        AbstractEnchantment djTwo = registry.get(DJ_TWO);
        AbstractEnchantment crit = registry.get(CRIT);
        if (peroxide == null || newYear == null || djTwo == null || crit == null) {
            message(sender, "&c所需附魔尚未全部加载；本次未改动。");
            return;
        }

        ItemStack before = current.clone();
        IMythicItem mythic = Utils.getMythicItem(current);
        if (!(mythic instanceof MythicLeggingsItem)) {
            message(sender, "&c目标物品不是可修改的神话裤子；本次未改动。");
            return;
        }

        LinkedHashMap<AbstractEnchantment, Integer> exact = new LinkedHashMap<>();
        exact.put(peroxide, 3);
        exact.put(newYear, 3);
        exact.put(djTwo, 1);
        exact.put(crit, 3);
        mythic.setEnchantments(exact);
        ItemStack tuned = mythic.toItemStack();
        if (!itemUuid.equals(ItemUtil.getUUID(tuned)) || !isRetunedPants(tuned)) {
            message(sender, "&c生成后的裤子未通过 UUID/属性校验；本次未改动。");
            return;
        }

        boolean mailChanged = ref.mail;
        try {
            ref.set(tuned);
            if (player == null) {
                profile.saveData(null);
            } else {
                profile.save(player);
                player.updateInventory();
            }
            if (mailChanged) profile.getMailData().save();

            getLogger().info("DJ-COMP retuned player=" + profile.getPlayerName()
                    + " playerUuid=" + profile.getPlayerUuid()
                    + " itemUuid=" + itemUuid
                    + " location=" + ref.location
                    + " before=swift3,newyear3,dj6,crit3"
                    + " after=peroxide3,newyear3,dj2,crit3");
            message(sender, "&a已完成：" + profile.getPlayerName() + " 的目标裤子已改为"
                    + "过氧化物 III、新年快乐 III、Genesis! DJ #2、致命节奏 III。UUID 保持 " + itemUuid);
            if (player != null) {
                message(player, "&a管理员已将你的目标裤子调整为过氧化物 III、新年快乐 III、Genesis! DJ #2、致命节奏 III。");
            }
            verifyRetunePersisted(sender, profile.getPlayerUuid(), itemUuid);
        } catch (Throwable throwable) {
            ref.set(before);
            try {
                if (player == null) {
                    profile.saveData(null);
                } else {
                    profile.save(player);
                    player.updateInventory();
                }
                if (mailChanged) profile.getMailData().save();
            } catch (Throwable rollbackFailure) {
                throwable.addSuppressed(rollbackFailure);
            }
            throw throwable;
        }
    }

    private void verifyRetunePersisted(CommandSender sender, UUID playerUuid, String itemUuid) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            PlayerProfile persisted = PlayerProfile.loadPlayerProfileByUuid(playerUuid);
            Bukkit.getScheduler().runTask(this, () -> {
                List<ItemRef> refs = persisted == null
                        ? new ArrayList<>()
                        : findByUuidOffline(persisted, itemUuid);
                boolean verified = refs.size() == 1 && isRetunedPants(refs.get(0).get());
                getLogger().info("DJ-COMP retune persistence verification playerUuid=" + playerUuid
                        + " itemUuid=" + itemUuid + " copies=" + refs.size()
                        + " verified=" + verified
                        + (refs.size() == 1 ? " result=" + describe(refs.get(0).get()) : ""));
                message(sender, verified
                        ? "&a数据库读回核验通过：目标 UUID 恰好 1 份，DJ #2 与过氧化物 III 已持久化。"
                        : "&c数据库读回核验未通过：copies=" + refs.size());
            });
        });
    }

    private void applyCommand(CommandSender sender, String sourceName, String targetName) {
        if (isArmed()) {
            message(sender, "&c已有待执行补偿；请先 status/verify，不能覆盖。");
            return;
        }
        Player source = Bukkit.getPlayerExact(sourceName);
        Player target = Bukkit.getPlayerExact(targetName);
        if (source == null || !source.isOnline() || target == null || !target.isOnline()) {
            message(sender, "&c来源玩家和接收玩家必须同时在线。");
            return;
        }
        if (source.getUniqueId().equals(target.getUniqueId())) {
            message(sender, "&c来源与接收玩家不能相同。");
            return;
        }
        PlayerProfile sourceProfile = loadedProfile(source);
        PlayerProfile targetProfile = loadedProfile(target);
        if (sourceProfile == null || targetProfile == null) {
            message(sender, "&c至少一名玩家的档案尚未加载。");
            return;
        }
        if (sourceProfile.isTempInvUsing() || targetProfile.isTempInvUsing()) {
            message(sender, "&c至少一名玩家正在使用活动临时背包；为避免覆盖数据，本次未改动。");
            return;
        }

        // Closing first makes trade/stash close listeners return or persist their current contents.
        source.closeInventory();
        target.closeInventory();

        List<ItemRef> djItems = findDjItems(source, sourceProfile);
        List<ItemRef> exactItems = new ArrayList<>();
        for (ItemRef ref : djItems) {
            if (isPicturePants(ref.get())) exactItems.add(ref);
        }
        if (exactItems.size() != 1) {
            message(sender, "&c要求精确找到 1 条图片签名裤子，但找到 " + exactItems.size()
                    + " 条（全部 DJ #6 共 " + djItems.size() + " 条）；本次未改动。");
            for (ItemRef ref : djItems) {
                message(sender, "&7- &f" + ref.location + " &8| &f" + describe(ref.get()));
            }
            return;
        }

        MythicLeggingsItem fresh = new MythicLeggingsItem();
        fresh.setColor(MythicColor.RED);
        fresh.uuid = UUID.randomUUID();
        ItemStack issued = fresh.toItemStack();
        String issuedUuid = ItemUtil.getUUID(issued);
        if (issuedUuid == null || !InventoryUtil.canFit(target, issued)) {
            message(sender, "&c接收玩家背包没有空位，或新裤子 UUID 生成失败；本次未改动。");
            return;
        }

        ItemRef reclaimedRef = exactItems.get(0);
        ItemStack reclaimed = reclaimedRef.get().clone();
        String reclaimedUuid = ItemUtil.getUUID(reclaimed);
        boolean mailChanged = reclaimedRef.mail;

        try {
            reclaimedRef.set(null);
            Map<Integer, ItemStack> leftovers = target.getInventory().addItem(issued);
            if (!leftovers.isEmpty()) {
                throw new IllegalStateException("recipient inventory changed and no longer fits issued pants");
            }

            arm(target, issuedUuid, source, reclaimedUuid, reclaimedRef.location);
            sourceProfile.save(source);
            if (mailChanged) sourceProfile.getMailData().save();
            targetProfile.save(target);
            source.updateInventory();
            target.updateInventory();

            getLogger().info("DJ-COMP applied source=" + source.getName()
                    + " sourceUuid=" + source.getUniqueId()
                    + " reclaimedItemUuid=" + reclaimedUuid
                    + " reclaimedLocation=" + reclaimedRef.location
                    + " target=" + target.getName()
                    + " targetUuid=" + target.getUniqueId()
                    + " issuedItemUuid=" + issuedUuid);
            message(sender, "&a已完成：回收 " + source.getName() + " 的图片 DJ 裤子（" + reclaimedRef.location
                    + "），给 " + target.getName() + " 新红裤，并绑定下一次附魔。新 UUID=" + issuedUuid);
            message(source, "&c管理员已回收争议中的 Genesis! DJ #6 神话之甲。");
            message(target, "&a你已收到一条补偿红色神话之甲；该裤子下一次放入神话之井附魔时，会固定得到指定属性。");
        } catch (Throwable throwable) {
            removeByUuid(target.getInventory(), issuedUuid);
            reclaimedRef.set(reclaimed);
            clearPending("rollback after apply failure");
            try {
                sourceProfile.save(source);
                if (mailChanged) sourceProfile.getMailData().save();
                targetProfile.save(target);
            } catch (Throwable rollbackFailure) {
                throwable.addSuppressed(rollbackFailure);
            }
            throw throwable;
        }
    }

    private void scanOfflineCommand(CommandSender sender, String playerName) {
        if (Bukkit.getPlayerExact(playerName) != null) {
            message(sender, "&c玩家当前在线，请使用 scan，离线路径未执行。");
            return;
        }
        message(sender, "&7正在异步读取离线档案: " + playerName);
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            PlayerProfile profile;
            try {
                profile = loadOfflineProfile(playerName);
            } catch (Throwable throwable) {
                Bukkit.getScheduler().runTask(this,
                        () -> message(sender, "&c读取离线档案失败: " + throwable.getMessage()));
                return;
            }
            Bukkit.getScheduler().runTask(this, () -> {
                if (Bukkit.getPlayerExact(playerName) != null) {
                    message(sender, "&c玩家在读取期间上线，离线扫描已中止。");
                    return;
                }
                if (profile == null) {
                    message(sender, "&c未找到玩家档案: " + playerName);
                    return;
                }
                List<ItemRef> djItems = findDjItemsOffline(profile);
                message(sender, "&7离线 DJ #6 扫描: &f" + profile.getPlayerName()
                        + " &7共 &e" + djItems.size() + " &7条");
                for (ItemRef ref : djItems) {
                    message(sender, "&7- &f" + ref.location + " &8| &f" + describe(ref.get()));
                }
                long exact = djItems.stream().filter(ref -> isPicturePants(ref.get())).count();
                message(sender, "&7与图片四附魔签名一致: &a" + exact);
            });
        });
    }

    private void applyOfflineCommand(CommandSender sender, String sourceName, String targetName) {
        if (isArmed()) {
            message(sender, "&c已有待执行补偿；请先 status/verify，不能覆盖。");
            return;
        }
        if (Bukkit.getPlayerExact(sourceName) != null || Bukkit.getPlayerExact(targetName) != null) {
            message(sender, "&c离线路径要求双方都不在线；本次未改动。");
            return;
        }
        message(sender, "&7正在异步读取双方离线档案；读取完成后会再次检查在线状态。");
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            PlayerProfile sourceProfile;
            PlayerProfile targetProfile;
            try {
                sourceProfile = loadOfflineProfile(sourceName);
                targetProfile = loadOfflineProfile(targetName);
            } catch (Throwable throwable) {
                Bukkit.getScheduler().runTask(this,
                        () -> message(sender, "&c读取离线档案失败: " + throwable.getMessage()));
                return;
            }
            Bukkit.getScheduler().runTask(this,
                    () -> applyOfflineOnMain(sender, sourceName, targetName, sourceProfile, targetProfile));
        });
    }

    private void traceOfflineCommand(CommandSender sender, String playerName) {
        if (Bukkit.getPlayerExact(playerName) != null) {
            message(sender, "&c历史追踪要求玩家离线；本次未执行。");
            return;
        }
        message(sender, "&7正在读取正规背包备份与交易记录: " + playerName);
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            TraceData trace;
            try {
                trace = loadTraceData(playerName);
            } catch (Throwable throwable) {
                Bukkit.getScheduler().runTask(this,
                        () -> message(sender, "&c历史追踪读取失败: " + throwable.getMessage()));
                return;
            }
            Bukkit.getScheduler().runTask(this, () -> reportTrace(sender, playerName, trace));
        });
    }

    private void issueOfflineCommand(CommandSender sender, String sourceName, String targetName) {
        if (isArmed()) {
            message(sender, "&c已有待执行补偿；不能覆盖。");
            return;
        }
        if (Bukkit.getPlayerExact(sourceName) != null || Bukkit.getPlayerExact(targetName) != null) {
            message(sender, "&c历史确认发放要求双方离线；本次未改动。");
            return;
        }
        message(sender, "&7正在核对来源玩家的当前档案、历史 UUID 与后续交易。");
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            TraceData trace;
            PlayerProfile targetProfile;
            try {
                trace = loadTraceData(sourceName);
                targetProfile = loadOfflineProfile(targetName);
            } catch (Throwable throwable) {
                Bukkit.getScheduler().runTask(this,
                        () -> message(sender, "&c历史确认读取失败: " + throwable.getMessage()));
                return;
            }
            Bukkit.getScheduler().runTask(this,
                    () -> issueOfflineOnMain(sender, sourceName, targetName, trace, targetProfile));
        });
    }

    private void issueOfflineOnMain(CommandSender sender, String sourceName, String targetName,
                                    TraceData trace, PlayerProfile targetProfile) {
        if (Bukkit.getPlayerExact(sourceName) != null || Bukkit.getPlayerExact(targetName) != null) {
            message(sender, "&c至少一名玩家在核对期间上线；本次未改动。");
            return;
        }
        if (trace == null || trace.profile == null || targetProfile == null || isArmed()) {
            message(sender, "&c档案或待执行状态已变化；本次未改动。");
            return;
        }
        List<ItemRef> currentExact = new ArrayList<>();
        for (ItemRef ref : findDjItemsOffline(trace.profile)) {
            if (isPicturePants(ref.get())) currentExact.add(ref);
        }
        if (!currentExact.isEmpty()) {
            message(sender, "&c来源玩家当前又存在图片裤子，请改用 applyoffline；本次未改动。");
            return;
        }

        Map<String, HistoricalHit> history = historicalPictureItems(trace.backups);
        if (history.size() > 1) {
            message(sender, "&c历史中找到多个图片裤 UUID（" + history.size() + " 个），无法唯一确认；本次未改动。");
            reportTrace(sender, sourceName, trace);
            return;
        }
        HistoricalHit hit;
        if (history.isEmpty()) {
            List<TransferHit> signatureTransfers = transfersForPictureSignature(trace.trades);
            for (TransferHit transfer : signatureTransfers) {
                if (trace.profile.getUuid().equals(transfer.fromUuid)) {
                    message(sender, "&c虽无备份命中，但交易记录显示图片签名裤已转给 " + transfer.toName
                            + "（" + transfer.toUuid + "），不能按已回收处理；本次未改动。");
                    return;
                }
            }
            hit = new HistoricalHit("confirmed-absent:" + trace.profile.getUuid(), 0L,
                    "当前全部可访问存储", "无当前副本、无图片签名外转记录");
        } else {
            hit = history.values().iterator().next();
        }
        if (hit.itemUuid.startsWith("missing-uuid:")) {
            message(sender, "&c历史图片裤缺少物品 UUID，无法安全判定去向；本次未改动。");
            return;
        }
        List<TransferHit> transfers = transfersForUuid(trace.trades, hit.itemUuid);
        for (TransferHit transfer : transfers) {
            if (transfer.time > hit.latestBackupTime
                    && trace.profile.getPlayerUuid().toString().equals(transfer.fromUuid)) {
                message(sender, "&c历史裤子在最后一次备份持有后已转给 " + transfer.toName
                        + "（" + transfer.toUuid + "），不能按“已回收”处理；本次未改动。");
                return;
            }
        }

        PlayerInv targetInv = targetProfile.getInventory();
        if (targetInv == null) {
            targetInv = new PlayerInv();
            targetProfile.setInventory(targetInv);
        }
        ItemStack[] contents = targetInv.getContents();
        if (contents == null) contents = new ItemStack[36];
        if (contents.length < 36) contents = Arrays.copyOf(contents, 36);
        int emptySlot = -1;
        for (int slot = 0; slot < 36; slot++) {
            if (contents[slot] == null || contents[slot].getType() == Material.AIR) {
                emptySlot = slot;
                break;
            }
        }
        if (emptySlot < 0) {
            message(sender, "&c接收玩家离线背包已满；本次未改动。");
            return;
        }

        MythicLeggingsItem fresh = new MythicLeggingsItem();
        fresh.setColor(MythicColor.RED);
        fresh.uuid = UUID.randomUUID();
        ItemStack issued = fresh.toItemStack();
        String issuedUuid = ItemUtil.getUUID(issued);
        if (issuedUuid == null) {
            message(sender, "&c新裤子未生成有效 UUID；本次未改动。");
            return;
        }
        final ItemStack[] targetContents = contents;
        final int targetSlot = emptySlot;
        try {
            targetContents[targetSlot] = issued;
            targetInv.setContents(targetContents);
            armOffline(targetProfile, issuedUuid, trace.profile, hit.itemUuid,
                    "当前已不存在；历史最后位置=" + hit.location + "@" + hit.latestBackupTime);
            targetProfile.saveData(null);
            getLogger().info("DJ-COMP offline issue-only source=" + trace.profile.getPlayerName()
                    + " sourceUuid=" + trace.profile.getPlayerUuid()
                    + " historicalItemUuid=" + hit.itemUuid
                    + " historicalLastSeen=" + hit.latestBackupTime
                    + " target=" + targetProfile.getPlayerName()
                    + " targetUuid=" + targetProfile.getPlayerUuid()
                    + " targetSlot=" + targetSlot
                    + " issuedItemUuid=" + issuedUuid);
            message(sender, "&a确认原图片裤当前已不存在且最后持有后无外转；已给 "
                    + targetProfile.getPlayerName() + " 新红裤并绑定下一次附魔。新 UUID=" + issuedUuid);
            verifyOfflinePersisted(sender, trace.profile.getPlayerUuid(), targetProfile.getPlayerUuid(),
                    hit.itemUuid, issuedUuid);
        } catch (Throwable throwable) {
            if (issuedUuid != null && issuedUuid.equals(ItemUtil.getUUID(targetContents[targetSlot]))) {
                targetContents[targetSlot] = null;
                targetInv.setContents(targetContents);
            }
            clearPending("rollback after issue-only failure");
            try {
                targetProfile.saveData(null);
            } catch (Throwable rollbackFailure) {
                throwable.addSuppressed(rollbackFailure);
            }
            throw throwable;
        }
    }

    private TraceData loadTraceData(String playerName) {
        PlayerProfile profile = loadOfflineProfile(playerName);
        if (profile == null) return null;
        List<PlayerInvBackup> backups = new ArrayList<>();
        ThePit.getInstance().getMongoDB().getInvCollection()
                .find(Filters.eq("uuid", profile.getUuid())).into(backups);
        List<TradeData> trades = new ArrayList<>();
        ThePit.getInstance().getMongoDB().getTradeCollection()
                .find(Filters.or(Filters.eq("playerA", profile.getUuid()),
                        Filters.eq("playerB", profile.getUuid()))).into(trades);
        return new TraceData(profile, backups, trades);
    }

    private void reportTrace(CommandSender sender, String playerName, TraceData trace) {
        if (Bukkit.getPlayerExact(playerName) != null) {
            message(sender, "&c玩家在追踪期间上线，结果未用于修改。");
            return;
        }
        if (trace == null || trace.profile == null) {
            message(sender, "&c未找到玩家档案。");
            return;
        }
        List<ItemRef> current = findDjItemsOffline(trace.profile);
        Map<String, HistoricalHit> history = historicalPictureItems(trace.backups);
        message(sender, "&7历史追踪: 当前 DJ #6=&e" + current.size()
                + " &7图片裤历史 UUID 数=&e" + history.size()
                + " &7备份数=&e" + trace.backups.size()
                + " &7交易记录数=&e" + trace.trades.size());
        for (HistoricalHit hit : history.values()) {
            message(sender, "&7- 历史图片裤 uuid=&f" + hit.itemUuid + " &7最后出现="
                    + hit.latestBackupTime + " 位置=" + hit.location + " " + hit.description);
            List<TransferHit> transfers = transfersForUuid(trace.trades, hit.itemUuid);
            if (transfers.isEmpty()) {
                message(sender, "&7  该 UUID 在此玩家相关交易记录中无转移。");
            } else {
                for (TransferHit transfer : transfers) {
                    message(sender, "&7  交易 " + transfer.time + ": &f" + transfer.fromName
                            + " &7-> &f" + transfer.toName + " &8(" + transfer.tradeUuid + ")");
                }
            }
        }
        List<TransferHit> signatureTransfers = transfersForPictureSignature(trace.trades);
        message(sender, "&7图片四附魔签名相关交易物品数=&e" + signatureTransfers.size());
        for (TransferHit transfer : signatureTransfers) {
            message(sender, "&7- 签名交易 " + transfer.time + ": &f" + transfer.fromName
                    + " &7-> &f" + transfer.toName + " &7item=&f" + transfer.itemUuid
                    + " &8(" + transfer.tradeUuid + ")");
        }
    }

    private Map<String, HistoricalHit> historicalPictureItems(List<PlayerInvBackup> backups) {
        Map<String, HistoricalHit> result = new HashMap<>();
        List<PlayerInvBackup> ordered = new ArrayList<>(backups);
        ordered.sort(Comparator.comparingLong(PlayerInvBackup::getTimeStamp).reversed());
        for (PlayerInvBackup backup : ordered) {
            scanHistoricalInventory(result, backup.getInv(), "背包备份", backup.getTimeStamp());
            if (backup.getChest() != null) {
                Inventory chest = backup.getChest().getInventory();
                for (int slot = 0; slot < chest.getSize(); slot++) {
                    ItemStack item = chest.getItem(slot);
                    if (isPicturePants(item)) addHistorical(result, item,
                            "末影箱备份槽位 " + slot, backup.getTimeStamp());
                }
            }
        }
        return result;
    }

    private void scanHistoricalInventory(Map<String, HistoricalHit> result, PlayerInv inventory,
                                         String label, long timestamp) {
        if (inventory == null) return;
        ItemStack[] contents = inventory.getContents();
        if (contents != null) {
            for (int slot = 0; slot < contents.length; slot++) {
                if (isPicturePants(contents[slot])) addHistorical(result, contents[slot],
                        label + "槽位 " + slot, timestamp);
            }
        }
        ItemStack[] armor = inventory.getArmorContents();
        if (armor != null) {
            for (int slot = 0; slot < armor.length; slot++) {
                if (isPicturePants(armor[slot])) addHistorical(result, armor[slot],
                        label + "装备 " + slot, timestamp);
            }
        }
    }

    private void addHistorical(Map<String, HistoricalHit> result, ItemStack item,
                               String location, long timestamp) {
        String uuid = ItemUtil.getUUID(item);
        if (uuid == null) uuid = "missing-uuid:" + describe(item);
        HistoricalHit existing = result.get(uuid);
        if (existing == null || timestamp > existing.latestBackupTime) {
            result.put(uuid, new HistoricalHit(uuid, timestamp, location, describe(item)));
        }
    }

    private List<TransferHit> transfersForUuid(List<TradeData> trades, String itemUuid) {
        List<TransferHit> result = new ArrayList<>();
        for (TradeData trade : trades) {
            if (containsUuid(trade.getAPaidItem(), itemUuid)) {
                result.add(new TransferHit(trade.getCompleteTime(), trade.getPlayerA(), trade.getPlayerAName(),
                        trade.getPlayerB(), trade.getPlayerBName(), trade.getTradeUuid(), itemUuid));
            }
            if (containsUuid(trade.getBPaidItem(), itemUuid)) {
                result.add(new TransferHit(trade.getCompleteTime(), trade.getPlayerB(), trade.getPlayerBName(),
                        trade.getPlayerA(), trade.getPlayerAName(), trade.getTradeUuid(), itemUuid));
            }
        }
        result.sort(Comparator.comparingLong((TransferHit hit) -> hit.time).reversed());
        return result;
    }

    private List<TransferHit> transfersForPictureSignature(List<TradeData> trades) {
        List<TransferHit> result = new ArrayList<>();
        for (TradeData trade : trades) {
            addPictureTransfers(result, trade.getAPaidItem(), trade.getCompleteTime(),
                    trade.getPlayerA(), trade.getPlayerAName(), trade.getPlayerB(), trade.getPlayerBName(),
                    trade.getTradeUuid());
            addPictureTransfers(result, trade.getBPaidItem(), trade.getCompleteTime(),
                    trade.getPlayerB(), trade.getPlayerBName(), trade.getPlayerA(), trade.getPlayerAName(),
                    trade.getTradeUuid());
        }
        result.sort(Comparator.comparingLong((TransferHit transfer) -> transfer.time).reversed());
        return result;
    }

    private void addPictureTransfers(List<TransferHit> result, PlayerInv inventory, long time,
                                     String fromUuid, String fromName, String toUuid, String toName,
                                     String tradeUuid) {
        if (inventory == null) return;
        List<ItemStack> items = new ArrayList<>();
        if (inventory.getContents() != null) items.addAll(Arrays.asList(inventory.getContents()));
        if (inventory.getArmorContents() != null) items.addAll(Arrays.asList(inventory.getArmorContents()));
        for (ItemStack item : items) {
            if (!isPicturePants(item)) continue;
            String itemUuid = ItemUtil.getUUID(item);
            result.add(new TransferHit(time, fromUuid, fromName, toUuid, toName, tradeUuid,
                    itemUuid == null ? "missing-uuid" : itemUuid));
        }
    }

    private boolean containsUuid(PlayerInv inventory, String itemUuid) {
        if (inventory == null || itemUuid == null) return false;
        ItemStack[] contents = inventory.getContents();
        if (contents != null) {
            for (ItemStack item : contents) {
                if (itemUuid.equals(ItemUtil.getUUID(item))) return true;
            }
        }
        ItemStack[] armor = inventory.getArmorContents();
        if (armor != null) {
            for (ItemStack item : armor) {
                if (itemUuid.equals(ItemUtil.getUUID(item))) return true;
            }
        }
        return false;
    }

    private void applyOfflineOnMain(CommandSender sender, String sourceName, String targetName,
                                    PlayerProfile sourceProfile, PlayerProfile targetProfile) {
        if (Bukkit.getPlayerExact(sourceName) != null || Bukkit.getPlayerExact(targetName) != null) {
            message(sender, "&c至少一名玩家在读取期间上线；离线补偿已中止且未改动。");
            return;
        }
        if (sourceProfile == null || targetProfile == null) {
            message(sender, "&c至少一名玩家没有可用档案；本次未改动。");
            return;
        }
        if (isArmed()) {
            message(sender, "&c读取期间出现了另一个待执行补偿；本次未改动。");
            return;
        }

        List<ItemRef> djItems = findDjItemsOffline(sourceProfile);
        List<ItemRef> exactItems = new ArrayList<>();
        for (ItemRef ref : djItems) {
            if (isPicturePants(ref.get())) exactItems.add(ref);
        }
        if (exactItems.size() != 1) {
            message(sender, "&c要求精确找到 1 条图片签名裤子，但离线档案中找到 " + exactItems.size()
                    + " 条（全部 DJ #6 共 " + djItems.size() + " 条）；本次未改动。");
            for (ItemRef ref : djItems) {
                message(sender, "&7- &f" + ref.location + " &8| &f" + describe(ref.get()));
            }
            return;
        }

        PlayerInv targetInv = targetProfile.getInventory();
        if (targetInv == null) {
            targetInv = new PlayerInv();
            targetProfile.setInventory(targetInv);
        }
        ItemStack[] targetContents = targetInv.getContents();
        if (targetContents == null) targetContents = new ItemStack[36];
        if (targetContents.length < 36) targetContents = Arrays.copyOf(targetContents, 36);
        int emptySlot = -1;
        for (int slot = 0; slot < 36; slot++) {
            if (targetContents[slot] == null || targetContents[slot].getType() == Material.AIR) {
                emptySlot = slot;
                break;
            }
        }
        if (emptySlot < 0) {
            message(sender, "&c接收玩家离线背包已满；本次未改动。");
            return;
        }

        MythicLeggingsItem fresh = new MythicLeggingsItem();
        fresh.setColor(MythicColor.RED);
        fresh.uuid = UUID.randomUUID();
        ItemStack issued = fresh.toItemStack();
        String issuedUuid = ItemUtil.getUUID(issued);
        if (issuedUuid == null) {
            message(sender, "&c新裤子 UUID 生成失败；本次未改动。");
            return;
        }

        ItemRef reclaimedRef = exactItems.get(0);
        ItemStack reclaimed = reclaimedRef.get().clone();
        String reclaimedUuid = ItemUtil.getUUID(reclaimed);
        boolean mailChanged = reclaimedRef.mail;
        final int issuedSlot = emptySlot;
        final ItemStack[] persistedTargetContents = targetContents;

        try {
            reclaimedRef.set(null);
            persistedTargetContents[issuedSlot] = issued;
            targetInv.setContents(persistedTargetContents);
            armOffline(targetProfile, issuedUuid, sourceProfile, reclaimedUuid, reclaimedRef.location);
            sourceProfile.saveData(null);
            if (mailChanged) sourceProfile.getMailData().save();
            targetProfile.saveData(null);

            getLogger().info("DJ-COMP offline applied source=" + sourceProfile.getPlayerName()
                    + " sourceUuid=" + sourceProfile.getPlayerUuid()
                    + " reclaimedItemUuid=" + reclaimedUuid
                    + " reclaimedLocation=" + reclaimedRef.location
                    + " target=" + targetProfile.getPlayerName()
                    + " targetUuid=" + targetProfile.getPlayerUuid()
                    + " targetSlot=" + issuedSlot
                    + " issuedItemUuid=" + issuedUuid);
            message(sender, "&a离线补偿已写入：回收 " + sourceProfile.getPlayerName() + " 的图片 DJ 裤子（"
                    + reclaimedRef.location + "），给 " + targetProfile.getPlayerName()
                    + " 新红裤，并绑定下一次附魔。新 UUID=" + issuedUuid);
            verifyOfflinePersisted(sender, sourceProfile.getPlayerUuid(), targetProfile.getPlayerUuid(),
                    reclaimedUuid, issuedUuid);
        } catch (Throwable throwable) {
            if (issuedUuid.equals(ItemUtil.getUUID(persistedTargetContents[issuedSlot]))) {
                persistedTargetContents[issuedSlot] = null;
                targetInv.setContents(persistedTargetContents);
            }
            reclaimedRef.set(reclaimed);
            clearPending("rollback after offline apply failure");
            try {
                sourceProfile.saveData(null);
                if (mailChanged) sourceProfile.getMailData().save();
                targetProfile.saveData(null);
            } catch (Throwable rollbackFailure) {
                throwable.addSuppressed(rollbackFailure);
            }
            throw throwable;
        }
    }

    private PlayerProfile loadOfflineProfile(String playerName) {
        PlayerProfile profile = PlayerProfile.getOrLoadPlayerProfileByName(playerName);
        if (profile != null) PlayerProfile.loadMail(profile, profile.getPlayerUuid());
        return profile;
    }

    private void verifyOfflinePersisted(CommandSender sender, UUID sourceUuid, UUID targetUuid,
                                        String reclaimedUuid, String issuedUuid) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            PlayerProfile persistedSource = PlayerProfile.loadPlayerProfileByUuid(sourceUuid);
            PlayerProfile persistedTarget = PlayerProfile.loadPlayerProfileByUuid(targetUuid);
            Bukkit.getScheduler().runTask(this, () -> {
                int sourceCopies = persistedSource == null ? -1
                        : findByUuidOffline(persistedSource, reclaimedUuid).size();
                int targetCopies = persistedTarget == null ? -1
                        : findByUuidOffline(persistedTarget, issuedUuid).size();
                boolean verified = sourceCopies == 0 && targetCopies == 1 && isArmed()
                        && issuedUuid.equals(getConfig().getString("pending.item-uuid"));
                getLogger().info("DJ-COMP offline persistence verification sourceItemCopies=" + sourceCopies
                        + " targetItemCopies=" + targetCopies + " armed=" + isArmed()
                        + " issuedItemUuid=" + issuedUuid + " verified=" + verified);
                message(sender, verified
                        ? "&a数据库读回核验通过：原裤子 0 份，新裤子 1 份，UUID 绑定已持久化。"
                        : "&c数据库读回核验未通过：sourceCopies=" + sourceCopies
                        + " targetCopies=" + targetCopies + " armed=" + isArmed());
            });
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPitEnchant(PitPlayerEnchantEvent event) {
        if (!isArmed()) return;
        String targetUuid = getConfig().getString("pending.target-uuid");
        String itemUuid = getConfig().getString("pending.item-uuid");
        if (targetUuid == null || itemUuid == null
                || !event.getPlayer().getUniqueId().toString().equals(targetUuid)) return;

        IMythicItem item = event.getAfterItem();
        if (!(item instanceof MythicLeggingsItem) || item.uuid == null
                || !itemUuid.equals(item.uuid.toString())) return;

        Map<String, AbstractEnchantment> registry = ThePit.getInstance()
                .getEnchantmentFactor().getEnchantmentMap();
        AbstractEnchantment swift = registry.get(SWIFT);
        AbstractEnchantment newYear = registry.get(NEW_YEAR);
        AbstractEnchantment djSix = registry.get(DJ_SIX);
        AbstractEnchantment crit = registry.get(CRIT);
        if (swift == null || newYear == null || djSix == null || crit == null) {
            getLogger().severe("DJ-COMP not consumed: one or more required enchantments are unavailable");
            event.getPlayer().sendMessage(color("&c指定附魔暂未生效，请保留裤子并联系管理员。"));
            return;
        }

        LinkedHashMap<AbstractEnchantment, Integer> exact = new LinkedHashMap<>();
        exact.put(swift, 3);
        exact.put(newYear, 3);
        exact.put(djSix, 1);
        exact.put(crit, 3);
        item.setEnchantments(exact);
        item.setColor(MythicColor.RED);
        item.setTier(3);
        item.setMaxLive(40);
        item.setLive(40);
        item.setPrefix(null);
        item.setDyeColor(null);
        item.setBoostedByGem(false);
        item.setBoostedByGlobalGem(false);
        item.boostedByBook = false;
        item.setHiddenJewelKills(0);
        item.setHeartripperShards(0);
        item.getEnchantmentRecords().add(new EnchantmentRecord(
                event.getPlayer().getName(), "管理员 DJ 裤补偿", System.currentTimeMillis()));

        clearPending("consumed by enchant event for " + event.getPlayer().getName());
        getLogger().info("DJ-COMP consumed target=" + event.getPlayer().getName()
                + " targetUuid=" + event.getPlayer().getUniqueId()
                + " itemUuid=" + itemUuid + " result=RED T3 40/40 swift3,newyear3,dj6,crit3");
        event.getPlayer().sendMessage(color("&a补偿附魔已生效：疾走 III、新年快乐 III、Genesis! DJ #6、致命节奏 III（40/40）。"));

        Bukkit.getScheduler().runTask(this, () -> verifyConsumedResult(event.getPlayer(), itemUuid));
    }

    private void verifyConsumedResult(Player player, String expectedUuid) {
        try {
            PlayerProfile profile = loadedProfile(player);
            ItemStack wellItem = profile == null ? null
                    : InventoryUtil.deserializeItemStack(profile.getEnchantingItem());
            boolean verified = expectedUuid.equals(ItemUtil.getUUID(wellItem)) && isPicturePants(wellItem)
                    && Utils.getMythicItem(wellItem).getLive() == 40
                    && Utils.getMythicItem(wellItem).getMaxLive() == 40;
            getLogger().info("DJ-COMP post-event verification target=" + player.getName()
                    + " itemUuid=" + expectedUuid + " verified=" + verified);
        } catch (Throwable throwable) {
            getLogger().severe("DJ-COMP post-event verification failed: " + throwable.getMessage());
        }
    }

    private void statusCommand(CommandSender sender) {
        if (!isArmed()) {
            message(sender, "&7当前没有待执行的指定附魔。");
            return;
        }
        message(sender, "&a待执行 &7target=" + getConfig().getString("pending.target-name")
                + " uuid=" + getConfig().getString("pending.target-uuid")
                + " item=" + getConfig().getString("pending.item-uuid")
                + " source=" + getConfig().getString("pending.source-name")
                + " reclaimed=" + getConfig().getString("pending.reclaimed-item-uuid"));
    }

    private void verifyCommand(CommandSender sender) {
        if (!isArmed()) {
            message(sender, "&7没有待验证的补偿（可能尚未设置，或已经成功消耗）。");
            return;
        }
        String targetUuid = getConfig().getString("pending.target-uuid");
        String itemUuid = getConfig().getString("pending.item-uuid");
        Player target = targetUuid == null ? null : Bukkit.getPlayer(UUID.fromString(targetUuid));
        if (target == null || !target.isOnline()) {
            message(sender, "&e待执行状态已持久化，但接收玩家当前不在线。item=" + itemUuid);
            return;
        }
        PlayerProfile profile = loadedProfile(target);
        List<ItemRef> matches = profile == null ? new ArrayList<>() : findByUuid(target, profile, itemUuid);
        message(sender, "&7待执行状态已持久化；接收玩家可访问存储中找到新裤子 &a" + matches.size()
                + " &7份，位置=" + locations(matches) + "，item=" + itemUuid);
    }

    private void arm(Player target, String itemUuid, Player source, String reclaimedUuid, String location) {
        getConfig().set("pending.armed", true);
        getConfig().set("pending.target-name", target.getName());
        getConfig().set("pending.target-uuid", target.getUniqueId().toString());
        getConfig().set("pending.item-uuid", itemUuid);
        getConfig().set("pending.source-name", source.getName());
        getConfig().set("pending.source-uuid", source.getUniqueId().toString());
        getConfig().set("pending.reclaimed-item-uuid", reclaimedUuid);
        getConfig().set("pending.reclaimed-location", location);
        getConfig().set("pending.armed-at", System.currentTimeMillis());
        saveConfig();
    }

    private void armOffline(PlayerProfile target, String itemUuid, PlayerProfile source,
                            String reclaimedUuid, String location) {
        getConfig().set("pending.armed", true);
        getConfig().set("pending.target-name", target.getPlayerName());
        getConfig().set("pending.target-uuid", target.getPlayerUuid().toString());
        getConfig().set("pending.item-uuid", itemUuid);
        getConfig().set("pending.source-name", source.getPlayerName());
        getConfig().set("pending.source-uuid", source.getPlayerUuid().toString());
        getConfig().set("pending.reclaimed-item-uuid", reclaimedUuid);
        getConfig().set("pending.reclaimed-location", location);
        getConfig().set("pending.armed-at", System.currentTimeMillis());
        saveConfig();
    }

    private void clearPending(String reason) {
        getConfig().set("pending.armed", false);
        getConfig().set("pending.cleared-at", System.currentTimeMillis());
        getConfig().set("pending.clear-reason", reason);
        saveConfig();
    }

    private boolean isArmed() {
        return getConfig().getBoolean("pending.armed", false);
    }

    private PlayerProfile loadedProfile(Player player) {
        PlayerProfile profile = PlayerProfile.getCacheProfile().get(player.getUniqueId());
        return profile != null && profile.isLoaded() ? profile : null;
    }

    private List<ItemRef> findDjItems(Player player, PlayerProfile profile) {
        List<ItemRef> result = new ArrayList<>();
        for (ItemRef ref : allOwnedItems(player, profile)) {
            if (isDjPants(ref.get())) result.add(ref);
        }
        return result;
    }

    private List<ItemRef> findByUuid(Player player, PlayerProfile profile, String uuid) {
        List<ItemRef> result = new ArrayList<>();
        if (uuid == null) return result;
        for (ItemRef ref : allOwnedItems(player, profile)) {
            if (uuid.equals(ItemUtil.getUUID(ref.get()))) result.add(ref);
        }
        return result;
    }

    private List<ItemRef> findDjItemsOffline(PlayerProfile profile) {
        List<ItemRef> result = new ArrayList<>();
        for (ItemRef ref : allStoredItems(profile)) {
            if (isDjPants(ref.get())) result.add(ref);
        }
        return result;
    }

    private List<ItemRef> findByUuidOffline(PlayerProfile profile, String uuid) {
        List<ItemRef> result = new ArrayList<>();
        if (uuid == null) return result;
        for (ItemRef ref : allStoredItems(profile)) {
            if (uuid.equals(ItemUtil.getUUID(ref.get()))) result.add(ref);
        }
        return result;
    }

    private List<ItemRef> allStoredItems(PlayerProfile profile) {
        List<ItemRef> refs = new ArrayList<>();
        PlayerInv savedInventory = profile.getInventory();
        if (savedInventory != null) {
            ItemStack[] contents = savedInventory.getContents();
            if (contents != null) {
                for (int slot = 0; slot < contents.length; slot++) {
                    final int index = slot;
                    add(refs, "离线背包槽位 " + slot,
                            () -> savedInventory.getContents()[index],
                            item -> {
                                ItemStack[] current = savedInventory.getContents();
                                current[index] = item;
                                savedInventory.setContents(current);
                            }, false);
                }
            }
            ItemStack[] armor = savedInventory.getArmorContents();
            if (armor != null) {
                for (int slot = 0; slot < armor.length; slot++) {
                    final int index = slot;
                    add(refs, "离线装备槽位 " + slot,
                            () -> savedInventory.getArmorContents()[index],
                            item -> {
                                ItemStack[] current = savedInventory.getArmorContents();
                                current[index] = item;
                                savedInventory.setArmorContents(current);
                            }, false);
                }
            }
        }

        if (profile.getEnderChest() != null) {
            Inventory ender = profile.getEnderChest().getInventory();
            for (int slot = 0; slot < ender.getSize(); slot++) {
                final int index = slot;
                add(refs, "末影箱槽位 " + slot, () -> ender.getItem(index), item -> ender.setItem(index, item), false);
            }
        }

        List<String> pages = profile.getStash().getPages();
        for (int page = 0; page < pages.size(); page++) {
            final int pageIndex = page;
            ItemStack[] items = InventoryUtil.stringToItems(pages.get(page));
            if (items == null) items = new ItemStack[0];
            final ItemStack[] pageItems = items;
            for (int slot = 0; slot < pageItems.length; slot++) {
                final int index = slot;
                add(refs, "寄存处 " + (page + 1) + " 页槽位 " + slot,
                        () -> pageItems[index],
                        item -> {
                            pageItems[index] = item;
                            pages.set(pageIndex, InventoryUtil.itemsToString(pageItems));
                        }, false);
            }
        }

        add(refs, "神话之井输入槽",
                () -> InventoryUtil.deserializeItemStack(profile.getEnchantingItem()),
                item -> profile.setEnchantingItem(item == null ? null : InventoryUtil.serializeItemStack(item)), false);

        OfferData offer = profile.getOfferData();
        if (offer != null && (offer.hasActiveOffer() || offer.hasUnclaimedOffer())) {
            add(refs, "交易报价", offer::getItemStack,
                    item -> profile.setOfferData(item == null ? new OfferData() : offer), false);
        }

        PlayerMailData mailData = profile.getMailData();
        if (mailData != null && mailData.getMails() != null) {
            for (Mail mail : mailData.getMails()) {
                if (mail == null || mail.isClaimed() || mail.getItem() == null
                        || mail.getItem().getContents() == null) continue;
                PlayerInv attachments = mail.getItem();
                ItemStack[] contents = attachments.getContents();
                for (int slot = 0; slot < contents.length; slot++) {
                    final int index = slot;
                    add(refs, "未领取邮件 " + mail.getUuid() + " 槽位 " + slot,
                            () -> attachments.getContents()[index],
                            item -> {
                                ItemStack[] current = attachments.getContents();
                                current[index] = item;
                                attachments.setContents(current);
                            }, true);
                }
            }
        }
        return refs;
    }

    private List<ItemRef> allOwnedItems(Player player, PlayerProfile profile) {
        List<ItemRef> refs = new ArrayList<>();
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            final int index = slot;
            add(refs, "背包槽位 " + slot, () -> inventory.getItem(index), item -> inventory.setItem(index, item), false);
        }
        ItemStack[] armor = inventory.getArmorContents();
        for (int slot = 0; slot < armor.length; slot++) {
            final int index = slot;
            add(refs, "装备槽位 " + slot,
                    () -> inventory.getArmorContents()[index],
                    item -> {
                        ItemStack[] current = inventory.getArmorContents();
                        current[index] = item;
                        inventory.setArmorContents(current);
                    }, false);
        }
        add(refs, "鼠标指针", player::getItemOnCursor, player::setItemOnCursor, false);

        if (profile.getEnderChest() != null) {
            Inventory ender = profile.getEnderChest().getInventory();
            for (int slot = 0; slot < ender.getSize(); slot++) {
                final int index = slot;
                add(refs, "末影箱槽位 " + slot, () -> ender.getItem(index), item -> ender.setItem(index, item), false);
            }
        }

        List<String> pages = profile.getStash().getPages();
        for (int page = 0; page < pages.size(); page++) {
            final int pageIndex = page;
            ItemStack[] items = InventoryUtil.stringToItems(pages.get(page));
            if (items == null) items = new ItemStack[0];
            final ItemStack[] pageItems = items;
            for (int slot = 0; slot < pageItems.length; slot++) {
                final int index = slot;
                add(refs, "寄存处 " + (page + 1) + " 页槽位 " + slot,
                        () -> pageItems[index],
                        item -> {
                            pageItems[index] = item;
                            pages.set(pageIndex, InventoryUtil.itemsToString(pageItems));
                        }, false);
            }
        }

        add(refs, "神话之井输入槽",
                () -> InventoryUtil.deserializeItemStack(profile.getEnchantingItem()),
                item -> profile.setEnchantingItem(item == null ? null : InventoryUtil.serializeItemStack(item)), false);

        OfferData offer = profile.getOfferData();
        if (offer != null && (offer.hasActiveOffer() || offer.hasUnclaimedOffer())) {
            add(refs, "交易报价",
                    offer::getItemStack,
                    item -> profile.setOfferData(item == null ? new OfferData() : offer), false);
        }

        PlayerMailData mailData = profile.getMailData();
        if (mailData != null && mailData.getMails() != null) {
            for (Mail mail : mailData.getMails()) {
                if (mail == null || mail.isClaimed() || mail.getItem() == null
                        || mail.getItem().getContents() == null) continue;
                PlayerInv attachments = mail.getItem();
                ItemStack[] contents = attachments.getContents();
                for (int slot = 0; slot < contents.length; slot++) {
                    final int index = slot;
                    add(refs, "未领取邮件 " + mail.getUuid() + " 槽位 " + slot,
                            () -> attachments.getContents()[index],
                            item -> {
                                ItemStack[] current = attachments.getContents();
                                current[index] = item;
                                attachments.setContents(current);
                            }, true);
                }
            }
        }
        return refs;
    }

    private void add(List<ItemRef> refs, String location, Supplier<ItemStack> getter,
                     Consumer<ItemStack> setter, boolean mail) {
        ItemStack item = getter.get();
        if (item != null && item.getType() != Material.AIR) {
            refs.add(new ItemRef(location, getter, setter, mail));
        }
    }

    private boolean isDjPants(ItemStack item) {
        return item != null && item.getType() != Material.AIR
                && INTERNAL.equals(ItemUtil.getInternalName(item))
                && Utils.getEnchantLevel(item, DJ_SIX) == 1;
    }

    private boolean isPicturePants(ItemStack item) {
        if (!isDjPants(item)
                || Utils.getEnchantLevel(item, SWIFT) != 3
                || Utils.getEnchantLevel(item, NEW_YEAR) != 3
                || Utils.getEnchantLevel(item, CRIT) != 3) return false;
        IMythicItem mythic = Utils.getMythicItem(item);
        return mythic != null && mythic.getTier() == 3
                && (mythic.getColor() == MythicColor.RED || mythic.getDyeColor() == DyeColor.RED)
                && mythic.getEnchantments().size() == 4;
    }

    private boolean isRetunedPants(ItemStack item) {
        if (item == null || item.getType() == Material.AIR
                || !INTERNAL.equals(ItemUtil.getInternalName(item))
                || Utils.getEnchantLevel(item, SWIFT) > 0
                || Utils.getEnchantLevel(item, PEROXIDE) != 3
                || Utils.getEnchantLevel(item, NEW_YEAR) != 3
                || Utils.getEnchantLevel(item, DJ_SIX) > 0
                || Utils.getEnchantLevel(item, DJ_TWO) != 1
                || Utils.getEnchantLevel(item, CRIT) != 3) return false;
        IMythicItem mythic = Utils.getMythicItem(item);
        return mythic != null && mythic.getTier() == 3
                && mythic.getLive() == 40 && mythic.getMaxLive() == 40
                && (mythic.getColor() == MythicColor.RED || mythic.getDyeColor() == DyeColor.RED)
                && mythic.getEnchantments().size() == 4;
    }

    private String describe(ItemStack item) {
        IMythicItem mythic = Utils.getMythicItem(item);
        return "uuid=" + ItemUtil.getUUID(item)
                + " color=" + (mythic == null ? "?" : mythic.getColor())
                + " tier=" + (mythic == null ? "?" : mythic.getTier())
                + " lives=" + (mythic == null ? "?" : mythic.getLive() + "/" + mythic.getMaxLive())
                + " ench=[swift:" + Utils.getEnchantLevel(item, SWIFT)
                + ",peroxide:" + Utils.getEnchantLevel(item, PEROXIDE)
                + ",newyear:" + Utils.getEnchantLevel(item, NEW_YEAR)
                + ",dj2:" + Utils.getEnchantLevel(item, DJ_TWO)
                + ",dj6:" + Utils.getEnchantLevel(item, DJ_SIX)
                + ",crit:" + Utils.getEnchantLevel(item, CRIT) + "]"
                + " total=" + (mythic == null ? "?" : mythic.getEnchantments().size());
    }

    private void removeByUuid(PlayerInventory inventory, String uuid) {
        if (uuid == null) return;
        for (int slot = 0; slot < 36; slot++) {
            if (uuid.equals(ItemUtil.getUUID(inventory.getItem(slot)))) {
                inventory.setItem(slot, null);
                return;
            }
        }
    }

    private String locations(List<ItemRef> refs) {
        if (refs.isEmpty()) return "[]";
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < refs.size(); i++) {
            if (i > 0) builder.append(", ");
            builder.append(refs.get(i).location);
        }
        return builder.append(']').toString();
    }

    private void message(CommandSender sender, String text) {
        sender.sendMessage(color("&8[DJ补偿] &r" + text));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private static final class ItemRef {
        private final String location;
        private final Supplier<ItemStack> getter;
        private final Consumer<ItemStack> setter;
        private final boolean mail;

        private ItemRef(String location, Supplier<ItemStack> getter,
                        Consumer<ItemStack> setter, boolean mail) {
            this.location = location;
            this.getter = getter;
            this.setter = setter;
            this.mail = mail;
        }

        private ItemStack get() {
            return getter.get();
        }

        private void set(ItemStack item) {
            setter.accept(item);
        }
    }

    private static final class TraceData {
        private final PlayerProfile profile;
        private final List<PlayerInvBackup> backups;
        private final List<TradeData> trades;

        private TraceData(PlayerProfile profile, List<PlayerInvBackup> backups,
                          List<TradeData> trades) {
            this.profile = profile;
            this.backups = backups;
            this.trades = trades;
        }
    }

    private static final class HistoricalHit {
        private final String itemUuid;
        private final long latestBackupTime;
        private final String location;
        private final String description;

        private HistoricalHit(String itemUuid, long latestBackupTime,
                              String location, String description) {
            this.itemUuid = itemUuid;
            this.latestBackupTime = latestBackupTime;
            this.location = location;
            this.description = description;
        }
    }

    private static final class TransferHit {
        private final long time;
        private final String fromUuid;
        private final String fromName;
        private final String toUuid;
        private final String toName;
        private final String tradeUuid;
        private final String itemUuid;

        private TransferHit(long time, String fromUuid, String fromName,
                            String toUuid, String toName, String tradeUuid, String itemUuid) {
            this.time = time;
            this.fromUuid = fromUuid;
            this.fromName = fromName;
            this.toUuid = toUuid;
            this.toName = toName;
            this.tradeUuid = tradeUuid;
            this.itemUuid = itemUuid;
        }
    }
}
