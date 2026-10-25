package com.eventdesk.request;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One month laid out as Monday-first weeks, like a wall calendar. Days from
 * the neighbouring months fill the first and last week.
 *
 * <p>Pure Java with no database access, so the layout rules are unit tested
 * (MonthGridTest) apart from the queries that feed it.
 */
public record MonthGrid(YearMonth month, String monthLabel, List<Week> weeks) {

    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    public record Week(List<Day> days) {
    }

    public record Day(LocalDate date, boolean inMonth, boolean today, List<Entry> entries) {
        public int dayOfMonth() {
            return date.getDayOfMonth();
        }
    }

    /**
     * One booking as shown on one day. {@code continued} marks the days after
     * the first for an event that runs overnight. {@code linkable} is false
     * when the viewer may not open the request (another club's booking).
     */
    public record Entry(long id, String title, String clubName, String venueName,
                        String startLabel, String tone, boolean continued, boolean linkable) {

        Entry asContinued() {
            return new Entry(id, title, clubName, venueName, startLabel, tone, true, linkable);
        }
    }

    /** Input: a booking that occupies every day from firstDay to lastDay, inclusive. */
    public record Booking(LocalDate firstDay, LocalDate lastDay, Entry entry) {
    }

    /** First day shown in the grid: the Monday on or before the 1st. */
    public static LocalDate firstVisibleDay(YearMonth month) {
        return month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** Last day shown in the grid: the Sunday on or after the month's last day. */
    public static LocalDate lastVisibleDay(YearMonth month) {
        return month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
    }

    /** Bookings should arrive sorted by start time; each day keeps that order. */
    public static MonthGrid of(YearMonth month, LocalDate today, List<Booking> bookings) {
        LocalDate first = firstVisibleDay(month);
        LocalDate last = lastVisibleDay(month);

        Map<LocalDate, List<Entry>> byDay = new HashMap<>();
        for (Booking booking : bookings) {
            LocalDate from = booking.firstDay().isBefore(first) ? first : booking.firstDay();
            LocalDate to = booking.lastDay().isAfter(last) ? last : booking.lastDay();
            for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
                Entry entry = day.equals(booking.firstDay()) ? booking.entry() : booking.entry().asContinued();
                byDay.computeIfAbsent(day, d -> new ArrayList<>()).add(entry);
            }
        }

        List<Week> weeks = new ArrayList<>();
        for (LocalDate monday = first; !monday.isAfter(last); monday = monday.plusWeeks(1)) {
            List<Day> days = new ArrayList<>(7);
            for (int i = 0; i < 7; i++) {
                LocalDate date = monday.plusDays(i);
                days.add(new Day(date, YearMonth.from(date).equals(month), date.equals(today),
                        List.copyOf(byDay.getOrDefault(date, List.of()))));
            }
            weeks.add(new Week(List.copyOf(days)));
        }
        return new MonthGrid(month, LABEL.format(month), List.copyOf(weeks));
    }

    public YearMonth previous() {
        return month.minusMonths(1);
    }

    public YearMonth next() {
        return month.plusMonths(1);
    }

    public boolean isEmpty() {
        return weeks.stream().flatMap(w -> w.days().stream()).allMatch(d -> d.entries().isEmpty());
    }
}