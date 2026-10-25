package com.eventdesk.web;

import com.eventdesk.request.RequestQueryService;
import com.eventdesk.security.CurrentUser;
import com.eventdesk.venue.VenueRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** The shared calendar: every signed-in user sees every booking, one month at a time. */
@Controller
public class CalendarController {

    private static final DateTimeFormatter EARLIEST = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final RequestQueryService queries;
    private final VenueRepository venues;

    public CalendarController(RequestQueryService queries, VenueRepository venues) {
        this.queries = queries;
        this.venues = venues;
    }

    /** /calendar?month=2026-11&venue=3 — both optional. */
    @GetMapping("/calendar")
    public String calendar(@AuthenticationPrincipal CurrentUser user,
                           @RequestParam(required = false) YearMonth month,
                           @RequestParam(required = false) Long venue,
                           Model model) {
        YearMonth shown = month != null ? month : queries.defaultCalendarMonth();
        model.addAttribute("user", user);
        model.addAttribute("view", queries.calendar(shown, venue, user.getId()));
        model.addAttribute("venues", venues.findAllByOrderByNameAsc());
        LocalDate earliest = queries.earliestEventDate();   // first day a club may request (30-day notice)
        model.addAttribute("earliest", earliest);
        model.addAttribute("earliestLabel", EARLIEST.format(earliest));
        return "calendar";
    }
}