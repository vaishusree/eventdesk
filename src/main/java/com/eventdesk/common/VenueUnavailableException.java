package com.eventdesk.common;

/** The database's overlap constraint refused the booking: the slot is taken (HTTP 409). */
public class VenueUnavailableException extends DomainException {

    public VenueUnavailableException(String venueName) {
        super(venueName + " is already booked or held for part of that time. "
                + "Check the calendar and choose another time or venue.");
    }
}