package com.eventdesk.request;

import java.util.List;

/** Everything the request detail page shows. */
public record RequestDetail(RequestSummary summary, String description, String submittedByName,
                            String submittedLabel, List<HistoryEntry> history) {
}