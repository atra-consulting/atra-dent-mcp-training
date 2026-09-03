package consulting.atra.wissen.comparison;

import consulting.atra.produktmodell.LeistungsbereichDescription;
import consulting.atra.produktmodell.Leistung;
import consulting.atra.produktmodell.Staffel;
import consulting.atra.produktmodell.Tarif;
import consulting.atra.produktmodell.TarifCatalog;
import consulting.atra.wissen.documents.CatalogDocument;
import consulting.atra.wissen.documents.DocumentCatalog;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class TarifComparison {

    private final TarifCatalog tarife;
    private final DocumentCatalog documents;

    public TarifComparison(TarifCatalog tarife, DocumentCatalog dokumente) {
        this.tarife = Objects.requireNonNull(tarife, "tarife");
        this.documents = Objects.requireNonNull(dokumente, "dokumente");
    }

    public ComparisonResult compare(List<String> tarifschluessel,
                                        List<String> bereichsschluessel) {

        List<String> chosenTarife = select(
                tarifschluessel, tarife.tarifKeys(), "Tarif");
        List<String> chosenAreas = select(
                bereichsschluessel, tarife.leistungsbereichKeys(), "Leistungsbereich");

        List<Tarifangabe> columns = chosenTarife.stream()
                .map(schluessel -> column(tarif(schluessel)))
                .toList();

        List<LeistungsbereichComparison> lines = new ArrayList<>();
        for (LeistungsbereichDescription bereich : tarife.leistungsbereiche()) {
            if (!chosenAreas.contains(bereich.schluessel())) {
                continue;
            }
            lines.add(line(bereich, chosenTarife));
        }

        return new ComparisonResult(tarife.asOf(), columns, List.copyOf(lines));
    }

    private static List<String> select(List<String> desired, List<String> alle,
                                           String was) {
        if (desired == null || desired.isEmpty()) {
            return alle;
        }
        List<String> unknown = desired.stream().filter(s -> !alle.contains(s)).toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(was + " nicht bekannt: " + unknown
                    + ". Gueltig sind " + alle);
        }
        return alle.stream().filter(desired::contains).toList();
    }

    private Tarif tarif(String schluessel) {
        return tarife.tarif(schluessel).orElseThrow(() -> new IllegalStateException(
                "Tarif " + schluessel + " steht in der Schluesselliste, aber nicht im Katalog"));
    }

    private Tarifangabe column(Tarif tarif) {
        List<Staffel.Stufe> tiers = tarife.staffel(tarif.staffel())
                .map(Staffel::stufen)
                .orElse(List.of());
        String bedingungswerk = documents.bedingungswerk(tarif.schluessel())
                .map(CatalogDocument::dokumentId)
                .orElse("");
        return new Tarifangabe(
                tarif.schluessel(),
                tarif.anzeigename(),
                tarif.positionierung(),
                tarif.eintrittsalter(),
                tarif.selbstbehalt(),
                tarif.jahreshoechstgrenze(),
                tarif.wartezeitMonate(),
                tarif.wartezeitEntfaelltBeiVorversicherung(),
                tiers,
                bedingungswerk);
    }

    private LeistungsbereichComparison line(LeistungsbereichDescription bereich, List<String> gewaehlteTarife) {
        Map<String, Leistung> leistungen = new LinkedHashMap<>();
        for (String schluessel : gewaehlteTarife) {
            tarife.findLeistung(schluessel, bereich.schluessel())
                    .ifPresent(leistung -> leistungen.put(schluessel, leistung));
        }
        return new LeistungsbereichComparison(
                bereich.schluessel(), bereich.name(), bereich.beschreibung(), leistungen);
    }
}
