package com.eventdesk.web;

import com.eventdesk.common.DomainException;
import com.eventdesk.request.EventRequestService;
import com.eventdesk.request.EventType;
import com.eventdesk.request.RequestQueryService;
import com.eventdesk.security.CurrentUser;
import com.eventdesk.venue.VenueRepository;
import jakarta.validation.Valid;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Pages for club coordinators. SecurityConfig already limits /club/** to the
 * CLUB role; the club itself always comes from the signed-in user, never from
 * the form, so a coordinator can't act for another club.
 *
 * <p>After every successful POST we redirect (Post/Redirect/Get), so
 * refreshing the page never submits the same action twice.
 */
@Controller
@RequestMapping("/club/requests")
public class ClubRequestController {

    static final String CONCURRENT_UPDATE =
            "Someone else updated this request at the same moment. Check its status and try again.";

    private final EventRequestService requestService;
    private final RequestQueryService queries;
    private final VenueRepository venues;
    private final ZoneId zone;

    public ClubRequestController(EventRequestService requestService, RequestQueryService queries,
                                 VenueRepository venues, ZoneId zone) {
        this.requestService = requestService;
        this.queries = queries;
        this.venues = venues;
        this.zone = zone;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal CurrentUser user, Model model) {
        model.addAttribute("user", user);
        model.addAttribute("requests", queries.forClubOf(user.getId()));
        return "club/requests";
    }

    @GetMapping("/new")
    public String newForm(@AuthenticationPrincipal CurrentUser user, Model model) {
        model.addAttribute("form", new NewRequestForm());
        addFormOptions(user, model);
        return "club/new-request";
    }

    @PostMapping
    public String submit(@AuthenticationPrincipal CurrentUser user,
                         @Valid @ModelAttribute("form") com.eventdesk.web.NewRequestForm form, BindingResult binding,
                         Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            addFormOptions(user, model);
            return "club/new-request";
        }
        try {
            requestService.submit(user.getId(), form.getVenueId(), form.toDetails(zone));
        } catch (DomainException e) {
            model.addAttribute("error", e.getMessage());
            addFormOptions(user, model);
            return "club/new-request";
        }
        redirect.addFlashAttribute("success", "Request submitted. The hall reservation office will review it.");
        return "redirect:/club/requests";
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
                         RedirectAttributes redirect) {
        try {
            requestService.cancel(id, user.getId());
            redirect.addFlashAttribute("success", "Request cancelled. The venue slot is free again.");
        } catch (DomainException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (OptimisticLockingFailureException e) {
            redirect.addFlashAttribute("error", CONCURRENT_UPDATE);
        }
        return "redirect:/club/requests";
    }

    @PostMapping("/{id}/remind")
    public String remind(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
                         RedirectAttributes redirect) {
        try {
            requestService.remind(id, user.getId());
            redirect.addFlashAttribute("success", "Reminder sent to the reviewer.");
        } catch (DomainException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (OptimisticLockingFailureException e) {
            redirect.addFlashAttribute("error", CONCURRENT_UPDATE);
        }
        return "redirect:/club/requests";
    }

    private void addFormOptions(CurrentUser user, Model model) {
        model.addAttribute("user", user);
        model.addAttribute("venues", venues.findAllByOrderByNameAsc());
        model.addAttribute("eventTypes", EventType.values());
        model.addAttribute("minDate", queries.earliestEventDate().toString());
        model.addAttribute("minDateLabel",
                queries.earliestEventDate().format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH)));
    }
}