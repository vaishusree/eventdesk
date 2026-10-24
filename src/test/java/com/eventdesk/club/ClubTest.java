package com.eventdesk.club;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClubTest {

    /** Imitates a Hibernate lazy proxy: its own fields are empty, only getId() answers. */
    private static final class LazyProxy extends Club {
        private final Long proxiedId;

        LazyProxy(Long proxiedId) {
            this.proxiedId = proxiedId;
        }

        @Override
        public Long getId() {
            return proxiedId;
        }
    }

    private static Club savedClub(long id) throws Exception {
        Club club = new Club("Coding Club", "codingclub@example.org");
        Field field = Club.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(club, id);
        return club;
    }

    @Test
    void sameAsRecognisesALazyProxyOfTheSameClub() throws Exception {
        Club loaded = savedClub(1L);

        assertTrue(loaded.sameAs(new LazyProxy(1L)), "must read the id through getId(), not the field");
        assertFalse(loaded.sameAs(new LazyProxy(2L)));
    }

    @Test
    void unsavedClubsAreOnlyTheSameAsThemselves() {
        Club a = new Club("A", "a@example.org");
        Club b = new Club("B", "b@example.org");

        assertTrue(a.sameAs(a));
        assertFalse(a.sameAs(b));
        assertFalse(a.sameAs(null));
    }
}