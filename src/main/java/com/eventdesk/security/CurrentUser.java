package com.eventdesk.security;

import com.eventdesk.club.Club;
import com.eventdesk.user.AppUser;
import com.eventdesk.user.Role;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;

/**
 * The signed-in user as stored in the HTTP session: a small snapshot, not a
 * JPA entity. Entities don't belong in the session (they go stale and their
 * lazy relationships fail outside a transaction), so services load a fresh
 * AppUser by {@link #getId()} whenever they need to act.
 */
public final class CurrentUser implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String email;
    private String passwordHash;
    private final String displayName;
    private final Role role;
    private final Long clubId;
    private final String clubName;

    private CurrentUser(Long id, String email, String passwordHash, String displayName,
                        Role role, Long clubId, String clubName) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
        this.clubId = clubId;
        this.clubName = clubName;
    }

    public static CurrentUser from(AppUser user) {
        Club club = user.getClub();
        return new CurrentUser(user.getId(), user.getEmail(), user.getPasswordHash(), user.getDisplayName(),
                user.getRole(), club == null ? null : club.getId(), club == null ? null : club.getName());
    }

    /** Spring Security grants roles as "ROLE_" + name, which hasRole("CLUB") etc. match. */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    /** Called by Spring Security after a successful login, so the hash isn't kept in the session. */
    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }

    public Long getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Role getRole() {
        return role;
    }

    /** Role name for badges, e.g. "Club coordinator". */
    public String getRoleLabel() {
        String label = role.label();
        return Character.toUpperCase(label.charAt(0)) + label.substring(1);
    }

    public Long getClubId() {
        return clubId;
    }

    public String getClubName() {
        return clubName;
    }

    public boolean isClub() {
        return role == Role.CLUB;
    }
}