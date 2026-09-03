package consulting.atra.wissen.api;

import consulting.atra.wissen.api.generated.model.Fehler;
import consulting.atra.wissen.api.generated.model.Tarifschluessel;
import consulting.atra.wissen.goz.UnknownTarifException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.ValueInstantiationException;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice(basePackages = "consulting.atra.wissen.api")
class ErrorHandling {

    private static final String TYPE_BASE = "https://atra.example/fehler/";

    @ExceptionHandler(DocumentNotFoundException.class)
    ResponseEntity<Fehler> documentNotFound(DocumentNotFoundException exception,
                                                 HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "dokument-nicht-gefunden", "Dokument nicht gefunden",
                exception.getMessage(), request);
    }

    @ExceptionHandler(UnknownTarifException.class)
    ResponseEntity<Fehler> unknownTarif(UnknownTarifException exception,
                                            HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "unbekannter-tarif", "Unbekannter Tarif",
                exception.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Fehler> invalidArgument(IllegalArgumentException exception,
                                               HttpServletRequest request) {
        return badRequest(exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Fehler> unsuitableValue(MethodArgumentTypeMismatchException exception,
                                           HttpServletRequest request) {
        if (Tarifschluessel.class.equals(exception.getRequiredType())) {
            return tarifNotKnown(String.valueOf(exception.getValue()), request);
        }
        return badRequest("Der Wert '" + exception.getValue() + "' passt nicht zum Parameter '"
                + exception.getName() + "'.", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Fehler> bodyNotValid(MethodArgumentNotValidException exception,
                                               HttpServletRequest request) {
        String complaints = exception.getBindingResult().getAllErrors().stream()
                .map(ErrorHandling::complaint)
                .collect(Collectors.joining("; "));
        return badRequest("Die Anfrage ist nicht gültig: " + complaints, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<Fehler> parameterNotValid(HandlerMethodValidationException exception,
                                                 HttpServletRequest request) {
        String complaints = exception.getAllErrors().stream()
                .map(fehler -> fehler instanceof ObjectError objektfehler
                        ? complaint(objektfehler)
                        : String.valueOf(fehler.getDefaultMessage()))
                .collect(Collectors.joining("; "));
        return badRequest("Die Anfrage ist nicht gültig: " + complaints, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Fehler> violatedCondition(ConstraintViolationException exception,
                                              HttpServletRequest request) {
        String complaints = exception.getConstraintViolations().stream()
                .map(ErrorHandling::complaint)
                .collect(Collectors.joining("; "));
        return badRequest("Die Anfrage ist nicht gültig: " + complaints, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Fehler> bodyNotReadable(HttpMessageNotReadableException exception,
                                              HttpServletRequest request) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof InvalidFormatException formatfehler
                    && Tarifschluessel.class.equals(formatfehler.getTargetType())) {
                return tarifNotKnown(String.valueOf(formatfehler.getValue()), request);
            }
            if (cause instanceof ValueInstantiationException instanzfehler
                    && Tarifschluessel.class.equals(instanzfehler.getType().getRawClass())) {
                return error(HttpStatus.BAD_REQUEST, "unbekannter-tarif", "Unbekannter Tarif",
                        "Das Feld 'tarif' trägt keinen bekannten Tarifschlüssel. Gültig sind "
                                + validTarife(), request);
            }
        }
        return badRequest(
                "Der Anfragekörper ist kein gültiges JSON nach der Spezifikation.", request);
    }

    private ResponseEntity<Fehler> tarifNotKnown(String value, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "unbekannter-tarif", "Unbekannter Tarif",
                "Tarif '" + value + "' ist nicht bekannt. Gültig sind " + validTarife(), request);
    }

    private ResponseEntity<Fehler> badRequest(String beschreibung, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "fehlerhafte-anfrage", "Fehlerhafte Anfrage",
                beschreibung, request);
    }

    private ResponseEntity<Fehler> error(HttpStatus status, String typ, String title,
                                          String beschreibung, HttpServletRequest request) {
        Fehler body = new Fehler(title, status.value())
                .type(URI.create(TYPE_BASE + typ))
                .detail(beschreibung)
                .instance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    private static String complaint(ObjectError fehler) {
        String field = fehler instanceof FieldError feldfehler ? feldfehler.getField() : fehler.getObjectName();
        return "Feld '" + field + "': " + fehler.getDefaultMessage();
    }

    private static String complaint(ConstraintViolation<?> verletzung) {
        return "Feld '" + verletzung.getPropertyPath() + "': " + verletzung.getMessage();
    }

    private static String validTarife() {
        List<String> key = Arrays.stream(Tarifschluessel.values())
                .map(Tarifschluessel::getValue)
                .toList();
        return String.join(", ", key);
    }
}
