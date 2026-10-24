package com.eventdesk.request;

public enum EventType {
    TECHNICAL("Technical event", true),
    WORKSHOP("Workshop", true),
    WEBINAR("Webinar", false),
    CULTURAL("Cultural event", false),
    OTHER("Other", false);

    private final String label;
    private final boolean requiresAttendance;

    EventType(String label, boolean requiresAttendance) {
        this.label = label;
        this.requiresAttendance = requiresAttendance;
    }

    public String label() {
        return label;
    }

    /**
     * Whether the club must give an estimated head count. The college tracks
     * numbers for technical events and workshops; for other types the count is
     * optional. Must match the attendance_required_for_counted_events
     * constraint in V4.
     */
    public boolean requiresAttendance() {
        return requiresAttendance;
    }
}