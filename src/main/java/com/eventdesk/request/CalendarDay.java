package com.eventdesk.request;

import java.util.List;

/** One date on the shared calendar with its bookings, earliest first. */
public record CalendarDay(String dateLabel, List<RequestSummary> bookings) {
}