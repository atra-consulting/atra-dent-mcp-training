package consulting.atra.wissen.goz;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GozPruefungTest {

    private final GozPruefung pruefung = TestCatalogs.pruefung();

    private static final String IMPLANTAT = "9010";
    private static final String UNTERSUCHUNG = "0010";
    private static final String KIEFERUMFORMUNG = "6030";
    private static final String ZAHNREINIGUNG = "1040";
    private static final String VOLLKRONE = "2200";
    private static final String INVENTED = "9999";

    @Test
    @DisplayName("Implantat: not enthalten in atra.dent.smart, enthalten in atra.dent.balance")
    void implantatPerTarif() {
        GozBefund smart = single("ATRA_DENT_S", IMPLANTAT);
        assertThat(smart.status()).isEqualTo(GozStatus.NICHT_ENTHALTEN);
        assertThat(smart.leistungsbereich()).isEqualTo("IMP");
        assertThat(smart.abschnitt()).isEqualTo("K");
        assertThat(smart.bezeichnung()).contains("Implantatinsertion");
        assertThat(smart.quote()).isNull();
        assertThat(smart.grenzen()).isNull();
        assertThat(smart.begruendung())
                .contains("Implantate")
                .contains("atra.dent.smart")
                .contains("nicht versichert");

        GozBefund balance = single("ATRA_DENT_B", IMPLANTAT);
        assertThat(balance.status()).isEqualTo(GozStatus.ENTHALTEN);
        assertThat(balance.leistungsbereich()).isEqualTo("IMP");
        assertThat(balance.quote()).isEqualTo(85);
        assertThat(balance.grenzen().maxFaelle()).isEqualTo(4);
        assertThat(balance.grenzen().zeitraumJahre()).isEqualTo(5);
        assertThat(balance.begruendung())
                .contains("atra.dent.balance")
                .contains("zu 85 Prozent versichert")
                .contains("höchstens 4 Fälle in 5 Jahren");
    }

    @Test
    @DisplayName("an Untersuchung without a Leistungsbereich is nicht bestimmbar, not rejected")
    void examinationNotDeterminable() {
        GozBefund finding = single("ATRA_DENT_S", UNTERSUCHUNG);

        assertThat(finding.status()).isEqualTo(GozStatus.NICHT_BESTIMMBAR);
        assertThat(finding.leistungsbereich()).isNull();
        assertThat(finding.bezeichnung()).contains("Eingehende Untersuchung");
        assertThat(finding.abschnitt()).isEqualTo("A");
        assertThat(finding.quote()).isNull();
        assertThat(finding.grenzen()).isNull();

        assertThat(finding.begruendung())
                .contains("zu klären ist")
                .contains("zu welcher Behandlung")
                .doesNotContain("nicht versichert");
    }

    @Test
    @DisplayName("a Nummer outside appendix 1 of the GOZ is unbekannt, not rejected")
    void nummerOutsideTheGoz() {
        GozBefund finding = single("ATRA_DENT_X", INVENTED);

        assertThat(finding.status()).isEqualTo(GozStatus.UNBEKANNT);
        assertThat(finding.nummer()).isEqualTo(INVENTED);
        assertThat(finding.bezeichnung()).isNull();
        assertThat(finding.abschnitt()).isNull();
        assertThat(finding.leistungsbereich()).isNull();
        assertThat(finding.quote()).isNull();
        assertThat(finding.grenzen()).isNull();
        assertThat(finding.begruendung())
                .contains("zu klären ist")
                .contains("Gebührenordnung für Ärzte")
                .doesNotContain("nicht versichert");
    }

    @Test
    @DisplayName("a panoramic X-ray per GOAe is nicht bestimmbar, not unbekannt")
    void panoramicXrayFromTheGoae() {
        GozBefund finding = single("ATRA_DENT_B", "5370");

        assertThat(finding.status()).isEqualTo(GozStatus.NICHT_BESTIMMBAR);
        assertThat(finding.leistungsbereich()).isNull();
        assertThat(finding.bezeichnung()).contains("Panoramaschichtaufnahme");
        assertThat(finding.abschnitt()).isNull();
        assertThat(finding.quote()).isNull();
        assertThat(finding.grenzen()).isNull();
        assertThat(finding.begruendung())
                .contains("Gebührenordnung für Ärzte")
                .contains("Zu klären ist")
                .contains("geplanten Behandlung")
                .doesNotContain("nicht versichert");
    }

    @Test
    @DisplayName("a Nummer in both fee schedules is answered per GOZ and says so")
    void nummerInBothFeeSchedules() {
        GozBefund finding = single("ATRA_DENT_B", "5000");

        assertThat(finding.status()).isEqualTo(GozStatus.ENTHALTEN);
        assertThat(finding.leistungsbereich()).isEqualTo("ZE");
        assertThat(finding.abschnitt()).isEqualTo("F");
        assertThat(finding.begruendung())
                .contains("Zahnersatz")
                .contains("auch in der Gebührenordnung für Ärzte")
                .contains("Röntgenaufnahme eines Zahnes")
                .contains("geht von der GOZ aus");
    }

    @Test
    @DisplayName("the same Leistungsbereich yields a different status per Tarif")
    void kieferorthopaedieAcrossAllTarife() {
        GozBefund smart = single("ATRA_DENT_S", KIEFERUMFORMUNG);
        GozBefund balance = single("ATRA_DENT_B", KIEFERUMFORMUNG);
        GozBefund brillant = single("ATRA_DENT_X", KIEFERUMFORMUNG);
        GozBefund brillantSb = single("ATRA_DENT_X_SB", KIEFERUMFORMUNG);

        assertThat(smart.status()).isEqualTo(GozStatus.NICHT_ENTHALTEN);
        assertThat(smart.leistungsbereich()).isEqualTo("KFO");

        assertThat(balance.status()).isEqualTo(GozStatus.ENTHALTEN);
        assertThat(balance.quote()).isEqualTo(60);
        assertThat(balance.grenzen().limitGesamt()).isEqualByComparingTo("1200");
        assertThat(balance.grenzen().wartezeitMonate()).isEqualTo(8);
        assertThat(balance.begruendung())
                .contains("begrenzt auf 1.200,00 Euro über die gesamte Vertragslaufzeit")
                .contains("nach einer Wartezeit von 8 Monaten");

        assertThat(brillant.status()).isEqualTo(GozStatus.ENTHALTEN);
        assertThat(brillant.quote()).isEqualTo(70);
        assertThat(brillant.grenzen().limitGesamt()).isEqualByComparingTo("2000");
        assertThat(brillant.grenzen().wartezeitMonate()).isEqualTo(6);

        assertThat(brillantSb.status()).isEqualTo(GozStatus.ENTHALTEN);
        assertThat(brillantSb.quote()).isEqualTo(70);
        assertThat(brillantSb.grenzen()).isEqualTo(brillant.grenzen());
        assertThat(brillantSb.begruendung()).contains("atra.dent.brillant mit Selbstbehalt");
    }

    @Test
    @DisplayName("the annual limit and case count of Zahnreinigung are in the Befund")
    void annualLimitForZahnreinigung() {
        GozBefund smart = single("ATRA_DENT_S", ZAHNREINIGUNG);
        assertThat(smart.status()).isEqualTo(GozStatus.ENTHALTEN);
        assertThat(smart.quote()).isEqualTo(100);
        assertThat(smart.grenzen().limitProJahr()).isEqualByComparingTo("80");
        assertThat(smart.grenzen().faelleProJahr()).isEqualTo(1);
        assertThat(smart.grenzen().limitGesamt()).isNull();
        assertThat(smart.begruendung())
                .contains("begrenzt auf 80,00 Euro je Versicherungsjahr")
                .contains("höchstens 1 Fall je Versicherungsjahr");

        GozBefund balance = single("ATRA_DENT_B", ZAHNREINIGUNG);
        assertThat(balance.grenzen().limitProJahr()).isEqualByComparingTo("150");
        assertThat(balance.grenzen().faelleProJahr()).isEqualTo(2);
        assertThat(balance.begruendung()).contains("höchstens 2 Fälle je Versicherungsjahr");

        GozBefund brillant = single("ATRA_DENT_X", ZAHNREINIGUNG);
        assertThat(brillant.grenzen().limitProJahr()).isEqualByComparingTo("300");
        assertThat(brillant.grenzen().faelleProJahr()).isNull();
        assertThat(brillant.begruendung()).doesNotContain("Fälle je Versicherungsjahr");
    }

    @Test
    @DisplayName("a Leistungsbereich without its own limit yields empty limits, not null")
    void leistungsbereichWithoutItsOwnLimit() {
        GozBefund finding = single("ATRA_DENT_B", VOLLKRONE);

        assertThat(finding.status()).isEqualTo(GozStatus.ENTHALTEN);
        assertThat(finding.leistungsbereich()).isEqualTo("ZE");
        assertThat(finding.quote()).isEqualTo(85);
        assertThat(finding.grenzen()).isNotNull();
        assertThat(finding.grenzen().istLeer()).isTrue();
        assertThat(finding.begruendung()).contains("zu 85 Prozent versichert.");
    }

    @Test
    @DisplayName("the Hinweis of the GOZ mapping flows into the Begruendung")
    void theHinweisFlowsIn() {
        String coreLeistung = single("ATRA_DENT_B", IMPLANTAT).begruendung();
        assertThat(coreLeistung).contains("Die Kernleistung des Bereichs");

        String examination = single("ATRA_DENT_S", UNTERSUCHUNG).begruendung();
        assertThat(examination).contains("Allgemeine Untersuchungsleistung");

        String heilUndKostenplan = single("ATRA_DENT_B", "0040").begruendung();
        assertThat(heilUndKostenplan)
                .contains("nur für KFO oder FUN berechnungsfähig")
                .doesNotContain("\n")
                .doesNotContain("  ")
                .endsWith(".");
    }

    @Test
    @DisplayName("a Bedingung of the Tarif is in the sentence")
    void bedingungwerkInTheSentence() {
        Leistungsgrenzen limits = Leistungsgrenzen.aus(
                TestCatalogs.tarifCatalog().findLeistung("ATRA_DENT_B", "NAR").orElseThrow());

        assertThat(limits.bedingung()).isEqualTo("medizinischer Indikation");
        assertThat(Begruendung.restrictions(limits)).isEqualTo("bei medizinischer Indikation");
    }

    @Test
    @DisplayName("length and order follow the request, even with duplicates")
    void orderAndDuplicates() {
        List<String> requested =
                List.of(IMPLANTAT, UNTERSUCHUNG, IMPLANTAT, INVENTED, VOLLKRONE);

        List<GozBefund> findings = pruefung.check("ATRA_DENT_B", requested);

        assertThat(findings).extracting(GozBefund::nummer).containsExactlyElementsOf(requested);
        assertThat(findings).extracting(GozBefund::status).containsExactly(
                GozStatus.ENTHALTEN,
                GozStatus.NICHT_BESTIMMBAR,
                GozStatus.ENTHALTEN,
                GozStatus.UNBEKANNT,
                GozStatus.ENTHALTEN);
        assertThat(findings.get(2)).isEqualTo(findings.getFirst());
    }

    @Test
    @DisplayName("an empty Nummern list yields an empty result")
    void emptyList() {
        assertThat(pruefung.check("ATRA_DENT_S", List.of())).isEmpty();
    }

    @Test
    @DisplayName("an unknown Tarif is a call error and no Befund")
    void unknownTarif() {
        assertThatThrownBy(() -> pruefung.check("ATRA_DENT_Z", List.of(IMPLANTAT)))
                .isInstanceOf(UnknownTarifException.class)
                .hasMessageContaining("ATRA_DENT_Z")
                .hasMessageContaining("ATRA_DENT_S, ATRA_DENT_B, ATRA_DENT_X, ATRA_DENT_X_SB");

        UnknownTarifException exception = catchException("atra_dent_s");
        assertThat(exception.tarif()).isEqualTo("atra_dent_s");
        assertThat(exception.validTarife()).containsExactly(
                "ATRA_DENT_S", "ATRA_DENT_B", "ATRA_DENT_X", "ATRA_DENT_X_SB");

        assertThatThrownBy(() -> pruefung.check("ATRA_DENT_Z", List.of()))
                .isInstanceOf(UnknownTarifException.class);
    }

    @Test
    @DisplayName("missing arguments are named")
    void missingArguments() {
        assertThatNullPointerException()
                .isThrownBy(() -> pruefung.check(null, List.of(IMPLANTAT)))
                .withMessageContaining("tarifschluessel");
        assertThatNullPointerException()
                .isThrownBy(() -> pruefung.check("ATRA_DENT_S", null))
                .withMessageContaining("nummern");
    }

    private GozBefund single(String tarif, String nummer) {
        List<GozBefund> findings = pruefung.check(tarif, List.of(nummer));
        assertThat(findings).hasSize(1);
        return findings.getFirst();
    }

    private UnknownTarifException catchException(String tarif) {
        try {
            pruefung.check(tarif, List.of(IMPLANTAT));
            throw new AssertionError("Erwartet wurde eine UnbekannterTarifException");
        } catch (UnknownTarifException erwartet) {
            return erwartet;
        }
    }
}
