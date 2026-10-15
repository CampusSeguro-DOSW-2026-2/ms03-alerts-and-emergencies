package edu.eci.dosw.campusseguro.alertas.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(AlertNotFoundException.class)
    public ResponseEntity<Object> handleAlertNotFound(AlertNotFoundException ex, HttpServletRequest request) {
        return respond(ErrorCode.ALERT_NOT_FOUND, ex.getMessage(), request.getRequestURI(), List.of());
    }

    @ExceptionHandler(InvalidAlertStateException.class)
    public ResponseEntity<Object> handleInvalidAlertState(InvalidAlertStateException ex, HttpServletRequest request) {
        return respond(ErrorCode.INVALID_ALERT_STATE, ex.getMessage(), request.getRequestURI(), List.of());
    }
    @ExceptionHandler(InvalidTransitionException.class)
    public ResponseEntity<Object> handleInvalidTransition(InvalidTransitionException ex, HttpServletRequest request) {
        return respond(ErrorCode.INVALID_TRANSITION, ex.getMessage(), request.getRequestURI(), List.of());
    }
    @ExceptionHandler(InvalidActivationDateException.class)
    public ResponseEntity<Object> handleInvalidActivationDate(InvalidActivationDateException ex, HttpServletRequest request) {
        return respond(ErrorCode.INVALID_ACTIVATION_DATE, ex.getMessage(), request.getRequestURI(), List.of());
    }
    @ExceptionHandler(InvalidReferenceException.class)
    public ResponseEntity<Object> handleInvalidReference(InvalidReferenceException ex, HttpServletRequest request) {
        List<ErrorResponse.Detail> details = ex.getInvalidIds().stream()
                .map(id -> new ErrorResponse.Detail(ex.getField(), "El identificador no existe: " + id))
                .toList();
        return respond(ErrorCode.INVALID_REFERENCE, ErrorCode.INVALID_REFERENCE.defaultMessage(), request.getRequestURI(), details);
    }

    @ExceptionHandler(InvalidPaginationException.class)
    public ResponseEntity<Object> handleInvalidPagination(InvalidPaginationException ex, HttpServletRequest request) {
        List<ErrorResponse.Detail> details = List.of(new ErrorResponse.Detail(ex.getField(), ex.getMessage()));
        return respond(ErrorCode.INVALID_PAGINATION, ErrorCode.INVALID_PAGINATION.defaultMessage(), request.getRequestURI(), details);
    }

    @ExceptionHandler(ExternalServiceUnavailableException.class)
    public ResponseEntity<Object> handleExternalServiceUnavailable(ExternalServiceUnavailableException ex, HttpServletRequest request) {
        log.warn("Servicio externo no disponible: {}", ex.getMessage(), ex);
        return respond(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, ex.getMessage(), request.getRequestURI(), List.of());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Object> handleOptimisticLocking(OptimisticLockingFailureException ex, HttpServletRequest request) {
        return respond(ErrorCode.CONCURRENT_MODIFICATION, ErrorCode.CONCURRENT_MODIFICATION.defaultMessage(), request.getRequestURI(), List.of());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<ErrorResponse.Detail> details = ex.getConstraintViolations().stream()
                .map(violation -> new ErrorResponse.Detail(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return respond(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), request.getRequestURI(), details);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return respond(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.defaultMessage(), request.getRequestURI(), List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return respond(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.defaultMessage(), request.getRequestURI(), List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Error no controlado en {}", request.getRequestURI(), ex);
        return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), request.getRequestURI(), List.of());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErrorResponse.Detail> details = new ArrayList<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> details.add(new ErrorResponse.Detail(error.getField(), error.getDefaultMessage())));
        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> details.add(new ErrorResponse.Detail(error.getObjectName(), error.getDefaultMessage())));
        return respond(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), pathOf(request), details);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return respond(ErrorCode.VALIDATION_ERROR, "El cuerpo de la solicitud no es un JSON válido", pathOf(request), List.of());
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String field = ex instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName() : ex.getPropertyName();
        List<ErrorResponse.Detail> details = List.of(new ErrorResponse.Detail(field, "El valor enviado no es válido"));
        return respond(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), pathOf(request), details);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ErrorCode code = codeFor(statusCode);
        ErrorResponse response = new ErrorResponse(
                clock.instant(), statusCode.value(), code.name(), code.defaultMessage(), pathOf(request), List.of());
        return ResponseEntity.status(statusCode).headers(headers).body(response);
    }

    private ErrorCode codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> ErrorCode.RESOURCE_NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> status.is5xxServerError() ? ErrorCode.INTERNAL_ERROR : ErrorCode.VALIDATION_ERROR;
        };
    }

    private String pathOf(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest ? servletRequest.getRequest().getRequestURI() : "";
    }

    private ResponseEntity<Object> respond(ErrorCode code, String message, String path, List<ErrorResponse.Detail> details) {
        ErrorResponse response = ErrorResponse.of(clock.instant(), code, message, path, details);
        return ResponseEntity.status(code.status()).body(response);
    }
}
