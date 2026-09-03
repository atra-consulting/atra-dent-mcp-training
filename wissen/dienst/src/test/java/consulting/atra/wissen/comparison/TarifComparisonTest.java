package consulting.atra.wissen.comparison;

import consulting.atra.produktmodell.TarifCatalog;
import consulting.atra.wissen.documents.GeneratedDocuments;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TarifComparisonTest {

    private static final Path DATA = Path.of("..", "daten", "tarife.yaml");

    private static TarifComparison comparison;

    @BeforeAll
    static void build() {
        if (!Files.isReadable(DATA)) {
            throw new IllegalStateException(
                    "Datendatei nicht lesbar: " + DATA.toAbsolutePath().normalize());
        }
        try {
            comparison = new TarifComparison(TarifCatalog.read(DATA), GeneratedDocuments.catalog());
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }

    @Nested
    @DisplayName("the cut")
    class Zuschnitt {

        @Test
        @DisplayName("without a value: all four Tarife, all ten Leistungsbereiche")
        void alles() {
            ComparisonResult result = comparison.compare(null, null);

            assertThat(result.tarife()).hasSize(4);
            assertThat(result.leistungsbereiche()).hasSize(10);
            assertThat(result.stand()).isNotNull();
        }

        @Test
        @DisplayName("follows the Produktmodell and not the order of the request")
        void order() {
            ComparisonResult result = comparison.compare(
                    List.of("ATRA_DENT_X", "ATRA_DENT_S"), List.of("PZR", "IMP"));

            assertThat(result.tarife()).extracting(Tarifangabe::schluessel)
                    .containsExactly("ATRA_DENT_S", "ATRA_DENT_X");
            assertThat(result.leistungsbereiche()).extracting(LeistungsbereichComparison::schluessel)
                    .containsExactly("IMP", "PZR");
        }

        @Test
        @DisplayName("names an unknown key instead of returning an empty column")
        void unknown() {
            assertThatThrownBy(() -> comparison.compare(List.of("ATRA_DENT_XXL"), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ATRA_DENT_XXL")
                    .hasMessageContaining("ATRA_DENT_S");

            assertThatThrownBy(() -> comparison.compare(null, List.of("ZAHNFEE")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ZAHNFEE");
        }
    }

    @Nested
    @DisplayName("one line")
    class Zeile {

        @Test
        @DisplayName("carries every requested Tarif -- including the one that does not pay")
        void everyTarif() {
            ComparisonResult result = comparison.compare(null, List.of("IMP"));

            LeistungsbereichComparison implantate = result.leistungsbereiche().getFirst();
            assertThat(implantate.name()).isNotBlank();
            assertThat(implantate.beschreibung()).isNotBlank();
            assertThat(implantate.leistungen()).containsOnlyKeys(
                    "ATRA_DENT_S", "ATRA_DENT_B", "ATRA_DENT_X", "ATRA_DENT_X_SB");
            assertThat(implantate.leistungen().get("ATRA_DENT_S").versichert()).isFalse();
            assertThat(implantate.leistungen().get("ATRA_DENT_X").versichert()).isTrue();
        }

        @Test
        @DisplayName("shows the resolved Leistungen of the Selbstbehalt Tarif")
        void leistungenLike() {
            ComparisonResult result = comparison.compare(
                    List.of("ATRA_DENT_X", "ATRA_DENT_X_SB"), List.of("ZE"));

            var zahnersatz = result.leistungsbereiche().getFirst().leistungen();
            assertThat(zahnersatz.get("ATRA_DENT_X_SB"))
                    .isEqualTo(zahnersatz.get("ATRA_DENT_X"));
        }
    }

    @Nested
    @DisplayName("one column")
    class Spalte {

        @Test
        @DisplayName("carries the base values of the Tarif")
        void baseValues() {
            Tarifangabe brillant = comparison.compare(List.of("ATRA_DENT_X"), List.of())
                    .tarife().getFirst();

            assertThat(brillant.anzeigename()).isNotBlank();
            assertThat(brillant.positionierung()).isNotBlank();
            assertThat(brillant.eintrittsalter()).isNotNull();
            assertThat(brillant.jahreshoechstgrenze()).isNotNull();
            assertThat(brillant.wartezeitMonate()).isNotNegative();
            assertThat(brillant.zahnstaffel()).isNotEmpty();
        }

        @Test
        @DisplayName("distinguishes the two brillant Tarife by the Selbstbehalt")
        void selbstbehalt() {
            List<Tarifangabe> both = comparison.compare(
                    List.of("ATRA_DENT_X", "ATRA_DENT_X_SB"), List.of()).tarife();

            assertThat(both.get(0).selbstbehalt()).isNotEqualTo(both.get(1).selbstbehalt());
        }

        @Test
        @DisplayName("points at the Bedingungswerk -- for the Selbstbehalt Tarif too")
        void bedingungswerk() {
            ComparisonResult result = comparison.compare(
                    List.of("ATRA_DENT_X", "ATRA_DENT_X_SB"), List.of());

            assertThat(result.tarife()).extracting(Tarifangabe::bedingungswerk)
                    .allSatisfy(kennung -> assertThat(kennung).endsWith("-avb"));
            assertThat(result.tarife().get(0).bedingungswerk())
                    .isEqualTo(result.tarife().get(1).bedingungswerk());
        }
    }
}
