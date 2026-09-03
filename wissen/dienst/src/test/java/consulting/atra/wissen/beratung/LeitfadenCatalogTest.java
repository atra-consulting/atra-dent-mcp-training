package consulting.atra.wissen.beratung;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeitfadenCatalogTest {

    private final LeitfadenCatalog catalog = TestLeitfaden.leitfadenCatalog();

    @Test
    @DisplayName("the Leitfaden is read completely")
    void readCompletely() {
        assertThat(catalog.stand()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(catalog.confidentiality()).isEqualTo("intern");
        assertThat(catalog.giltFuer()).contains("Telefonberatung");

        assertThat(catalog.order()).containsExactly(
                "ATRA_DENT_X", "ATRA_DENT_X_SB", "ATRA_DENT_B", "ATRA_DENT_S");
        assertThat(catalog.targetKunden()).hasSize(4);
        assertThat(catalog.hardCriteria()).extracting(HardCriterion::schluessel)
                .containsExactly("EINTRITTSALTER", "BEREICH_NICHT_VERSICHERT", "FEHLENDE_ZAEHNE");
        assertThat(catalog.conversationReasons()).extracting(ConversationReason::schluessel)
                .containsExactly("VOR_DER_DIAGNOSE", "VORVERSICHERUNG", "FAMILIENZUWACHS",
                        "BERUFSEINSTIEG");
        assertThat(catalog.complianceLimits()).extracting(ComplianceLimit::schluessel)
                .contains("KEINE_ERSTATTUNGSZUSAGE", "AUSSCHLUESSE_NENNEN", "WARTEZEIT_NENNEN",
                        "KEIN_DRAENGEN", "BEDINGUNGEN_HABEN_VORRANG");
    }

    @Test
    @DisplayName("the principle stands beside the order, not behind it")
    void principleAndOrder() {
        Principle principle = catalog.principle();
        assertThat(principle.vorrang()).contains("Massgeblich ist der Bedarf");
        assertThat(principle.pflichtZurOffenheit()).contains("guenstigerer Tarif ebenso gut");

        EmpfehlungOrder order = catalog.recommendationOrder();
        assertThat(order.giltNurBei()).isEqualTo("gleichwertiger Eignung");
        assertThat(order.vorrangDesBedarfs()).contains("nicht den Bedarfsfall");
        assertThat(order.rank("ATRA_DENT_X")).isEqualTo(1);
        assertThat(order.rank("ATRA_DENT_S")).isEqualTo(4);
        assertThat(order.reihenfolgeGreiftNichtWenn())
                .extracting(EmpfehlungOrder.Exception::schluessel)
                .containsExactly("ALTER", "BUDGET_GENANNT", "BEDARF_ENGER");
    }

    @Test
    @DisplayName("folded blocks become single-line sentences")
    void textsWithoutLineBreaks() {
        assertThat(catalog.principle().begruendung()).doesNotContain("\n");
        assertThat(catalog.targetKunde("ATRA_DENT_S").orElseThrow().hinweispflicht())
                .doesNotContain("\n")
                .contains("Implantate, Parodontose");
        catalog.complianceLimits().forEach(grenze ->
                assertThat(grenze.text()).doesNotContain("\n").doesNotContain("  "));
    }

    @Test
    @DisplayName("the target-Kunde entry mostly says when the Tarif does not fit")
    void targetKundeRead() {
        TargetKunde smart = catalog.targetKunde("ATRA_DENT_S").orElseThrow();

        assertThat(smart.kurzformel()).isEqualTo("Grundschutz fuer Zahnersatz und Zahnerhalt");
        assertThat(smart.passtWenn()).hasSize(3);
        assertThat(smart.passtNichtWenn()).hasSize(4)
                .anyMatch(text -> text.contains("Implantate"))
                .anyMatch(text -> text.contains("Kieferorthopaedie"));
        assertThat(catalog.targetKunde("ATRA_DENT_Z")).isEmpty();
    }

    @Test
    @DisplayName("a Leitfaden without compliance limits does not load")
    void withoutComplianceLimits() {
        String withoutLimits = MIN_LEITFADEN.replace("""
                compliance_grenzen:
                  - schluessel: KEINE_ERSTATTUNGSZUSAGE
                    text: Keine Zusage ueber die Hoehe einer konkreten Erstattung.
                """, "compliance_grenzen: []\n");

        assertThatThrownBy(() -> read(withoutLimits))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Compliance-Grenzen");
    }

    @Test
    @DisplayName("a Tarif without a target-Kunde entry does not load")
    void withoutATargetKunde() {
        String wrongTarif = MIN_LEITFADEN.replace("  - tarif: ATRA_DENT_X",
                "  - tarif: ATRA_DENT_UNBEKANNT");

        assertThatThrownBy(() -> read(wrongTarif))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dieselben Tarife");
    }

    @Test
    @DisplayName("an unknown key in the file aborts the startup")
    void unknownKey() {
        assertThatThrownBy(() -> read(MIN_LEITFADEN + "unbekanntes_feld: wert\n"))
                .isInstanceOf(JacksonException.class);
    }

    private static LeitfadenCatalog read(String content) throws IOException {
        return LeitfadenCatalog.read(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    private static final String MIN_LEITFADEN = """
            stand: 2026-01-01
            vertraulichkeit: intern
            gilt_fuer: Telefonberatung
            grundsatz:
              vorrang: Massgeblich ist der Bedarf.
              begruendung: Ein Tarif, der nicht passt, wird storniert.
              pflicht_zur_offenheit: Ein guenstigerer Tarif wird genannt.
            empfehlungsreihenfolge:
              reihenfolge:
                - ATRA_DENT_X
              gilt_nur_bei: gleichwertiger Eignung
              begruendung: Die Nachversicherung ist der Regelfall, in dem etwas schiefgeht.
              vorrang_des_bedarfs: Die Reihenfolge entscheidet den Zweifelsfall.
              reihenfolge_greift_nicht_wenn:
                - schluessel: ALTER
                  text: Die Kundin oder der Kunde ist ueber 65.
            zielkunden:
              - tarif: ATRA_DENT_X
                kurzformel: Vollschutz ohne Selbstbehalt
                passt_wenn:
                  - Umfassender Schutz ist gewuenscht
                passt_nicht_wenn:
                  - Eintrittsalter ueber 65
                hinweispflicht: Die Jahreshoechstgrenze ist zu nennen.
            harte_kriterien:
              - schluessel: EINTRITTSALTER
                pruefung: Alter gegen eintrittsalter des Tarifs
                wirkung: Tarif entfaellt vollstaendig aus der Empfehlung
            gespraechsanlaesse:
              - schluessel: VOR_DER_DIAGNOSE
                text: Noch keine Behandlung angeraten.
            compliance_grenzen:
              - schluessel: KEINE_ERSTATTUNGSZUSAGE
                text: Keine Zusage ueber die Hoehe einer konkreten Erstattung.
            """;
}
