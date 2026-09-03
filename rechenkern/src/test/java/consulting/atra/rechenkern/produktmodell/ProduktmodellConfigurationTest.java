package consulting.atra.rechenkern.produktmodell;

import consulting.atra.produktmodell.Tarif;
import consulting.atra.produktmodell.TarifCatalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProduktmodellConfigurationTest {

    @Autowired
    TarifCatalog tarifCatalog;

    @Test
    @DisplayName("the catalog carries all four Tarife")
    void fourTarife() {
        assertThat(tarifCatalog.tarifKeys())
                .containsExactlyInAnyOrder(
                        "ATRA_DENT_S", "ATRA_DENT_B", "ATRA_DENT_X", "ATRA_DENT_X_SB");
    }

    @Test
    @DisplayName("leistungen_wie is resolved: ATRA_DENT_X_SB carries its own Leistungen")
    void leistungenAreResolved() {
        Tarif baseTarif = tarifCatalog.tarif("ATRA_DENT_X").orElseThrow();
        Tarif withSelbstbehalt = tarifCatalog.tarif("ATRA_DENT_X_SB").orElseThrow();

        assertThat(withSelbstbehalt.leistungenWie()).isEqualTo("ATRA_DENT_X");

        assertThat(withSelbstbehalt.leistungen())
                .isNotEmpty()
                .isEqualTo(baseTarif.leistungen());
    }

    @Test
    @DisplayName("every Tarif carries Leistungen for all ten Leistungsbereiche")
    void everyTarifCoversAllLeistungsbereiche() {
        assertThat(tarifCatalog.tarife()).allSatisfy(tarif ->
                assertThat(tarif.leistungen().keySet())
                        .as("Leistungsbereiche of %s", tarif.schluessel())
                        .containsExactlyInAnyOrderElementsOf(tarifCatalog.leistungsbereichKeys()));
    }
}
