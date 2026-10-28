package edu.eci.dosw.campusseguro.alertas.exception;

import edu.eci.dosw.campusseguro.alertas.security.CorrelationIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
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
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AlertNotFoundException.class)
    public ResponseEntity<Object> handleAlertNotFound(
            AlertNotFoundException ex) {
        return respond(
                ErrorCode.ALERT_NOT_FOUND,
                ex.getMessage(),
                List.of());
    }

    @ExceptionHandler(InvalidAlertStateException.class)
    public ResponseEntity<Object> handleInvalidAlertState(
            InvalidAlertStateException ex) {
        return respond(
                ErrorCode.INVALID_ALERT_STATE,
                ex.getMessage(),
                List.of());
    }

    @ExceptionHandler(InvalidTransitionException.class)
    public ResponseEntity<Object> handleInvalidTransition(
            InvalidTransitionException ex) {
        return respond(
                ErrorCode.INVALID_TRANSITION,
                ex.getMessage(),
                List.of());
    }

    @ExceptionHandler(InvalidActivationDateException.class)
    public ResponseEntity<Object> handleInvalidActivationDate(
            InvalidActivationDateException ex) {
        return respond(
                ErrorCode.INVALID_ACTIVATION_DATE,
                ex.getMessage(),
                List.of());
    }

    @ExceptionHandler(InvalidReferenceException.class)
    public ResponseEntity<Object> handleInvalidReference(
            InvalidReferenceException ex) {

        List<ErrorResponse.ErrorDetail> errors = ex.getInvalidIds().stream()
                .map(id -> new ErrorResponse.ErrorDetail(
                        ex.getField(),
                        "La referencia no existe: " + id))
                .toList();

        return respond(
                ErrorCode.INVALID_REFERENCE,
                ErrorCode.INVALID_REFERENCE.defaultMessage(),
                errors);
    }

    @ExceptionHandler(InvalidPaginationException.class)
    public ResponseEntity<Object> handleInvalidPagination(
            InvalidPaginationException ex) {

        List<ErrorResponse.ErrorDetail> errors = List.of(
                new ErrorResponse.ErrorDetail(
                        ex.getField(),
                        ex.getMessage()));

        return respond(
                ErrorCode.INVALID_PAGINATION,
                ErrorCode.INVALID_PAGINATION.defaultMessage(),
                errors);
    }

    @ExceptionHandler(ExternalServiceUnavailableException.class)
    public ResponseEntity<Object> handleExternalServiceUnavailable(
            ExternalServiceUnavailableException ex,
            HttpServletRequest request) {

        log.warn(
                "Servicio externo no disponible en {}: {}",
                request.getRequestURI(),
                ex.getMessage(),
                ex);

        return respond(
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE.defaultMessage(),
                List.of());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Object> handleOptimisticLocking(
            OptimisticLockingFailureException ex,
            HttpServletRequest request) {

        log.warn(
                "Modificación concurrente detectada en {}",
                request.getRequestURI());

        return respond(
                ErrorCode.CONCURRENT_MODIFICATION,
                ErrorCode.CONCURRENT_MODIFICATION.defaultMessage(),
                List.of());
    }

    @ExceptionHandler(SimulationMismatchException.class)
    public ResponseEntity<Object> handleSimulationMismatch(
            SimulationMismatchException ex) {
        return respond(
                ErrorCode.SIMULATION_MISMATCH,
                ex.getMessage(),
                List.of());
    }

    @ExceptionHandler(DuplicateSimulationAlertException.class)
    public ResponseEntity<Object> handleDuplicateSimulationAlert(
            DuplicateSimulationAlertException ex) {
        return respond(
                ErrorCode.DUPLICATE_SIMULATION_ALERT,
                ex.getMessage(),
                List.of());
    }

    @ExceptionHandler(NotARecipientException.class)
    public ResponseEntity<Object> handleNotARecipient(
            NotARecipientException ex) {
        return respond(
                ErrorCode.NOT_A_RECIPIENT,
                ex.getMessage(),
                List.of());
    }

    @ExceptionHandler(AlertNotActiveException.class)
    public ResponseEntity<Object> handleAlertNotActive(
            AlertNotActiveException ex) {
        return respond(
                ErrorCode.ALERT_NOT_ACTIVE,
                ex.getMessage(),
                List.of());
    }

    @ExceptionHandler(InvalidAlertRequestException.class)
    public ResponseEntity<Object> handleInvalidAlertRequest(
            InvalidAlertRequestException ex) {
        return respond(
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.defaultMessage(),
                List.of(new ErrorResponse.ErrorDetail(
                        ex.getField(),
                        ex.getMessage())));
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<Object> handleForbiddenOperation(
            ForbiddenOperationException ex) {
        return respond(
                ErrorCode.FORBIDDEN,
                ex.getMessage(),
                List.of());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(
            ConstraintViolationException ex) {

        List<ErrorResponse.ErrorDetail> errors =
                ex.getConstraintViolations().stream()
                        .map(violation -> new ErrorResponse.ErrorDetail(
                                violation.getPropertyPath().toString(),
                                violation.getMessage()))
                        .toList();

        return respond(
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.defaultMessage(),
                errors);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(
            AccessDeniedException ex) {
        return respond(
                ErrorCode.FORBIDDEN,
                ErrorCode.FORBIDDEN.defaultMessage(),
                List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthentication(
            AuthenticationException ex) {
        return respond(
                ErrorCode.UNAUTHORIZED,
                ErrorCode.UNAUTHORIZED.defaultMessage(),
                List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(
            Exception ex,
            HttpServletRequest request) {

        log.error(
                "Error inesperado en {}",
                request.getRequestURI(),
                ex);

        return respond(
                ErrorCode.INTERNAL_ERROR,
                ErrorCode.INTERNAL_ERROR.defaultMessage(),
                List.of());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        List<ErrorResponse.ErrorDetail> errors = new ArrayList<>();

        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.add(
                        new ErrorResponse.ErrorDetail(
                                error.getField(),
                                error.getDefaultMessage())));

        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> errors.add(
                        new ErrorResponse.ErrorDetail(
                                error.getObjectName(),
                                error.getDefaultMessage())));

        return respond(
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.defaultMessage(),
                errors);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        return respond(
                ErrorCode.VALIDATION_ERROR,
                "El cuerpo de la solicitud falta, está mal formado o contiene valores inválidos",
                List.of());
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        String field =
                ex instanceof MethodArgumentTypeMismatchException mismatch
                        ? mismatch.getName()
                        : ex.getPropertyName();

        List<ErrorResponse.ErrorDetail> errors = List.of(
                new ErrorResponse.ErrorDetail(
                        field,
                        "El valor enviado no es válido"));

        return respond(
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.defaultMessage(),
                errors);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {

        ErrorCode code = codeFor(statusCode);

        ErrorResponse response = ErrorResponse.of(
                code,
                code.defaultMessage(),
                List.of(),
                CorrelationIds.current());

        return ResponseEntity.status(statusCode)
                .headers(headers)
                .body(response);
    }

    private ErrorCode codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> ErrorCode.VALIDATION_ERROR;
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.RESOURCE_NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 409 -> ErrorCode.INVALID_ALERT_STATE;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case 503 -> ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE;
            default -> status.is5xxServerError()
                    ? ErrorCode.INTERNAL_ERROR
                    : ErrorCode.VALIDATION_ERROR;
        };
    }

    private ResponseEntity<Object> respond(
            ErrorCode code,
            String message,
            List<ErrorResponse.ErrorDetail> errors) {

        ErrorResponse response = ErrorResponse.of(
                code,
                message,
                errors,
                CorrelationIds.current());

        return ResponseEntity.status(code.status())
                .body(response);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrity(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {

        log.warn(
                "Violación de integridad de datos en {}",
                request.getRequestURI(),
                ex);

        return respond(
                ErrorCode.DUPLICATE_SIMULATION_ALERT,
                ErrorCode.DUPLICATE_SIMULATION_ALERT.defaultMessage(),
                List.of());
    }
}
