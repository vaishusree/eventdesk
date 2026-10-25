package com.eventdesk.request;

/**
 * One request as a page shows it: plain values only, already formatted in the
 * college's time zone. Built inside a read-only transaction, so templates
 * never touch lazy relationships (open-in-view is off).
 *
 * <p>The *Tone fields name a CSS style: ok, warn, bad, info or muted.
 */
public record RequestSummary(
        long id,
        String title,
        String clubName,
        String venueName,
        int venueCapacity,
        String eventTypeLabel,
        Integer expectedAttendance,
        String dateLabel,
        String timeLabel,
        String statusLabel,
        String hallLabel,
        String hallTone,
        String permissionLabel,
        String permissionTone,
        boolean overdue,
        boolean escalated,
        boolean overCapacity,
        boolean extended,
        String departmentApprovalRef,
        boolean canCancel,
        boolean canRemind,
        boolean canDecide,
        String waitingLabel,
        String rejectionReason,
        long venueId,
        String calendarMonth   // e.g. "2026-11": links to the hall's calendar for the event's month
) {
}