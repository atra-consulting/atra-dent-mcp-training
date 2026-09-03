package consulting.atra.rechenkern.api;

import consulting.atra.rechenkern.domain.DomainException;
import consulting.atra.rechenkern.domain.NotFoundException;
import consulting.atra.rechenkern.generated.model.Fehler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice(basePackages = "consulting.atra.rechenkern.api")
class ErrorHandling {

    private static final String TYPE_BASE = "https://atra.example/fehler/";

    @ExceptionHandler(DomainException.class)
    ResponseEntity<Fehler> domainException(DomainException exception,
                                           HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "fehlerhafte-anfrage", "Fehlerhafte Anfrage",
                exception.getMessage(), request);
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<Fehler> notFoundException(NotFoundException exception,
                                             HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "nicht-gefunden", "Nicht gefunden",
                exception.getMessage(), request);
    }

    private ResponseEntity<Fehler> error(HttpStatus status, String type, String title,
                                         String detail, HttpServletRequest request) {
        Fehler body = new Fehler(title, status.value())
                .type(URI.create(TYPE_BASE + type))
                .detail(detail)
                .instance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }
}
