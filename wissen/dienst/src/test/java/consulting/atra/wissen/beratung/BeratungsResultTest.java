package consulting.atra.wissen.beratung;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BeratungsResultTest {

    private static final Principle PRINCIPLE = new Principle(
            "Massgeblich ist der Bedarf.",
            "Ein Tarif, der nicht passt, wird storniert.",
            "Ein guenstigerer Tarif wird genannt.");
    private static final List<ComplianceLimit> LIMITS = List.of(
            new ComplianceLimit("KEIN_DRAENGEN", "Kein Zeitdruck, keine kuenstliche Verknappung."));
    private static final BeratungRequest ANLIEGEN = BeratungRequest.fuerAlter(40);
    private static final ExcludedTarif AUSGESCHLOSSEN = new ExcludedTarif(
            "ATRA_DENT_X", "atra.dent.brillant", "EINTRITTSALTER",
            "Mit 80 Jahren nicht abschließbar.");

    @Test
    @DisplayName("without compliance limits no result is produced")
    void withoutComplianceLimits() {
        assertThatThrownBy(() -> result(DecisionPath.KEIN_TARIF, List.of(),
                List.of(AUSGESCHLOSSEN), PRINCIPLE, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Compliance-Grenzen");
    }

    @Test
    @DisplayName("without a principle no result is produced")
    void withoutAPrinciple() {
        assertThatThrownBy(() -> result(DecisionPath.KEIN_TARIF, List.of(),
                List.of(AUSGESCHLOSSEN), null, LIMITS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Grundsatz");
    }

    @Test
    @DisplayName("an empty Empfehlung list without an ausschluss reason is no answer")
    void emptyWithoutExplanation() {
        assertThatThrownBy(() -> result(DecisionPath.KEIN_TARIF, List.of(), List.of(),
                PRINCIPLE, LIMITS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ohne Erklaerung");
    }

    @Test
    @DisplayName("an empty list and KEIN_TARIF belong together")
    void thePathMatchesTheList() {
        assertThatThrownBy(() -> result(DecisionPath.REIHENFOLGE, List.of(),
                List.of(AUSGESCHLOSSEN), PRINCIPLE, LIMITS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("KEIN_TARIF");

        assertThatThrownBy(() -> result(DecisionPath.KEIN_TARIF, List.of(recommendation(1)),
                List.of(), PRINCIPLE, LIMITS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("KEIN_TARIF");
    }

    @Test
    @DisplayName("the ranks are consecutive")
    void ranksAreConsecutive() {
        assertThatThrownBy(() -> result(DecisionPath.BEDARF,
                List.of(recommendation(1), recommendation(3)), List.of(), PRINCIPLE, LIMITS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fortlaufend");
    }

    @Test
    @DisplayName("a valid result keeps its lists immutable")
    void unmodifiable() {
        BeratungsResult result = result(DecisionPath.BEDARF, List.of(recommendation(1)),
                List.of(), PRINCIPLE, LIMITS);

        assertThat(result.bestRecommendation().rang()).isEqualTo(1);
        assertThatThrownBy(() -> result.complianceGrenzen().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.empfehlungen().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("an Empfehlung without a mandatory Hinweis is never produced")
    void withoutAMandatoryHinweis() {
        assertThatThrownBy(() -> new Empfehlung(1, "ATRA_DENT_S", "atra.dent.smart", "Grundschutz",
                4, List.of(), "Begründung.", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Hinweispflicht");
    }

    private static BeratungsResult result(DecisionPath weg, List<Empfehlung> empfehlungen,
                                              List<ExcludedTarif> ausgeschlossene,
                                              Principle grundsatz,
                                              List<ComplianceLimit> grenzen) {
        return new BeratungsResult(ANLIEGEN, weg, "Eine Zusammenfassung.", empfehlungen,
                ausgeschlossene, List.of(), grundsatz, grenzen, "intern");
    }

    private static Empfehlung recommendation(int rang) {
        return new Empfehlung(rang, "ATRA_DENT_X", "atra.dent.brillant", "Vollschutz", 1,
                List.of(), "Begründung.", "Die Jahreshöchstgrenze ist zu nennen.");
    }
}
