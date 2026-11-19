package edu.eci.dosw.campusseguro.alertas.client.alerttype;

/**
 * Tipo de alerta publicado por MS06.
 *
 * @param code código canónico, por ejemplo {@code SISMO}
 * @param name etiqueta visible, por ejemplo {@code Sismo}
 */
public record RemoteAlertType(String code, String name) {
}
