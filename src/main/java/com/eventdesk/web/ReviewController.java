package com.eventdesk.web;

import com.eventdesk.common.DomainException;
import com.eventdesk.request.EventRequestService;
import com.eventdesk.request.RequestQueryService;
import com.eventdesk.security.CurrentUser;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Review queues for the venue office, the dean and the backup approver.
 * SecurityConfig limits /review/** to those roles; EventRequest still checks
 * on every decision that this person may decide this request at this stage.
 */
@Controller
@RequestMapping("/review")
public class ReviewController {

    private final EventRequestService requestService;
    private final RequestQueryService queries;

    public ReviewController(EventRequestService requestService, RequestQueryService queries) {
        this.requestService = requestService;
        this.queries = queries;
    }

    @GetMapping
    public String queue(@AuthenticationPrincipal CurrentUser user, Model model) {
        model.addAttribute("user", user);
        model.addAttribute("queue", queries.queueFor(user.getId()));
        return "review/queue";
    }

    @PostMapping("/{id}/approve")
    public String approve(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
                          @RequestParam(defaultValue = "/review") String returnTo, RedirectAttributes redirect) {
        try {
            requestService.approve(id, user.getId());
            redirect.addFlashAttribute("success", "Approved.");
        } catch (DomainException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (OptimisticLockingFailureException e) {
            redirect.addFlashAttribute("error", ClubRequestController.CONCURRENT_UPDATE);
        }
        return "redirect:" + safe(returnTo);
    }

    @PostMapping("/{id}/reject")
    public String reject(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
                         @RequestParam(defaultValue = "") String reason,
                         @RequestParam(defaultValue = "/review") String returnTo, RedirectAttributes redirect) {
        try {
            requestService.reject(id, user.getId(), reason);
            redirect.addFlashAttribute("success", "Rejected. The club will see your reason.");
        } catch (DomainException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (OptimisticLockingFailureException e) {
            redirect.addFlashAttribute("error", ClubRequestController.CONCURRENT_UPDATE);
        }
        return "redirect:" + safe(returnTo);
    }

    /**
     * Only allow redirects back to our own pages. Accepting any returnTo
     * would be an "open redirect": a crafted link could send a reviewer to
     * another site after they click Approve.
     */
    private static String safe(String returnTo) {
        boolean ownPage = returnTo.equals("/review") || returnTo.matches("/requests/\\d+");
        return ownPage ? returnTo : "/review";
    }
}