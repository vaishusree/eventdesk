package com.eventdesk.web;

import com.eventdesk.request.EventDetails;
import com.eventdesk.request.EventType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * What the new-request form posts. Bean Validation catches missing or
 * malformed fields for a friendly per-field message; the business rules
 * (30-day notice, 12-hour limit, attendance by type, extended technical
 * events) stay in EventRequest and OperatingHours.
 *
 * <p>Most events are one date with a start and end time. An optional end
 * date covers events that run overnight, such as 24-hour hackathons.
 */
public class NewRequestForm {

    @NotBlank(message = "Please give the event a title")
    @Size(max = 150, message = "Keep the title under 150 characters")
    private String title;

    @NotBlank(message = "Please describe the event")
    @Size(max = 2000, message = "Keep the description under 2000 characters")
    private String description;

    @NotNull(message = "Please choose an event type")
    private EventType eventType;

    @Min(value = 1, message = "Expected attendance must be at least 1")
    private Integer expectedAttendance;

    @NotNull(message = "Please choose a venue")
    private Long venueId;

    @NotNull(message = "Please choose a date")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;

    @NotNull(message = "Please choose a start time")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @NotNull(message = "Please choose an end time")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime endTime;

    /** Only for events that end on a later day (e.g. 24-hour hackathons); empty means same day. */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    @Size(max = 100, message = "Keep the approval reference under 100 characters")
    private String departmentApprovalRef;

    /** Only for event type Other: what kind of event it is. */
    @Size(max = 60, message = "Keep the kind of event under 60 characters")
    private String eventTypeDetail;

    /** Converts the form's local dates and times into instants in the college's time zone. */
    public EventDetails toDetails(ZoneId zone) {
        LocalDate lastDay = endDate != null ? endDate : date;
        return new EventDetails(title, description, eventType, expectedAttendance,
                date.atTime(startTime).atZone(zone).toInstant(),
                lastDay.atTime(endTime).atZone(zone).toInstant(),
                departmentApprovalRef, eventTypeDetail);
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }

    public String getEventTypeDetail() { return eventTypeDetail; }
    public void setEventTypeDetail(String eventTypeDetail) { this.eventTypeDetail = eventTypeDetail; }

    public Integer getExpectedAttendance() { return expectedAttendance; }
    public void setExpectedAttendance(Integer expectedAttendance) { this.expectedAttendance = expectedAttendance; }

    public Long getVenueId() { return venueId; }
    public void setVenueId(Long venueId) { this.venueId = venueId; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public String getDepartmentApprovalRef() { return departmentApprovalRef; }
    public void setDepartmentApprovalRef(String departmentApprovalRef) { this.departmentApprovalRef = departmentApprovalRef; }
}