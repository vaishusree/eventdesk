package com.eventdesk.request;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every few minutes, stores EXPIRED on requests nobody decided before their
 * event started. If the server was asleep (free hosting), nothing is wrong in
 * the meantime: EventRequest.isExpired() already treats such requests as
 * expired, and this job catches up on the next run.
 */
@Component
public class ExpirySweeper {

    private static final Logger log = LoggerFactory.getLogger(ExpirySweeper.class);

    private final EventRequestService requests;

    public ExpirySweeper(EventRequestService requests) {
        this.requests = requests;
    }

    @Scheduled(initialDelayString = "${app.expiry-sweep.initial-delay-ms:60000}",
            fixedDelayString = "${app.expiry-sweep.interval-ms:300000}")
    public void sweep() {
        int expired = requests.expireDue();
        if (expired > 0) {
            log.info("Marked {} request(s) as expired: no decision before the event started", expired);
        }
    }
}