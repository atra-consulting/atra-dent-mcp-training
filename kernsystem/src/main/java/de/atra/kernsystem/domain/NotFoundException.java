package de.atra.kernsystem.domain;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String what, Object id) {
        super(what + " mit der ID " + id + " ist nicht gefunden");
    }
}
