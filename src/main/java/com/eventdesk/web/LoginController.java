package com.eventdesk.web;

import com.eventdesk.user.AppUser;
import com.eventdesk.user.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Comparator;
import java.util.List;

@Controller
public class LoginController {

    private final AppUserRepository users;
    private final boolean demoLoginsEnabled;
    private final String demoPassword;

    public LoginController(AppUserRepository users,
                           @Value("${app.demo-logins-enabled:false}") boolean demoLoginsEnabled,
                           @Value("${app.demo-password:}") String demoPassword) {
        this.users = users;
        this.demoLoginsEnabled = demoLoginsEnabled;
        this.demoPassword = demoPassword;
    }

    @GetMapping("/login")
    public String login(Model model) {
        if (demoLoginsEnabled) {
            model.addAttribute("demoAccounts", demoAccounts());
            model.addAttribute("demoPassword", demoPassword);
        }
        return "login";
    }

    /** Clubs first, then venue office, dean, backup approver (the order of the Role enum). */
    private List<DemoAccount> demoAccounts() {
        return users.findAllByOrderByDisplayNameAsc().stream()
                .sorted(Comparator.comparing(AppUser::getRole))
                .map(DemoAccount::from)
                .toList();
    }

    /** One button on the sign-in page. Templates call record accessors as account.email(). */
    public record DemoAccount(String email, String name, String roleLabel) {

        static DemoAccount from(AppUser user) {
            String label = user.getRole().label();
            String roleLabel = Character.toUpperCase(label.charAt(0)) + label.substring(1);
            String name = user.getClub() != null ? user.getClub().getName() : user.getDisplayName();
            return new DemoAccount(user.getEmail(), name, roleLabel);
        }
    }
}