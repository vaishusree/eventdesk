package com.eventdesk.request;

/**
 * Whether the event is allowed to happen. Kept separate from {@link HallStatus}:
 * a confirmed hall is not permission to hold the event.
 * Only {@link #GRANTED} authorizes the event.
 */
public enum PermissionStatus {
    PENDING("Permission pending"),
    GRANTED("Permission granted"),
    DENIED("Permission denied"),
    WITHDRAWN("Withdrawn by club"),
    EXPIRED("Expired: no decision before the event");

    private final String label;

    PermissionStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}