package consulting.atra.wissen.beratung;

import consulting.atra.wissen.api.TestEmbedding;
import consulting.atra.wissen.documents.CatalogDocument;
import consulting.atra.wissen.documents.DocumentCatalog;
import consulting.atra.wissen.documents.Confidentiality;
import consulting.atra.wissen.search.BedingungenSearch;
import consulting.atra.wissen.search.HtmlParser;
import consulting.atra.wissen.search.Hit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.ai.model.embedding=none")
@Import(TestEmbedding.class)
class SearchSpaceSeparationTest {

    private static final String HANDBOOK = "atra-dent-beratungshandbuch";
    private static final List<String> TARIFE =
            List.of("ATRA_DENT_S", "ATRA_DENT_B", "ATRA_DENT_X", "ATRA_DENT_X_SB");

    @Autowired
    private BedingungenSearch bedingungenSearch;

    @Autowired
    private BeratungSearch beratungSearch;

    @Autowired
    private DocumentCatalog catalog;

    @Autowired
    private HtmlParser bedingungenSplitter;

    private final HtmlParser beratungSplitter = new HtmlParser(
            BeratungSearch.DEFAULT_MAX_CHARS, BeratungSearch.DEFAULT_OVERLAP);

    private static Stream<String> ambiguousQuestions() {
        return Stream.of(
                "Wie lange ist die Wartezeit?",
                "Wie hoch ist der Selbstbehalt?",
                "Was gilt bei fehlenden Zähnen?",
                "Was passiert, wenn ich kündige?",
                "Wird eine bereits angeratene Behandlung noch bezahlt?",
                "Was ist bei Implantaten versichert?",
                "Ab welchem Alter kann ich noch abschließen?");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ambiguousQuestions")
    @DisplayName("bedingungen_suchen never returns a passage from the Beratungshandbuch")
    void bedingungenSearchWithoutTheHandbuch(String question) {
        for (String tarif : TARIFE) {
            List<Hit> hits = bedingungenSearch.search(tarif, question, 20);

            assertThat(hits).as("[%s] %s", tarif, question).isNotEmpty();
            assertThat(hits).extracting(Hit::dokumentId)
                    .as("[%s] %s", tarif, question)
                    .doesNotContain(HANDBOOK);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ambiguousQuestions")
    @DisplayName("beratungsleitfaden_suchen never returns a passage from a Bedingungswerk")
    void beratungSearchWithoutBedingungen(String question) {
        List<BeratungHit> hits = beratungSearch.search(question, 20);

        assertThat(hits).as(question).isNotEmpty();
        assertThat(hits).extracting(BeratungHit::dokumentId)
                .as(question)
                .containsOnly(HANDBOOK);
    }

    @Test
    @DisplayName("every index carries exactly its own material: nothing missing, nothing duplicated")
    void theIndexesAreDisjoint() throws IOException {
        int oeffentlich = passageCount(Confidentiality.OEFFENTLICH, bedingungenSplitter);
        int intern = passageCount(Confidentiality.INTERN, beratungSplitter);

        assertThat(intern).as("das Handbuch traegt Passagen bei").isGreaterThan(100);
        assertThat(beratungSearch.passageCount())
                .as("der interne Index ist genau das interne Material")
                .isEqualTo(intern);
        assertThat(bedingungenSearch.passageCount())
                .as("der Bedingungsindex ist genau das veroeffentlichte Material")
                .isEqualTo(oeffentlich);

        assertThat(beratungSearch.documentIds()).containsExactly(HANDBOOK);
    }

    @Test
    @DisplayName("the catalog still lists the Handbuch and the Tarif filter does not name it")
    void theHandbuchStaysInTheCatalogYetOutOfTheBedingungenSearch() {
        assertThat(catalog.document(HANDBOOK)).isPresent();
        assertThat(catalog.fuerTarif("ATRA_DENT_X")).extracting(CatalogDocument::dokumentId)
                .contains(HANDBOOK);
        assertThat(catalog.fuerTarif("ATRA_DENT_X", Confidentiality.OEFFENTLICH))
                .extracting(CatalogDocument::dokumentId)
                .doesNotContain(HANDBOOK);
    }

    private int passageCount(Confidentiality stufe, HtmlParser parser) throws IOException {
        int summe = 0;
        for (CatalogDocument dokument : catalog.documents(stufe)) {
            summe += parser.split(dokument.dokumentId(), dokument.htmlDatei()).size();
        }
        return summe;
    }
}
