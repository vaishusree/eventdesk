package com.eventdesk.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Session-based login (server-rendered pages, so no JWT needed).
 *
 * <p>URL rules here are the first layer: a club can't even open a review page.
 * The second layer is inside EventRequest, which checks the role and club on
 * every action, so a wrong URL rule still can't let anyone approve.
 *
 * <p>CSRF protection stays on (the default); Thymeleaf adds the token to
 * every form automatically.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/js/**", "/favicon.ico", "/error").permitAll()
                        .requestMatchers("/club/**").hasRole("CLUB")
                        .requestMatchers("/review/**").hasAnyRole("VENUE_OFFICE", "DEAN", "BACKUP_APPROVER")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("email")
                        .defaultSuccessUrl("/", true)
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll());
        return http.build();
    }
}