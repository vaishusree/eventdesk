package com.eventdesk.common;

/** The action is not valid for the request's current status (HTTP 409). */
public class IllegalTransitionException extends DomainException {

    public IllegalTransitionException(String message) {
        super(message);
    }
}
