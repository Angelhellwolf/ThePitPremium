package cn.charlotte.pit.reward;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class RewardCalendarTest {
    @Test public void weekdaysAndCalendarWeekendsUseOneAndTwo() {
        List<RewardCalendar.Period> empty = Collections.emptyList();
        assertEquals(1, RewardCalendar.multiplier(LocalDate.parse("2026-09-07"), empty), 0);
        assertEquals(1, RewardCalendar.multiplier(LocalDate.parse("2026-09-11"), empty), 0);
        assertEquals(2, RewardCalendar.multiplier(LocalDate.parse("2026-09-12"), empty), 0);
        assertEquals(2, RewardCalendar.multiplier(LocalDate.parse("2026-09-13"), empty), 0);
    }
    @Test public void bundledOfficial2026HolidayRangesIncludeBothEndpointsAndOverrideWeekends() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        List<RewardCalendar.Period> periods = new ArrayList<>();
        for (String value : config.getStringList("reward-calendar.periods")) periods.add(RewardCalendar.Period.parse(value));
        assertEquals(7, periods.size());
        String[] dates = {"2026-01-01", "2026-01-03", "2026-01-04", "2026-02-14", "2026-02-15", "2026-02-23", "2026-02-24",
                "2026-04-04", "2026-04-06", "2026-05-05", "2026-06-21", "2026-09-25", "2026-09-27", "2026-09-28",
                "2026-10-01", "2026-10-07", "2026-10-08", "2026-09-20"};
        double[] multipliers = {3, 3, 2, 2, 5, 5, 1, 3, 3, 3, 3, 3, 3, 1, 5, 5, 1, 2};
        for (int i = 0; i < dates.length; i++) {
            assertEquals(dates[i], multipliers[i], RewardCalendar.multiplier(LocalDate.parse(dates[i]), periods), 0);
        }
    }
    @Test public void fiveTimesWinsWhenHolidayRangesOverlap() {
        List<RewardCalendar.Period> periods = Arrays.asList(
                RewardCalendar.Period.parse("2026-10-01,2026-10-07,5,国庆节"),
                RewardCalendar.Period.parse("2026-10-03,2026-10-03,3,其他假期"));
        assertEquals(5, RewardCalendar.multiplier(LocalDate.parse("2026-10-03"), periods), 0);
    }
    @Test public void malformedDatesAndUnboundedRangesAreRejected() {
        for (String value : Arrays.asList("bad", "2026-02-31,2026-03-01,3,错误", "2026-10-07,2026-10-01,5,错误",
                "2026-01-01,2026-12-31,5,错误", "2026-01-01,2026-01-02,100,错误")) {
            try { RewardCalendar.Period.parse(value); fail(value); } catch (IllegalArgumentException expected) { }
        }
    }
}
