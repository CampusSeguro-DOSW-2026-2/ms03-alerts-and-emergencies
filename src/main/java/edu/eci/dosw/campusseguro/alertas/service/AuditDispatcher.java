package edu.eci.dosw.campusseguro.alertas.service;

import edu.eci.dosw.campusseguro.alertas.client.audit.AuditClient;
import edu.eci.dosw.campusseguro.alertas.client.audit.AuditRejectedException;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Entrega a MS08 los hechos que están en la bandeja de salida.
 *
 * <p>La llamada HTTP ocurre fuera de la transacción que toma el lote, así que MS08 lento no
 * bloquea ni la base ni el negocio. Un 4xx descarta el hecho (reintentarlo no lo arregla); un
 * 5xx, un timeout o un error de red lo reprograma con backoff exponencial hasta agotar los
 * intentos.
 */
@Service
public class AuditDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AuditDispatcher.class);

    private static final int MAX_EXPONENT = 20;

    private final AuditOutboxService outboxService;
    private final AuditClient auditClient;
    private final Clock clock;
    private final int maxAttempts;
    private final long backoffBaseMs;
    private final long backoffMaxMs;

    public AuditDispatcher(
            AuditOutboxService outboxService,
            AuditClient auditClient,
            Clock clock,
            @Value("${app.audit.max-attempts:10}") int maxAttempts,
            @Value("${app.audit.backoff-base-ms:5000}") long backoffBaseMs,
            @Value("${app.audit.backoff-max-ms:3600000}") long backoffMaxMs) {
        this.outboxService = outboxService;
        this.auditClient = auditClient;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.backoffBaseMs = backoffBaseMs;
        this.backoffMaxMs = backoffMaxMs;
    }

    /**
     * Toma los pendientes vencidos y los entrega.
     *
     * @return cuántos hechos se tomaron en el lote
     */
    public int dispatch() {
        List<AuditOutboxMessage> pending = outboxService.claimDue();

        for (AuditOutboxMessage message : pending) {
            deliver(message);
        }

        return pending.size();
    }

    private void deliver(AuditOutboxMessage message) {
        try {
            auditClient.send(message.payload());
            outboxService.markSent(message.id());
        } catch (AuditRejectedException ex) {
            outboxService.markDiscarded(message.id(), ex.getMessage());
        } catch (RuntimeException ex) {
            if (message.intentos() >= maxAttempts) {
                log.warn("Hecho de auditoria sin intentos disponibles: id={} intentos={}",
                        message.id(), message.intentos());
                outboxService.markDiscarded(message.id(), reason(ex));
                return;
            }

            outboxService.markRetry(
                    message.id(),
                    reason(ex),
                    clock.instant().plusMillis(backoffMillis(message.intentos())));
        }
    }

    /**
     * Espera antes del siguiente intento: la base, el doble, el cuádruple, con un techo.
     *
     * @param attempts intentos hechos hasta ahora, incluido el que acaba de fallar
     */
    long backoffMillis(int attempts) {
        int exponent = Math.max(0, Math.min(attempts - 1, MAX_EXPONENT));
        return Math.min(backoffBaseMs * (1L << exponent), backoffMaxMs);
    }

    private String reason(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank()
                ? ex.getClass().getSimpleName()
                : message;
    }
}
