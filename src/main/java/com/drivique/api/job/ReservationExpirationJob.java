package com.drivique.api.job;

import com.drivique.api.dto.ExpiredReservationsSummaryDTO;
import com.drivique.api.service.ReservationExpirationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationExpirationJob {

    private static final Logger LOG = LoggerFactory.getLogger(ReservationExpirationJob.class);

    private final ReservationExpirationService expirationService;

    public ReservationExpirationJob(ReservationExpirationService expirationService) {
        this.expirationService = expirationService;
    }

    @Scheduled(cron = "${app.jobs.reservation-expiration.cron:0 */5 * * * *}")
    public void executeReservationExpiration() {
        LOG.info("Starting scheduled reservation expiration job...");
        try {
            ExpiredReservationsSummaryDTO result = expirationService.expirePendingReservations();
            LOG.info("Scheduled reservation expiration job completed. Expired count: {}", result.expiredCount());
        } catch (Exception ex) {
            LOG.error("Error executing scheduled reservation expiration job", ex);
        }
    }
}
