package edu.eci.dosw.campusseguro.alertas.exception;

public class InvalidPaginationException extends RuntimeException {

    private final String field;

    public InvalidPaginationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
