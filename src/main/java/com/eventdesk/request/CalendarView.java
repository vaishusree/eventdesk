package com.eventdesk.request;

import java.time.YearMonth;
import java.util.List;

/**
 * Everything the calendar page shows for one month: the grid, and the same
 * bookings as a list by date (with hall and permission status), which is
 * also what phones see instead of the grid.
 */
public record CalendarView(MonthGrid grid, List<CalendarDay> agenda, Long venueId, YearMonth thisMonth) {
}