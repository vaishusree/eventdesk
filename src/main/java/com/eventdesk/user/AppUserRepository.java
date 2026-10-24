package com.eventdesk.user;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /** Login lookup. Loads the club in the same query, since the session snapshot needs its name. */
    @EntityGraph(attributePaths = "club")
    Optional<AppUser> findByEmailIgnoreCase(String email);

    /** All accounts with their club, for the demo sign-in buttons. One query, no N+1. */
    @EntityGraph(attributePaths = "club")
    List<AppUser> findAllByOrderByDisplayNameAsc();
}