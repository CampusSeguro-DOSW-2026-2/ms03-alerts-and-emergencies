package edu.eci.dosw.campusseguro.alertas.client.email;

import edu.eci.dosw.campusseguro.alertas.exception.EmailSendException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface EmailSender {

    void send(EmailMessage message);

    /**
     * Envía todos los correos de una alerta a la vez, para que lleguen al mismo tiempo.
     *
     * @return el motivo de cada correo que no se pudo enviar, por su posición en {@code messages}; vacío si
     *         todos salieron. Por defecto se envían uno por uno.
     */
    default Map<Integer, String> sendAll(List<EmailMessage> messages) {
        Map<Integer, String> failures = new LinkedHashMap<>();
        for (int i = 0; i < messages.size(); i++) {
            try {
                send(messages.get(i));
            } catch (EmailSendException ex) {
                failures.put(i, ex.getMessage());
            }
        }
        return failures;
    }
}
