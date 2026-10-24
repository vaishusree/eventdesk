package com.eventdesk.request;

import com.eventdesk.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One entry in a request's audit history. Created only by {@link EventRequest}
 * and never modified afterwards, so the history always explains how the
 * request reached its current status.
 */
@Entity
@Table(name = "request_actions")
public class RequestAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_request_id", nullable = false)
    private EventRequest eventRequest;

    /** Null only for system actions (EXPIRED). Enforced by actor_required_unless_system in V5. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private AppUser actor;
    //actor can only be null, if the system performs the action
    //i.e when the event request expired

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActionType action;

    /** Null for the initial SUBMITTED entry. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private RequestStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private RequestStatus toStatus;

    @Column(length = 500)
    private String remark;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RequestAction() {
        // for JPA
    }

    RequestAction(EventRequest eventRequest, AppUser actor, ActionType action,
                  RequestStatus fromStatus, RequestStatus toStatus, String remark, Instant createdAt) {
        this.eventRequest = eventRequest;
        this.actor = actor;
        this.action = action;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.remark = remark;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public EventRequest getEventRequest() {
        return eventRequest;
    }

    public AppUser getActor() {
        return actor;
    }

    public ActionType getAction() {
        return action;
    }

    public RequestStatus getFromStatus() {
        return fromStatus;
    }

    public RequestStatus getToStatus() {
        return toStatus;
    }

    public String getRemark() {
        return remark;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
