package edu.eci.dosw.campusseguro.alertas.exception;

public class InvalidActivationDateException extends RuntimeException {

    public InvalidActivationDateException() {
        super("La fecha de activación debe ser posterior a la fecha y hora actuales");
    }
}