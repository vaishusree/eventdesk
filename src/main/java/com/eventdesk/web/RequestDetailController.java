package com.eventdesk.web;

import com.eventdesk.request.RequestQueryService;
import com.eventdesk.security.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** One request with its proposal and full audit history. */
@Controller
public class RequestDetailController {

    private final RequestQueryService queries;

    public RequestDetailController(RequestQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/requests/{id}")
    public String detail(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, Model model) {
        model.addAttribute("user", user);
        model.addAttribute("detail", queries.detail(id, user.getId()));
        return "request-detail";
    }
}