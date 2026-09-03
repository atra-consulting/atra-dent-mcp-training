package de.atra.kernsystem.api;

import de.atra.kernsystem.domain.ConflictException;
import de.atra.kernsystem.domain.DomainException;
import de.atra.kernsystem.domain.NotFoundException;
import de.atra.kernsystem.generated.model.Fehler;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ErrorHandling extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandling.class);

    static final URI TYPE_VALIDATION = URI.create("https://atra.example/fehler/validierung");
    static final URI TYPE_NOT_FOUND = URI.create("https://atra.example/fehler/nicht-gefunden");
    static final URI TYPE_AUTHENTICATION = URI.create("https://atra.example/fehler/authentifizierung");
    static final URI TYPE_REQUEST = URI.create("https://atra.example/fehler/anfrage");
    static final URI TYPE_INTERNAL_ERROR = URI.create("https://atra.example/fehler/interner-fehler");
    static final URI TYPE_CONFLICT = URI.create("https://atra.example/fehler/konflikt");

    private static final String INTERNAL_TITLE = "Interner Fehler";
    private static final String INTERNAL_DETAIL = "Ein unerwarteter Fehler ist aufgetreten";

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<Fehler> notFound(NotFoundException exception) {
        log.debug("{}", exception.getMessage());
        return response(HttpStatus.NOT_FOUND, TYPE_NOT_FOUND,
                "Ressource nicht gefunden", exception.getMessage());
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<Fehler> domain(DomainException exception) {
        log.debug("{}", exception.getMessage());
        return response(HttpStatus.BAD_REQUEST, TYPE_VALIDATION,
                "Validierung fehlgeschlagen", exception.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<Fehler> conflict(ConflictException exception) {
        log.debug("{}", exception.getMessage());
        return response(HttpStatus.CONFLICT, TYPE_CONFLICT, "Konflikt", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Fehler> typeConversionError(MethodArgumentTypeMismatchException exception) {
        log.debug("Type conversion error: {}", exception.getValue());
        String detail = "Parameter '" + exception.getName() + "' hat einen ungueltigen Wert";
        return response(HttpStatus.BAD_REQUEST, TYPE_VALIDATION,
                "Validierung fehlgeschlagen", detail);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Fehler> parameterValidation(ConstraintViolationException exception) {
        String detail = exception.getConstraintViolations().stream()
                .map(violation -> "Parameter '" + lastPathSegment(violation.getPropertyPath())
                        + "' " + violation.getMessage())
                .collect(Collectors.joining("; "));
        log.debug("Parameter validation failed: {}", detail);
        return response(HttpStatus.BAD_REQUEST, TYPE_VALIDATION,
                "Validierung fehlgeschlagen", withoutUmlauts(detail));
    }

    private static String lastPathSegment(Path path) {
        String full = path.toString();
        int point = full.lastIndexOf('.');
        return point < 0 ? full : full.substring(point + 1);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Fehler> unexpectedError(Exception exception) {
        log.error("Unexpected error", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, TYPE_INTERNAL_ERROR,
                INTERNAL_TITLE, INTERNAL_DETAIL);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(f -> "Feld '" + f.getField() + "' " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.debug("Validation failed: {}", detail);
        return handleExceptionInternal(exception,
                ProblemDetail.forStatusAndDetail(status, withoutUmlauts(detail)),
                headers, status, request);
    }

    private static String withoutUmlauts(String text) {
        return text
                .replace("ä", "ae").replace("Ä", "Ae")
                .replace("ö", "oe").replace("Ö", "Oe")
                .replace("ü", "ue").replace("Ü", "Ue")
                .replace("ß", "ss");
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers,
                                                          HttpStatusCode status,
                                                          WebRequest request) {
        ErrorKind kind = kindFor(status);
        String detail = status.is5xxServerError()
                ? INTERNAL_DETAIL
                : (body instanceof ProblemDetail problem ? problem.getDetail() : kind.title());
        return ResponseEntity.status(status)
                .headers(headers)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(error(kind.type(), kind.title(), status, detail));
    }

    static ResponseEntity<Fehler> response(HttpStatus status, URI type, String title,
                                           String detail) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(error(type, title, status, detail));
    }

    private static Fehler error(URI type, String title, HttpStatusCode status, String detail) {
        return new Fehler()
                .type(type)
                .title(title)
                .status(status.value())
                .detail(detail);
    }

    private record ErrorKind(URI type, String title) {
    }

    private static ErrorKind kindFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> new ErrorKind(TYPE_VALIDATION, "Validierung fehlgeschlagen");
            case 401 -> new ErrorKind(TYPE_AUTHENTICATION, "Nicht authentifiziert");
            case 404 -> new ErrorKind(TYPE_NOT_FOUND, "Ressource nicht gefunden");
            case 405 -> new ErrorKind(TYPE_REQUEST, "Methode nicht erlaubt");
            case 406 -> new ErrorKind(TYPE_REQUEST, "Medientyp nicht lieferbar");
            case 409 -> new ErrorKind(TYPE_CONFLICT, "Konflikt");
            case 415 -> new ErrorKind(TYPE_REQUEST, "Medientyp nicht unterstuetzt");
            default -> status.is5xxServerError()
                    ? new ErrorKind(TYPE_INTERNAL_ERROR, INTERNAL_TITLE)
                    : new ErrorKind(TYPE_REQUEST, "Anfrage nicht verarbeitbar");
        };
    }
}
