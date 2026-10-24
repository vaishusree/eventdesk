package com.eventdesk.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class TimeConfig {

    /**
     * Services ask this Clock for the current time instead of calling
     * Instant.now(), so tests can fix the time and check time-based rules.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * The college's time zone. Times are stored in UTC and converted to this
     * zone only when shown to people or read from forms.
     */
    @Bean
    public ZoneId appZone(@Value("${app.time-zone}") String zone) {
        return ZoneId.of(zone);
    }
}