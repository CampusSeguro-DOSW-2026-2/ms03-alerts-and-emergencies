package edu.eci.dosw.campusseguro.alertas.scheduler;

import edu.eci.dosw.campusseguro.alertas.service.AuditDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Despierta al despachador de auditoría.
 *
 * <p>Corre siempre con el mismo hilo del planificador y no mantiene estado: si MS08 está caído, el
 * despachador reprograma los hechos y el siguiente ciclo vuelve a intentarlo.
 */
@Component
public class AuditDispatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuditDispatchScheduler.class);

    private final AuditDispatcher dispatcher;
    private final boolean enabled;

    public AuditDispatchScheduler(
            AuditDispatcher dispatcher,
            @Value("${app.audit.enabled:true}") boolean enabled) {
        this.dispatcher = dispatcher;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${app.audit.dispatch-interval-ms:5000}")
    public void dispatchPending() {
        if (!enabled) {
            return;
        }

        try {
            int claimed = dispatcher.dispatch();
            if (claimed > 0) {
                log.debug("Auditoria despachada: {} hechos", claimed);
            }
        } catch (RuntimeException ex) {
            log.warn("No se pudo despachar la auditoria pendiente: {}", ex.getMessage());
        }
    }
}
