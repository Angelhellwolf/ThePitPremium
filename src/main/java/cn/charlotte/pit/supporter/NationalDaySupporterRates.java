package cn.charlotte.pit.supporter;

import java.time.LocalDateTime;
import java.time.ZoneId;
import org.bukkit.configuration.ConfigurationSection;

/** Absolute seasonal base rates; gameplay modifiers remain at their existing call sites. */
public final class NationalDaySupporterRates {
    private static final String ROOT = "supporter.national-day-2026.";
    private static volatile Window window;

    private NationalDaySupporterRates() { }

    public static String reload(ConfigurationSection config) {
        if (!config.getBoolean(ROOT + "enabled", false)) {
            window = null;
            return "PIT_NATIONAL_DAY_RATES disabled";
        }
        try {
            ZoneId zone = ZoneId.of(config.getString(ROOT + "timezone", "Asia/Hong_Kong"));
            String startText = config.getString(ROOT + "starts-at", "2026-10-01T00:00:00");
            String endText = config.getString(ROOT + "ends-at", "2026-10-08T00:00:00");
            long start = LocalDateTime.parse(startText).atZone(zone).toInstant().toEpochMilli();
            long end = LocalDateTime.parse(endText).atZone(zone).toInstant().toEpochMilli();
            if (start >= end) throw new IllegalArgumentException("End must follow start");
            double[] drop = readRates(config, "drop-chance");
            double[] rare = readRates(config, "rare-chance");
            window = new Window(start, end, drop, rare);
            return "PIT_NATIONAL_DAY_RATES configured start=" + startText + " end=" + endText
                    + " zone=" + zone + " drop=" + format(drop) + " rare=" + format(rare);
        } catch (RuntimeException invalid) {
            window = null;
            return "PIT_NATIONAL_DAY_RATES disabled: invalid activity configuration ("
                    + invalid.getClass().getSimpleName() + ")";
        }
    }

    private static double[] readRates(ConfigurationSection config, String type) {
        String[] tiers = {"base", "mythic", "sacred"};
        // Owner revision on 2026-10-01: base / mythic / sacred.
        double[] rates = "rare-chance".equals(type)
                ? new double[]{0.02, 0.03, 0.04}
                : new double[]{0.04, 0.06, 0.06};
        for (int i = 0; i < rates.length; i++) {
            rates[i] = config.getDouble(ROOT + type + "." + tiers[i], rates[i]);
            if (!Double.isFinite(rates[i]) || rates[i] < 0 || rates[i] > 1
                    || (i > 0 && rates[i] < rates[i - 1])) {
                throw new IllegalArgumentException("Invalid probability");
            }
        }
        return rates;
    }

    private static String format(double[] values) {
        return values[0] + "/" + values[1] + "/" + values[2];
    }

    public static double dropChance(SupporterTier tier, double ordinary, long now) {
        Window current = window;
        return current != null && current.includes(now) ? current.drop[index(tier)] : ordinary;
    }

    public static double rareChance(SupporterTier tier, double ordinary, long now) {
        Window current = window;
        return current != null && current.includes(now) ? current.rare[index(tier)] : ordinary;
    }

    public static boolean active(long now) {
        Window current = window;
        return current != null && current.includes(now);
    }

    private static int index(SupporterTier tier) {
        return tier == SupporterTier.SACRED ? 2 : tier == SupporterTier.MYTHIC ? 1 : 0;
    }

    private static final class Window {
        final long start, end;
        final double[] drop, rare;
        Window(long start, long end, double[] drop, double[] rare) {
            this.start = start;
            this.end = end;
            this.drop = drop;
            this.rare = rare;
        }
        boolean includes(long now) { return now >= start && now < end; }
    }
}
