package consulting.atra.rechenkern.domain;

import consulting.atra.produktmodell.TarifCatalog;
import consulting.atra.rechenkern.generated.model.ErstattungsberechnungRequest;
import consulting.atra.rechenkern.generated.model.ErstattungsberechnungResult;
import consulting.atra.rechenkern.generated.model.Leistungsbereich;
import consulting.atra.rechenkern.generated.model.Rechenschritt;
import consulting.atra.rechenkern.generated.model.RechenschrittResult;
import consulting.atra.rechenkern.generated.model.Schadenposition;
import consulting.atra.rechenkern.generated.model.TarifId;
import consulting.atra.rechenkern.generated.model.Vorverbrauch;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static consulting.atra.rechenkern.generated.model.Rechenschritt.ERSTATTUNGSFAEHIG;
import static consulting.atra.rechenkern.generated.model.Rechenschritt.GKV;
import static consulting.atra.rechenkern.generated.model.Rechenschritt.QUOTE;
import static consulting.atra.rechenkern.generated.model.Rechenschritt.SELBSTBEHALT;
import static consulting.atra.rechenkern.generated.model.Rechenschritt.STAFFEL;
import static consulting.atra.rechenkern.generated.model.Rechenschritt.SUBLIMIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ErstattungsberechnungTest {

    static final LocalDate START = LocalDate.of(2022, 3, 1);

    @Autowired
    Erstattungsberechnung berechnung;


    @Test
    void case_1_a_ze_position_on_balance_runs_through_all_six_steps() {
        ErstattungsberechnungResult result = berechnung.calculate(
                request(TarifId.ATRA_DENT_B, 1, position(Leistungsbereich.ZE, "1000.00"))
                        .gkvLeistung(new BigDecimal("300.00")));

        assertThat(result.getSchritte()).extracting(RechenschrittResult::getSchritt)
                .containsExactly(ERSTATTUNGSFAEHIG, QUOTE, SUBLIMIT, GKV, SELBSTBEHALT, STAFFEL);

        assertThat(step(result, ERSTATTUNGSFAEHIG)).isEqualByComparingTo("1000.00");
        assertThat(step(result, QUOTE)).isEqualByComparingTo("850.00");
        assertThat(step(result, SUBLIMIT)).isEqualByComparingTo("850.00");
        assertThat(step(result, GKV)).isEqualByComparingTo("550.00");
        assertThat(step(result, SELBSTBEHALT)).isEqualByComparingTo("450.00");
        assertThat(step(result, STAFFEL)).isEqualByComparingTo("450.00");

        assertThat(result.getErstattungsbetrag()).isEqualByComparingTo("450.00");
        assertThat(result.getRechnungsbetrag()).isEqualByComparingTo("1000.00");
        assertThat(result.getEigenanteil()).isEqualByComparingTo("250.00");
        assertThat(result.getVersicherungsjahr()).isEqualTo(1);
    }


    @Test
    void case_2_an_uninsured_leistungsbereich_drops_out_in_step_one() {
        ErstattungsberechnungResult result = berechnung.calculate(request(TarifId.ATRA_DENT_S, 1,
                position(Leistungsbereich.ZE, "600.00"),
                position(Leistungsbereich.IMP, "500.00")));

        assertThat(step(result, ERSTATTUNGSFAEHIG)).isEqualByComparingTo("600.00");
        assertThat(step(result, QUOTE)).isEqualByComparingTo("360.00");
        assertThat(result.getErstattungsbetrag()).isEqualByComparingTo("360.00");
        assertThat(result.getRechnungsbetrag()).isEqualByComparingTo("1100.00");
        assertThat(result.getEigenanteil()).isEqualByComparingTo("740.00");
    }


    @Test
    void case_3_a_rechnung_carries_two_leistungsbereiche_with_different_quoten() {
        ErstattungsberechnungResult result = berechnung.calculate(request(TarifId.ATRA_DENT_X, 1,
                position(Leistungsbereich.ZE, "1000.00"),
                position(Leistungsbereich.KFO, "500.00")));

        assertThat(step(result, ERSTATTUNGSFAEHIG)).isEqualByComparingTo("1500.00");
        assertThat(step(result, QUOTE)).isEqualByComparingTo("1350.00");
        assertThat(step(result, SUBLIMIT)).isEqualByComparingTo("1350.00");
        assertThat(result.getErstattungsbetrag()).isEqualByComparingTo("1350.00");
    }


    @Test
    void case_4_the_annual_limit_of_pzr_caps_and_counts_the_verbrauch() {
        ErstattungsberechnungResult result = berechnung.calculate(
                request(TarifId.ATRA_DENT_X, 1, position(Leistungsbereich.PZR, "120.00"))
                        .verbrauch(new Vorverbrauch()
                                .leistungsbereiche(Map.of("PZR", new BigDecimal("250.00")))));

        assertThat(step(result, QUOTE)).isEqualByComparingTo("120.00");
        assertThat(step(result, SUBLIMIT)).isEqualByComparingTo("50.00");
        assertThat(result.getErstattungsbetrag()).isEqualByComparingTo("50.00");
    }


    @Test
    void case_5_the_total_limit_of_kfo_counts_against_the_verbrauch_since_versicherungsbeginn() {
        ErstattungsberechnungResult result = berechnung.calculate(
                request(TarifId.ATRA_DENT_X, 4, position(Leistungsbereich.KFO, "1000.00"))
                        .verbrauch(new Vorverbrauch()
                                .leistungsbereicheSeitVersicherungsbeginn(
                                        Map.of("KFO", new BigDecimal("1800.00")))));

        assertThat(step(result, QUOTE)).isEqualByComparingTo("700.00");
        assertThat(step(result, SUBLIMIT)).isEqualByComparingTo("200.00");
        assertThat(result.getErstattungsbetrag()).isEqualByComparingTo("200.00");
        assertThat(result.getVersicherungsjahr()).isEqualTo(4);
    }

    @Test
    void case_5b_the_annual_limit_and_the_total_limit_are_two_separate_pots() {
        ErstattungsberechnungResult result = berechnung.calculate(
                request(TarifId.ATRA_DENT_X, 4, position(Leistungsbereich.KFO, "1000.00"))
                        .verbrauch(new Vorverbrauch()
                                .leistungsbereiche(Map.of("KFO", new BigDecimal("1800.00")))));

        assertThat(step(result, SUBLIMIT)).isEqualByComparingTo("700.00");
    }


    @Test
    void case_6_a_partly_used_selbstbehalt_is_deducted_only_with_the_remainder() {
        ErstattungsberechnungResult balance = berechnung.calculate(
                request(TarifId.ATRA_DENT_B, 1, position(Leistungsbereich.ZE, "1000.00"))
                        .verbrauch(new Vorverbrauch()
                                .selbstbehalt(new BigDecimal("40.00"))
                                .staffel(new BigDecimal("200.00"))));

        assertThat(step(balance, QUOTE)).isEqualByComparingTo("850.00");
        assertThat(step(balance, SELBSTBEHALT)).isEqualByComparingTo("790.00");
        assertThat(balance.getErstattungsbetrag()).isEqualByComparingTo("790.00");

        ErstattungsberechnungResult withSb = berechnung.calculate(
                request(TarifId.ATRA_DENT_X_SB, 1, position(Leistungsbereich.ZE, "1000.00"))
                        .verbrauch(new Vorverbrauch()
                                .selbstbehalt(new BigDecimal("100.00"))
                                .staffel(new BigDecimal("300.00"))));

        assertThat(step(withSb, QUOTE)).isEqualByComparingTo("1000.00");
        assertThat(step(withSb, SELBSTBEHALT)).isEqualByComparingTo("850.00");
        assertThat(withSb.getErstattungsbetrag()).isEqualByComparingTo("850.00");
    }

    @Test
    void case_7_without_a_selbstbehalt_the_step_still_appears_in_the_result() {
        for (TarifId tarif : List.of(TarifId.ATRA_DENT_S, TarifId.ATRA_DENT_X)) {
            ErstattungsberechnungResult result = berechnung.calculate(
                    request(tarif, 1, position(Leistungsbereich.ZE, "500.00")));

            assertThat(result.getSchritte()).extracting(RechenschrittResult::getSchritt)
                    .contains(SELBSTBEHALT);
            assertThat(step(result, SELBSTBEHALT))
                    .isEqualByComparingTo(step(result, GKV));
        }
        assertThat(berechnung.calculate(request(TarifId.ATRA_DENT_S, 1,
                position(Leistungsbereich.ZE, "500.00"))).getErstattungsbetrag())
                .isEqualByComparingTo("300.00");
        assertThat(berechnung.calculate(request(TarifId.ATRA_DENT_X, 1,
                position(Leistungsbereich.ZE, "500.00"))).getErstattungsbetrag())
                .isEqualByComparingTo("500.00");
    }


    @Test
    void case_8_the_staffel_caps_at_the_remaining_betrag() {
        ErstattungsberechnungResult result = berechnung.calculate(
                request(TarifId.ATRA_DENT_B, 2, position(Leistungsbereich.ZE, "1000.00"))
                        .verbrauch(new Vorverbrauch().staffel(new BigDecimal("1800.00"))));

        assertThat(step(result, QUOTE)).isEqualByComparingTo("850.00");
        assertThat(step(result, SELBSTBEHALT)).isEqualByComparingTo("750.00");
        assertThat(step(result, STAFFEL)).isEqualByComparingTo("200.00");
        assertThat(result.getErstattungsbetrag()).isEqualByComparingTo("200.00");
        assertThat(result.getVersicherungsjahr()).isEqualTo(2);
    }

    @Test
    void case_9_after_the_staffel_expires_the_jahreshoechstgrenze_takes_its_place() {
        ErstattungsberechnungResult result = berechnung.calculate(
                request(TarifId.ATRA_DENT_X, 4, position(Leistungsbereich.ZE, "1000.00"))
                        .verbrauch(new Vorverbrauch()
                                .staffel(new BigDecimal("9000.00"))
                                .jahr(new BigDecimal("4800.00"))));

        assertThat(step(result, SELBSTBEHALT)).isEqualByComparingTo("1000.00");
        assertThat(step(result, STAFFEL)).isEqualByComparingTo("200.00");
        assertThat(result.getErstattungsbetrag()).isEqualByComparingTo("200.00");
    }

    @Test
    void case_10_while_the_staffel_runs_the_jahreshoechstgrenze_does_not_apply() {
        ErstattungsberechnungResult result = berechnung.calculate(
                request(TarifId.ATRA_DENT_X, 2, position(Leistungsbereich.ZE, "1000.00"))
                        .verbrauch(new Vorverbrauch()
                                .staffel(new BigDecimal("500.00"))
                                .jahr(new BigDecimal("4800.00"))));

        assertThat(step(result, STAFFEL)).isEqualByComparingTo("1000.00");
        assertThat(result.getErstattungsbetrag()).isEqualByComparingTo("1000.00");
    }


    @Test
    void case_11_unfallbedingt_drops_the_staffel_the_jahreshoechstgrenze_remains() {
        ErstattungsberechnungResult brillant = berechnung.calculate(
                request(TarifId.ATRA_DENT_X, 1, position(Leistungsbereich.ZE, "2000.00"))
                        .unfallbedingt(true)
                        .verbrauch(new Vorverbrauch()
                                .staffel(new BigDecimal("1400.00"))
                                .jahr(new BigDecimal("4000.00"))));

        assertThat(step(brillant, STAFFEL)).isEqualByComparingTo("1000.00");

        ErstattungsberechnungResult balance = berechnung.calculate(
                request(TarifId.ATRA_DENT_B, 1, position(Leistungsbereich.ZE, "2000.00"))
                        .unfallbedingt(true)
                        .verbrauch(new Vorverbrauch().staffel(new BigDecimal("900.00"))));

        assertThat(step(balance, SELBSTBEHALT)).isEqualByComparingTo("1600.00");
        assertThat(step(balance, STAFFEL)).isEqualByComparingTo("1600.00");
        assertThat(balance.getErstattungsbetrag()).isEqualByComparingTo("1600.00");
    }


    @Test
    void case_12_a_high_gkv_vorleistung_does_not_push_the_result_below_zero() {
        ErstattungsberechnungResult result = berechnung.calculate(
                request(TarifId.ATRA_DENT_S, 1, position(Leistungsbereich.ZE, "500.00"))
                        .gkvLeistung(new BigDecimal("400.00")));

        assertThat(step(result, QUOTE)).isEqualByComparingTo("300.00");
        assertThat(step(result, GKV)).isEqualByComparingTo("0.00");
        assertThat(step(result, SELBSTBEHALT)).isEqualByComparingTo("0.00");
        assertThat(step(result, STAFFEL)).isEqualByComparingTo("0.00");
        assertThat(result.getErstattungsbetrag()).isEqualByComparingTo("0.00");
        assertThat(result.getEigenanteil()).isEqualByComparingTo("100.00");
    }


    @Test
    void case_13_rounding_happens_once_at_the_end_and_not_per_leistungsbereich() {
        ErstattungsberechnungResult result = berechnung.calculate(request(TarifId.ATRA_DENT_S, 1,
                position(Leistungsbereich.ZE, "10.01"),
                position(Leistungsbereich.ZERH, "10.01")));

        assertThat(step(result, QUOTE)).isEqualByComparingTo("13.01");
        assertThat(step(result, QUOTE).scale()).isEqualTo(2);

        assertThat(step(result, STAFFEL))
                .isEqualByComparingTo(result.getErstattungsbetrag());

        assertThat(result.getErstattungsbetrag().scale()).isEqualTo(2);
        assertThat(result.getErstattungsbetrag()).isEqualByComparingTo("13.01");
        assertThat(result.getErstattungsbetrag()).isNotEqualByComparingTo("13.02");
    }


    @Test
    void case_14_an_unknown_tarif_and_an_unknown_leistungsbereich_are_domain_errors()
            throws IOException {
        Erstattungsberechnung trimmed = new Erstattungsberechnung(smallCatalog());

        assertThatThrownBy(() -> trimmed.calculate(
                request(TarifId.ATRA_DENT_X, 1, position(Leistungsbereich.ZE, "100.00"))))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("ATRA_DENT_X");

        assertThatThrownBy(() -> trimmed.calculate(
                request(TarifId.ATRA_DENT_B, 1, position(Leistungsbereich.IMP, "100.00"))))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("IMP");
    }

    @Test
    void case_14b_a_behandlung_before_the_versicherungsbeginn_is_a_domain_error() {
        ErstattungsberechnungRequest request = new ErstattungsberechnungRequest(
                TarifId.ATRA_DENT_B, START, START.minusDays(1),
                List.of(position(Leistungsbereich.ZE, "100.00")));

        assertThatThrownBy(() -> berechnung.calculate(request))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void case_14c_an_unknown_leistungsbereich_key_in_the_verbrauch_is_a_domain_error() {
        ErstattungsberechnungRequest withWrongStaffelMap = request(
                TarifId.ATRA_DENT_B, 1, position(Leistungsbereich.ZE, "1000.00"))
                .verbrauch(new Vorverbrauch()
                        .leistungsbereiche(Map.of("Zahnersatz", new BigDecimal("450.00"))));

        assertThatThrownBy(() -> berechnung.calculate(withWrongStaffelMap))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Zahnersatz")
                .hasMessageContaining("leistungsbereiche")
                .hasMessageContaining("ZE");

        ErstattungsberechnungRequest withWrongTotalMap = request(
                TarifId.ATRA_DENT_B, 1, position(Leistungsbereich.ZE, "1000.00"))
                .verbrauch(new Vorverbrauch()
                        .leistungsbereicheSeitVersicherungsbeginn(
                                Map.of("KIEFERORTHOPAEDIE", new BigDecimal("650.00"))));

        assertThatThrownBy(() -> berechnung.calculate(withWrongTotalMap))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("KIEFERORTHOPAEDIE")
                .hasMessageContaining("leistungsbereicheSeitVersicherungsbeginn")
                .hasMessageContaining("KFO");
    }


    @Test
    void case_15a_the_jahreshoechstgrenze_suffix_depends_on_the_tarif_not_on_the_staffel() {
        ErstattungsberechnungResult balance = berechnung.calculate(
                request(TarifId.ATRA_DENT_B, 1, position(Leistungsbereich.ZE, "1000.00"))
                        .gkvLeistung(new BigDecimal("300.00")));
        assertThat(explanation(balance, STAFFEL))
                .contains("Der Tarif ATRA_DENT_B traegt keine Jahreshoechstgrenze")
                .doesNotContain("greift erst nach Ablauf der Staffel");

        ErstattungsberechnungResult brillant = berechnung.calculate(
                request(TarifId.ATRA_DENT_X, 1, position(Leistungsbereich.ZE, "100.00")));
        assertThat(explanation(brillant, STAFFEL))
                .contains("Die Jahreshoechstgrenze greift erst nach Ablauf der Staffel")
                .doesNotContain("traegt keine Jahreshoechstgrenze");
    }

    @Test
    void case_15b_the_gkv_erlaeuterung_names_only_the_betrag_actually_deducted() {
        ErstattungsberechnungResult uncapped = berechnung.calculate(
                request(TarifId.ATRA_DENT_B, 1, position(Leistungsbereich.ZE, "1000.00"))
                        .gkvLeistung(new BigDecimal("300.00")));
        assertThat(explanation(uncapped, GKV))
                .contains("GKV-Vorleistung 300.00 EUR abgezogen")
                .doesNotContain("davon");

        ErstattungsberechnungResult capped = berechnung.calculate(
                request(TarifId.ATRA_DENT_S, 1, position(Leistungsbereich.ZE, "500.00"))
                        .gkvLeistung(new BigDecimal("400.00")));
        assertThat(explanation(capped, GKV))
                .contains("GKV-Vorleistung 400.00 EUR, davon 300.00 EUR abgezogen")
                .contains("mehr liess der erstattungsfaehige Betrag nicht mehr zu");
    }

    @Test
    void case_15c_the_selbstbehalt_erlaeuterung_names_only_the_betrag_actually_deducted() {
        ErstattungsberechnungResult result = berechnung.calculate(
                request(TarifId.ATRA_DENT_B, 1, position(Leistungsbereich.ZE, "100.00"))
                        .gkvLeistung(new BigDecimal("80.00")));

        assertThat(step(result, GKV)).isEqualByComparingTo("5.00");
        assertThat(explanation(result, SELBSTBEHALT))
                .contains("Selbstbehalt 100.00 EUR je Versicherungsjahr, davon 0.00 EUR "
                        + "bereits verbraucht; 5.00 EUR abgezogen")
                .contains("mehr liess der erstattungsfaehige Betrag nicht mehr zu");
        assertThat(result.getErstattungsbetrag()).isEqualByComparingTo("0.00");
    }


    @Test
    void case_16a_a_position_without_a_gebuehrennummer_lands_in_the_sublimit_of_its_leistungsbereich() {
        ErstattungsberechnungResult result = berechnung.calculate(request(TarifId.ATRA_DENT_X, 1,
                position(Leistungsbereich.ZE, "200.00"),
                withoutNummer(Leistungsbereich.ZE, "486.30", "Vollkeramische Krone, Zirkondioxid"),
                withoutNummer(Leistungsbereich.PZR, "400.00", "Prophylaxematerial")));

        assertThat(result.getRechnungsbetrag()).isEqualByComparingTo("1086.30");
        assertThat(step(result, ERSTATTUNGSFAEHIG)).isEqualByComparingTo("1086.30");
        assertThat(step(result, QUOTE)).isEqualByComparingTo("1086.30");
        assertThat(step(result, SUBLIMIT)).isEqualByComparingTo("986.30");
        assertThat(explanation(result, SUBLIMIT)).contains("PZR");
        assertThat(result.getErstattungsbetrag()).isEqualByComparingTo("986.30");
    }

    @Test
    void case_16b_the_staffel_hits_the_sum_and_not_the_single_position_without_a_nummer() {
        ErstattungsberechnungResult result = berechnung.calculate(
                request(TarifId.ATRA_DENT_X, 1,
                        withoutNummer(Leistungsbereich.ZE, "700.00", "Laborrechnung Zirkonkrone"),
                        withoutNummer(Leistungsbereich.IMP, "500.00", "Implantatkoerper"))
                        .verbrauch(new Vorverbrauch().staffel(new BigDecimal("500.00"))));

        assertThat(result.getRechnungsbetrag()).isEqualByComparingTo("1200.00");
        assertThat(step(result, QUOTE)).isEqualByComparingTo("1200.00");
        assertThat(step(result, SUBLIMIT)).isEqualByComparingTo("1200.00");
        assertThat(step(result, STAFFEL)).isEqualByComparingTo("1000.00");
        assertThat(result.getErstattungsbetrag()).isEqualByComparingTo("1000.00");
    }


    private static BigDecimal step(ErstattungsberechnungResult result, Rechenschritt step) {
        return result.getSchritte().stream()
                .filter(entry -> entry.getSchritt() == step)
                .findFirst()
                .orElseThrow(() -> new AssertionError("step missing from the result: " + step))
                .getBetrag();
    }

    private static String explanation(ErstattungsberechnungResult result, Rechenschritt step) {
        return result.getSchritte().stream()
                .filter(entry -> entry.getSchritt() == step)
                .findFirst()
                .orElseThrow(() -> new AssertionError("step missing from the result: " + step))
                .getErlaeuterung();
    }

    private static Schadenposition position(Leistungsbereich leistungsbereich, String betrag) {
        return new Schadenposition(leistungsbereich, new BigDecimal(betrag),
                "Position im Leistungsbereich " + leistungsbereich.getValue()).goz("2210");
    }

    private static Schadenposition withoutNummer(Leistungsbereich leistungsbereich, String betrag,
                                              String beschreibung) {
        return new Schadenposition(leistungsbereich, new BigDecimal(betrag), beschreibung);
    }

    private static ErstattungsberechnungRequest request(TarifId tarif, int versicherungsjahr,
                                                        Schadenposition... positionen) {
        LocalDate behandlungsdatum = START.plusYears(versicherungsjahr - 1L).plusMonths(2);
        return new ErstattungsberechnungRequest(tarif, START, behandlungsdatum, List.of(positionen));
    }

    private static TarifCatalog smallCatalog() throws IOException {
        String yaml = """
                stand: 2026-01-01
                leistungsbereiche:
                  - schluessel: ZE
                    name: Zahnersatz
                    beschreibung: der einzige Bereich dieses Katalogs
                tarife:
                  - schluessel: ATRA_DENT_B
                    anzeigename: Testtarif
                    positionierung: nur fuer diesen Test
                    eintrittsalter: { von: 18, bis: 70 }
                    selbstbehalt: 0
                    jahreshoechstgrenze: null
                    wartezeit_monate: 0
                    staffel: STAFFEL_B
                    leistungen:
                      ZE: { versichert: true, quote: 100 }
                staffeln:
                  - schluessel: STAFFEL_B
                    dauer_jahre: 1
                    stufen:
                      - { bis_jahr: null, betrag: null }
                ausschluesse: []
                """;
        return TarifCatalog.read(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }
}
