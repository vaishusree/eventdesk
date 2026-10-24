package com.eventdesk.web;

import com.eventdesk.common.NotFoundException;
import com.eventdesk.common.NotPermittedException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Turns "not found" and "not allowed" from page loads into proper 404 / 403
 * pages with a clear message, instead of a 500 error with a stack trace.
 * Form actions catch their own exceptions and show a message on the page.
 */
@ControllerAdvice
public class WebExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(NotFoundException e, Model model) {
        model.addAttribute("status", 404);
        model.addAttribute("error", "Not found");
        model.addAttribute("message", e.getMessage());
        return "error";
    }

    @ExceptionHandler(NotPermittedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String notPermitted(NotPermittedException e, Model model) {
        model.addAttribute("status", 403);
        model.addAttribute("error", "Not allowed");
        model.addAttribute("message", e.getMessage());
        return "error";
    }
}