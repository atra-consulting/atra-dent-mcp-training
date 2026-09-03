package consulting.atra.wissen.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GozCatalogTest {

    private final GozCatalog catalog = TestData.gozCatalog();

    @Test
    @DisplayName("leading zeros are preserved")
    void leadingZeros() {
        assertThat(catalog.position("0010")).isPresent();
        assertThat(catalog.position("10")).isEmpty();
    }

    @Test
    @DisplayName("an unknown Nummer and an undeterminable Leistungsbereich are two different cases")
    void unknownVersusNotDeterminable() {
        assertThat(catalog.knowsNumber("9999")).isFalse();
        assertThat(catalog.position("9999")).isEmpty();

        GozPosition heilUndKostenplan = catalog.position("0030").orElseThrow();
        assertThat(catalog.knowsNumber("0030")).isTrue();
        assertThat(heilUndKostenplan.areaDeterminable()).isFalse();
        assertThat(heilUndKostenplan.leistungsbereich()).isNull();
        assertThat(heilUndKostenplan.hinweis()).isNotBlank();
    }

    @Test
    @DisplayName("a mapped Position carries Leistungsbereich, section and Bezeichnung")
    void mappedPosition() {
        GozPosition implantat = catalog.position("9010").orElseThrow();

        assertThat(implantat.leistungsbereich()).isEqualTo("IMP");
        assertThat(implantat.abschnitt()).isEqualTo("K");
        assertThat(implantat.bezeichnung()).isNotBlank();
        assertThat(implantat.areaDeterminable()).isTrue();
    }

    @Test
    @DisplayName("header data and Leistungsbereiche without a Position are read")
    void headerData() {
        assertThat(catalog.stand()).contains("GOZ 2012");
        assertThat(catalog.source()).isNotBlank();
        assertThat(catalog.fetched()).isNotNull();
        assertThat(catalog.hint()).isNotBlank();
        assertThat(catalog.areasWithoutPosition())
                .extracting(LeistungsbereichWithoutPosition::leistungsbereich)
                .containsExactlyInAnyOrder("NAR", "AKUT");
        assertThat(catalog.areasWithoutPosition())
                .allSatisfy(bereich -> assertThat(bereich.begruendung()).isNotBlank());
    }

    @Test
    @DisplayName("a duplicate Gebuehrennummer aborts the read")
    void duplicateNummer() {
        String yaml = """
                stand: GOZ 2012
                quelle: Test
                abgerufen: 2026-08-11
                hinweis: Test
                abdeckung: []
                bereiche_ohne_position: []
                positionen:
                  - nummer: "2270"
                    bezeichnung: Erste Fassung
                    abschnitt: C
                    leistungsbereich: ZE
                  - nummer: "2270"
                    bezeichnung: Zweite Fassung
                    abschnitt: C
                    leistungsbereich: INL
                """;

        assertThatThrownBy(() -> read(yaml))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2270");
    }

    private static GozCatalog read(String yaml) throws IOException {
        return GozCatalog.read(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }
}
