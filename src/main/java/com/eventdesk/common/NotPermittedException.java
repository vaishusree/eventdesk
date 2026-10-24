package com.eventdesk.common;

/** The user is signed in but their role or club does not allow this action (HTTP 403). */
public class NotPermittedException extends DomainException {

    public NotPermittedException(String message) {
        super(message);
    }
}
