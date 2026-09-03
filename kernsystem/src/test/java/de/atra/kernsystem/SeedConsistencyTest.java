package de.atra.kernsystem;

import de.atra.kernsystem.generated.model.Ablehnungsgrund;
import de.atra.kernsystem.generated.model.Bewertungsposition;
import de.atra.kernsystem.generated.model.GozZustand;
import de.atra.kernsystem.generated.model.Kunde;
import de.atra.kernsystem.generated.model.Leistungsbereich;
import de.atra.kernsystem.generated.model.Schadensfall;
import de.atra.kernsystem.generated.model.Schadensfallstatus;
import de.atra.kernsystem.generated.model.Schadenposition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import org.yaml.snakeyaml.Yaml;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SeedConsistencyTest {

    static final ObjectMapper MAPPER = JsonMapper.builder().build();

    static final Path GOZ_ZUORDNUNG = Path.of("..", "wissen", "daten", "goz-zuordnung.yaml");

    static List<Kunde> kunden;
    static List<Schadensfall> schadensfaelle;

    static Map<String, String> mapping;

    @BeforeAll
    static void loadTheStore() throws Exception {
        kunden = MAPPER.readValue(Files.readString(Path.of("data/kunden.json")),
                new TypeReference<List<Kunde>>() {});
        schadensfaelle = MAPPER.readValue(Files.readString(Path.of("data/schadensfaelle.json")),
                new TypeReference<List<Schadensfall>>() {});
        mapping = loadMapping();
    }

    @SuppressWarnings("unchecked")
    static Map<String, String> loadMapping() throws Exception {
        Map<String, Object> wurzel = new Yaml().load(
                Files.readString(GOZ_ZUORDNUNG, StandardCharsets.UTF_8));
        Map<String, String> bereiche = new HashMap<>();
        for (Map<String, Object> position
                : (List<Map<String, Object>>) wurzel.get("positionen")) {
            Object number = position.get("nummer");
            if (number != null) {
                Object area = position.get("leistungsbereich");
                bereiche.put(String.valueOf(number),
                        area == null ? null : String.valueOf(area));
            }
        }
        return bereiche;
    }

    @Test
    void the_store_holds_fifteen_kunden_and_twenty_nine_schadensfaelle() {
        assertThat(kunden).hasSize(15);
        assertThat(schadensfaelle).hasSize(29);
    }

    @Test
    void every_rechnungsbetrag_is_the_sum_of_its_positionen() {
        for (Schadensfall fall : schadensfaelle) {
            BigDecimal sum = fall.getPositionen().stream()
                    .map(Schadenposition::getBetrag)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(fall.getRechnungsbetrag())
                    .as("Schadensfall %d", fall.getId())
                    .isEqualByComparingTo(sum);
        }
    }

    @Test
    void no_bewertung_zustand_contradicts_the_goz_mapping() {
        assertThat(mapping)
                .as("Zuordnung aus %s", GOZ_ZUORDNUNG)
                .hasSizeGreaterThan(100)
                .containsKey("0010");

        List<String> contradictions = new ArrayList<>();
        for (Schadensfall fall : schadensfaelle) {
            if (fall.getBewertung() == null) {
                continue;
            }
            for (Bewertungsposition position : fall.getBewertung().getPositionen()) {
                if (!mapping.containsKey(position.getGoz())) {
                    continue;
                }
                String area = mapping.get(position.getGoz());
                boolean unbestimmbar = position.getZustand() == GozZustand.NICHT_BESTIMMBAR;
                if (area == null && !unbestimmbar) {
                    contradictions.add("Fall %d, Position %s: Bestand sagt %s, aber %s fuehrt die "
                            .formatted(fall.getId(), position.getGoz(), position.getZustand(),
                                    GOZ_ZUORDNUNG)
                            + "Nummer mit leistungsbereich: null -- goz_pruefen antwortet dazu "
                            + GozZustand.NICHT_BESTIMMBAR);
                } else if (area != null && unbestimmbar) {
                    contradictions.add("Fall %d, Position %s: Bestand sagt %s, aber %s ordnet die "
                            .formatted(fall.getId(), position.getGoz(), position.getZustand(),
                                    GOZ_ZUORDNUNG)
                            + "Nummer dem Leistungsbereich " + area
                            + " zu -- goz_pruefen bestimmt sie also");
                }
            }
        }

        assertThat(contradictions)
                .as("Bewertungszustaende, die der Wissensbasis widersprechen")
                .isEmpty();
    }

    @Test
    void every_schadensfall_points_at_an_existing_kunde() {
        Set<Long> knownIds = kunden.stream().map(Kunde::getId).collect(Collectors.toSet());

        assertThat(schadensfaelle).allSatisfy(fall ->
                assertThat(knownIds).contains(fall.getKundenId()));
    }

    @Test
    void all_seven_stati_occur() {
        assertThat(schadensfaelle).extracting(Schadensfall::getStatus)
                .contains(Schadensfallstatus.values());
    }

    @Test
    void all_ten_leistungsbereiche_occur() {
        Set<Leistungsbereich> covered = schadensfaelle.stream()
                .flatMap(f -> f.getPositionen().stream())
                .map(Schadenposition::getLeistungsbereich)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        assertThat(covered).containsExactlyInAnyOrder(Leistungsbereich.values());
    }

    @Test
    void all_seven_ablehnungsgruende_are_used() {
        Set<Ablehnungsgrund> reasons = schadensfaelle.stream()
                .map(Schadensfall::getAblehnungsgrund)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        assertThat(reasons).containsExactlyInAnyOrder(Ablehnungsgrund.values());
    }

    @Test
    void rejected_faelle_always_carry_a_grund() {
        assertThat(schadensfaelle)
                .filteredOn(f -> f.getStatus() == Schadensfallstatus.ABGELEHNT)
                .allSatisfy(f -> assertThat(f.getAblehnungsgrund()).isNotNull());
    }

    @Test
    void unprocessed_schadensfaelle_carry_no_erstattungsbetrag_yet() {
        assertThat(schadensfaelle)
                .filteredOn(f -> f.getStatus() == Schadensfallstatus.EINGEREICHT
                        || f.getStatus() == Schadensfallstatus.IN_PRUEFUNG)
                .allSatisfy(f -> assertThat(f.getErstattungsbetrag()).isNull());
    }

    @Test
    void every_tarif_is_used_at_least_once() {
        assertThat(kunden).extracting(Kunde::getTarifId)
                .contains(de.atra.kernsystem.generated.model.TarifId.values());
    }

    @Test
    void both_kundenstatus_values_occur() {
        Set<de.atra.kernsystem.generated.model.Kundenstatus> stati = kunden.stream()
                .map(Kunde::getStatus)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        assertThat(stati).containsExactlyInAnyOrder(
                de.atra.kernsystem.generated.model.Kundenstatus.values());
    }

    @Test
    void fehlende_zaehne_covers_zero_through_four() {
        Set<Integer> values = kunden.stream()
                .map(Kunde::getFehlendeZaehne)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        assertThat(values).contains(0, 1, 2, 3, 4);
    }

    @Test
    void both_vorversicherung_values_occur() {
        Set<Boolean> values = kunden.stream()
                .map(Kunde::getVorversicherung)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        assertThat(values).containsExactlyInAnyOrder(true, false);
    }
}
