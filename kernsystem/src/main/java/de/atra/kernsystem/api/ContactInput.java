package de.atra.kernsystem.api;

import de.atra.kernsystem.domain.DomainException;
import de.atra.kernsystem.generated.model.Adresse;
import de.atra.kernsystem.generated.model.KundeAendern;

public record ContactInput(String email, String phone, String street,
                           String postalCode, String city, String country) {

    public KundeAendern toInternal() {
        boolean adresseStarted = street != null || postalCode != null
                || city != null || country != null;
        boolean adresseComplete = street != null && postalCode != null
                && city != null && country != null;
        if (adresseStarted && !adresseComplete) {
            throw new DomainException("Eine Anschrift wird nur vollstaendig geaendert: "
                    + "strasse, plz, ort und land gehoeren zusammen und muessen gemeinsam "
                    + "angegeben werden, auch die unveraenderten");
        }
        if (email == null && phone == null && !adresseComplete) {
            throw new DomainException("Es wurde nichts angegeben, was zu aendern waere: "
                    + "erwartet wird mindestens email, telefon oder eine vollstaendige Anschrift");
        }

        KundeAendern change = new KundeAendern().email(email).telefon(phone);
        return adresseComplete
                ? change.adresse(new Adresse().strasse(street).plz(postalCode)
                        .ort(city).land(country))
                : change;
    }
}
