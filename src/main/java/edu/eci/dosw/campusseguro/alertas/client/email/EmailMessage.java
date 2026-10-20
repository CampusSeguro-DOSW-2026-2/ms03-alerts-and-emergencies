package edu.eci.dosw.campusseguro.alertas.client.email;

public record EmailMessage(String to, String toName, String subject, String html, String text) {
}
