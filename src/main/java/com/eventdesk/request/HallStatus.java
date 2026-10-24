package com.eventdesk.request;

/** Whether the venue is held for this request. Separate from {@link PermissionStatus}. */
public enum HallStatus {
    TENTATIVE("Hall tentatively held"),
    CONFIRMED("Hall confirmed"),
    RELEASED("Hall released");

    private final String label;

    HallStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}