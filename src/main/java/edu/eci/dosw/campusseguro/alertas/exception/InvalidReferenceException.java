
package edu.eci.dosw.campusseguro.alertas.exception;

import java.util.List;

public class InvalidReferenceException extends RuntimeException {

    private final String field;
    private final List<String> invalidIds;

    public InvalidReferenceException(String field, List<String> invalidIds) {
        super("Referencias inválidas en " + field + ": "
                + String.join(", ", invalidIds));
        this.field = field;
        this.invalidIds = List.copyOf(invalidIds);
    }

    public String getField() {
        return field;
    }

    public List<String> getInvalidIds() {
        return invalidIds;
    }
}
