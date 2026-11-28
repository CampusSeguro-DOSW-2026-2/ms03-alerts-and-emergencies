package edu.eci.dosw.campusseguro.alertas.scheduler;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edu.eci.dosw.campusseguro.alertas.entity.enums.DeliveryStatus;
import edu.eci.dosw.campusseguro.alertas.repository.NotificationDeliveryRepository;
import edu.eci.dosw.campusseguro.alertas.service.NotificationService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationRetrySchedulerTest {

    private static final Instant NOW = Instant.parse("2026-11-27T15:00:00Z");
    private static final UUID FIRST = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SECOND = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private NotificationDeliveryRepository deliveryRepository;

    @Mock
    private NotificationService notificationService;

    private NotificationRetryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new NotificationRetryScheduler(
                deliveryRepository, notificationService, Clock.fixed(NOW, ZoneOffset.UTC), 60_000, 1_800_000);
    }

    @Test
    void retry_shouldNotifyAgainEachActiveAlertWithFailedDeliveriesInsideTheWindow() {
        when(deliveryRepository.findAlertIdsWithDeliveriesToRetry(
                DeliveryStatus.FAILED,
                Instant.parse("2026-11-27T14:30:00Z"),
                Instant.parse("2026-11-27T14:59:00Z"),
                NotificationService.DAILY_LIMIT_REASON))
                .thenReturn(List.of(FIRST, SECOND));

        scheduler.retryFailedDeliveries();

        verify(notificationService).notifyAlertActivated(FIRST);
        verify(notificationService).notifyAlertActivated(SECOND);
    }

    @Test
    void retry_shouldContinueWithTheNextAlert_whenOneRetryFails() {
        when(deliveryRepository.findAlertIdsWithDeliveriesToRetry(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(FIRST, SECOND));
        doThrow(new IllegalStateException("MS01 caído")).when(notificationService).notifyAlertActivated(FIRST);

        scheduler.retryFailedDeliveries();

        verify(notificationService).notifyAlertActivated(SECOND);
    }

    @Test
    void retry_shouldDoNothing_whenThereIsNothingToRetry() {
        when(deliveryRepository.findAlertIdsWithDeliveriesToRetry(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        scheduler.retryFailedDeliveries();

        verifyNoInteractions(notificationService);
    }
}
