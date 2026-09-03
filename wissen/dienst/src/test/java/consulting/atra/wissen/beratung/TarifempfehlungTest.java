package consulting.atra.wissen.beratung;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TarifempfehlungTest {

    private static final String BRILLANT = "ATRA_DENT_X";
    private static final String BRILLANT_SB = "ATRA_DENT_X_SB";
    private static final String BALANCE = "ATRA_DENT_B";
    private static final String SMART = "ATRA_DENT_S";

    private final Tarifempfehlung recommendation = TestLeitfaden.recommendation();


    @Test
    @DisplayName("at 67 both brillant Tarife drop out, balance comes first")
    void eintrittsalterUeber65() {
        BeratungsResult result = recommendation.recommend(BeratungRequest.fuerAlter(67));

        assertThat(result.empfehlungen()).extracting(Empfehlung::tarifschluessel)
                .containsExactly(BALANCE, SMART)
                .doesNotContain(BRILLANT, BRILLANT_SB);
        assertThat(result.bestRecommendation().tarifschluessel()).isEqualTo(BALANCE);

        assertThat(result.ausgeschlossene()).extracting(ExcludedTarif::tarifschluessel)
                .containsExactly(BRILLANT, BRILLANT_SB);
        assertThat(result.ausgeschlossene()).allSatisfy(ausgeschlossen -> {
            assertThat(ausgeschlossen.kriterium()).isEqualTo("EINTRITTSALTER");
            assertThat(ausgeschlossen.begruendung())
                    .contains("67 Jahren")
                    .contains("18 bis 65")
                    .contains("nicht abschließbar");
        });

        assertThat(result.weg()).isEqualTo(DecisionPath.REIHENFOLGE);
        assertThat(result.zusammenfassung())
                .contains("atra.dent.balance")
                .contains("An harten Kriterien scheiden 2 Tarife aus");
    }

    @Test
    @DisplayName("foreseeable need for Implantate rules out smart")
    void implantateRuleOutSmart() {
        BeratungsResult result = recommendation.recommend(
                BeratungRequest.fuerAlter(45).withFocusAreas("IMP"));

        assertThat(result.empfehlungen()).extracting(Empfehlung::tarifschluessel)
                .containsExactly(BRILLANT, BRILLANT_SB, BALANCE)
                .doesNotContain(SMART);
        assertThat(result.ausgeschlossene()).singleElement().satisfies(ausgeschlossen -> {
            assertThat(ausgeschlossen.tarifschluessel()).isEqualTo(SMART);
            assertThat(ausgeschlossen.kriterium()).isEqualTo("BEREICH_NICHT_VERSICHERT");
            assertThat(ausgeschlossen.begruendung())
                    .contains("atra.dent.smart")
                    .contains("Implantate")
                    .contains("nicht");
        });

        assertThat(result.weg()).isEqualTo(DecisionPath.BEDARF);
        assertThat(result.bestRecommendation().needReasons())
                .extracting(NeedReason::art).containsExactly(NeedReasonType.SCHWERPUNKT);
        assertThat(result.bestRecommendation().begruendung())
                .contains("höchstens 8 Fälle in 5 Jahren");
    }

    @Test
    @DisplayName("Kieferorthopaedie rules out smart")
    void kieferorthopaedieRulesOutSmart() {
        BeratungsResult result = recommendation.recommend(
                BeratungRequest.fuerAlter(30).withFocusAreas("KFO"));

        assertThat(result.empfehlungen()).extracting(Empfehlung::tarifschluessel)
                .doesNotContain(SMART);
        assertThat(result.ausgeschlossene()).singleElement()
                .satisfies(ausgeschlossen -> assertThat(ausgeschlossen.tarifschluessel())
                        .isEqualTo(SMART));
        assertThat(result.empfehlungen().getLast().begruendung())
                .contains("Kieferorthopädie zu 60 Prozent")
                .contains("höchstens 1.200,00 Euro insgesamt")
                .contains("eigenen Wartezeit von 8 Monaten");
    }

    @Test
    @DisplayName("two uninsured Schwerpunkte rule out smart only once")
    void severalSchwerpunkteOneExclusion() {
        BeratungsResult result = recommendation.recommend(
                BeratungRequest.fuerAlter(40).withFocusAreas("IMP", "PAR", "ZE"));

        assertThat(result.ausgeschlossene()).hasSize(1);
        assertThat(result.empfehlungen()).hasSize(3);
    }


    @Test
    @DisplayName("without a distinguishing Bedarf the order applies, brillant comes first")
    void equivalentSuitability() {
        BeratungsResult result = recommendation.recommend(BeratungRequest.fuerAlter(35));

        assertThat(result.weg()).isEqualTo(DecisionPath.REIHENFOLGE);
        assertThat(result.empfehlungen()).extracting(Empfehlung::tarifschluessel)
                .containsExactly(BRILLANT, BRILLANT_SB, BALANCE, SMART);
        assertThat(result.ausgeschlossene()).isEmpty();
        assertThat(result.empfehlungen()).allSatisfy(einzelne ->
                assertThat(einzelne.hasNeedReasons()).isFalse());

        assertThat(result.bestRecommendation().rang()).isEqualTo(1);
        assertThat(result.bestRecommendation().reihenfolgeplatz()).isEqualTo(1);
        assertThat(result.bestRecommendation().begruendung())
                .contains("Keine der genannten Angaben unterscheidet diesen Tarif")
                .contains("nur bei gleichwertiger Eignung");
        assertThat(result.zusammenfassung())
                .contains("Empfehlungsreihenfolge des Leitfadens")
                .contains("geht der Bedarf vor");
    }

    @Test
    @DisplayName("two fehlende Zaehne lead to brillant, because only there the exemption applies")
    void missingZaehne() {
        BeratungsResult result = recommendation.recommend(
                BeratungRequest.fuerAlter(40).mitFehlendenZaehnen(2));

        assertThat(result.weg()).isEqualTo(DecisionPath.BEDARF);
        assertThat(result.empfehlungen()).extracting(Empfehlung::tarifschluessel)
                .containsExactly(BRILLANT, BRILLANT_SB, BALANCE, SMART);

        assertThat(result.bestRecommendation().needReasons()).singleElement()
                .satisfies(grund -> {
                    assertThat(grund.art()).isEqualTo(NeedReasonType.FEHLENDE_ZAEHNE_AUSNAHME);
                    assertThat(grund.text())
                            .contains("Exception")
                            .contains("bis zu zwei bei Vertragsschluss fehlenden Zähnen")
                            .contains("zwölf Monaten");
                });
        assertThat(recommendationFor(result, BRILLANT_SB).hasNeedReasons()).isTrue();

        assertThat(recommendationFor(result, BALANCE).hasNeedReasons()).isFalse();
        assertThat(recommendationFor(result, BALANCE).begruendung())
                .contains("Für den Ersatz der bei Vertragsschluss fehlenden Zähne leistet dieser "
                        + "Tarif nicht");
        assertThat(recommendationFor(result, SMART).begruendung()).contains("leistet dieser Tarif nicht");

        Hinweis hint = hintFor(result, "FEHLENDE_ZAEHNE");
        assertThat(hint.dringlichkeit()).isEqualTo(Urgency.VORRANGIG);
        assertThat(hint.text())
                .contains("Bei Vertragsschluss fehlen 2 Zähne")
                .contains("atra.dent.balance und atra.dent.smart")
                .contains("ausgeschlossen")
                .contains("atra.dent.brillant und atra.dent.brillant mit Selbstbehalt");
    }

    @Test
    @DisplayName("uninterrupted Vorversicherung marks out the Tarife in which the Wartezeit falls away")
    void vorversicherung() {
        BeratungsResult result = recommendation.recommend(
                BeratungRequest.fuerAlter(28).mitVorversicherung(Vorversicherung.LUECKENLOS));

        assertThat(result.weg()).isEqualTo(DecisionPath.BEDARF);
        assertThat(result.bestRecommendation().needReasons()).extracting(NeedReason::art)
                .containsExactly(NeedReasonType.WARTEZEIT_ENTFAELLT);
        assertThat(result.bestRecommendation().begruendung())
                .contains("Die Wartezeit beträgt 3 Monate")
                .contains("entfällt hier wegen des lückenlosen Vorversicherungsschutzes");
        assertThat(recommendationFor(result, BALANCE).hasNeedReasons()).isFalse();
        assertThat(recommendationFor(result, SMART).begruendung())
                .contains("Die Wartezeit beträgt 8 Monate")
                .doesNotContain("entfällt");
    }

    @Test
    @DisplayName("Prophylaxe distinguishes the Tarife by the annual limit, not by the Quote")
    void prophylaxeBeyondTheLimits() {
        BeratungsResult result = recommendation.recommend(
                BeratungRequest.fuerAlter(33).withFocusAreas("PZR"));

        assertThat(result.weg()).isEqualTo(DecisionPath.BEDARF);
        assertThat(result.empfehlungen()).extracting(Empfehlung::tarifschluessel)
                .containsExactly(BRILLANT, BRILLANT_SB, BALANCE, SMART);
        assertThat(result.bestRecommendation().needReasons()).singleElement()
                .satisfies(grund -> {
                    assertThat(grund.art()).isEqualTo(NeedReasonType.SCHWERPUNKT);
                    assertThat(grund.bereich()).isEqualTo("PZR");
                    assertThat(grund.text()).contains("höchstens 300,00 Euro je Versicherungsjahr");
                });
        assertThat(recommendationFor(result, SMART).hasNeedReasons()).isFalse();
    }

    @Test
    @DisplayName("if the Bedarf is narrower the cheaper Tarif is named, not put first")
    void narrowerBedarf() {
        BeratungsResult result = recommendation.recommend(
                BeratungRequest.fuerAlter(26).withFocusAreas("ZERH"));

        assertThat(result.bestRecommendation().tarifschluessel()).isEqualTo(BRILLANT);

        Hinweis hint = hintFor(result, "GUENSTIGEREN_TARIF_NENNEN");
        assertThat(hint.dringlichkeit()).isEqualTo(Urgency.ERGAENZEND);
        assertThat(hint.text())
                .contains("atra.dent.smart")
                .contains("Zahnerhalt zu 70 Prozent")
                .contains("die Entscheidung trifft die Kundin oder der Kunde");
    }


    @Test
    @DisplayName("an angeratene Behandlung leads to a clear Hinweis")
    void angerateneBehandlung() {
        BeratungsResult result = recommendation.recommend(BeratungRequest.fuerAlter(50)
                .mitAngeratenerBehandlung(AngerateneBehandlung.ANGERATEN));

        Hinweis hint = hintFor(result, "ANGERATEN");
        assertThat(hint.dringlichkeit()).isEqualTo(Urgency.VORRANGIG);
        assertThat(hint.text())
                .contains("bereits eine Behandlung angeraten oder begonnen")
                .contains("kein Tarif von atra.dent")
                .contains("Vor Vertragsschluss angeratene oder begonnene Behandlungen")
                .contains("Heil- und Kostenplan");

        assertThat(result.hinweise().getFirst().schluessel()).isEqualTo("ANGERATEN");
        assertThat(result.hasRecommendation()).isTrue();
    }

    @Test
    @DisplayName("the unasked question about angeratene Behandlungen stays visible")
    void angeratenNotCollected() {
        BeratungsResult result = recommendation.recommend(BeratungRequest.fuerAlter(50));

        assertThat(hintFor(result, "ANGERATEN_OFFEN").text())
                .contains("nicht erhoben")
                .contains("bevor ein Tarif empfohlen wird");
        assertThat(hintFor(result, "FEHLENDE_ZAEHNE_OFFEN").text())
                .contains("nicht erhoben")
                .contains("atra.dent.brillant und atra.dent.brillant mit Selbstbehalt");
    }

    @Test
    @DisplayName("an explicit denial produces no clarification Hinweis")
    void nothingOpen() {
        BeratungsResult result = recommendation.recommend(BeratungRequest.fuerAlter(40)
                .mitAngeratenerBehandlung(AngerateneBehandlung.KEINE)
                .mitFehlendenZaehnen(0));

        assertThat(result.hinweise()).extracting(Hinweis::schluessel)
                .doesNotContain("ANGERATEN", "ANGERATEN_OFFEN",
                        "FEHLENDE_ZAEHNE", "FEHLENDE_ZAEHNE_OFFEN");
    }

    @Test
    @DisplayName("the compliance limits and the principle travel with every result")
    void complianceInEveryResult() {
        List<BeratungRequest> anliegen = List.of(
                BeratungRequest.fuerAlter(35),
                BeratungRequest.fuerAlter(67),
                BeratungRequest.fuerAlter(80),
                BeratungRequest.fuerAlter(45).withFocusAreas("IMP", "KFO"),
                BeratungRequest.fuerAlter(19).mitFehlendenZaehnen(3)
                        .mitAngeratenerBehandlung(AngerateneBehandlung.ANGERATEN)
                        .mitVorversicherung(Vorversicherung.LUECKENLOS));

        for (BeratungRequest einzelnes : anliegen) {
            BeratungsResult result = recommendation.recommend(einzelnes);

            assertThat(result.complianceGrenzen())
                    .as("Compliance-Grenzen zu %s", einzelnes)
                    .isEqualTo(TestLeitfaden.leitfadenCatalog().complianceLimits())
                    .isNotEmpty();
            assertThat(result.grundsatz().vorrang()).contains("Massgeblich ist der Bedarf");
            assertThat(result.vertraulichkeit()).isEqualTo("intern");
            assertThat(result.anliegen()).isEqualTo(einzelnes);
            assertThat(result.zusammenfassung()).isNotBlank();
        }
    }

    @Test
    @DisplayName("if every Tarif is excluded an explained result remains instead of an empty list")
    void noTarifFits() {
        BeratungsResult result = recommendation.recommend(BeratungRequest.fuerAlter(80));

        assertThat(result.weg()).isEqualTo(DecisionPath.KEIN_TARIF);
        assertThat(result.empfehlungen()).isEmpty();
        assertThat(result.bestRecommendation()).isNull();
        assertThat(result.hasRecommendation()).isFalse();

        assertThat(result.ausgeschlossene()).hasSize(4)
                .allSatisfy(ausgeschlossen -> {
                    assertThat(ausgeschlossen.kriterium()).isEqualTo("EINTRITTSALTER");
                    assertThat(ausgeschlossen.begruendung()).contains("80 Jahren");
                });
        assertThat(result.zusammenfassung())
                .contains("Kein Tarif von atra.dent kommt für dieses Anliegen in Betracht")
                .contains("alle 4 Tarife");
        assertThat(hintFor(result, "KEIN_TARIF").text())
                .contains("nichts zu empfehlen")
                .contains("offen zu sagen");
        assertThat(result.complianceGrenzen()).isNotEmpty();
    }

    @Test
    @DisplayName("under 18 every Tarif is excluded too, with the same Begruendung")
    void tooYoung() {
        BeratungsResult result = recommendation.recommend(BeratungRequest.fuerAlter(15));

        assertThat(result.weg()).isEqualTo(DecisionPath.KEIN_TARIF);
        assertThat(result.ausgeschlossene()).hasSize(4);
        assertThat(result.ausgeschlossene().getFirst().begruendung()).contains("15 Jahren");
    }


    @Test
    @DisplayName("an unknown Leistungsbereich is a call error, not a silent skip")
    void unknownLeistungsbereich() {
        BeratungRequest anliegen = BeratungRequest.fuerAlter(40).withFocusAreas("ZAHNGOLD");

        assertThatThrownBy(() -> recommendation.recommend(anliegen))
                .isInstanceOf(UnknownLeistungsbereichException.class)
                .hasMessageContaining("ZAHNGOLD")
                .hasMessageContaining("IMP");
    }

    @Test
    @DisplayName("without a BeratungRequest there is no Empfehlung")
    void withoutAnliegen() {
        assertThatNullPointerException().isThrownBy(() -> recommendation.recommend(null));
    }

    @Test
    @DisplayName("the BeratungRequest validates its own input")
    void impossibleInputs() {
        assertThatThrownBy(() -> BeratungRequest.fuerAlter(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Alter");
        assertThatThrownBy(() -> BeratungRequest.fuerAlter(40).mitFehlendenZaehnen(33))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fehlender Zaehne");
    }

    @Test
    @DisplayName("Leistungsbereich keys are normalized, duplicates are dropped")
    void schwerpunkteNormalized() {
        BeratungRequest anliegen = BeratungRequest.fuerAlter(40)
                .withFocusAreas(" imp ", "IMP", "ze");

        assertThat(anliegen.behandlungsschwerpunkte()).containsExactly("IMP", "ZE");
    }

    private static Empfehlung recommendationFor(BeratungsResult ergebnis, String tarifschluessel) {
        return ergebnis.empfehlungen().stream()
                .filter(einzelne -> einzelne.tarifschluessel().equals(tarifschluessel))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Der Tarif " + tarifschluessel + " steht nicht in der Empfehlung"));
    }

    private static Hinweis hintFor(BeratungsResult ergebnis, String schluessel) {
        return ergebnis.hinweise().stream()
                .filter(hinweis -> hinweis.schluessel().equals(schluessel))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Kein Hinweis " + schluessel + ", vorhanden: "
                        + ergebnis.hinweise().stream().map(Hinweis::schluessel).toList()));
    }
}
