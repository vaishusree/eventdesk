package com.eventdesk.club;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "clubs")
public class Club {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    protected Club() {
        // for JPA
    }

    public Club(String name, String email) {
        this.name = name;
        this.email = email;
    }

    /**
     * Same club? Compares database ids once the entity is saved, and falls back
     * to object identity for unsaved instances (as in unit tests).
     *
     * <p>Uses other.getId(), never other.id: a lazily loaded club is a Hibernate
     * proxy whose own fields are empty, and only its getId() returns the id.
     * Reading other.id directly would make every ownership check fail.
     */
    public boolean sameAs(Club other) {
        if (other == null) {
            return false;
        }
        if (this == other) {
            return true;
        }
        return id != null && id.equals(other.getId());
        //other.id reads the field directly. If other is a Hibernate proxy, that field is empty (null),
        // so the comparison is always false, and clubs can’t cancel or send reminders for their own requests.
        //other.getId() calls a method. The proxy handles it and returns the real id.
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }
}
