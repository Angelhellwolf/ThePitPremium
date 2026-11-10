package cn.charlotte.pit.bot;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.listener.CombatListener;
import cn.charlotte.pit.util.combat.CombatDamage;
import cn.charlotte.pit.util.inventory.InventoryUtil;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.MemoryNPCDataStore;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.api.event.NPCDeathEvent;
import net.citizensnpcs.api.event.NPCDamageEntityEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.util.NMS;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityDestroy;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityEquipment;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Chunk;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;

/** Three durable identities, one main-thread owner, no public summons or calendar population windows. */
public final class PitBotManager implements Listener {
    private static final String REGISTRY = "thepit-public-simulated";
    private static final String KEY = "thepit-public-simulated-player";
    private static final String LEGACY_KEY = "thepit-arena-bot";
    private static final int LIMIT = 3;
    private final ThePit plugin;
    private final Random random = new Random();
    private final List<PublicPlayerState> players = new ArrayList<>(LIMIT);
    private final Map<UUID, PublicPlayerState> identities = new HashMap<>();
    private final Queue<Runnable> completions = new ConcurrentLinkedQueue<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "pit-public-identities");
        thread.setDaemon(true);
        return thread;
    });
    private NPCRegistry registry;
    private PublicPitArena arena;
    private PublicPlayerCombat combat;
    private List<BotSkins> skins = Collections.emptyList();
    private PublicPlayerIdentityStore identityStore;
    private PublicPlayerProgressStore progressStore;
    private PublicPlayerProgression progression;
    private List<String> preferredNames;
    private BukkitTask task;
    private long tick, nextSpawnTick, nextIdentityRetry, tickNanos, peakNanos, samples;
    private int respawnDelay = 30;
    private boolean started, ready, resolving, rosterDirty;
    private JunxuTrainingBots junxuBots;
    private final PublicNpcNametags publicNametags=new PublicNpcNametags();
    private final PublicCombatDifficulty difficulty=new PublicCombatDifficulty();

    public void setJunxuBots(JunxuTrainingBots bots) { this.junxuBots = bots; }

    public PitBotManager(ThePit plugin) { this.plugin = plugin; }

    public void start() {
        if (started) return;
        if (!isPublicEnabled()) {
            if (Bukkit.getPluginManager().isPluginEnabled("Citizens")) cleanupOwnedNpcs();
            plugin.getLogger().info("PUBLIC_PLAYERS enabled=false spawned=0 privateTraining=independent");
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            plugin.getLogger().severe("[公共模拟玩家] Citizens 未启用。");
            return;
        }
        try {
            arena = new PublicPitArena(plugin.getConfig(), plugin.getPitConfig());
            preferredNames = new ArrayList<>();
            for (Map<?, ?> profile : plugin.getConfig().getMapList("public-simulated-players.profiles")) {
                preferredNames.add(Objects.toString(profile.get("name"), ""));
            }
            respawnDelay = Math.max(20, plugin.getConfig().getInt("public-simulated-players.respawn-delay-ticks", 30));
            combat = new PublicPlayerCombat(arena, random);
            cleanupOwnedNpcs();
            NPCRegistry old = CitizensAPI.getNamedNPCRegistry(REGISTRY);
            if (old != null) { old.deregisterAll(); CitizensAPI.removeNamedNPCRegistry(REGISTRY); }
            registry = CitizensAPI.createNamedNPCRegistry(REGISTRY, new MemoryNPCDataStore());
            identityStore = new PublicPlayerIdentityStore(plugin.getDataFolder().toPath().resolve("public-player-identities.dat"));
            progressStore = new PublicPlayerProgressStore(plugin.getDataFolder().toPath().resolve("public-player-progress.json"));
            progression = new PublicPlayerProgression(plugin);
            started = true;
            Bukkit.getPluginManager().registerEvents(this, plugin);
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 1, 1);
            resolveIdentities();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "[公共模拟玩家] 配置无效，未创建任何模拟玩家。", exception);
        }
    }

    private void resolveIdentities() {
        if (!started || resolving) return;
        resolving = true;
        Map<String, UUID> online = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) if (!player.hasMetadata("NPC")) online.put(player.getName(), player.getUniqueId());
        io.execute(() -> {
            try {
                List<PublicPlayerIdentityStore.Identity> resolved = identityStore.resolve(preferredNames, new PublicPlayerRecords(plugin, online));
                Map<UUID, PublicPlayerProgressStore.Progress> saved = progressStore.load();
                Path skinFile = plugin.getDataFolder().toPath().resolve("public-player-skins.properties");
                if (Files.notExists(skinFile)) try (InputStream source = plugin.getResource("public-player-skins.properties")) {
                    if (source == null) throw new IllegalStateException("Missing public player skin resource");
                    Files.copy(source, skinFile);
                }
                List<BotSkins> loadedSkins = BotSkins.load(Files.newInputStream(skinFile));
                completions.add(() -> {
                    resolving = false;
                    if (!started) return;
                    skins = loadedSkins;
                    for (PublicPlayerIdentityStore.Identity identity : resolved) {
                        PublicPlayerState previous = identities.get(identity.uuid);
                        if (previous != null && previous.identity.equals(identity)) continue;
                        for (PublicPlayerState existing : new ArrayList<>(players)) if (existing.identity.slot == identity.slot) {
                            retire(existing, true); players.remove(existing); identities.remove(existing.identity.uuid); clearProfile(existing.identity.uuid);
                        }
                        PublicPlayerState state = new PublicPlayerState(identity);
                        state.profile = previous == null ? new PlayerProfile(identity.uuid, identity.name) : previous.profile;
                        if (previous == null && saved.containsKey(identity.uuid)) saved.get(identity.uuid).apply(state.profile);
                        state.profile.setPlayerName(identity.name);
                        state.profile.setBot(true); state.profile.setLoaded(true);
                        state.profile.getPlayerOption().setOutfit(true);
                        BotProfileSetup.initializeMedals(plugin, state.profile);
                        state.profile.init();
                        state.dueTick = tick + identity.slot * 5L;
                        players.add(state); identities.put(identity.uuid, state);
                        plugin.getLogger().info("PUBLIC_IDENTITY slot=" + identity.slot + " name=" + identity.name + " uuid=" + identity.uuid + " recordsChecked=true");
                    }
                    players.sort(Comparator.comparingInt(state -> state.identity.slot));
                    ready = players.size() == LIMIT;
                    rosterDirty = true;
                });
            } catch (Exception exception) {
                completions.add(() -> { resolving = false; nextIdentityRetry = tick + 600; });
                plugin.getLogger().log(Level.SEVERE, "[公共模拟玩家] 身份或皮肤载入失败，保留已有身份记录。", exception);
            }
        });
    }

    public void shutdown() {
        saveProgress();
        started = false; ready = false;
        if (task != null) task.cancel();
        for (PublicPlayerState state : players) {
            retire(state, true);
            clearProfile(state.identity.uuid);
        }
        players.clear(); identities.clear();
        if (registry != null) { registry.deregisterAll(); CitizensAPI.removeNamedNPCRegistry(REGISTRY); }
        io.shutdown();
        try { io.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        completions.clear();
    }

    public boolean isBot(Entity entity) { return entity != null && (identities.containsKey(entity.getUniqueId()) || junxuBots != null && junxuBots.isBot(entity)); }
    public boolean isReady() { return started && ready; }
    public boolean isPublicEnabled() { return plugin.getConfig().getBoolean("public-simulated-players.enabled", false); }

    public boolean ownsProfile(PlayerProfile profile) {
        if (profile == null) return false;
        PublicPlayerState state = identities.get(profile.getPlayerUuid());
        return state != null && state.profile == profile || junxuBots != null && junxuBots.ownsProfile(profile);
    }

    public Player getPlayer(UUID id) {
        PublicPlayerState state = identities.get(id);
        return state == null ? (junxuBots == null ? null : junxuBots.getPlayer(id)) : state.entity;
    }

    public List<Player> getActivePlayers() {
        List<Player> result = new ArrayList<>(LIMIT);
        for (PublicPlayerState state : players) if (state.phase != PublicPlayerState.Phase.RESPAWNING
                && state.entity != null && state.entity.isValid()) result.add(state.entity);
        if (junxuBots != null) result.addAll(junxuBots.activePlayers());
        return result;
    }

    private void saveProgress() {
        if (!ready || progressStore == null) return;
        List<PublicPlayerProgressStore.Progress> snapshot = new ArrayList<>(LIMIT);
        for (PublicPlayerState state : players) snapshot.add(PublicPlayerProgressStore.Progress.capture(state.profile));
        io.execute(() -> {
            try { progressStore.save(snapshot); }
            catch (Exception failure) { plugin.getLogger().log(Level.SEVERE, "[公共模拟玩家] 成长存档写入失败，下次保存会重试。", failure); }
        });
    }

    private void tickAll() {
        long begin = System.nanoTime();
        try {
            if (!started) return;
            tick++;
            Runnable completion;
            while ((completion = completions.poll()) != null) completion.run();
            if (!ready) { if (!resolving && tick >= nextIdentityRetry) resolveIdentities(); return; }
            if (tick % 20 == 0) { reconcile(); saveProgress(); }
            for (PublicPlayerState state : players) {
                try { tickPlayer(state); }
                catch (RuntimeException exception) {
                    plugin.getLogger().log(Level.WARNING, "[公共模拟玩家] 恢复 " + state.identity.name, exception);
                    recover(state, "exception");
                    state.dueTick = tick + 40;
                }
            }
            if (rosterDirty || tick % 20 == 0) { updatePresentation(); rosterDirty = false; }
        } finally {
            long elapsed = System.nanoTime() - begin;
            tickNanos += elapsed; peakNanos = Math.max(peakNanos, elapsed); samples++;
        }
    }

    private void tickPlayer(PublicPlayerState state) {
        if (state.phase == PublicPlayerState.Phase.RESPAWNING) {
            if (state.npc != null) retire(state, false);
            if (tick >= state.dueTick && tick >= nextSpawnTick && !resolving) {
                nextSpawnTick = tick + 5;
                if (!spawn(state)) state.dueTick = tick + 40;
            }
            return;
        }
        if (state.entity != null && (state.entity.isDead() || state.entity.getHealth() <= 0)) {
            handleDeath(state.entity, state.entity.getKiller()); return;
        }
        if (state.npc == null || !state.npc.isSpawned() || state.entity == null || !state.entity.isValid()) {
            recover(state, "entity-lost"); return;
        }
        if (state.entity != state.npc.getEntity()) { recover(state, "entity-replaced"); return; }
        Player player = state.entity;
        Location here = player.getLocation();
        if (arena.outside(here)) { recover(state, "outside-map"); return; }
        boolean inArena = arena.combat(here);
        PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(state.identity.uuid);
        profile.setInArena(inArena);
        GameMode mode = inArena ? GameMode.SURVIVAL : GameMode.ADVENTURE;
        if (player.getGameMode() != mode) player.setGameMode(mode);

        if (state.phase == PublicPlayerState.Phase.WAITING) {
            if (tick >= state.dueTick) changePhase(state, PublicPlayerState.Phase.ENTERING, "DEPART");
            return;
        }
        if (state.phase == PublicPlayerState.Phase.ENTERING) {
            List<Location> route = arena.routes.get(state.identity.slot);
            if (!arena.safeZone(here) && here.getY() < arena.jumpCenter.getY() - 1) {
                changePhase(state, PublicPlayerState.Phase.DROPPING, "JUMP"); return;
            }
            Location point = route.get(state.routeIndex);
            PublicPlayerCombat.look(player, point.clone().add(0, 1.6, 0), 14);
            if (horizontalDistance(here, point) < 0.85) {
                PublicPlayerCombat.cancel(state);
                if (++state.routeIndex == route.size()) {
                    Vector direction = arena.jumpCenter.toVector().subtract(here.toVector()).setY(0).normalize();
                    Vector velocity = direction.multiply(PublicPlayerCombat.speed(player, true));
                    velocity.setY(0.42);
                    player.setSprinting(true); player.setVelocity(velocity);
                    changePhase(state, PublicPlayerState.Phase.DROPPING, "JUMP");
                }
            } else combat.navigate(state, point, tick);
            checkProgress(state, here, 200);
            if (tick - state.phaseTick > 600) recover(state, "departure-timeout");
            return;
        }
        if (state.phase == PublicPlayerState.Phase.DROPPING) {
            PublicPlayerCombat.cancel(state);
            if (inArena && here.getY() <= arena.entryY && player.isOnGround()) {
                state.enteredPit(tick); state.lastProgressTick = tick;
                trace(state, "ENTER_PIT");
                return;
            }
            Vector toward = arena.jumpCenter.toVector().subtract(here.toVector()).setY(0);
            if (toward.lengthSquared() > 0.1) combat.move(player, toward.normalize(), true, true);
            PublicPlayerCombat.look(player, arena.jumpCenter.clone().add(0, -20, 0), 14);
            if (tick - state.phaseTick > 160) recover(state, "jump-timeout");
            return;
        }
        if (!inArena) { recover(state, "left-combat-area"); return; }
        Player target = state.target == null ? null : Bukkit.getPlayer(state.target);
        if (!combat.targetable(player, target) || (target != null && here.distanceSquared(target.getLocation()) > 40 * 40)) {
            target = null; state.target = null;
        }
        if (target == null && tick >= state.nextTargetTick) {
            state.nextTargetTick = tick + 10 + state.identity.slot * 3L;
            target = combat.findTarget(state, Bukkit.getOnlinePlayers());
            if (target != null) { state.target = target.getUniqueId(); trace(state, "TARGET"); }
        }
        if (target != null) {
            if(!target.getUniqueId().equals(state.difficultyTarget)){
                state.difficultyTarget=target.getUniqueId();state.difficulty=difficulty.forPlayer(PlayerProfile.getPlayerProfileByUuid(target.getUniqueId()),System.currentTimeMillis());
                state.nextAttackTick=Math.max(state.nextAttackTick,tick+state.difficulty.reaction);state.nextDifficultyTick=tick+60;
            }else if(tick>=state.nextDifficultyTick){state.difficulty=difficulty.forPlayer(PlayerProfile.getPlayerProfileByUuid(target.getUniqueId()),System.currentTimeMillis());state.nextDifficultyTick=tick+60;}
            combat.fight(state, target, tick,state.difficulty);
            if (here.distanceSquared(target.getLocation()) < 16) state.lastProgressTick = tick;
            else checkProgress(state, here, 200);
        } else {
            if (state.patrolGoal == null || tick >= state.nextPatrolTick || horizontalDistance(here, state.patrolGoal) < 1.1) {
                state.patrolGoal = arena.patrol(state.identity.slot, tick, random);
                state.nextPatrolTick = tick + 120 + random.nextInt(100);
                PublicPlayerCombat.cancel(state);
            }
            if (!PublicPlayerCombat.recoveringKnockback(state,tick)) {
                combat.navigate(state, state.patrolGoal, tick);
                if (state.patrolGoal != null) PublicPlayerCombat.look(player, state.patrolGoal.clone().add(0, 1.6, 0), 8);
            } else PublicPlayerCombat.cancel(state);
            if (state.patrolGoal != null) checkProgress(state, here, 240);
        }
    }

    private void checkProgress(PublicPlayerState state, Location here, int timeout) {
        if (state.progressLocation == null || state.progressLocation.getWorld() != here.getWorld()
                || state.progressLocation.distanceSquared(here) > 0.36) {
            state.progressLocation = here.clone(); state.lastProgressTick = tick;
        } else if (tick - state.lastProgressTick > timeout) recover(state, "stuck");
    }
    private static double horizontalDistance(Location a, Location b) { return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ()); }

    boolean spawn(PublicPlayerState state) {
        if (!started || countOwnedSpawned() >= LIMIT) return false;
        Location location = arena.spawns.get(state.identity.slot).clone();
        if (!arena.standable(location)) return false;
        Player collision = Bukkit.getPlayerExact(state.identity.name);
        if (collision != null && !collision.hasMetadata("NPC")) { resolveIdentities(); return false; }
        NPC existing = registry.getByUniqueId(state.identity.uuid);
        if (existing != null) removeNpc(existing, true);
        NPC npc = registry.createNPC(EntityType.PLAYER, state.identity.uuid, state.identity.slot, state.identity.name);
        try {
            npc.data().setPersistent(KEY, state.identity.slot);
            npc.data().setPersistent(NPC.Metadata.SHOULD_SAVE, false);
            npc.data().setPersistent(NPC.Metadata.RESPAWN_DELAY, -1);
            npc.data().setPersistent(NPC.Metadata.REMOVE_FROM_PLAYERLIST, false);
            npc.data().setPersistent(NPC.Metadata.REMOVE_FROM_TABLIST, false);
            npc.setAlwaysUseNameHologram(false);
            npc.setUseMinecraftAI(false); npc.setFlyable(false);
            enableDamage(npc);
            skins.get(arena.skinIndexes[state.identity.slot] % skins.size()).apply(npc);
            if (!npc.spawn(location) || !(npc.getEntity() instanceof Player)) { removeNpc(npc, true); return false; }
            Player entity = (Player) npc.getEntity();
            initializePlayer(state, npc, entity);
            state.born(npc, entity, tick, arena.waits[state.identity.slot] + random.nextInt(16));
            rosterDirty = true;
            trace(state, "SPAWN");
            return true;
        } catch (RuntimeException exception) { removeNpc(npc, true); throw exception; }
    }

    private void initializePlayer(PublicPlayerState state, NPC npc, Player entity) {
        if (!entity.getUniqueId().equals(state.identity.uuid)) throw new IllegalStateException("NPC did not preserve its reserved UUID");
        if (CombatListener.INSTANCE != null) CombatListener.INSTANCE.forgetCombatState(state.identity.uuid);
        PlayerProfile profile = state.profile;
        profile.setLoaded(true); profile.setBot(true); profile.setInArena(false);
        profile.getPlayerOption().setOutfit(true);
        profile.init();
        entity.setDisplayName(state.identity.name);
        entity.setCustomNameVisible(false);
        entity.setGameMode(GameMode.ADVENTURE);
        entity.getInventory().clear();
        entity.getInventory().setArmorContents(new ItemStack[4]);
        entity.setWalkSpeed(profile.getMoveSpeed());
        entity.setMaxHealth(profile.getMaxHealth()); entity.setHealth(profile.getMaxHealth());
        InventoryUtil.supplyItems(entity);
        progression.prepare(state, entity);
        entity.getInventory().setHeldItemSlot(0); entity.updateInventory();
        enableDamage(npc);
        entity.setNoDamageTicks(0);
        ((CraftPlayer) entity).getHandle().invulnerableTicks = 0;
        ((CraftPlayer) entity).getHandle().abilities.isInvulnerable = false;
        // Tab is explicitly synchronized, while database/heartbeat work remains limited to real connections.
        NMS.removeFromServerPlayerList(entity);
        cn.charlotte.pit.supporter.SupporterService supporter = cn.charlotte.pit.supporter.SupporterService.get();
        if (supporter != null && profile.getSupporterAccount() == null) supporter.refresh(entity);
    }

    private void enableDamage(NPC npc) {
        npc.setProtected(false);
        npc.data().setPersistent(NPC.Metadata.DEFAULT_PROTECTED, false);
        npc.data().setPersistent(NPC.Metadata.TARGETABLE, true);
        npc.data().setPersistent(NPC.Metadata.COLLIDABLE, true);
        npc.data().setPersistent(NPC.Metadata.SPAWN_NODAMAGE_TICKS, 0);
        npc.data().setPersistent(NPC.Metadata.DAMAGE_OTHERS, true);
        npc.data().set("damage_others", true);
    }

    public boolean handleDeath(Player victim, Player killer) {
        if (victim == null) return false;
        PublicPlayerState state = identities.get(victim.getUniqueId());
        if (state == null) return junxuBots != null && junxuBots.handleDeath(victim, killer);
        if (state.entity != victim || !state.claimDeath()) return true;
        state.phase = PublicPlayerState.Phase.RESPAWNING;
        state.dueTick = tick + respawnDelay + state.identity.slot * 3L;
        trace(state, "DEATH");
        try { retire(state, false); }
        catch (RuntimeException failure) { plugin.getLogger().log(Level.WARNING, "[公共模拟玩家] 注销异常", failure); }
        try {
            if (CombatListener.INSTANCE != null) CombatListener.INSTANCE.handlePlayerDeath(victim, killer, false);
        } finally { rosterDirty = true; }
        return true;
    }

    private void recover(PublicPlayerState state, String reason) {
        if (state.phase == PublicPlayerState.Phase.RESPAWNING) return;
        state.recoveries++;
        state.phase = PublicPlayerState.Phase.RESPAWNING; state.deathHandled = true;
        state.dueTick = tick + 20 + state.identity.slot * 5L;
        trace(state, "RECOVER_" + reason);
        retire(state, false);
        rosterDirty = true;
    }

    private void retire(PublicPlayerState state, boolean removeTab) {
        PublicPlayerCombat.cancel(state);
        if (state.npc != null) {
            NPC npc = state.npc; state.npc = null;
            removeNpc(npc, removeTab);
        }
        if(removeTab)publicNametags.remove(state.identity.uuid);
    }

    private void removeNpc(NPC npc, boolean removeTab) {
        Entity entity = npc.getEntity();
        if (entity instanceof Player) {
            Player player = (Player) entity;
            PacketPlayOutEntityDestroy packet = new PacketPlayOutEntityDestroy(player.getEntityId());
            for (Player viewer : Bukkit.getOnlinePlayers()) if (!viewer.hasMetadata("NPC")) {
                ((CraftPlayer) viewer).getHandle().playerConnection.sendPacket(packet);
                if (removeTab) {
                    NMS.sendTabListRemove(viewer, player);
                    for (int slot = 0; slot < LIMIT; slot++) {
                        org.bukkit.scoreboard.Team team = viewer.getScoreboard().getTeam("ptp_" + slot);
                        if (team != null && team.hasEntry(player.getName())) team.unregister();
                    }
                }
            }
            if (player.isValid()) player.remove();
        }
        npc.despawn(); npc.destroy();
    }

    private void clearProfile(UUID id) {
        PlayerProfile profile = PlayerProfile.getCacheProfile().get(id);
        if (profile != null && profile.isBot()) PlayerProfile.getCacheProfile().remove(id, profile);
        if (CombatListener.INSTANCE != null) CombatListener.INSTANCE.forgetCombatState(id);
    }

    private int countOwnedSpawned() {
        int count = 0;
        for (NPCRegistry candidate : CitizensAPI.getNPCRegistries()) for (NPC npc : candidate) {
            if (npc.data().has(KEY) && npc.isSpawned()) count++;
        }
        return count;
    }

    private void cleanupOwnedNpcs() {
        List<NPC> stale = new ArrayList<>();
        for (NPCRegistry candidate : CitizensAPI.getNPCRegistries()) for (NPC npc : candidate) {
            if (npc.data().has(KEY) || npc.data().has(LEGACY_KEY)) stale.add(npc);
        }
        for (NPC npc : stale) { UUID id = npc.getUniqueId(); removeNpc(npc, true); clearProfile(id); }
    }

    private void reconcile() {
        List<NPC> extras = new ArrayList<>();
        for (NPCRegistry candidate : CitizensAPI.getNPCRegistries()) for (NPC npc : candidate) {
            if (npc.data().has(LEGACY_KEY)) { extras.add(npc); continue; }
            if (!npc.data().has(KEY)) continue;
            PublicPlayerState owner = identities.get(npc.getUniqueId());
            if (owner == null || owner.npc != npc) extras.add(npc);
        }
        for (NPC npc : extras) removeNpc(npc, true);
    }

    private void updatePresentation() {
        List<cn.charlotte.pit.util.nametag.BufferedNametag> plates = plugin.getNametagHandler() == null
                ? Collections.emptyList() : plugin.getNametagHandler().getAdapter().getPlate(null);
        for (Player viewer : Bukkit.getOnlinePlayers()) if (!viewer.hasMetadata("NPC")) {
            for (PublicPlayerState state : players) {
                Player entity = state.entity;
                if (entity == null) continue;
                PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(state.identity.uuid);
                String prefix = cn.charlotte.pit.util.chat.CC.translate(profile.getFormattedLevelTag() + " &7");
                String suffix = "";
                for (cn.charlotte.pit.util.nametag.BufferedNametag plate : plates) {
                    if (plate.getPlayer() != null && state.identity.uuid.equals(plate.getPlayer().getUniqueId())) {
                        prefix = Objects.toString(plate.getPrefix(), "");
                        suffix = Objects.toString(plate.getSuffix(), "");
                        break;
                    }
                }
                String label = prefix + state.identity.name + suffix;
                ((CraftPlayer) entity).getHandle().listName = new net.minecraft.server.v1_8_R3.ChatComponentText(label);
                org.bukkit.scoreboard.Scoreboard board = viewer.getScoreboard();
                String teamName = "ptp_" + state.identity.slot;
                boolean nativeRenderer=publicNametags.publish(viewer,state.identity.uuid,state.identity.name,state.identity.slot,prefix,suffix);
                if(!nativeRenderer){
                org.bukkit.scoreboard.Team team = board.getTeam(teamName);
                if (team == null) team = board.registerNewTeam(teamName);
                String[] legacy=PublicNpcNametags.legacy(prefix,suffix);prefix=legacy[0];suffix=legacy[1];
                if (!prefix.equals(team.getPrefix())) team.setPrefix(prefix);
                if (suffix.length() > 16) suffix = suffix.substring(0, suffix.charAt(15) == '§' ? 15 : 16);
                if (!suffix.equals(team.getSuffix())) team.setSuffix(suffix);
                team.setNameTagVisibility(org.bukkit.scoreboard.NameTagVisibility.ALWAYS);
                for (String entry : new HashSet<>(team.getEntries())) if (!entry.equals(state.identity.name)) team.removeEntry(entry);
                if (!team.hasEntry(state.identity.name)) team.addEntry(state.identity.name);
                }
                NMS.sendTabListAdd(viewer, entity);
                if (state.npc == null || !state.npc.isSpawned() || viewer.getWorld() != entity.getWorld()) continue;
                if (viewer.getLocation().distanceSquared(entity.getLocation()) > 96 * 96) continue;
                ItemStack[] equipment = {entity.getItemInHand(), entity.getInventory().getBoots(), entity.getInventory().getLeggings(),
                        entity.getInventory().getChestplate(), entity.getInventory().getHelmet()};
                for (int slot = 0; slot < equipment.length; slot++) {
                    ((CraftPlayer) viewer).getHandle().playerConnection.sendPacket(new PacketPlayOutEntityEquipment(
                            entity.getEntityId(), slot, CraftItemStack.asNMSCopy(equipment[slot])));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent event) {
        PublicPlayerState state = identities.get(event.getEntity().getUniqueId());
        if (state != null && (state.entity != event.getEntity() || state.phase == PublicPlayerState.Phase.RESPAWNING)) event.setCancelled(true);
        if (event instanceof EntityDamageByEntityEvent) {
            Entity damager = ((EntityDamageByEntityEvent) event).getDamager();
            PublicPlayerState attacker = identities.get(damager.getUniqueId());
            if (attacker != null && (attacker.entity != damager || attacker.phase != PublicPlayerState.Phase.COMBAT)) event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || CombatDamage.accepted(event) <= 0) return;
        PublicPlayerState victim = identities.get(event.getEntity().getUniqueId());
        if (victim != null) {
            victim.knockbackUntil = tick + 8;
            PublicPlayerCombat.cancel(victim);
            if (event.getDamager() instanceof Player && !isBot(event.getDamager()) && !event.getDamager().hasMetadata("NPC")) {
                victim.target = event.getDamager().getUniqueId();
                difficulty.hit(event.getDamager().getUniqueId(),true,System.currentTimeMillis());
            }
        }
        PublicPlayerState attacker = identities.get(event.getDamager().getUniqueId());
        if (attacker != null && event.getEntity() instanceof Player && !isBot(event.getEntity()) && !event.getEntity().hasMetadata("NPC")) {
            difficulty.hit(event.getEntity().getUniqueId(),false,System.currentTimeMillis());
            boolean first = !attacker.hitThisLife;
            attacker.hitHuman();
            if (first) trace(attacker, "HIT_HUMAN");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onNpcAttack(NPCDamageEntityEvent event) {
        if (!event.getNPC().data().has(KEY)) return;
        PublicPlayerState state = identities.get(event.getNPC().getUniqueId());
        if (state == null || state.phase != PublicPlayerState.Phase.COMBAT) event.setCancelled(true);
    }

    @EventHandler public void onNpcDeath(NPCDeathEvent event) {
        if (!event.getNPC().data().has(KEY)) return;
        event.getDrops().clear(); event.setDroppedExp(0);
        if (event.getEvent().getEntity() instanceof Player) {
            Player player = (Player) event.getEvent().getEntity(); handleDeath(player, player.getKiller());
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onClick(NPCRightClickEvent event) {
        if (event.getNPC().data().has(KEY)) event.setCancelled(true);
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) {
        if (event.getPlayer().hasMetadata("NPC")) return;
        for (PublicPlayerState state : players) if (state.identity.name.equalsIgnoreCase(event.getPlayer().getName())) {
            recover(state, "name-conflict"); resolveIdentities(); break;
        }
        rosterDirty = true;
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        for (PublicPlayerState state : players) if (event.getPlayer().getUniqueId().equals(state.target)) state.target = null;
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        for (PublicPlayerState state : players) if (state.entity != null && state.phase != PublicPlayerState.Phase.RESPAWNING) {
            Location at = state.entity.getLocation();
            if (at.getWorld() == chunk.getWorld() && (at.getBlockX() >> 4) == chunk.getX() && (at.getBlockZ() >> 4) == chunk.getZ()) {
                recover(state, "chunk-unload");
            }
        }
    }
    @EventHandler public void onChunkLoad(ChunkLoadEvent event) { rosterDirty = true; }

    private void changePhase(PublicPlayerState state, PublicPlayerState.Phase phase, String event) {
        state.phase = phase; state.phaseTick = tick; state.lastProgressTick = tick; trace(state, event);
    }
    private void trace(PublicPlayerState state, String event) {
        Location at = state.entity == null ? null : state.entity.getLocation();
        plugin.getLogger().info("PUBLIC_PLAYER name=" + state.identity.name + " uuid=" + state.identity.uuid + " life=" + state.life
                + " event=" + event + " phase=" + state.phase + " loops=" + state.completedLoops()
                + (at == null ? "" : " xyz=" + at.getX() + "," + at.getY() + "," + at.getZ()));
    }
    public List<String> diagnostics() {
        List<String> lines = new ArrayList<>();
        lines.add("PUBLIC_PLAYERS enabled=" + isPublicEnabled() + " ready=" + isReady() + " identities=" + players.size() + " spawned=" + countOwnedSpawned() + " cap=3"
                + " nametagRenderer="+publicNametags.status()
                + " meanMicros=" + (samples == 0 ? 0 : tickNanos / samples / 1000) + " peakMicros=" + peakNanos / 1000);
        for (PublicPlayerState state : players) lines.add("PUBLIC_SLOT name=" + state.identity.name + " uuid=" + state.identity.uuid
                + " phase=" + state.phase + " life=" + state.life + " jumps=" + state.jumps + " hits=" + state.hits + " deaths="
                + state.deaths + " loops=" + state.completedLoops() + " recoveries=" + state.recoveries
                + " entity=" + (state.entity == null ? -1 : state.entity.getEntityId()) + " hp=" + (state.entity == null ? 0 : state.entity.getHealth())
                + " difficulty="+state.difficulty.name
                + " prestige=" + state.profile.getPrestige() + " level=" + state.profile.getLevel() + " coins=" + state.profile.getCoins()
                + " xp=" + state.profile.getExperience() + " grinded=" + state.profile.getGrindedCoins() + " renown=" + state.profile.getRenown()
                + " perks=" + state.profile.getChosePerk().values());
        return lines;
    }
}
