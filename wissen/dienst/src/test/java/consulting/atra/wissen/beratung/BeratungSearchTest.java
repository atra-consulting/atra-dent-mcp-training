package consulting.atra.wissen.beratung;

import consulting.atra.wissen.search.BedingungenSearch;
import consulting.atra.wissen.search.Hit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfSystemProperty(named = "wissen.modelltests", matches = "true",
        disabledReason = "Braucht das Einbettungsmodell: mvn test -Dwissen.modelltests=true")
class BeratungSearchTest {

    private static final String HANDBOOK = "atra-dent-beratungshandbuch";

    @Autowired
    private BeratungSearch search;

    @Autowired
    private BedingungenSearch bedingungenSearch;

    private static Stream<Arguments> goldeneFragen() {
        return Stream.of(
                Arguments.of("Der Kunde sagt, es sei zu teuer",
                        List.of("einwand-zu-teuer")),
                Arguments.of("Wie steige ich ins Gespräch ein?",
                        List.of("gespraechseroeffnung")),
                Arguments.of("Der Kunde hat schon einen Heil- und Kostenplan",
                        List.of("angeratene-behandlungen-wirkung", "angeratene-behandlungen-frage",
                                "angeratene-behandlungen-auswertung", "einwand-nach-diagnose")),
                Arguments.of("Der Kunde will das erst mit seiner Frau besprechen",
                        List.of("einwand-ruecksprache-partner")),
                Arguments.of("Der Kunde sagt, seine Krankenkasse zahle das doch schon",
                        List.of("einwand-krankenkasse")),
                Arguments.of("Was muss ich ungefragt zu jeder Tarifempfehlung sagen?",
                        List.of("pflichtangaben")),
                Arguments.of("Dem Kunden fehlen zwei Zähne, was folgt daraus?",
                        List.of("fehlende-zaehne")),
                Arguments.of("Die Kundin ist 68 Jahre alt, welche Tarife stehen ihr noch offen?",
                        List.of("eintrittsalter", "einwand-zu-alt")),
                Arguments.of("Darf ich sagen, wie viel der Kunde erstattet bekommt?",
                        List.of("erstattungshoehen-ohne-zusage")),
                Arguments.of("Der Kunde braucht Bedenkzeit",
                        List.of("bedenkzeit")),
                Arguments.of("Was ist in der Einwandbehandlung verboten?",
                        List.of("einwand-grenzen")),
                Arguments.of("Der Kunde will absehbar ein Implantat",
                        List.of("implantatbedarf")),
                Arguments.of("Wie beende ich ein Gespräch ohne Abschluss?",
                        List.of("gespraech-ohne-abschluss-beenden",
                                "einwand-traegt-gespraech-beenden")));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("goldeneFragen")
    @DisplayName("a question from advisory practice finds the section that covers it")
    void goldenQuestion(String question, List<String> erwartet) {
        List<BeratungHit> hits = search.search(question, 3);

        System.out.println(question);
        hits.forEach(einer -> System.out.printf("    %.4f  %-36s %s%n",
                einer.bewertung(), einer.abschnittId(), einer.ueberschrift()));

        assertThat(hits).extracting(BeratungHit::abschnittId)
                .containsAnyElementsOf(erwartet);
    }

    @Test
    @DisplayName("even with the real model the Bedingungen index holds no Handbuch passage")
    void separationWithARealModel() {
        for (String question : List.of("Wie lange ist die Wartezeit?",
                "Warum gibt es einen Selbstbehalt?",
                "Was ist, wenn ich kündige — war das dann umsonst?",
                "Was gilt bei fehlenden Zähnen?")) {
            List<Hit> hits = bedingungenSearch.search("ATRA_DENT_X", question, 20);

            System.out.printf("bedingungen_suchen [ATRA_DENT_X] %s%n", question);
            hits.stream().limit(3).forEach(einer -> System.out.printf("    %.4f  %-24s %s%n",
                    einer.bewertung(), einer.dokumentId(), einer.abschnittId()));

            assertThat(hits).as(question).isNotEmpty();
            assertThat(hits).extracting(Hit::dokumentId).as(question).doesNotContain(HANDBOOK);
        }
    }

    @Test
    @DisplayName("even with the real model the Beratung index holds no Bedingungen passage")
    void separationWithARealModelTheOtherWayRound() {
        for (String question : List.of("Wie lange ist die Wartezeit?",
                "Wie hoch ist der Selbstbehalt bei brillant?",
                "Welche Unterlagen muss ich einreichen?",
                "Wird Bleaching bezahlt?")) {
            List<BeratungHit> hits = search.search(question, 20);

            System.out.printf("beratungsleitfaden_suchen %s%n", question);
            hits.stream().limit(3).forEach(einer -> System.out.printf("    %.4f  %-36s %s%n",
                    einer.bewertung(), einer.abschnittId(), einer.ueberschrift()));

            assertThat(hits).as(question).isNotEmpty();
            assertThat(hits).extracting(BeratungHit::dokumentId)
                    .as(question).containsOnly(HANDBOOK);
        }
    }

    @Test
    @DisplayName("the index carries the sections of the Handbuch")
    void theIndexIsBuilt() {
        assertThat(search.passageCount()).isGreaterThan(100);
        assertThat(search.documentIds()).containsExactly(HANDBOOK);
    }

    @Test
    @DisplayName("the hit count is respected, the score is within 0 to 1 and decreases")
    void theHitListIsOrdered() {
        for (int count : new int[] {1, 3, 7}) {
            List<BeratungHit> hits = search.search("Wie beende ich ein Gespräch?", count);

            assertThat(hits).hasSize(count);
            assertThat(hits).allSatisfy(einer ->
                    assertThat(einer.bewertung()).isBetween(0.0, 1.0));
            assertThat(hits).isSortedAccordingTo(
                    Comparator.comparingDouble(BeratungHit::bewertung).reversed());
        }
    }

    @Test
    @DisplayName("a section appears at most once in the hit list")
    void noDuplicateFundstelle() {
        List<BeratungHit> hits = search.search(
                "Wie gehe ich mit einem Einwand um, der berechtigt ist?", 10);

        assertThat(hits).extracting(BeratungHit::abschnittId).doesNotHaveDuplicates();
        assertThat(hits).allSatisfy(einer -> {
            assertThat(einer.ueberschrift()).isNotBlank();
            assertThat(einer.text()).isNotBlank();
        });
    }

    @Test
    @DisplayName("an empty question and an anzahl below one are errors")
    void unusableRequests() {
        assertThatThrownBy(() -> search.search("  ", 5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> search.search("Wie steige ich ins Gespräch ein?", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
