package consulting.atra.wissen.documents;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class FundstelleTest {

    private static final String MATERIAL_UND_LABOR = "Material und zahntechnische Laborleistungen";

    private static final Pattern PARAGRAPH_HEADING =
            Pattern.compile("§\\s*(\\d+)\\s+Umfang des Versicherungsschutzes");

    @ParameterizedTest
    @ValueSource(strings = {"atra-dent-smart-avb", "atra-dent-balance-avb", "atra-dent-brillant-avb"})
    @DisplayName("every Bedingungswerk declares Material and Labor erstattungsfaehig")
    void materialAndLaborAreInTheBedingungswerk(String dokumentId) {
        String text = plainText(html(dokumentId));

        assertThat(text)
                .as("Zusage zu Material und Labor in %s", dokumentId)
                .contains(MATERIAL_UND_LABOR)
                .contains("Zu den erstattungsfähigen Aufwendungen gehören auch die Kosten")
                .contains("dem Leistungsbereich der Behandlung zugerechnet")
                .contains("teilen deren Erstattungsquote und Begrenzung");
    }

    @ParameterizedTest
    @ValueSource(strings = {"atra-dent-smart-avb", "atra-dent-balance-avb", "atra-dent-brillant-avb"})
    @DisplayName("the assurance is in the paragraph the knowledge base names")
    void theFundstelleMatches(String dokumentId) {
        String scope = plainText(section(dokumentId, "umfang"));

        Matcher heading = PARAGRAPH_HEADING.matcher(scope);
        assertThat(heading.find())
                .as("Ueberschrift des Abschnitts umfang in %s", dokumentId)
                .isTrue();

        String fundstelle = "Paragraf " + heading.group(1);
        assertThat(scope)
                .as("die Zusage steht im Abschnitt umfang von %s", dokumentId)
                .contains(MATERIAL_UND_LABOR);
        assertThat(gozMapping())
                .as("goz-zuordnung.yaml nennt %s als fundstelle", fundstelle)
                .contains("fundstelle: " + fundstelle + "\n");
    }

    @Test
    @DisplayName("the paragraph number of the assurance depends on the Tarif and is therefore no Fundstelle")
    void theParagraphNumberDependsOnTheTarif() {
        assertThat(paragraphNumber("atra-dent-smart-avb"))
                .isNotEqualTo(paragraphNumber("atra-dent-balance-avb"));
        assertThat(paragraphNumber("atra-dent-balance-avb"))
                .isEqualTo(paragraphNumber("atra-dent-brillant-avb"));
    }

    private static int paragraphNumber(String dokumentId) {
        String scope = plainText(section(dokumentId, "umfang"));
        Matcher hits = Pattern.compile("\\((\\d+)\\)\\s+Zu den erstattungsfähigen Aufwendungen")
                .matcher(scope);
        assertThat(hits.find()).as("nummerierter Absatz der Zusage in %s", dokumentId).isTrue();
        return Integer.parseInt(hits.group(1));
    }

    private static String section(String dokumentId, String id) {
        String html = html(dokumentId);
        int anfang = html.indexOf("<section id=\"" + id + "\"");
        assertThat(anfang).as("Abschnitt %s in %s", id, dokumentId).isNotNegative();
        int ende = html.indexOf("<section", anfang + 10);
        return ende < 0 ? html.substring(anfang) : html.substring(anfang, ende);
    }

    private static String plainText(String html) {
        return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ");
    }

    private static String html(String dokumentId) {
        return read(GeneratedDocuments.WURZEL.resolve("html").resolve(dokumentId + ".html"));
    }

    private static String gozMapping() {
        return read(Path.of("..", "daten", "goz-zuordnung.yaml"));
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }
}
