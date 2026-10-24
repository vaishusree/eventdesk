package com.eventdesk.user;

public enum Role {
    CLUB("club coordinator"),
    VENUE_OFFICE("hall reservation office"),
    DEAN("Dean of Clubs & Chapters"),
    /**
     * Designated backup with authority to decide escalated requests when the
     * regular reviewer hasn't responded. Rename the label to the real office
     * (e.g. Associate Dean) once the college confirms who it is.
     */
    BACKUP_APPROVER("backup approver");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    /** Human-readable name used in messages shown to users. */
    public String label() {
        return label;
    }
}
