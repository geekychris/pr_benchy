package com.prbotbenchmark.runner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledRunScheduler {

    private final ScheduledRunService scheduledRunService;

    @Scheduled(fixedDelay = 60000)
    public void checkSchedules() {
        try {
            scheduledRunService.checkAndTriggerDue();
        } catch (Exception e) {
            log.error("Error checking scheduled runs", e);
        }
    }
}
