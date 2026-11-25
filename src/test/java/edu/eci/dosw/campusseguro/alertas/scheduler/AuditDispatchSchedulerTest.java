package edu.eci.dosw.campusseguro.alertas.scheduler;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edu.eci.dosw.campusseguro.alertas.service.AuditDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditDispatchSchedulerTest {

    @Mock
    private AuditDispatcher dispatcher;

    @Test
    void dispatchPending_shouldDoNothing_whenTheIntegrationIsDisabled() {
        new AuditDispatchScheduler(dispatcher, false).dispatchPending();

        verifyNoInteractions(dispatcher);
    }

    @Test
    void dispatchPending_shouldAskTheDispatcherForPendingFacts() {
        new AuditDispatchScheduler(dispatcher, true).dispatchPending();

        verify(dispatcher).dispatch();
    }

    @Test
    void dispatchPending_shouldNotFail_whenTheDispatcherFails() {
        when(dispatcher.dispatch()).thenThrow(new IllegalStateException("base no disponible"));

        assertDoesNotThrow(
                () -> new AuditDispatchScheduler(dispatcher, true).dispatchPending());
    }
}
