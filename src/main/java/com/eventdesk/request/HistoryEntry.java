package com.eventdesk.request;

/** One line of a request's audit trail, ready to show. */
public record HistoryEntry(String whenLabel, String actorName, String actionLabel,
                           String fromLabel, String toLabel, String remark) {
}