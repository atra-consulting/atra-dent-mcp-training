package consulting.atra.rechenkern.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import consulting.atra.rechenkern.domain.DomainException;
import consulting.atra.rechenkern.generated.model.Leistungsbereich;
import consulting.atra.rechenkern.generated.model.Vorverbrauch;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public record VorverbrauchInput(

        @JsonProperty()
        @JsonPropertyDescription("""
                Seit Versicherungsbeginn ausgezahlt, kumuliert ueber alle \
                Jahre, als Dezimalstring ("450.00"). Zaehlt gegen die \
                Zahnstaffel. Ohne Angabe: 0.00.""")
        String staffel,

        @JsonProperty()
        @JsonPropertyDescription("""
                Im laufenden Versicherungsjahr bereits verbrauchter \
                Selbstbehalt, als Dezimalstring. Der Selbstbehalt faellt \
                einmal je Versicherungsjahr an, ueber alle Leistungsbereiche \
                zusammen und nicht je Bereich. Ohne Angabe: 0.00.""")
        String selbstbehalt,

        @JsonProperty()
        @JsonPropertyDescription("""
                Im laufenden Versicherungsjahr ausgezahlt, als Dezimalstring. \
                Zaehlt gegen die Jahreshoechstgrenze. Ohne Angabe: 0.00.""")
        String jahr,

        @JsonProperty()
        @JsonPropertyDescription("""
                Im LAUFENDEN Versicherungsjahr je Leistungsbereich \
                ausgezahlt, Schluessel ZE/IMP/INL/ZERH/PAR/PZR/KFO/FUN/NAR/AKUT, \
                Werte als Dezimalstring. Zaehlt gegen ein Sublimit mit \
                `limitProJahr`. Nicht zu verwechseln mit \
                `leistungsbereicheSeitVersicherungsbeginn`: dieses Feld setzt \
                am Versicherungsjahr zurueck, das andere nicht.""")
        Map<String, String> leistungsbereiche,

        @JsonProperty()
        @JsonPropertyDescription("""
                Seit Versicherungsbeginn je Leistungsbereich ausgezahlt, \
                kumuliert ueber die gesamte Vertragslaufzeit und NICHT am \
                Versicherungsjahr zurueckgesetzt, Werte als Dezimalstring. \
                Zaehlt gegen ein Sublimit mit `limitGesamt` (etwa KFO). Nicht \
                zu verwechseln mit `leistungsbereiche`, das nur das laufende \
                Versicherungsjahr traegt.""")
        Map<String, String> leistungsbereicheSeitVersicherungsbeginn) {

    Vorverbrauch toInternal() {
        Vorverbrauch verbrauch = new Vorverbrauch()
                .staffel(amount("staffel", staffel))
                .selbstbehalt(amount("selbstbehalt", selbstbehalt))
                .jahr(amount("jahr", jahr));
        if (leistungsbereiche != null) {
            leistungsbereiche.forEach((bereich, value) ->
                    verbrauch.putLeistungsbereicheItem(checked("leistungsbereiche", bereich),
                            amount("leistungsbereiche." + bereich, value)));
        }
        if (leistungsbereicheSeitVersicherungsbeginn != null) {
            leistungsbereicheSeitVersicherungsbeginn.forEach((bereich, value) ->
                    verbrauch.putLeistungsbereicheSeitVersicherungsbeginnItem(
                            checked("leistungsbereicheSeitVersicherungsbeginn", bereich),
                            amount("leistungsbereicheSeitVersicherungsbeginn." + bereich, value)));
        }
        return verbrauch;
    }

    private static final String PATTERN = "[0-9]{1,10}(\\.[0-9]{1,2})?";

    private static final String ALLOWED_LEISTUNGSBEREICHE = Arrays.stream(Leistungsbereich.values())
            .map(Leistungsbereich::getValue)
            .collect(Collectors.joining(", "));

    private static String checked(String field, String bereich) {
        try {
            return Leistungsbereich.fromValue(bereich).getValue();
        } catch (IllegalArgumentException _) {
            throw new DomainException("Der Leistungsbereich \"" + bereich + "\" in " + field
                    + " ist unbekannt. Zulaessig sind: " + ALLOWED_LEISTUNGSBEREICHE);
        }
    }

    private static BigDecimal amount(String field, String value) {
        if (value == null) {
            return null;
        }
        if (!value.matches(PATTERN)) {
            throw new DomainException("Der Verbrauch zu " + field + " muss ein Dezimalstring "
                    + "mit hoechstens zwei Nachkommastellen sein, etwa \"450.00\". "
                    + "Nicht verwertbar: " + value);
        }
        return new BigDecimal(value);
    }
}
