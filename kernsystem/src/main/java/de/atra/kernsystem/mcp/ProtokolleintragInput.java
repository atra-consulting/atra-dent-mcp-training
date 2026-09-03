package de.atra.kernsystem.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import de.atra.kernsystem.domain.DomainException;
import de.atra.kernsystem.generated.model.Protokollakteur;
import de.atra.kernsystem.generated.model.Protokolleintrag;

import java.time.OffsetDateTime;
import java.util.List;

public record ProtokolleintragInput(

        @JsonPropertyDescription("Zeitpunkt des Schritts, ISO-8601.")
        OffsetDateTime zeitpunkt,

        @JsonPropertyDescription("Akteur des Schritts: kunde, agent, sachbearbeitung oder system.")
        String akteur,

        @JsonPropertyDescription("Kurzbezeichnung des Schritts, z. B. \"GOZ-Pruefung\".")
        String schritt,

        @JsonProperty()
        @JsonPropertyDescription("Ein Satz Detail zum Schritt.")
        String detail,

        @JsonProperty()
        @JsonPropertyDescription("""
                Die Aufrufe dieses Schritts, je mit art (modell, tool, a2a), name, \
                eingabeKurz, ergebnisKurz, dauerMs.""")
        List<AgentCallInput> calls) {

    Protokolleintrag toInternal() {
        if (zeitpunkt == null) {
            throw new DomainException("protokoll[].zeitpunkt ist Pflicht");
        }
        if (akteur == null) {
            throw new DomainException("protokoll[].akteur ist Pflicht: kunde, agent, "
                    + "sachbearbeitung oder system");
        }
        Protokollakteur value;
        try {
            value = Protokollakteur.fromValue(akteur);
        } catch (IllegalArgumentException cause) {
            throw new DomainException("protokoll[].akteur muss kunde, agent, sachbearbeitung "
                    + "oder system sein. Nicht verwertbar: " + akteur);
        }
        if (schritt == null || schritt.isBlank()) {
            throw new DomainException("protokoll[].schritt fehlt");
        }
        return new Protokolleintrag()
                .zeitpunkt(zeitpunkt)
                .akteur(value)
                .schritt(schritt)
                .detail(detail)
                .calls(calls == null ? List.of()
                        : calls.stream().map(AgentCallInput::toInternal).toList());
    }
}
