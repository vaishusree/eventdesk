package com.eventdesk.request;

import com.eventdesk.common.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertThrows;

class OperatingHoursTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalDate DAY = LocalDate.of(2026, 12, 10);

    private static EventDetails at(LocalTime start, LocalTime end) {
        return new EventDetails("t", "d", EventType.OTHER, null,
                DAY.atTime(start).atZone(IST).toInstant(), DAY.atTime(end).atZone(IST).toInstant());
    }

    @Test
    void eventsWithinCampusHoursAreAccepted() {
        OperatingHours.check(at(LocalTime.of(7, 0), LocalTime.of(22, 0)), IST);
        OperatingHours.check(at(LocalTime.of(15, 0), LocalTime.of(17, 30)), IST);
    }

    @Test
    void earlyMorningStartIsRejected() {
        // The 24-hour-clock slip: 03:00 typed for 3 PM
        assertThrows(InvalidRequestException.class,
                () -> OperatingHours.check(at(LocalTime.of(3, 0), LocalTime.of(6, 0)), IST));
        assertThrows(InvalidRequestException.class,
                () -> OperatingHours.check(at(LocalTime.of(6, 59), LocalTime.of(9, 0)), IST));
    }

    @Test
    void endingAfterTenPmIsRejected() {
        assertThrows(InvalidRequestException.class,
                () -> OperatingHours.check(at(LocalTime.of(20, 0), LocalTime.of(22, 1)), IST));
    }

    @Test
    void endingAtMidnightIsRejected() {
        // 20:00 to 00:00 runs two hours past closing time, even though the clock reads 00:00
        EventDetails toMidnight = new EventDetails("t", "d", EventType.OTHER, null,
                DAY.atTime(20, 0).atZone(IST).toInstant(), DAY.plusDays(1).atStartOfDay(IST).toInstant());
        assertThrows(InvalidRequestException.class, () -> OperatingHours.check(toMidnight, IST));
    }
}