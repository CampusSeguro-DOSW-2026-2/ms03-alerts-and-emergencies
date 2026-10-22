
package edu.eci.dosw.campusseguro.alertas.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    VALIDATION_ERROR(
            HttpStatus.BAD_REQUEST,
            "The submitted data is invalid"),
    INVALID_REFERENCE(
            HttpStatus.BAD_REQUEST,
            "One or more references do not exist"),
    INVALID_PAGINATION(
            HttpStatus.BAD_REQUEST,
            "The query parameters are invalid"),
    INVALID_ACTIVATION_DATE(
            HttpStatus.BAD_REQUEST,
            "The activation date is invalid"),

    UNAUTHORIZED(
            HttpStatus.UNAUTHORIZED,
            "Authentication is required or the token is invalid"),
    FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "You do not have permission to perform this operation"),
    NOT_A_RECIPIENT(
            HttpStatus.FORBIDDEN,
            "You are not a recipient of this alert"),

    ALERT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "The alert does not exist"),
    RESOURCE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "The requested resource was not found"),

    METHOD_NOT_ALLOWED(
            HttpStatus.METHOD_NOT_ALLOWED,
            "The HTTP method is not allowed"),
    UNSUPPORTED_MEDIA_TYPE(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "The content type is not supported"),

    INVALID_ALERT_STATE(
            HttpStatus.CONFLICT,
            "The alert state does not allow this operation"),
    INVALID_TRANSITION(
            HttpStatus.CONFLICT,
            "The requested state transition is invalid"),
    ALERT_NOT_ACTIVE(
            HttpStatus.CONFLICT,
            "The alert is not active"),
    EVENT_MISMATCH(
            HttpStatus.CONFLICT,
            "The event does not match the alert"),
    DUPLICATE_EVENT_ALERT(
            HttpStatus.CONFLICT,
            "An active or scheduled alert already exists for this event"),
    CONCURRENT_MODIFICATION(
            HttpStatus.CONFLICT,
            "The alert was modified by another user"),

    EXTERNAL_SERVICE_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "An external service is temporarily unavailable. Please try again later"),
    INTERNAL_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
