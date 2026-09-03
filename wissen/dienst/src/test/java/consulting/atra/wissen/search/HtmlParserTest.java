package consulting.atra.wissen.search;

import consulting.atra.wissen.documents.CatalogDocument;
import consulting.atra.wissen.documents.DocumentCatalog;
import consulting.atra.wissen.documents.GeneratedDocuments;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HtmlParserTest {

    private final DocumentCatalog catalog = GeneratedDocuments.catalog();
    private final HtmlParser parser = new HtmlParser();

    private List<Passage> split(String dokumentId) throws IOException {
        CatalogDocument document = catalog.document(dokumentId).orElseThrow();
        return parser.split(document.dokumentId(), document.htmlDatei());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "atra-dent-smart-avb", "atra-dent-balance-avb", "atra-dent-brillant-avb",
            "atra-dent-tarifvergleich", "atra-dent-goz-zuordnung"})
    @DisplayName("every passage carries a heading, text and a section id")
    void everyPassageIsCitable(String dokumentId) throws IOException {
        List<Passage> passages = split(dokumentId);

        assertThat(passages).isNotEmpty();
        assertThat(passages).allSatisfy(passage -> {
            assertThat(passage.dokumentId()).isEqualTo(dokumentId);
            assertThat(passage.abschnittId()).isNotBlank();
            assertThat(passage.ueberschrift()).isNotBlank();
            assertThat(passage.text()).isNotBlank();
            assertThat(passage.teil()).isBetween(1, passage.parts());
        });
        assertThat(passages).extracting(Passage::id).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("the sections of the Bedingungen come complete and in document order")
    void sectionsOfTheBedingungen() throws IOException {
        List<String> sections = split("atra-dent-smart-avb").stream()
                .map(Passage::abschnittId)
                .distinct()
                .toList();

        assertThat(sections).hasSize(26);
        assertThat(sections).doesNotContain("uebersicht");
        assertThat(sections).startsWith("gegenstand", "begriffe", "versicherungsfaehigkeit",
                "wartezeiten", "umfang");
        assertThat(sections).endsWith("anlage-a", "anlage-b", "anlage-c");
    }

    @Test
    @DisplayName("the heading is the citable name of the paragraph")
    void theHeadingIsCitable() throws IOException {
        Map<String, Passage> firstPerSection = firstPerSection("atra-dent-smart-avb");

        assertThat(firstPerSection.get("umfang").ueberschrift())
                .isEqualTo("§ 5 Umfang des Versicherungsschutzes");
        assertThat(firstPerSection.get("wartezeiten").ueberschrift())
                .startsWith("§ 4 ");
        assertThat(firstPerSection.get("anlage-a").ueberschrift())
                .startsWith("Anlage A");
    }

    @Test
    @DisplayName("the benefit table of appendix A carries its Quoten in the text")
    void appendixAWithQuoten() throws IOException {
        String text = textOf("atra-dent-smart-avb", "anlage-a");

        assertThat(text).contains("Zahnersatz");
        assertThat(text).contains("60 %");
        assertThat(text).contains("Implantate");
        assertThat(text).contains("Leistungsbereich: Zahnersatz");
        assertThat(text).contains("Quote: 60 %");
        assertThat(text).contains("nicht versichert");
    }

    @Test
    @DisplayName("paragraph numbers and letters stay in the text")
    void paragraphNumbersStay() throws IOException {
        String text = textOf("atra-dent-smart-avb", "umfang");

        assertThat(text).contains("(1) Der Versicherer erstattet");
        assertThat(text).contains("(4) In diesem Tarif sind Aufwendungen");
        assertThat(text).contains("a) Implantate (IMP)");
    }

    @Test
    @DisplayName("no chunk reaches across a section boundary")
    void noCutAcrossSectionBoundaries() throws IOException {
        List<Passage> passages = split("atra-dent-smart-avb");

        List<Passage> annexC = passages.stream()
                .filter(passage -> passage.abschnittId().equals("anlage-c"))
                .toList();
        assertThat(annexC).hasSizeGreaterThan(1);
        assertThat(annexC).allSatisfy(passage -> {
            assertThat(passage.ueberschrift()).isEqualTo(annexC.getFirst().ueberschrift());
            assertThat(passage.text().length())
                    .isLessThanOrEqualTo(HtmlParser.DEFAULT_MAX_CHARS
                            + HtmlParser.DEFAULT_OVERLAP + 1);
        });
        assertThat(annexC).extracting(Passage::teil)
                .containsExactlyElementsOf(
                        java.util.stream.IntStream.rangeClosed(1, annexC.size()).boxed().toList());
    }

    @Test
    @DisplayName("consecutive chunks overlap")
    void chunksOverlap() throws IOException {
        List<Passage> annexC = split("atra-dent-smart-avb").stream()
                .filter(passage -> passage.abschnittId().equals("anlage-c"))
                .toList();

        String first = annexC.getFirst().text();
        String second = annexC.get(1).text();
        int kante = first.indexOf(firstLine(second));
        assertThat(kante).isNotNegative();
        assertThat(second).startsWith(first.substring(kante));
    }

    @Test
    @DisplayName("the Leistungsbereiche of the GOZ mapping are individually findable")
    void gozMappingPerLeistungsbereich() throws IOException {
        List<String> sections = split("atra-dent-goz-zuordnung").stream()
                .map(Passage::abschnittId)
                .distinct()
                .toList();

        assertThat(sections).contains("zuordnung-ZE", "zuordnung-IMP", "zuordnung-PZR");
        assertThat(textOf("atra-dent-goz-zuordnung", "zuordnung-IMP")).contains("9010");
    }

    @Test
    @DisplayName("the embedding carries the heading along")
    void embeddingTextWithHeading() throws IOException {
        Passage passage = firstPerSection("atra-dent-smart-avb").get("wartezeiten");

        assertThat(passage.embeddingText())
                .startsWith(passage.ueberschrift())
                .contains(passage.text());
    }

    @Test
    @DisplayName("an overlap beyond half the chunk length is rejected")
    void nonsensicalOverlap() {
        assertThatThrownBy(() -> new HtmlParser(1000, 900))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ueberlappung");
    }

    @Test
    @DisplayName("even cut small, every chunk stays inside its section")
    void smallChunks() throws IOException {
        CatalogDocument document = catalog.document("atra-dent-tarifvergleich").orElseThrow();
        List<Passage> passages = new HtmlParser(200, 50)
                .split(document.dokumentId(), document.htmlDatei());

        assertThat(passages).hasSizeGreaterThan(split("atra-dent-tarifvergleich").size());
        assertThat(passages).allSatisfy(passage ->
                assertThat(passage.text().length()).isLessThanOrEqualTo(200 + 50 + 1));
    }

    private Map<String, Passage> firstPerSection(String dokumentId) throws IOException {
        return split(dokumentId).stream().collect(Collectors.toMap(
                Passage::abschnittId, Function.identity(), (erste, weitere) -> erste));
    }

    private String textOf(String dokumentId, String abschnittId) throws IOException {
        return split(dokumentId).stream()
                .filter(passage -> passage.abschnittId().equals(abschnittId))
                .map(Passage::text)
                .collect(Collectors.joining("\n"));
    }

    private static String firstLine(String text) {
        int umbruch = text.indexOf('\n');
        return umbruch < 0 ? text : text.substring(0, umbruch);
    }
}
