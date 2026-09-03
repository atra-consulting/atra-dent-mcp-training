package consulting.atra.agenten.model;

import java.util.Objects;

public class ModelUnreachableException extends RuntimeException {

    private final transient String modell;

    public ModelUnreachableException(String modell, Throwable cause) {
        this("Das Modell '" + modell + "' ist nicht erreichbar: " + message(cause),
                modell, cause);
    }

    protected ModelUnreachableException(String message, String modell, Throwable cause) {
        super(message, cause);
        this.modell = Objects.requireNonNull(modell, "modell");
    }

    public String modell() {
        return modell;
    }

    private static String message(Throwable cause) {
        String text = cause == null ? null : cause.getMessage();
        return text == null || text.isBlank() ? String.valueOf(cause) : text;
    }
}
