package cn.charlotte.pit.nametag;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.data.PlayerProfile;
import cn.charlotte.pit.events.genesis.team.GenesisTeam;
import cn.charlotte.pit.events.impl.major.HamburgerEvent;
import cn.charlotte.pit.events.impl.major.RedVSBlueEvent;
import cn.charlotte.pit.events.impl.major.SpireEvent;
import cn.charlotte.pit.util.PlayerUtil;
import cn.charlotte.pit.util.chat.CC;
import cn.charlotte.pit.util.nametag.BufferedNametag;
import cn.charlotte.pit.util.nametag.NametagAdapter;
import cn.charlotte.pit.util.rank.RankUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;

/**
 * @Author: EmptyIrony
 * @Date: 2021/1/1 15:56
 */
public class NameTagImpl implements NametagAdapter {
    private static final long PLATE_CACHE_MS = 1000L;
    private final java.util.function.Supplier<? extends java.util.Collection<? extends Player>> onlinePlayers;

    public NameTagImpl() { this(Bukkit::getOnlinePlayers); }

    NameTagImpl(java.util.function.Supplier<? extends java.util.Collection<? extends Player>> onlinePlayers) {
        this.onlinePlayers = onlinePlayers;
    }

    private List<BufferedNametag> cachedPlate = Collections.emptyList();
    private long cachedPlateUntil;
    private UUID cachedPlateScope;

