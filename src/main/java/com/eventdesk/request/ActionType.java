package com.eventdesk.request;

/** What happened to a request, as recorded in its audit history. */
public enum ActionType {
    SUBMITTED,
    APPROVED,
    REJECTED,
    CANCELLED,
    /** The club nudged the reviewer; the status does not change. */
    REMINDED,
    EXPIRED
}
