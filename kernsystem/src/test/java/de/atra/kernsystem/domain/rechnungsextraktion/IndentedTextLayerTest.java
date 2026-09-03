package de.atra.kernsystem.domain.rechnungsextraktion;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

class IndentedTextLayerTest {

    static final Path PDF_DIRECTORY = Path.of("..", "submissions", "rechnungen", "pdf");

    @Test
    void the_smaller_set_footer_sits_left_of_the_table() throws IOException {
        List<TextLine> lines = lines("weissraum-prophylaxe");

        TextLine positionLine = one(lines, z -> z.text().startsWith("1040 Professionelle"));
        TextLine footer = one(lines, z -> z.text().startsWith("weissraum Tel."));

        assertThat(footer.indent()).isLessThan(positionLine.indent());
    }

    @Test
    void every_page_ends_with_a_blank_line() throws IOException {
        List<TextLine> lines = lines("kiefernwald-sanierung");

        assertThat(lines).extracting(TextLine::text)
                .contains("Seite 1 von 4")
                .noneMatch(z -> z.contains("von 4Klinik"));
        assertThat(successor(lines, "Seite 1 von 4")).isEmpty();
    }

    private static List<TextLine> lines(String fallName) throws IOException {
        byte[] pdf = Files.readAllBytes(PDF_DIRECTORY.resolve(fallName + ".pdf"));
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return ParserHelper.lines(new IndentedTextLayer().getText(document));
        }
    }

    private static TextLine one(List<TextLine> lines, Predicate<TextLine> merkmal) {
        List<TextLine> hit = lines.stream().filter(merkmal).toList();
        assertThat(hit).as("genau eine Zeile erwartet").isNotEmpty();
        return hit.getFirst();
    }

    private static String successor(List<TextLine> lines, String text) {
        int i = lines.indexOf(one(lines, z -> z.text().equals(text)));
        return lines.get(i + 1).text();
    }
}
