package edu.eci.dosw.campusseguro.alertas.exception;

public class InvalidAlertRequestException extends RuntimeException {

    private final String field;

    public InvalidAlertRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
