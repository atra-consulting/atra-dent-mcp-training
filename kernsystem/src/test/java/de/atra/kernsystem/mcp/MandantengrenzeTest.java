package de.atra.kernsystem.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MandantengrenzeTest extends WithMcpClient {

    @Test
    @DisplayName("without the header the tool says where the Kundennummer comes from -- and that it is not a parameter")
    void withoutTheHeaderNoRecords() {
        String message = exception(clientWithoutCustomer(), "mein_vertrag_lesen", Map.of());

        assertThat(message)
                .contains("x-kunden-id")
                .contains("kein Parameter");
    }

    @Test
    @DisplayName("a header that is not a Kundennummer is not guessed")
    void unusableHeader() {
        assertThat(exception(client(() -> "abc"), "mein_vertrag_lesen", Map.of()))
                .contains("x-kunden-id");
        assertThat(exception(client(() -> "0"), "mein_vertrag_lesen", Map.of()))
                .contains("x-kunden-id");
        assertThat(exception(client(() -> "  "), "mein_vertrag_lesen", Map.of()))
                .contains("x-kunden-id");
    }

    @Test
    @DisplayName("a tool without a Kunde reference keeps working without the header")
    void withoutTheHeaderTheExtractionStaysReachable() throws Exception {
        String pdf = Base64.getEncoder().encodeToString(Files.readAllBytes(
                Path.of("..", "submissions", "rechnungen", "pdf", "mueller-fuellung.pdf")));

        assertThat(result(clientWithoutCustomer(), "rechnung_extrahieren",
                Map.of("pdfBase64", pdf))
                .read("$.rechnungsnummer", String.class)).isEqualTo("2026-04892");
    }

    @Test
    @DisplayName("a foreign Schadensfall is not found -- indistinguishable from a number that does not exist")
    void aForeignSchadensfallIsNotFound() {
        var anna = clientFor(ANNA);

        String fremd = exception(anna, "schadensfall_lesen", Map.of("schadensfallId", CLARA_CASE));
        String doesNotExist = exception(anna, "schadensfall_lesen", Map.of("schadensfallId", 99999));

        assertThat(fremd).isEqualTo(doesNotExist.replace("99999", String.valueOf(CLARA_CASE)));

        assertThat(result(clientFor(CLARA), "schadensfall_lesen",
                Map.of("schadensfallId", CLARA_CASE))
                .read("$.kundenId", Number.class).longValue()).isEqualTo(CLARA);
    }

    @Test
    @DisplayName("the listing shows only the caller's own Schadensfaelle")
    void listingStaysInTheOwnRecords() {
        var cases = result(clientFor(ANNA), "meine_schadensfaelle_auflisten", Map.of());

        assertThat(cases.read("$[*].kundenId", List.class))
                .isNotEmpty()
                .allSatisfy(kundenId -> assertThat(((Number) kundenId).longValue()).isEqualTo(ANNA));
    }

    @Test
    @DisplayName("the limit applies per request, not per session")
    void aSessionBindsNoKunde() {
        AtomicReference<String> kunde = new AtomicReference<>(String.valueOf(ANNA));
        var client = client(kunde::get);

        assertThat(result(client, "mein_vertrag_lesen", Map.of())
                .read("$.id", Number.class).longValue()).isEqualTo(ANNA);

        kunde.set(String.valueOf(CLARA));

        assertThat(result(client, "mein_vertrag_lesen", Map.of())
                .read("$.id", Number.class).longValue()).isEqualTo(CLARA);
    }

    @Test
    @DisplayName("a submitted Schadensfall lands in the Akte from the header")
    void submitStaysInTheOwnRecords() {
        var updated = result(clientFor(ANNA), "schadensfall_einreichen", Map.of(
                "behandlungsdatum", "2026-02-15",
                "positionen", List.of(Map.of(
                        "goz", "0010",
                        "leistungsbereich", "ZERH",
                        "betrag", "45.60",
                        "beschreibung", "Eingehende Untersuchung"))));

        assertThat(updated.read("$.kundenId", Number.class).longValue()).isEqualTo(ANNA);
        assertThat(updated.read("$.rechnungsbetrag", Object.class)).isEqualTo("45.60");
        assertThat(updated.read("$.status", String.class)).isEqualTo("eingereicht");
    }

    @Test
    @DisplayName("a smuggled-in Kundennummer changes nothing")
    void aSmuggledInKundennummerDoesNotWork() {
        var anna = clientFor(ANNA);
        McpSchema.CallToolResult response = anna.callTool(McpSchema.CallToolRequest
                .builder("schadensfall_einreichen")
                .arguments(Map.of(
                        "kundenId", CLARA,
                        "behandlungsdatum", "2026-02-15",
                        "positionen", List.of(Map.of(
                                "goz", "0010",
                                "leistungsbereich", "ZERH",
                                "betrag", "45.60",
                                "beschreibung", "Eingehende Untersuchung"))))
                .build());

        if (!Boolean.TRUE.equals(response.isError())) {
            assertThat(com.jayway.jsonpath.JsonPath.parse(text(response))
                    .read("$.kundenId", Number.class).longValue()).isEqualTo(ANNA);
        }

        assertThat(result(clientFor(CLARA), "meine_schadensfaelle_auflisten", Map.of())
                .read("$[*].behandlungsdatum", List.class))
                .doesNotContain("2026-02-15");
    }

    @Test
    @DisplayName("a change of contact data takes effect only in the caller's own Akte")
    void changeStaysInTheOwnRecords() {
        McpSyncClient anna = clientFor(ANNA);
        result(anna, "meine_kontaktdaten_aendern", Map.of("telefon", "+49 30 0000001"));

        assertThat(result(anna, "mein_vertrag_lesen", Map.of())
                .read("$.telefon", String.class)).isEqualTo("+49 30 0000001");
        assertThat(result(clientFor(CLARA), "mein_vertrag_lesen", Map.of())
                .read("$.telefon", String.class)).isNotEqualTo("+49 30 0000001");
    }

    @Test
    @DisplayName("half an Anschrift is rejected instead of blanking the missing fields")
    void halfAnAddressIsRejected() {
        String message = exception(clientFor(ANNA), "meine_kontaktdaten_aendern",
                Map.of("ort", "Hamburg"));

        assertThat(message).contains("strasse", "plz", "ort", "land");
    }

    @Test
    @DisplayName("without a value there is no silent zero change")
    void anEmptyChangeIsRejected() {
        assertThat(exception(clientFor(ANNA), "meine_kontaktdaten_aendern", Map.of()))
                .contains("nichts angegeben");
    }
}
