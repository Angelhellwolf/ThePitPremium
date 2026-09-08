package cn.charlotte.pit.reward;

import cn.charlotte.pit.ThePit;
import cn.charlotte.pit.listener.CombatListener;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitTask;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Server-local dates, cached once per day. No file access, HTTP request or date parsing on the kill path. */
public final class RewardCalendar {
    private static List<Period> periods = Collections.emptyList();
    private static ZoneId zone = ZoneId.systemDefault();
    private static LocalDate appliedDay;
    private static BukkitTask task;
    private static boolean enabled;

    private RewardCalendar() { }

    public static void reload(ThePit plugin) {
        if (plugin == null) return;
        plugin.reloadConfig();
        ConfigurationSection config = plugin.getConfig();
        if (config == null) return;
        enabled = config.getBoolean("reward-calendar.enabled", true);
        List<Period> loaded = new ArrayList<>();
        for (String value : config.getStringList("reward-calendar.periods")) {
            try { loaded.add(Period.parse(value)); }
            catch (IllegalArgumentException exception) { plugin.getLogger().warning("奖励日历配置无效：" + value); }
        }
        periods = Collections.unmodifiableList(loaded);
        zone = ZoneId.systemDefault();
        appliedDay = null;
        refresh(plugin);
    }

    public static void start(ThePit plugin) {
        reload(plugin);
        if (task == null && plugin != null && plugin.getConfig() != null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, () -> refresh(plugin), 20L, 20L);
        }
    }

    private static void refresh(ThePit plugin) {
        if (!enabled) return;
        LocalDate day = LocalDate.now(zone);
        if (day.equals(appliedDay)) return;
        appliedDay = day;
        CombatListener.eventBoost = multiplier(day, periods);
        boolean yearConfigured = periods.stream().anyMatch(period -> period.start.getYear() == day.getYear());
        plugin.getLogger().info("REWARD_CALENDAR date=" + day + " zone=" + zone + " human=" + CombatListener.eventBoost
                + " bot=" + BotRewards.multiplier(CombatListener.eventBoost) + " yearConfigured=" + yearConfigured);
        if (!yearConfigured) plugin.getLogger().warning("奖励日历缺少 " + day.getYear() + " 年官方假期表；当前使用周一至周五/周末规则，请补充 reward-calendar.periods。");
    }

    public static double multiplier(LocalDate day, List<Period> periods) {
        double holiday = 0;
        for (Period period : periods) if (!day.isBefore(period.start) && !day.isAfter(period.end)) {
            holiday = Math.max(holiday, period.multiplier);
        }
        if (holiday > 0) return holiday;
        DayOfWeek weekday = day.getDayOfWeek();
        return weekday == DayOfWeek.SATURDAY || weekday == DayOfWeek.SUNDAY ? 2.0 : 1.0;
    }

    public static final class Period {
        public final LocalDate start, end;
        public final double multiplier;
        public final String name;
        private Period(LocalDate start, LocalDate end, double multiplier, String name) {
            this.start = start;
            this.end = end;
            this.multiplier = multiplier;
            this.name = name;
        }
        public static Period parse(String value) {
            try {
                String[] pieces = value.split(",", 4);
                if (pieces.length != 4) throw new IllegalArgumentException("Expected start,end,multiplier,name");
                LocalDate start = LocalDate.parse(pieces[0].trim()), end = LocalDate.parse(pieces[1].trim());
                double multiplier = Double.parseDouble(pieces[2].trim());
                if (end.isBefore(start) || end.toEpochDay() - start.toEpochDay() > 31 || (multiplier != 3 && multiplier != 5)) {
                    throw new IllegalArgumentException("Invalid holiday range");
                }
                return new Period(start, end, multiplier, pieces[3].trim());
            } catch (RuntimeException exception) { throw new IllegalArgumentException("Invalid holiday period", exception); }
        }
    }
}
