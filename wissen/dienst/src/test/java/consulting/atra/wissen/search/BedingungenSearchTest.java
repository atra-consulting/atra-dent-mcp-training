package consulting.atra.wissen.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfSystemProperty(named = "wissen.modelltests", matches = "true",
        disabledReason = "Braucht das Einbettungsmodell: mvn test -Dwissen.modelltests=true")
class BedingungenSearchTest {

    @Autowired
    private BedingungenSearch search;

    private static Stream<Arguments> goldeneFragen() {
        return Stream.of(
                Arguments.of("ATRA_DENT_S", "Wie lange ist die Wartezeit?",
                        List.of("wartezeiten")),
                Arguments.of("ATRA_DENT_B", "Gibt es für die professionelle Zahnreinigung eine Höchstgrenze im Jahr?",
                        List.of("umfang", "anlage-a")),
                Arguments.of("ATRA_DENT_X", "Wird eine Narkose erstattet?",
                        List.of("umfang", "anlage-a")),
                Arguments.of("ATRA_DENT_B", "Zählt eine Krone auf einem Implantat zum Zahnersatz oder zu den Implantaten?",
                        List.of("anlage-a")),
                Arguments.of("ATRA_DENT_X_SB", "Wie hoch ist der Selbstbehalt?",
                        List.of("selbstbehalt")),
                Arguments.of("ATRA_DENT_S", "Wie viel bekomme ich in den ersten Versicherungsjahren erstattet?",
                        List.of("zahnstaffel")),
                Arguments.of("ATRA_DENT_B", "Zahlt die Versicherung auch bei einer Behandlung im Ausland?",
                        List.of("ausland")),
                Arguments.of("ATRA_DENT_X", "Wie kann ich den Vertrag kündigen?",
                        List.of("vertrag")),
                Arguments.of("ATRA_DENT_B", "Wird Bleaching bezahlt?",
                        List.of("ausschluesse")),
                Arguments.of("ATRA_DENT_S", "Welche Unterlagen muss ich einreichen, damit erstattet wird?",
                        List.of("obliegenheiten")),
                Arguments.of("ATRA_DENT_B", "Welche GOZ-Nummern gehören zum Knochenaufbau vor einem Implantat?",
                        List.of("zuordnung-IMP")));
    }

    @ParameterizedTest(name = "[{0}] {1} -> {2}")
    @MethodSource("goldeneFragen")
    @DisplayName("an advisor question finds the section that answers it")
    void goldenQuestion(String tarif, String question, List<String> erwartet) {
        List<Hit> hits = search.search(tarif, question, 3);

        System.out.printf("%-14s %s%n", tarif, question);
        hits.forEach(einer -> System.out.printf("    %.4f  %-18s %-24s %s%n",
                einer.bewertung(), einer.abschnittId(), einer.dokumentId(), einer.ueberschrift()));

        assertThat(hits).extracting(Hit::abschnittId).containsAnyElementsOf(erwartet);
    }

    @Test
    @DisplayName("the index carries the passages of all five documents")
    void theIndexIsBuilt() {
        assertThat(search.passageCount()).isGreaterThan(100);
    }

    @Test
    @DisplayName("the Tarif is a filter: no hit from a foreign Bedingungswerk")
    void tarifFilter() {
        List<Hit> hits = search.search("ATRA_DENT_S",
                "Wie hoch ist die Erstattung für Zahnersatz?", 20);

        assertThat(hits).isNotEmpty();
        assertThat(hits).extracting(Hit::dokumentId)
                .containsAnyOf("atra-dent-smart-avb")
                .doesNotContain("atra-dent-brillant-avb", "atra-dent-balance-avb");
    }

    @Test
    @DisplayName("the Selbstbehalt variant is answered from the brillant Bedingungswerk")
    void theSelbstbehaltVariantUsesBrillant() {
        List<Hit> hits = search.search("ATRA_DENT_X_SB",
                "Wird ein Selbstbehalt von der Erstattung abgezogen?", 5);

        assertThat(hits).extracting(Hit::dokumentId)
                .contains("atra-dent-brillant-avb")
                .doesNotContain("atra-dent-smart-avb", "atra-dent-balance-avb");
    }

    @Test
    @DisplayName("the hit count is respected, the score is within 0 to 1 and decreases")
    void theHitListIsOrdered() {
        for (int count : new int[] {1, 3, 7}) {
            List<Hit> hits = search.search("ATRA_DENT_B", "Was ist bei Zahnersatz versichert?", count);

            assertThat(hits).hasSize(count);
            assertThat(hits).allSatisfy(einer ->
                    assertThat(einer.bewertung()).isBetween(0.0, 1.0));
            assertThat(hits).isSortedAccordingTo(
                    java.util.Comparator.comparingDouble(Hit::bewertung).reversed());
        }
    }

    @Test
    @DisplayName("every hit points at its Fundstelle in both formats")
    void theHitPointsAtTheFundstelle() {
        Hit hits = search.search("ATRA_DENT_B", "Wann beginnt der Versicherungsschutz?", 1)
                .getFirst();

        assertThat(hits.ueberschrift()).isNotBlank();
        assertThat(hits.text()).isNotBlank();
        assertThat(hits.htmlUrl())
                .isEqualTo("/dokumente/" + hits.dokumentId() + "/html#" + hits.abschnittId());
        assertThat(hits.pdfUrl()).isEqualTo("/dokumente/" + hits.dokumentId() + "/pdf");
    }

    @Test
    @DisplayName("a section appears at most once in the hit list")
    void noDuplicateFundstelle() {
        List<Hit> hits = search.search("ATRA_DENT_X",
                "Welche Pflichten habe ich gegenüber dem Versicherer?", 10);

        assertThat(hits)
                .extracting(einer -> einer.dokumentId() + "#" + einer.abschnittId())
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("an empty question and an unknown Tarif are errors")
    void unusableRequests() {
        assertThatThrownBy(() -> search.search("ATRA_DENT_S", "  ", 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> search.search("ATRA_DENT_Z", "Wie lange ist die Wartezeit?", 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ATRA_DENT_Z");
        assertThatThrownBy(() -> search.search("ATRA_DENT_S", "Wie lange ist die Wartezeit?", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
