package com.eventdesk.common;

/** The submitted data breaks a business rule, e.g. ends before it starts (HTTP 400). */
public class InvalidRequestException extends DomainException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
