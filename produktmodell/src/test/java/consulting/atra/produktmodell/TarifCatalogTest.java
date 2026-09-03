package consulting.atra.produktmodell;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TarifCatalogTest {

    private final TarifCatalog catalog = ProduktmodellData.tarifCatalog();

    @Test
    @DisplayName("An unknown Tarif key yields empty and no error")
    void unknownTarif() {
        assertThat(catalog.tarif("ATRA_DENT_Z")).isEmpty();
        assertThat(catalog.findLeistung("ATRA_DENT_Z", "ZE")).isEmpty();
    }

    @Test
    @DisplayName("Quoten and limits come from the file")
    void leistungRead() {
        Leistung implants = catalog.findLeistung("ATRA_DENT_B", "IMP").orElseThrow();

        assertThat(implants.versichert()).isTrue();
        assertThat(implants.quote()).isEqualTo(85);
        assertThat(implants.maxFaelle()).isEqualTo(4);
        assertThat(implants.zeitraumJahre()).isEqualTo(5);
        assertThat(implants.limitProJahr()).isNull();
        assertThat(implants.hasLimits()).isTrue();
    }

    @Test
    @DisplayName("An uninsured Leistungsbereich is present, only without a Quote")
    void notInsured() {
        Leistung implants = catalog.findLeistung("ATRA_DENT_S", "IMP").orElseThrow();

        assertThat(implants.versichert()).isFalse();
        assertThat(implants.quote()).isNull();
        assertThat(implants.hasLimits()).isFalse();
    }

    @Test
    @DisplayName("leistungen_wie is resolved while reading")
    void leistungenAreResolved() {
        Tarif withSelbstbehalt = catalog.tarif("ATRA_DENT_X_SB").orElseThrow();
        Tarif baseTarif = catalog.tarif("ATRA_DENT_X").orElseThrow();

        assertThat(withSelbstbehalt.leistungenWie()).isEqualTo("ATRA_DENT_X");
        assertThat(withSelbstbehalt.leistungen()).isEqualTo(baseTarif.leistungen());
        assertThat(withSelbstbehalt.selbstbehalt()).isEqualByComparingTo(BigDecimal.valueOf(250));
        assertThat(baseTarif.selbstbehalt()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Staffeln, exclusions and the as-of date are read")
    void furtherParts() {
        assertThat(catalog.staffel("STAFFEL_X").orElseThrow().stufen())
                .last()
                .satisfies(stufe -> {
                    assertThat(stufe.bisJahr()).isNull();
                    assertThat(stufe.betrag()).isNull();
                });
        assertThat(catalog.exclusions()).extracting(Ausschluss::schluessel)
                .contains("ANGERATEN", "KOSMETIK", "NICHT_APPROBIERT", "FEHLENDE_ZAEHNE");
        assertThat(catalog.asOf()).isNotNull();
    }

    @Test
    @DisplayName("A reference to an unknown Tarif aborts reading")
    void referenceIntoNothing() {
        String yaml = """
                stand: 2026-01-01
                leistungsbereiche:
                  - schluessel: ZE
                    name: Zahnersatz
                    beschreibung: Ersatz fehlender Zaehne
                tarife:
                  - schluessel: ATRA_DENT_S
                    anzeigename: atra.dent.smart
                    positionierung: Einstieg
                    eintrittsalter: { von: 18, bis: 70 }
                    selbstbehalt: 0
                    jahreshoechstgrenze: null
                    wartezeit_monate: 8
                    staffel: STAFFEL_S
                    leistungen_wie: ATRA_DENT_GIBT_ES_NICHT
                staffeln: []
                ausschluesse: []
                """;

        assertThatThrownBy(() -> read(yaml))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ATRA_DENT_GIBT_ES_NICHT");
    }

    @Test
    @DisplayName("An unknown YAML key aborts reading")
    void typoInKey() {
        String yaml = """
                stand: 2026-01-01
                leistungsbereiche: []
                tarife: []
                staffeln: []
                ausschluesse: []
                unbekanntes_feld: 42
                """;

        assertThatThrownBy(() -> read(yaml)).isInstanceOf(JacksonException.class);
    }

    private static TarifCatalog read(String yaml) throws IOException {
        return TarifCatalog.read(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }
}
