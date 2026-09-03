package de.atra.kernsystem.mcp;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import de.atra.kernsystem.domain.DomainException;
import de.atra.kernsystem.generated.model.Arztauskunft;

public record ArztauskunftInput(

        @JsonPropertyDescription("plausibel oder auffaellig.")
        String plausibilitaet,

        @JsonPropertyDescription("ueblich oder fraglich.")
        String notwendigkeit,

        @JsonPropertyDescription("Text der Auskunft, unveraendert.")
        String text,

        @JsonPropertyDescription("Der Vorbehalt des Arztservice, unveraendert.")
        String hinweis) {

    Arztauskunft toInternal() {
        if (plausibilitaet == null) {
            throw new DomainException("arztauskunft.plausibilitaet ist Pflicht: plausibel oder auffaellig");
        }
        Arztauskunft.PlausibilitaetEnum plausibilitaetWert;
        try {
            plausibilitaetWert = Arztauskunft.PlausibilitaetEnum.fromValue(plausibilitaet);
        } catch (IllegalArgumentException cause) {
            throw new DomainException("arztauskunft.plausibilitaet muss plausibel oder auffaellig sein. "
                    + "Nicht verwertbar: " + plausibilitaet);
        }
        if (notwendigkeit == null) {
            throw new DomainException("arztauskunft.notwendigkeit ist Pflicht: ueblich oder fraglich");
        }
        Arztauskunft.NotwendigkeitEnum notwendigkeitWert;
        try {
            notwendigkeitWert = Arztauskunft.NotwendigkeitEnum.fromValue(notwendigkeit);
        } catch (IllegalArgumentException cause) {
            throw new DomainException("arztauskunft.notwendigkeit muss ueblich oder fraglich sein. "
                    + "Nicht verwertbar: " + notwendigkeit);
        }
        if (text == null || text.isBlank()) {
            throw new DomainException("arztauskunft.text fehlt");
        }
        if (hinweis == null || hinweis.isBlank()) {
            throw new DomainException("arztauskunft.hinweis fehlt");
        }
        return new Arztauskunft()
                .plausibilitaet(plausibilitaetWert)
                .notwendigkeit(notwendigkeitWert)
                .text(text)
                .hinweis(hinweis);
    }
}
