package com.eventdesk.request;

import java.util.List;

/**
 * What a reviewer sees: the requests they can decide now, and (for the dean)
 * requests stuck at the previous stage that they should know about.
 */
public record ReviewQueue(String heading, String explanation,
                          List<RequestSummary> toDecide,
                          String watchHeading, List<RequestSummary> toWatch) {
}