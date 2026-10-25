package com.eventdesk.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on @Scheduled methods (the expiry sweep). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}