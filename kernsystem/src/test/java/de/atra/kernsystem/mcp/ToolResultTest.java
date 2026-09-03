package de.atra.kernsystem.mcp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolResultTest extends WithMcpClient {

    @Test
    @DisplayName("dates come out in ISO form")
    void datumIsIso() {
        var vertrag = result(clientFor(ANNA), "mein_vertrag_lesen", Map.of());

        assertThat(vertrag.read("$.geburtsdatum", String.class)).isEqualTo("1990-04-12");
        assertThat(vertrag.read("$.tarifId", String.class)).isEqualTo("ATRA_DENT_B");
        assertThat(vertrag.read("$.id", Number.class).longValue()).isEqualTo(ANNA);
    }

    @Test
    @DisplayName("in nested Positionen the Betrag stays a string too")
    void betragInPositionenIsAString() {
        var fall = result(clientFor(ANNA), "schadensfall_lesen", Map.of("schadensfallId", 50001));

        assertThat(fall.read("$.rechnungsbetrag", Object.class)).isEqualTo("1862.10");
        assertThat(fall.read("$.positionen[0].betrag", Object.class)).isInstanceOf(String.class);
        assertThat(fall.read("$.positionen[0].goz", String.class)).matches("[0-9]{4}");
    }

    @Test
    @DisplayName("mein_beitrag_berechnen takes the Geburtsdatum from the Akte and the calculation from the Rechenkern")
    void ownBeitragNeedsNoGeburtsdatum() {
        rechenkern().responds(200, "application/json", "{\"monatsbeitrag\":\"77.77\"}");

        var own = result(clientFor(ANNA), "mein_beitrag_berechnen", Map.of(
                "tarifId", "ATRA_DENT_B",
                "gewuenschterBeginn", "2026-01-01"));

        assertThat(rechenkern().lastRequest())
                .contains("\"geburtsdatum\":\"1990-04-12\"")
                .contains("\"tarifId\":\"ATRA_DENT_B\"")
                .contains("\"gewuenschterBeginn\":\"2026-01-01\"");

        assertThat(own.read("$.monatsbeitrag", Object.class))
                .isInstanceOf(String.class)
                .isEqualTo("77.77");
    }

    @Test
    @DisplayName("a domain refusal from the Rechenkern arrives with its Begruendung")
    void domainRefusalOfTheRechenkern() {
        rechenkern().responds(400, "application/problem+json", """
                {"title":"Fehlerhafte Anfrage","status":400,
                 "detail":"Eintrittsalter 86 liegt ausserhalb der Grenzen von ATRA_DENT_X (18 bis 65)"}""");

        String message = exception(clientFor(ANNA), "mein_beitrag_berechnen",
                Map.of("tarifId", "ATRA_DENT_X", "gewuenschterBeginn", "2026-01-01"));

        assertThat(message).contains("Eintrittsalter", "18 bis 65");
    }

    @Test
    @DisplayName("a failing Rechenkern yields a failure message and no substitute number")
    void failingRechenkern() {
        rechenkern().responds(503, "application/problem+json",
                "{\"title\":\"Service Unavailable\",\"status\":503}");

        String message = exception(clientFor(ANNA), "mein_beitrag_berechnen",
                Map.of("tarifId", "ATRA_DENT_B", "gewuenschterBeginn", "2026-01-01"));

        assertThat(message).contains("Stoerung");
        assertThat(message).doesNotContain("monatsbeitrag");
    }

    @Test
    @DisplayName("a failed Rechenkern explains itself and does not just report the network error")
    void anOutageOfTheRechenkernExplainsItself() {
        rechenkern().breaks();

        String message = exception(clientFor(ANNA), "mein_beitrag_berechnen",
                Map.of("tarifId", "ATRA_DENT_B", "gewuenschterBeginn", "2026-01-01"));

        assertThat(message)
                .contains("nicht erreichbar")
                .contains("technische Stoerung")
                .contains("spaeter erneut");
    }

    @Test
    @DisplayName("an unknown Schadensfall number is an error, not an empty result")
    void unknownNummer() {
        String message = exception(clientFor(ANNA), "schadensfall_lesen",
                Map.of("schadensfallId", 99999));

        assertThat(message).contains("99999");
    }

    @Test
    @DisplayName("a Betrag with three decimals is rejected, not rounded")
    void betragWithThreeDecimals() {
        String message = exception(clientFor(ANNA), "schadensfall_einreichen", Map.of(
                "behandlungsdatum", "2026-02-15",
                "positionen", List.of(Map.of(
                        "goz", "0010",
                        "leistungsbereich", "ZERH",
                        "betrag", "45.678",
                        "beschreibung", "Eingehende Untersuchung"))));

        assertThat(message).contains("Nachkommastellen");
    }

    @Test
    @DisplayName("a Gebuehrennummer without a leading zero is rejected")
    void gebuehrennummerWithoutLeadingZero() {
        String message = exception(clientFor(ANNA), "schadensfall_einreichen", Map.of(
                "behandlungsdatum", "2026-02-15",
                "positionen", List.of(Map.of(
                        "goz", "10",
                        "leistungsbereich", "ZERH",
                        "betrag", "45.60",
                        "beschreibung", "Eingehende Untersuchung"))));

        assertThat(message).contains("vierstellig");
    }

    @Test
    @DisplayName("a Position may be submitted without a Leistungsbereich -- the Rechnung has none")
    void submitWithoutLeistungsbereich() {
        var result = result(clientFor(ANNA), "schadensfall_einreichen", Map.of(
                "behandlungsdatum", "2026-02-15",
                "rechnung", Map.of("rechnungsnummer", "2026-4711", "patient", "Anna Mueller",
                        "gesamtbetrag", "45.60"),
                "positionen", List.of(Map.of(
                        "goz", "0010",
                        "betrag", "45.60",
                        "beschreibung", "Eingehende Untersuchung"))));

        assertThat(result.read("$.status", String.class)).isEqualTo("eingereicht");
        assertThat(result.read("$.rechnung.rechnungsnummer", String.class)).isEqualTo("2026-4711");
        assertThat(result.read("$.positionen[0].leistungsbereich", Object.class)).isNull();
        assertThat(result.read("$.bearbeitungsprotokoll[0].akteur", String.class)).isEqualTo("kunde");
    }

    @Test
    @DisplayName("schadensfall_bewerten sets geprueft_eskalation -- only on the caller's own, claimed "
            + "case; arztauskunft and calls pass through unchanged")
    void assessOnTheOwnSchadensfall() {
        var updated = result(clientFor(ANNA), "schadensfall_einreichen", Map.of(
                "behandlungsdatum", "2026-02-15",
                "positionen", List.of(Map.of("goz", "0010", "betrag", "45.60",
                        "beschreibung", "Eingehende Untersuchung"))));
        long id = updated.read("$.id", Long.class);
        service().update(id, new de.atra.kernsystem.generated.model.SchadensfallAendern()
                .status(de.atra.kernsystem.generated.model.Schadensfallstatus.IN_PRUEFUNG));

        var result = result(clientFor(ANNA), "schadensfall_bewerten", Map.of(
                "schadensfallId", id,
                "bewertung", Map.of(
                        "empfehlung", "eskalation",
                        "eskalationsgruende", List.of(Map.of("code", "GOZ_UNCLEAR", "text", "0010 ohne Kontext")),
                        "positionen", List.of(Map.of("goz", "0010", "zustand", "NICHT_BESTIMMBAR",
                                "begruendung", "Untersuchung ist keinem Bereich fest zugeordnet")),
                        "arztauskunft", Map.of("plausibilitaet", "plausibel", "notwendigkeit", "ueblich",
                                "text", "Eingehende Untersuchung ist ueblich.", "hinweis", "Kein Befund vorgelegt."),
                        "begruendung", "Bitte Zuordnung pruefen.",
                        "agent", "schadensfall"),
                "protokoll", List.of(Map.of("zeitpunkt", "2026-08-18T10:00:00Z", "akteur", "agent",
                        "schritt", "GOZ-Pruefung", "detail", "1 von 1 NICHT_BESTIMMBAR",
                        "calls", List.of(Map.of("zeitpunkt", "2026-08-18T09:59:00Z", "art", "tool",
                                "name", "mein_vertrag_lesen", "ergebnisKurz", "ATRA_DENT_B", "dauerMs", 120))))));

        assertThat(result.read("$.status", String.class)).isEqualTo("geprueft_eskalation");
        assertThat(result.read("$.bewertung.eskalationsgruende[0].code", String.class)).isEqualTo("GOZ_UNCLEAR");
        assertThat(result.read("$.bewertung.arztauskunft.plausibilitaet", String.class)).isEqualTo("plausibel");
        assertThat(result.read("$.bewertung.arztauskunft.notwendigkeit", String.class)).isEqualTo("ueblich");
        assertThat(result.read(
                "$.bearbeitungsprotokoll[?(@.schritt == 'GOZ-Pruefung')].calls[0].art", java.util.List.class))
                .containsExactly("tool");
    }

    @Test
    @DisplayName("schadensfall_bewerten on a foreign Schadensfall is 'not found', not a conflict")
    void assessOnAForeignSchadensfall() {
        String message = exception(clientFor(ANNA), "schadensfall_bewerten", Map.of(
                "schadensfallId", CLARA_CASE,
                "bewertung", Map.of("empfehlung", "eskalation",
                        "eskalationsgruende", List.of(Map.of("code", "GOZ_UNCLEAR", "text", "x")),
                        "positionen", List.of(), "begruendung", "x", "agent", "schadensfall")));

        assertThat(message).contains("nicht gefunden").doesNotContain("Konflikt");
    }

    @Test
    @DisplayName("meine_schadensfaelle_auflisten filters by the contract value of the status, lowercase")
    void listWithValidStatus() {
        var result = result(clientFor(ANNA), "meine_schadensfaelle_auflisten",
                Map.of("status", "geprueft_freigabe"));

        assertThat(result.read("$", List.class)).isEmpty();
    }

    @Test
    @DisplayName("meine_schadensfaelle_auflisten with an unknown status is an error")
    void listWithUnknownStatus() {
        String message = exception(clientFor(ANNA), "meine_schadensfaelle_auflisten",
                Map.of("status", "UNFUG"));

        assertThat(message).contains("Status");
    }

    @Test
    @DisplayName("schadensfall_einreichen with anzahl 0 is rejected")
    void submitWithAnzahlZero() {
        String message = exception(clientFor(ANNA), "schadensfall_einreichen", Map.of(
                "behandlungsdatum", "2026-02-15",
                "positionen", List.of(Map.of("goz", "0010", "betrag", "45.60", "anzahl", 0,
                        "beschreibung", "Eingehende Untersuchung"))));

        assertThat(message).contains("anzahl").contains("mindestens 1");
    }

    @Test
    @DisplayName("schadensfall_einreichen takes over zahn, datum and anzahl per Position")
    void submitWithZahnDatumAndAnzahl() {
        var result = result(clientFor(ANNA), "schadensfall_einreichen", Map.of(
                "behandlungsdatum", "2026-04-28",
                "positionen", List.of(
                        Map.of("goz", "9010", "zahn", "46", "datum", "2026-04-28", "anzahl", 1,
                                "betrag", "410.20", "beschreibung", "Implantatinsertion"),
                        Map.of("betrag", "305.00",
                                "beschreibung", "Implantatkoerper und Verschlussschraube"))));

        assertThat(result.read("$.positionen[0].zahn", String.class)).isEqualTo("46");
        assertThat(result.read("$.positionen[0].datum", String.class)).isEqualTo("2026-04-28");
        assertThat(result.read("$.positionen[0].anzahl", Integer.class)).isEqualTo(1);
        assertThat(result.read("$.rechnungsbetrag", String.class)).isEqualTo("715.20");
    }

    @Test
    @DisplayName("schadensfall_bewerten with a Bewertungsposition without a zustand is rejected")
    void assessWithPositionWithoutZustand() {
        var updated = result(clientFor(ANNA), "schadensfall_einreichen", Map.of(
                "behandlungsdatum", "2026-02-15",
                "positionen", List.of(Map.of("goz", "0010", "betrag", "45.60",
                        "beschreibung", "Eingehende Untersuchung"))));
        long id = updated.read("$.id", Long.class);
        service().update(id, new de.atra.kernsystem.generated.model.SchadensfallAendern()
                .status(de.atra.kernsystem.generated.model.Schadensfallstatus.IN_PRUEFUNG));

        String message = exception(clientFor(ANNA), "schadensfall_bewerten", Map.of(
                "schadensfallId", id,
                "bewertung", Map.of(
                        "empfehlung", "eskalation",
                        "eskalationsgruende", List.of(Map.of("code", "GOZ_UNCLEAR", "text", "x")),
                        "positionen", List.of(Map.of("goz", "0010",
                                "begruendung", "Untersuchung ist keinem Bereich fest zugeordnet")),
                        "begruendung", "Bitte Zuordnung pruefen.",
                        "agent", "schadensfall")));

        assertThat(message).contains("zustand");
    }

    @Test
    @DisplayName("an unknown AgentCallKind is rejected, not silently accepted")
    void assessWithUnknownAgentCallKind() {
        var updated = result(clientFor(ANNA), "schadensfall_einreichen", Map.of(
                "behandlungsdatum", "2026-02-15",
                "positionen", List.of(Map.of("goz", "0010", "betrag", "45.60",
                        "beschreibung", "Eingehende Untersuchung"))));
        long id = updated.read("$.id", Long.class);
        service().update(id, new de.atra.kernsystem.generated.model.SchadensfallAendern()
                .status(de.atra.kernsystem.generated.model.Schadensfallstatus.IN_PRUEFUNG));

        String message = exception(clientFor(ANNA), "schadensfall_bewerten", Map.of(
                "schadensfallId", id,
                "bewertung", Map.of(
                        "empfehlung", "eskalation",
                        "eskalationsgruende", List.of(Map.of("code", "GOZ_UNCLEAR", "text", "x")),
                        "positionen", List.of(),
                        "begruendung", "x",
                        "agent", "schadensfall"),
                "protokoll", List.of(Map.of("zeitpunkt", "2026-08-18T10:00:00Z", "akteur", "agent",
                        "schritt", "GOZ-Pruefung",
                        "calls", List.of(Map.of("zeitpunkt", "2026-08-18T09:59:00Z", "art", "unfug",
                                "name", "mein_vertrag_lesen"))))));

        assertThat(message).contains("art");
    }
}
