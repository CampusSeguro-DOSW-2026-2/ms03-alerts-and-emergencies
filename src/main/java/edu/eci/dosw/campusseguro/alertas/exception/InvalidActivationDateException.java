
package edu.eci.dosw.campusseguro.alertas.exception;

public class InvalidActivationDateException extends RuntimeException {

    public InvalidActivationDateException() {
        super("The activation date and time must be in the future");
    }
}
