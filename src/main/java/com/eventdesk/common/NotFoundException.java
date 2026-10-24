package com.eventdesk.common;

/** The requested record does not exist (HTTP 404). */
public class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        super(message);
    }
}