    @Override
    public List<BufferedNametag> getPlate(Player player) {
        final long now = System.currentTimeMillis();
        cn.charlotte.pit.junxu.JunxuManager junxu = ThePit.getInstance().getJunxuManager();
        cn.charlotte.pit.junxu.JunxuRoom viewerRoom = junxu == null || player == null ? null : junxu.at(player.getLocation());
        UUID scope = viewerRoom == null || !viewerRoom.permits(player.getUniqueId()) ? null : viewerRoom.session;
        if (now < cachedPlateUntil && java.util.Objects.equals(scope,cachedPlateScope)) {
            return cachedPlate;
        }
        cachedPlateScope=scope;

        List<BufferedNametag> tags = new ArrayList<>();
        List<PlayerProfile> profiles = new ArrayList<>();

        Map<UUID, Player> targets = new LinkedHashMap<>();
        for (Player target : onlinePlayers.get()) targets.put(target.getUniqueId(), target);
        if (ThePit.getInstance().getPitBotManager() != null) {
            for (PlayerProfile profile : PlayerProfile.getCacheProfile().values()) {
                if (!ThePit.getInstance().getPitBotManager().ownsProfile(profile)) continue;
                Player target = ThePit.getInstance().getPitBotManager().getPlayer(profile.getPlayerUuid());
                cn.charlotte.pit.junxu.JunxuRoom privateRoom = junxu == null || junxu.bots() == null ? null : junxu.bots().room(target);
                if (privateRoom != null && (player == null || !cn.charlotte.pit.junxu.JunxuVisibility.canView(player.getUniqueId(),player.getLocation(),privateRoom))) continue;
                if (target != null) targets.put(target.getUniqueId(), target);
            }
        }
        for (Player target : targets.values()) {
            PlayerProfile profile = PlayerProfile.getPlayerProfileByUuid(target.getUniqueId());
            if (profile == null || !profile.isLoaded()) {
                continue;
            }
            profiles.add(profile);
        }

        if (ThePit.getInstance().getEventFactory() == null) {
            return tags;
        }

        String activeEpicEventName = ThePit.getInstance().getEventFactory().getActiveEpicEventName();
        profiles.sort((profile1, profile2) -> {
            int priority1 = cn.charlotte.pit.util.PublicProfileDisplay.prestige(profile1) + 1000 * cn.charlotte.pit.util.PublicProfileDisplay.level(profile1);
            int priority2 = cn.charlotte.pit.util.PublicProfileDisplay.prestige(profile2) + 1000 * cn.charlotte.pit.util.PublicProfileDisplay.level(profile2);

            if ("red_vs_blue".equals(activeEpicEventName)) {
                RedVSBlueEvent activeEpicEvent = (RedVSBlueEvent) ThePit.getInstance().getEventFactory().getActiveEpicEvent();
                if (activeEpicEvent.isRedTeam(profile1.getPlayerUuid())) {
                    priority1 += 10000000;
                }
                if (activeEpicEvent.isRedTeam(profile2.getPlayerUuid())) {
                    priority2 += 10000000;
                }
            }

            if ("spire".equals(activeEpicEventName)) {
                final SpireEvent event = (SpireEvent) ThePit.getInstance().getEventFactory().getActiveEpicEvent();
                Player first = Bukkit.getPlayer(profile1.getPlayerUuid()), second = Bukkit.getPlayer(profile2.getPlayerUuid());
                if (ThePit.getInstance().getPitBotManager() != null) {
                    if (first == null) first = ThePit.getInstance().getPitBotManager().getPlayer(profile1.getPlayerUuid());
                    if (second == null) second = ThePit.getInstance().getPitBotManager().getPlayer(profile2.getPlayerUuid());
                }
                priority1 += event.getRank(first) * 10000000;
                priority2 += event.getRank(second) * 10000000;
            }
            return Integer.compare(priority1, priority2);
        });
        int i = 200;
        for (PlayerProfile profile : profiles) {
            Player tagged = targets.get(profile.getPlayerUuid());
            if (tagged == null) {
                continue;
            }

            String displayName;
            if ("spire".equals(activeEpicEventName)) {
                final SpireEvent event = (SpireEvent) ThePit.getInstance().getEventFactory().getActiveEpicEvent();
                final int rank = event.getRank(tagged);
                if (rank == -1) {
                    displayName = CC.translate("&7[???&7] " + RankUtil.getPlayerRankColor(profile.getPlayerUuid()));
                } else {
                    displayName = CC.translate("&7[&e#" + rank + "&7] " + RankUtil.getPlayerRankColor(profile.getPlayerUuid()));
                }
            } else if ("red_vs_blue".equals(activeEpicEventName)) {
                RedVSBlueEvent activeEpicEvent = (RedVSBlueEvent) ThePit.getInstance().getEventFactory().getActiveEpicEvent();
                displayName = activeEpicEvent.isRedTeam(profile.getPlayerUuid()) ? CC.translate(profile.getFormattedLevelTag() + " &c") : CC.translate(profile.getFormattedLevelTag() + " &9");
            } else {
                displayName = CC.translate(profile.getFormattedLevelTag() + " " + RankUtil.getPlayerRankColor(profile.getPlayerUuid()));
                String activeMegaStreak = PlayerUtil.getActiveMegaStreak(tagged);
                if (activeMegaStreak != null) {
                    String streakLevel = "";
                    if ("uber_streak".equalsIgnoreCase(profile.getChosePerk().get(5).getPerkInternalName())) {
                        streakLevel = String.valueOf(Math.min(400, (((int) profile.getStreakKills()) / 100) * 100));
                    }
                    displayName = activeMegaStreak + ChatColor.BOLD + streakLevel + " "
                            + RankUtil.getPlayerRankColor(profile.getPlayerUuid());
                }
            }
            StringBuilder suffix = new StringBuilder();
            if ("spire".equals(activeEpicEventName)) {
                final SpireEvent event = (SpireEvent) ThePit.getInstance().getEventFactory().getActiveEpicEvent();
                final String displayFloor = event.getDisplayFloor(profile.getPlayerUuid());
                suffix.append(" ").append(displayFloor);

            } else if ("ham".equals(activeEpicEventName)) {
                final HamburgerEvent event = (HamburgerEvent) ThePit.getInstance()
                        .getEventFactory()
                        .getActiveEpicEvent();
                final HamburgerEvent.PizzaData data = event.getPizzaDataMap().get(profile.getPlayerUuid());
                if (data != null) {
                    suffix.append(" &c")
                            .append(data.getHamburger())
                            .append("ஐ &6")
                            .append(data.getMoney())
                            .append("$");
                }
            } else {
                if (profile.isSupporter() && profile.getPlayerOption().isSupporterStarDisplay() && !profile.isNicked()) {
                    suffix.append(" ").append(profile.getSupporterStar());
                }
                if (ThePit.getInstance().getPitConfig().isGenesisEnable()) {
                    if (profile.getGenesisData().getTeam() == GenesisTeam.ANGEL) {
                        //♆♨
                        suffix.append(" &b");
                    }
                    if (profile.getGenesisData().getTeam() == GenesisTeam.DEMON) {
                        //♨
                        suffix.append(" &c");
                    }
                    if (profile.getGenesisData().getTeam() == GenesisTeam.NONE) {
                        suffix.append(" &6");
                    }
                    if (profile.getBounty() != 0) {
                        suffix.append("&l").append(profile.getBounty()).append("g");
                    } else {
                        if (profile.getGenesisData().getTeam() == GenesisTeam.ANGEL) {
                            suffix.append("♆");
                        }
                        if (profile.getGenesisData().getTeam() == GenesisTeam.DEMON) {
                            suffix.append("♨");
                        }
                    }
                } else if (profile.getBounty() != 0) {
                    suffix.append("&6&l ").append(profile.getBounty()).append("g");
                } else {
                    suffix.append(" ");
                }
            }

            String translatedSuffix = CC.translate(suffix.toString());
            tags.add(new BufferedNametag(
                    String.format("%03d", i),
                    displayName,
                    translatedSuffix,
                    false,
                    tagged));
            i--;

        }

        cachedPlate = Collections.unmodifiableList(tags);
        cachedPlateUntil = now + PLATE_CACHE_MS;
        return cachedPlate;
    }

    @Override
    public boolean showHealthBelowName(Player player) {
        return ThePit.getInstance().getEventFactory() != null && "spire".equals(ThePit.getInstance().getEventFactory().getActiveEpicEventName());
    }
}
