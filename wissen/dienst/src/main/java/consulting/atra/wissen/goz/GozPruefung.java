package consulting.atra.wissen.goz;

import consulting.atra.produktmodell.LeistungsbereichDescription;
import consulting.atra.wissen.data.GoaeCatalog;
import consulting.atra.wissen.data.GoaePosition;
import consulting.atra.wissen.data.GozCatalog;
import consulting.atra.wissen.data.GozPosition;
import consulting.atra.produktmodell.Leistung;
import consulting.atra.produktmodell.Tarif;
import consulting.atra.produktmodell.TarifCatalog;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class GozPruefung {

    private final TarifCatalog tarifCatalog;
    private final GozCatalog gozCatalog;
    private final GoaeCatalog goaeCatalog;
    private final Map<String, String> areaNames;

    public GozPruefung(TarifCatalog tarifCatalog, GozCatalog gozKatalog, GoaeCatalog goaeKatalog) {
        this.tarifCatalog = Objects.requireNonNull(tarifCatalog, "tarifCatalog");
        this.gozCatalog = Objects.requireNonNull(gozKatalog, "gozKatalog");
        this.goaeCatalog = Objects.requireNonNull(goaeKatalog, "goaeKatalog");
        this.areaNames = areaNames(tarifCatalog);
    }

    public List<GozBefund> check(String tarifschluessel, List<String> nummern) {
        Objects.requireNonNull(tarifschluessel, "tarifschluessel");
        Objects.requireNonNull(nummern, "nummern");

        Tarif tarif = tarifCatalog.tarif(tarifschluessel).orElseThrow(
                () -> new UnknownTarifException(tarifschluessel, tarifCatalog.tarifKeys()));

        return nummern.stream().map(nummer -> finding(tarif, nummer)).toList();
    }

    private GozBefund finding(Tarif tarif, String nummer) {
        Objects.requireNonNull(nummer, "nummer");

        Optional<GozPosition> found = gozCatalog.position(nummer);
        if (found.isEmpty()) {
            return goaeFinding(tarif, nummer);
        }

        GozPosition position = found.get();
        String ambiguous = goaeCatalog.position(nummer)
                .map(goae -> Begruendung.alsoInGoae(nummer, goae.bezeichnung()))
                .orElse(null);

        if (!position.areaDeterminable()) {
            return GozBefund.notDeterminable(nummer, position.bezeichnung(), position.abschnitt(),
                    Begruendung.withPostscript(
                            Begruendung.notDeterminable(nummer, tarif.anzeigename(), position.hinweis()),
                            ambiguous));
        }

        String area = position.leistungsbereich();
        Leistung leistung = tarif.findLeistung(area).orElseThrow(() -> new IllegalStateException(
                "Die GOZ-Zuordnung verweist bei Nummer " + nummer + " auf den Leistungsbereich "
                        + area + ", den der Tarif " + tarif.schluessel() + " nicht kennt"));

        if (!leistung.versichert()) {
            return GozBefund.notIncluded(nummer, position.bezeichnung(), position.abschnitt(),
                    area, Begruendung.withPostscript(
                            Begruendung.notIncluded(nummer, leistungsbereichName(area),
                                    tarif.anzeigename(), position.hinweis()), ambiguous));
        }

        if (leistung.quote() == null) {
            throw new IllegalStateException("Der Tarif " + tarif.schluessel() + " versichert den "
                    + "Leistungsbereich " + area + ", nennt aber keine Quote");
        }

        Leistungsgrenzen limits = Leistungsgrenzen.aus(leistung);
        return GozBefund.included(nummer, position.bezeichnung(), position.abschnitt(), area,
                leistung.quote(), limits,
                Begruendung.withPostscript(
                        Begruendung.included(nummer, leistungsbereichName(area), tarif.anzeigename(),
                                leistung.quote(), limits, position.hinweis()), ambiguous));
    }

    private GozBefund goaeFinding(Tarif tarif, String nummer) {
        Optional<GoaePosition> found = goaeCatalog.position(nummer);
        if (found.isEmpty()) {
            return GozBefund.unknown(nummer, Begruendung.unknown(nummer, tarif.anzeigename()));
        }

        GoaePosition position = found.get();
        if (!position.areaDeterminable()) {
            return GozBefund.notDeterminable(nummer, position.bezeichnung(), null,
                    Begruendung.notDeterminableGoae(nummer, tarif.anzeigename(), position.hinweis()));
        }

        String area = position.leistungsbereich();
        Leistung leistung = tarif.findLeistung(area).orElseThrow(() -> new IllegalStateException(
                "Der GOAe-Auszug verweist bei Nummer " + nummer + " auf den Leistungsbereich "
                        + area + ", den der Tarif " + tarif.schluessel() + " nicht kennt"));

        if (!leistung.versichert()) {
            return GozBefund.notIncluded(nummer, position.bezeichnung(), null, area,
                    Begruendung.notIncluded(nummer, leistungsbereichName(area),
                            tarif.anzeigename(), position.hinweis()));
        }

        if (leistung.quote() == null) {
            throw new IllegalStateException("Der Tarif " + tarif.schluessel() + " versichert den "
                    + "Leistungsbereich " + area + ", nennt aber keine Quote");
        }

        Leistungsgrenzen limits = Leistungsgrenzen.aus(leistung);
        return GozBefund.included(nummer, position.bezeichnung(), null, area,
                leistung.quote(), limits,
                Begruendung.included(nummer, leistungsbereichName(area), tarif.anzeigename(),
                        leistung.quote(), limits, position.hinweis()));
    }

    private String leistungsbereichName(String schluessel) {
        return areaNames.getOrDefault(schluessel, schluessel);
    }

    private static Map<String, String> areaNames(TarifCatalog catalog) {
        Map<String, String> names = new LinkedHashMap<>();
        for (LeistungsbereichDescription bereich : catalog.leistungsbereiche()) {
            names.put(bereich.schluessel(), bereich.name());
        }
        return Map.copyOf(names);
    }
}
