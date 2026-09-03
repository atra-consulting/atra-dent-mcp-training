package de.atra.kernsystem.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import de.atra.kernsystem.domain.DomainException;
import de.atra.kernsystem.generated.model.AgentCallKind;
import de.atra.kernsystem.generated.model.AgentCall;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

public record AgentCallInput(

        @JsonPropertyDescription("Zeitpunkt des Aufrufs, ISO-8601.")
        String zeitpunkt,

        @JsonPropertyDescription("Art des Aufrufs: modell, tool oder a2a.")
        String art,

        @JsonPropertyDescription("Name des Aufrufs, z. B. goz_pruefen.")
        String name,

        @JsonProperty()
        @JsonPropertyDescription("Eingabe des Aufrufs, gekuerzt.")
        String eingabeKurz,

        @JsonProperty()
        @JsonPropertyDescription("Ergebnis des Aufrufs, gekuerzt.")
        String ergebnisKurz,

        @JsonProperty()
        @JsonPropertyDescription("Dauer des Aufrufs in Millisekunden.")
        Integer dauerMs) {

    AgentCall toInternal() {
        if (zeitpunkt == null) {
            throw new DomainException("protokoll[].calls[].zeitpunkt ist Pflicht");
        }
        OffsetDateTime zeitpunktWert;
        try {
            zeitpunktWert = OffsetDateTime.parse(zeitpunkt);
        } catch (DateTimeParseException _) {
            throw new DomainException("protokoll[].calls[].zeitpunkt muss ISO-8601 sein. "
                    + "Nicht verwertbar: " + zeitpunkt);
        }
        if (art == null) {
            throw new DomainException("protokoll[].calls[].art ist Pflicht: modell, tool oder a2a");
        }
        AgentCallKind artWert;
        try {
            artWert = AgentCallKind.fromValue(art);
        } catch (IllegalArgumentException _) {
            throw new DomainException("protokoll[].calls[].art muss modell, tool oder a2a sein. "
                    + "Nicht verwertbar: " + art);
        }
        if (name == null || name.isBlank()) {
            throw new DomainException("protokoll[].calls[].name fehlt");
        }
        return new AgentCall()
                .zeitpunkt(zeitpunktWert)
                .art(artWert)
                .name(name)
                .eingabeKurz(eingabeKurz)
                .ergebnisKurz(ergebnisKurz)
                .dauerMs(dauerMs);
    }
}
