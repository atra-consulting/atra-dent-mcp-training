package consulting.atra.agenten.beratung.tools;

import consulting.atra.agenten.mcp.McpToolSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static consulting.atra.agenten.beratung.agent.Beratungsmodus.BESTANDSBERATUNG;
import static consulting.atra.agenten.beratung.agent.Beratungsmodus.NEUBERATUNG;
import static consulting.atra.agenten.beratung.agent.Beratungsmodus.RECHNUNG_EINREICHEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolSelectionTest {

    private static final List<String> ALLE_KERNSYSTEM = List.of(
            "mein_vertrag_lesen", "meine_kontaktdaten_aendern", "meinen_tarif_wechseln",
            "mein_beitrag_berechnen", "meine_schadensfaelle_auflisten", "schadensfall_lesen",
            "schadensfall_einreichen", "rechnung_extrahieren", "schadensfall_bewerten");

    private static final List<String> ALLE_RECHENKERN = List.of(
            "tarife_auflisten", "tarif_lesen", "beitrag_berechnen", "erstattung_berechnen");

    private static final List<String> ALLE_WISSEN = List.of(
            "bedingungen_suchen", "goz_pruefen", "beratungsleitfaden_suchen", "tarifempfehlung",
            "tarife_vergleichen");

    @Test
    @DisplayName("the Beratung writes nothing but the Kontaktdaten")
    void onlyTheKontaktdatenAreWritten() {
        for (String schreibend : List.of("meinen_tarif_wechseln", "schadensfall_einreichen")) {
            assertThat(ToolSelection.allowed(schreibend, McpToolSource.KERNSYSTEM, BESTANDSBERATUNG))
                    .as(schreibend + " schreibt und darf nicht in der Beratung stehen")
                    .isFalse();
            assertThat(ToolSelection.allowed(schreibend, McpToolSource.KERNSYSTEM, NEUBERATUNG))
                    .isFalse();
        }
    }

    @Test
    @DisplayName("bestandsberatung allows twelve tools, four plus three plus five")
    void bestandsberatungHasTwelveTools() {
        List<String> kern = ALLE_KERNSYSTEM.stream()
                .filter(n -> ToolSelection.allowed(n, McpToolSource.KERNSYSTEM, BESTANDSBERATUNG))
                .toList();
        List<String> rechenkern = ALLE_RECHENKERN.stream()
                .filter(n -> ToolSelection.allowed(n, McpToolSource.RECHENKERN, BESTANDSBERATUNG))
                .toList();
        List<String> wissen = ALLE_WISSEN.stream()
                .filter(n -> ToolSelection.allowed(n, McpToolSource.WISSEN, BESTANDSBERATUNG))
                .toList();

        assertThat(kern).hasSize(4);
        assertThat(rechenkern).hasSize(3);
        assertThat(wissen).hasSize(5);
        assertThat(ToolSelection.allowedFor(BESTANDSBERATUNG)).hasSize(12);
        assertThat(ToolSelection.allowedFor(NEUBERATUNG)).hasSize(8);
    }

    @Test
    @DisplayName("in neuberatung the four tools requiring a Mandant fall away")
    void neuberatungHasFourFewerTools() {
        List<String> openKernsystem = ALLE_KERNSYSTEM.stream()
                .filter(n -> ToolSelection.allowed(n, McpToolSource.KERNSYSTEM, NEUBERATUNG))
                .toList();
        assertThat(openKernsystem).isEmpty();

        List<String> openRechenkern = ALLE_RECHENKERN.stream()
                .filter(n -> ToolSelection.allowed(n, McpToolSource.RECHENKERN, NEUBERATUNG))
                .toList();
        assertThat(openRechenkern).containsExactlyInAnyOrder(
                "tarife_auflisten", "tarif_lesen", "beitrag_berechnen");

        assertThat(ToolSelection.allowed("mein_vertrag_lesen", McpToolSource.KERNSYSTEM, NEUBERATUNG))
                .isFalse();
        assertThat(ToolSelection.allowed("mein_vertrag_lesen", McpToolSource.KERNSYSTEM,
                BESTANDSBERATUNG))
                .isTrue();
    }

    @Test
    @DisplayName("at the Rechenkern and the Wissensdienst the Beratung role decides nothing")
    void rechenkernAndWissenApplyRegardlessOfTheRole() {
        for (String name : ALLE_RECHENKERN) {
            assertThat(ToolSelection.allowed(name, McpToolSource.RECHENKERN, NEUBERATUNG))
                    .as(name + " am Rechenkern: Neuberatung wie Bestandsberatung dieselbe Antwort")
                    .isEqualTo(ToolSelection.allowed(name, McpToolSource.RECHENKERN,
                            BESTANDSBERATUNG));
        }
        for (String name : ALLE_WISSEN) {
            assertThat(ToolSelection.allowed(name, McpToolSource.WISSEN, NEUBERATUNG))
                    .as(name + " am Wissensdienst: Neuberatung wie Bestandsberatung dieselbe"
                            + " Antwort")
                    .isEqualTo(ToolSelection.allowed(name, McpToolSource.WISSEN, BESTANDSBERATUNG));
        }

        assertThat(ToolSelection.allowed("beitrag_berechnen", McpToolSource.RECHENKERN, NEUBERATUNG))
                .isTrue();
        assertThat(ToolSelection.allowed("erstattung_berechnen", McpToolSource.RECHENKERN,
                NEUBERATUNG))
                .isFalse();
        assertThat(ToolSelection.allowed("tarife_vergleichen", McpToolSource.WISSEN, NEUBERATUNG))
                .isTrue();
    }

    @Test
    @DisplayName("the same tool on the wrong server is not allowed")
    void theServerHasASay() {
        assertThat(ToolSelection.allowed("bedingungen_suchen", McpToolSource.KERNSYSTEM,
                BESTANDSBERATUNG))
                .isFalse();
        assertThat(ToolSelection.allowed("mein_vertrag_lesen", McpToolSource.WISSEN,
                BESTANDSBERATUNG))
                .isFalse();
        assertThat(ToolSelection.allowed("tarife_auflisten", McpToolSource.KERNSYSTEM,
                BESTANDSBERATUNG))
                .isFalse();
        assertThat(ToolSelection.allowed("beitrag_berechnen", McpToolSource.RECHENKERN,
                BESTANDSBERATUNG))
                .isTrue();
    }

    @Test
    @DisplayName("an unknown server returns nothing as long as nobody has decided")
    void anUnknownServerReturnsNothing() {
        assertThat(ToolSelection.allowed("bedingungen_suchen", "irgendwer", BESTANDSBERATUNG))
                .isFalse();
    }

    @Test
    @DisplayName("every allowed tool maps serverOf to the same connection that allows it")
    void theServerOfMatchesTheAllowlist() {
        for (String name : ToolSelection.allowedFor(BESTANDSBERATUNG)) {
            String server = ToolSelection.serverOf(name);
            assertThat(ToolSelection.allowed(name, server, BESTANDSBERATUNG))
                    .as(name + " sollte an seinem eigenen Server (" + server + ") erlaubt sein")
                    .isTrue();
        }

        assertThat(ToolSelection.serverOf("tarife_auflisten"))
                .isEqualTo(McpToolSource.RECHENKERN);
        assertThat(ToolSelection.serverOf("tarif_lesen")).isEqualTo(McpToolSource.RECHENKERN);
        assertThat(ToolSelection.serverOf("beitrag_berechnen"))
                .isEqualTo(McpToolSource.RECHENKERN);
        assertThat(ToolSelection.serverOf("mein_beitrag_berechnen"))
                .isEqualTo(McpToolSource.KERNSYSTEM);

        assertThatThrownBy(() -> ToolSelection.serverOf("nicht_erlaubt"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("intake may use exactly two tools: read the Vertrag and submit the Schadensfall")
    void intakeAllowlist() {
        assertThat(ToolSelection.allowedFor(RECHNUNG_EINREICHEN))
                .containsExactly("mein_vertrag_lesen", "schadensfall_einreichen");
    }

    @Test
    @DisplayName("schadensfall_einreichen is forbidden in the Beratung, allowed during intake")
    void submittingOnlyDuringIntake() {
        assertThat(ToolSelection.allowed("schadensfall_einreichen", "kernsystem",
                BESTANDSBERATUNG)).isFalse();
        assertThat(ToolSelection.allowed("schadensfall_einreichen", "kernsystem",
                NEUBERATUNG)).isFalse();
        assertThat(ToolSelection.allowed("schadensfall_einreichen", "kernsystem",
                RECHNUNG_EINREICHEN)).isTrue();
        assertThat(ToolSelection.allowed("goz_pruefen", "wissen",
                RECHNUNG_EINREICHEN)).isFalse();
        assertThat(ToolSelection.allowed("erstattung_berechnen", "rechenkern",
                RECHNUNG_EINREICHEN)).isFalse();
    }

    @Test
    @DisplayName("neuberatung is bestandsberatung without the four tools that require a Mandant")
    void neuberatungIsASubsetOfBestandsberatung() {
        assertThat(ToolSelection.allowedFor(BESTANDSBERATUNG))
                .containsAll(ToolSelection.allowedFor(NEUBERATUNG));
        assertThat(ToolSelection.allowedFor(BESTANDSBERATUNG).stream()
                .filter(name -> !ToolSelection.allowedFor(NEUBERATUNG).contains(name))
                .toList())
                .containsExactlyInAnyOrder("mein_vertrag_lesen", "mein_beitrag_berechnen",
                        "meine_schadensfaelle_auflisten", "meine_kontaktdaten_aendern");
    }

    @Test
    @DisplayName("allExpected() carries schadensfall_einreichen too -- otherwise it falls out of the startup check")
    void allExpected() {
        assertThat(ToolSelection.allExpected())
                .contains("schadensfall_einreichen", "goz_pruefen", "mein_vertrag_lesen")
                .hasSize(13);
    }

    @Test
    @DisplayName("catalog() is the supply, allExpected() the startup check")
    void theCatalogAlongsideTheStartupCheck() {
        assertThat(ToolSelection.catalog())
                .containsExactlyInAnyOrderElementsOf(ToolSelection.allExpected());
    }
}
