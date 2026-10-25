package com.eventdesk.request;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Layout of the month calendar: weeks, neighbouring days, and where bookings land. */
class MonthGridTest {

    private static final YearMonth NOV_2026 = YearMonth.of(2026, 11);   // 1 Nov 2026 is a Sunday
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 25);

    private static MonthGrid.Booking booking(long id, LocalDate first, LocalDate last) {
        MonthGrid.Entry entry = new MonthGrid.Entry(id, "Event " + id, "Coding Club", "Seminar Hall A",
                "10:00", "warn", false, true);
        return new MonthGrid.Booking(first, last, entry);
    }

    private static MonthGrid.Day day(MonthGrid grid, LocalDate date) {
        return grid.weeks().stream().flatMap(w -> w.days().stream())
                .filter(d -> d.date().equals(date)).findFirst().orElseThrow();
    }

    @Test
    void weeksRunMondayToSundayAndCoverTheWholeMonth() {
        MonthGrid grid = MonthGrid.of(NOV_2026, TODAY, List.of());

        assertEquals("November 2026", grid.monthLabel());
        assertEquals(6, grid.weeks().size(), "Nov 2026 starts on a Sunday, so it spans six weeks");
        grid.weeks().forEach(week -> {
            assertEquals(7, week.days().size());
            assertEquals(DayOfWeek.MONDAY, week.days().get(0).date().getDayOfWeek());
        });
        assertEquals(LocalDate.of(2026, 10, 26), grid.weeks().get(0).days().get(0).date());
        assertEquals(LocalDate.of(2026, 12, 6), grid.weeks().get(5).days().get(6).date());
    }

    @Test
    void daysFromNeighbouringMonthsAreMarkedOutside() {
        MonthGrid grid = MonthGrid.of(NOV_2026, TODAY, List.of());

        assertFalse(day(grid, LocalDate.of(2026, 10, 31)).inMonth());
        assertTrue(day(grid, LocalDate.of(2026, 11, 1)).inMonth());
        assertTrue(day(grid, LocalDate.of(2026, 11, 30)).inMonth());
        assertFalse(day(grid, LocalDate.of(2026, 12, 1)).inMonth());
    }

    @Test
    void todayIsMarkedOnlyWhenVisible() {
        MonthGrid october = MonthGrid.of(YearMonth.of(2026, 10), TODAY, List.of());
        assertTrue(day(october, TODAY).today());
        assertEquals(1L, october.weeks().stream().flatMap(w -> w.days().stream()).filter(MonthGrid.Day::today).count());

        MonthGrid december = MonthGrid.of(YearMonth.of(2026, 12), TODAY, List.of());
        assertTrue(december.weeks().stream().flatMap(w -> w.days().stream()).noneMatch(MonthGrid.Day::today));
    }

    @Test
    void aOneDayBookingAppearsOnItsDayOnly() {
        LocalDate date = LocalDate.of(2026, 11, 26);
        MonthGrid grid = MonthGrid.of(NOV_2026, TODAY, List.of(booking(1, date, date)));

        assertEquals(1, day(grid, date).entries().size());
        assertFalse(day(grid, date).entries().get(0).continued());
        assertTrue(day(grid, date.plusDays(1)).entries().isEmpty());
        assertFalse(grid.isEmpty());
    }

    @Test
    void anOvernightBookingContinuesOnTheNextDay() {
        LocalDate start = LocalDate.of(2026, 11, 28);
        MonthGrid grid = MonthGrid.of(NOV_2026, TODAY, List.of(booking(1, start, start.plusDays(1))));

        assertFalse(day(grid, start).entries().get(0).continued());
        assertTrue(day(grid, start.plusDays(1)).entries().get(0).continued());
        assertEquals(1L, day(grid, start.plusDays(1)).entries().get(0).id());
    }

    @Test
    void bookingsReachingPastTheGridAreClippedToIt() {
        // Starts before the first visible day (26 Oct) and ends after the last (6 Dec).
        MonthGrid grid = MonthGrid.of(NOV_2026, TODAY,
                List.of(booking(1, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 12, 10))));

        assertTrue(day(grid, LocalDate.of(2026, 10, 26)).entries().get(0).continued());
        assertEquals(1, day(grid, LocalDate.of(2026, 12, 6)).entries().size());
    }

    @Test
    void aDayKeepsBookingsInTheOrderGiven() {
        LocalDate date = LocalDate.of(2026, 11, 26);
        MonthGrid grid = MonthGrid.of(NOV_2026, TODAY, List.of(booking(1, date, date), booking(2, date, date)));

        List<MonthGrid.Entry> entries = day(grid, date).entries();
        assertEquals(1L, entries.get(0).id());
        assertEquals(2L, entries.get(1).id());
    }

    @Test
    void previousAndNextMoveOneMonth() {
        MonthGrid grid = MonthGrid.of(NOV_2026, TODAY, List.of());

        assertEquals(YearMonth.of(2026, 10), grid.previous());
        assertEquals(YearMonth.of(2026, 12), grid.next());
        assertEquals("Oct", grid.previousLabel());
        assertEquals("Dec", grid.nextLabel());
        assertTrue(grid.isEmpty());
    }
}