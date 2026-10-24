package com.eventdesk.web;

import com.eventdesk.security.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    /** Sends each role to its main page after sign-in. */
    @GetMapping("/")
    public String home(@AuthenticationPrincipal CurrentUser user) {
        return user.isClub() ? "redirect:/club/requests" : "redirect:/review";
    }
}