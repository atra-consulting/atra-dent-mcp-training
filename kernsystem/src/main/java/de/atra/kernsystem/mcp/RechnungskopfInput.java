package de.atra.kernsystem.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import de.atra.kernsystem.domain.DomainException;
import de.atra.kernsystem.generated.model.Rechnungskopf;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RechnungskopfInput(

        @JsonProperty()
        @JsonPropertyDescription("Rechnungsnummer laut Rechnung, sofern erkennbar.")
        String rechnungsnummer,

        @JsonProperty()
        @JsonPropertyDescription("Ausstellungsdatum JJJJ-MM-TT, sofern erkennbar. Nicht das Behandlungsdatum.")
        LocalDate rechnungsdatum,

        @JsonProperty()
        @JsonPropertyDescription("Absender laut Briefkopf, in der Regel der Behandler.")
        String absender,

        @JsonProperty()
        @JsonPropertyDescription("Name des Patienten laut Anschriftfeld.")
        String patient,

        @JsonProperty()
        @JsonPropertyDescription("""
                Ausgewiesener Gesamtbetrag der Rechnung in EUR als Dezimalstring \
                ("482.10"), sofern erkennbar.""")
        String gesamtbetrag) {

    private static final String PATTERN = "[0-9]{1,10}(\\.[0-9]{1,2})?";

    Rechnungskopf toInternal() {
        if (gesamtbetrag != null && !gesamtbetrag.matches(PATTERN)) {
            throw new DomainException("rechnung.gesamtbetrag muss ein Dezimalstring mit hoechstens "
                    + "zwei Nachkommastellen sein. Nicht verwertbar: " + gesamtbetrag);
        }
        return new Rechnungskopf()
                .rechnungsnummer(rechnungsnummer)
                .rechnungsdatum(rechnungsdatum)
                .absender(absender)
                .patient(patient)
                .gesamtbetrag(gesamtbetrag == null ? null : new BigDecimal(gesamtbetrag));
    }
}
