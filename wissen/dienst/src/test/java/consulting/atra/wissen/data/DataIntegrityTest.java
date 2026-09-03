package consulting.atra.wissen.data;

import consulting.atra.produktmodell.TarifCatalog;

import consulting.atra.wissen.api.generated.model.Leistungsbereich;
import consulting.atra.wissen.api.generated.model.Tarifschluessel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataIntegrityTest {

    private final TarifCatalog tarife = TestData.tarifCatalog();
    private final GozCatalog goz = TestData.gozCatalog();
    private final GoaeCatalog goae = TestData.goaeCatalog();

    @Test
    @DisplayName("every Tarif says something about each of the ten Leistungsbereiche")
    void tarifeCoverAllLeistungsbereiche() {
        List<String> areas = tarife.leistungsbereichKeys();
        assertThat(areas).hasSize(10);

        assertThat(tarife.tarife()).allSatisfy(tarif ->
                assertThat(tarif.leistungen())
                        .as("Leistungen von %s", tarif.schluessel())
                        .containsOnlyKeys(areas.toArray(String[]::new)));
    }

    @Test
    @DisplayName("every GOZ Position carries a valid Leistungsbereich key or null")
    void gozLeistungsbereicheAreValid() {
        Set<String> valid = Set.copyOf(tarife.leistungsbereichKeys());

        assertThat(goz.positionen()).allSatisfy(position -> {
            String area = position.leistungsbereich();
            if (area != null) {
                assertThat(area)
                        .as("Leistungsbereich von %s", position.nummer())
                        .isNotBlank()
                        .isIn(valid);
            }
        });
    }

    @Test
    @DisplayName("215 Gebuehrennummern, unique and four digits")
    void gozNummernAreUniqueAndFourDigits() {
        assertThat(goz.positionen()).hasSize(215);

        assertThat(goz.positionen())
                .extracting(GozPosition::nummer)
                .doesNotHaveDuplicates()
                .allSatisfy(nummer -> assertThat(nummer).matches("^[0-9]{4}$"));
    }

    @Test
    @DisplayName("the declared Position count per section matches the Positionen")
    void coverageMatchesPositionen() {
        Map<String, Integer> tatsaechlich = goz.positionenPerSection().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size()));

        Map<String, Integer> declared = goz.coverage().stream()
                .collect(Collectors.toMap(SectionCoverage::abschnitt,
                        SectionCoverage::positionen));

        assertThat(tatsaechlich).containsExactlyInAnyOrderEntriesOf(declared);

        assertThat(declared.keySet())
                .containsExactlyInAnyOrder("A", "B", "C", "D", "E", "F", "G", "H", "J", "K", "L");
        assertThat(declared.values().stream().mapToInt(Integer::intValue).sum())
                .isEqualTo(goz.positionen().size());
    }

    @Test
    @DisplayName("every GOZ Position names a section that is in the coverage")
    void sectionsAreDeclared() {
        Set<String> declared = goz.coverage().stream()
                .map(SectionCoverage::abschnitt)
                .collect(Collectors.toSet());

        assertThat(goz.positionen()).allSatisfy(position ->
                assertThat(position.abschnitt())
                        .as("Abschnitt von %s", position.nummer())
                        .isIn(declared));
    }

    @Test
    @DisplayName("the Tarif keys of the file and the contract enum agree")
    void tarifKeysMatchTheContract() {
        assertThat(tarife.tarifKeys())
                .containsExactlyInAnyOrderElementsOf(
                        values(Tarifschluessel.values(), Tarifschluessel::getValue));
    }

    @Test
    @DisplayName("the Leistungsbereiche of the file and the contract enum agree")
    void leistungsbereicheMatchTheContract() {
        assertThat(tarife.leistungsbereichKeys())
                .containsExactlyInAnyOrderElementsOf(
                        values(Leistungsbereich.values(), Leistungsbereich::getValue));
    }

    @Test
    @DisplayName("every Tarif references a Staffel that exists")
    void staffelReferencesPointSomewhere() {
        assertThat(tarife.tarife()).allSatisfy(tarif ->
                assertThat(tarife.staffel(tarif.staffel()))
                        .as("Staffel %s von %s", tarif.staffel(), tarif.schluessel())
                        .isPresent());
    }

    @Test
    @DisplayName("an insured Leistung names a Quote, an uninsured one does not")
    void quotenAreConsistent() {
        assertThat(tarife.tarife()).allSatisfy(tarif ->
                tarif.leistungen().forEach((bereich, leistung) -> {
                    if (leistung.versichert()) {
                        assertThat(leistung.quote())
                                .as("Quote fuer %s in %s", bereich, tarif.schluessel())
                                .isNotNull()
                                .isBetween(0, 100);
                    } else {
                        assertThat(leistung.quote())
                                .as("Quote fuer den nicht versicherten Bereich %s in %s",
                                        bereich, tarif.schluessel())
                                .isNull();
                    }
                }));
    }

    @Test
    @DisplayName("a Leistungsbereich without a Position really has none")
    void leistungsbereicheWithoutPositionAreEmpty() {
        Set<String> assigned = goz.positionen().stream()
                .map(GozPosition::leistungsbereich)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        assertThat(goz.areasWithoutPosition()).allSatisfy(bereich ->
                assertThat(assigned)
                        .as("%s ist als bereich_ohne_position gefuehrt", bereich.leistungsbereich())
                        .doesNotContain(bereich.leistungsbereich()));
    }

    @Test
    @DisplayName("every Nummer of the GOAe excerpt has four digits")
    void goaeNummernAreFourDigits() {
        assertThat(goae.positionen()).extracting(GoaePosition::nummer)
                .isNotEmpty()
                .allSatisfy(nummer -> assertThat(nummer).matches("[0-9]{4}"));
    }

    @Test
    @DisplayName("the shipped GOAe excerpt keeps the Leistungsbereich rule")
    void goaeLeistungsbereicheAreValid() {
        assertThatNoException().isThrownBy(() -> checkLeistungsbereiche(goae));
    }

    @Test
    @DisplayName("the Leistungsbereich rule accepts a valid key and rejects every other")
    void theLeistungsbereichRuleRejectsAnInventedKey() {
        assertThatNoException()
                .as("NAR ist ein Leistungsbereich des Produktmodells")
                .isThrownBy(() -> checkLeistungsbereiche(catalogWithArea("NAR")));

        assertThatThrownBy(() -> checkLeistungsbereiche(catalogWithArea("KIEFERORTHOPAEDIE")))
                .as("ausgeschrieben statt als Schluessel")
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("KIEFERORTHOPAEDIE");

        assertThatThrownBy(() -> checkLeistungsbereiche(catalogWithArea("\"\"")))
                .as("der leere String ist kein ehrliches null")
                .isInstanceOf(AssertionError.class);
    }

    private void checkLeistungsbereiche(GoaeCatalog catalog) {
        Set<String> valid = Set.copyOf(tarife.leistungsbereichKeys());
        for (GoaePosition position : catalog.positionen()) {
            String area = position.leistungsbereich();
            if (area == null) {
                continue;
            }
            assertThat(area)
                    .as("Leistungsbereich von %s", position.nummer())
                    .isNotBlank()
                    .isIn(valid);
        }
    }

    private static GoaeCatalog catalogWithArea(String leistungsbereich) {
        String yaml = """
                stand: Testfassung
                quelle: Testdaten
                abgerufen: 2026-08-19
                hinweis: Nur fuer diesen Test.
                positionen:
                  - nummer: "0000"
                    bezeichnung: Erfundene Leistung
                    leistungsbereich: %s
                    hinweis: Steht in keiner Gebuehrenordnung.
                """.formatted(leistungsbereich);
        try {
            return GoaeCatalog.read(
                    new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }

    @Test
    @DisplayName("the rule for Positionen without a Gebuehrennummer is in the knowledge base")
    void positionenWithoutANummerHaveARule() {
        assertThat(goz.ohneGebuehrennummer())
                .extracting(PositionWithoutNummer::art)
                .contains("material", "verlangen");

        assertThat(goz.ohneGebuehrennummer()).allSatisfy(regel -> {
            assertThat(regel.leistungsbereich())
                    .as("Leistungsbereich der Regel %s", regel.art())
                    .isNull();
            assertThat(regel.begruendung()).as("Begruendung der Regel %s", regel.art()).isNotBlank();
            assertThat(regel.fundstelle())
                    .as("Fundstelle der Regel %s im Bedingungswerk", regel.art())
                    .isNotBlank();
        });
    }

    @Test
    @DisplayName("Material and Labor are covered, Verlangensleistungen are not")
    void theRuleSaysWhatItShouldSay() {
        PositionWithoutNummer material = rule("material");
        assertThat(material.bezeichnung()).contains("Paragraf 9 GOZ");
        assertThat(material.begruendung())
                .startsWith("Erstattungsfähig")
                .contains("folgt dem Leistungsbereich der Behandlung")
                .contains("nicht zuzuordnen");
        assertThat(material.fundstelle()).isEqualTo("Paragraf 5");

        PositionWithoutNummer require = rule("verlangen");
        assertThat(require.begruendung())
                .startsWith("Nicht erstattungsfähig")
                .contains("zahnmedizinische Notwendigkeit");
    }

    private PositionWithoutNummer rule(String art) {
        return goz.ohneGebuehrennummer().stream()
                .filter(regel -> art.equals(regel.art()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Keine Regel fuer die Art " + art));
    }

    private static <T> List<String> values(T[] konstanten, Function<T, String> value) {
        return java.util.Arrays.stream(konstanten).map(value).toList();
    }
}
