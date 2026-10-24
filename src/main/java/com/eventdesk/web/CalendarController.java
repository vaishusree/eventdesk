package com.eventdesk.web;

import com.eventdesk.request.RequestQueryService;
import com.eventdesk.security.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** The shared calendar: every signed-in user sees every booking. */
@Controller
public class CalendarController {

    private final RequestQueryService queries;

    public CalendarController(RequestQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/calendar")
    public String calendar(@AuthenticationPrincipal CurrentUser user, Model model) {
        model.addAttribute("user", user);
        model.addAttribute("days", queries.calendar());
        return "calendar";
    }
}