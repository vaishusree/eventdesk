package com.eventdesk.common;

/**
 * Base class for business-rule failures. The message is written for the end
 * user, so the web layer can show it as-is.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
