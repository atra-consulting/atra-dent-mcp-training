package consulting.atra.rechenkern.domain;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String what, Object id) {
        super(what + " mit der ID " + id + " existiert nicht");
    }
}
