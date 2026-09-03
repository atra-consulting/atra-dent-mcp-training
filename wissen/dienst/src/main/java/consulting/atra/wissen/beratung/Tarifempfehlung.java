package consulting.atra.wissen.beratung;

import consulting.atra.produktmodell.Ausschluss;
import consulting.atra.produktmodell.LeistungsbereichDescription;
import consulting.atra.produktmodell.Leistung;
import consulting.atra.produktmodell.Tarif;
import consulting.atra.produktmodell.TarifCatalog;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class Tarifempfehlung {

    private static final String CRITERION_EINTRITTSALTER = "EINTRITTSALTER";
    private static final String CRITERION_AREA_NOT_COVERED = "BEREICH_NICHT_VERSICHERT";
    private static final String CRITERION_MISSING_ZAEHNE = "FEHLENDE_ZAEHNE";
    private static final String EXCLUSION_ANGERATEN = "ANGERATEN";
    private static final String EXCLUSION_MISSING_ZAEHNE = "FEHLENDE_ZAEHNE";

    private final TarifCatalog tarifCatalog;
    private final LeitfadenCatalog leitfaden;
    private final Map<String, String> areaNames;
    private final List<String> tarifeWithZahnException;
    private final String exceptionTextMissingZaehne;
    private final String exclusionTextAngeraten;

    public Tarifempfehlung(TarifCatalog tarifCatalog, LeitfadenCatalog leitfaden) {
        this.tarifCatalog = Objects.requireNonNull(tarifCatalog, "tarifCatalog");
        this.leitfaden = Objects.requireNonNull(leitfaden, "leitfaden");
        this.areaNames = areaNames(tarifCatalog);

        Ausschluss missingZaehne = ausschluss(EXCLUSION_MISSING_ZAEHNE);
        if (missingZaehne.ausnahme() == null) {
            throw new IllegalStateException("Der Ausschluss fehlender Zaehne kennt keine Ausnahme; "
                    + "ohne sie waere das harte Kriterium " + CRITERION_MISSING_ZAEHNE
                    + " nicht auswertbar");
        }
        this.tarifeWithZahnException = List.copyOf(missingZaehne.ausnahme().tarife());
        this.exceptionTextMissingZaehne = BeratungTexts.sentence(missingZaehne.ausnahme().text());
        this.exclusionTextAngeraten = BeratungTexts.sentence(ausschluss(EXCLUSION_ANGERATEN).text());

        checkFoundations();
    }

    public BeratungsResult recommend(BeratungRequest anliegen) {
        Objects.requireNonNull(anliegen, "anliegen");
        List<String> focusAreas = checkedAreas(anliegen.behandlungsschwerpunkte());

        List<ExcludedTarif> excluded = new ArrayList<>();
        List<Tarif> remaining = new ArrayList<>();
        for (String schluessel : leitfaden.order()) {
            Tarif tarif = tarif(schluessel);
            ExcludedTarif ausschluss = hardCriterion(tarif, anliegen, focusAreas);
            if (ausschluss == null) {
                remaining.add(tarif);
            } else {
                excluded.add(ausschluss);
            }
        }

        Map<String, List<NeedReason>> gruende = needReasons(remaining, anliegen, focusAreas);
        List<Tarif> ordered = ordered(remaining, gruende);
        List<Empfehlung> recommendations = recommendations(ordered, gruende, anliegen, focusAreas);

        DecisionPath path = path(recommendations, gruende);
        return new BeratungsResult(
                anliegen,
                path,
                summary(path, recommendations, excluded),
                recommendations,
                excluded,
                hints(anliegen, remaining, recommendations, focusAreas),
                leitfaden.principle(),
                leitfaden.complianceLimits(),
                leitfaden.confidentiality());
    }


    private ExcludedTarif hardCriterion(Tarif tarif, BeratungRequest anliegen,
                                                  List<String> schwerpunkte) {
        if (anliegen.alter() < tarif.eintrittsalter().von()
                || anliegen.alter() > tarif.eintrittsalter().bis()) {
            return new ExcludedTarif(tarif.schluessel(), tarif.anzeigename(),
                    CRITERION_EINTRITTSALTER, BeratungTexts.exclusionByAge(tarif, anliegen.alter()));
        }
        for (String bereich : schwerpunkte) {
            if (!leistung(tarif, bereich).versichert()) {
                return new ExcludedTarif(tarif.schluessel(), tarif.anzeigename(),
                        CRITERION_AREA_NOT_COVERED,
                        BeratungTexts.exclusionByArea(tarif, leistungsbereichName(bereich)));
            }
        }
        return null;
    }


    private Map<String, List<NeedReason>> needReasons(List<Tarif> verbleibende,
                                                           BeratungRequest anliegen,
                                                           List<String> schwerpunkte) {
        Map<String, List<NeedReason>> gruende = new LinkedHashMap<>();
        for (Tarif tarif : verbleibende) {
            gruende.put(tarif.schluessel(), new ArrayList<>());
        }

        for (String bereich : schwerpunkte) {
            for (Tarif tarif : bestCoverage(verbleibende, bereich)) {
                gruende.get(tarif.schluessel()).add(NeedReason.focusArea(bereich,
                        BeratungTexts.focusAreaReason(leistungsbereichName(bereich),
                                leistung(tarif, bereich))));
            }
        }

        if (anliegen.hasMissingZaehne()) {
            for (Tarif tarif : verbleibende) {
                if (tarifeWithZahnException.contains(tarif.schluessel())) {
                    gruende.get(tarif.schluessel()).add(NeedReason.missingZaehne(
                            BeratungTexts.missingZaehneReason(exceptionTextMissingZaehne)));
                }
            }
        }

        if (anliegen.vorversicherung() == Vorversicherung.LUECKENLOS) {
            for (Tarif tarif : verbleibende) {
                if (tarif.wartezeitEntfaelltBeiVorversicherung()) {
                    gruende.get(tarif.schluessel())
                            .add(NeedReason.wartezeit(BeratungTexts.wartezeitGrund(tarif)));
                }
            }
        }
        return gruende;
    }

    private List<Tarif> bestCoverage(List<Tarif> verbleibende, String bereich) {
        List<Tarif> best = new ArrayList<>();
        Leistung bestValue = null;
        boolean unterschied = false;
        for (Tarif tarif : verbleibende) {
            Leistung leistung = leistung(tarif, bereich);
            if (bestValue == null) {
                bestValue = leistung;
                best.add(tarif);
                continue;
            }
            int comparison = CoverageComparison.comparisons(leistung, bestValue);
            if (comparison > 0) {
                bestValue = leistung;
                best.clear();
                best.add(tarif);
                unterschied = true;
            } else if (comparison == 0) {
                best.add(tarif);
            } else {
                unterschied = true;
            }
        }
        return unterschied ? List.copyOf(best) : List.of();
    }

    private List<Tarif> ordered(List<Tarif> verbleibende, Map<String, List<NeedReason>> gruende) {
        Comparator<Tarif> byNeed = Comparator
                .comparingInt((Tarif tarif) -> gruende.get(tarif.schluessel()).size()).reversed();
        return verbleibende.stream()
                .sorted(byNeed.thenComparingInt(
                        tarif -> leitfaden.recommendationOrder().rank(tarif.schluessel())))
                .toList();
    }

    private List<Empfehlung> recommendations(List<Tarif> geordnete,
                                          Map<String, List<NeedReason>> gruende,
                                          BeratungRequest anliegen, List<String> schwerpunkte) {
        List<Empfehlung> recommendations = new ArrayList<>();
        for (int position = 0; position < geordnete.size(); position++) {
            Tarif tarif = geordnete.get(position);
            TargetKunde targetKunde = targetKunde(tarif.schluessel());
            List<NeedReason> own = List.copyOf(gruende.get(tarif.schluessel()));
            boolean ersatzOffen = anliegen.hasMissingZaehne()
                    && !tarifeWithZahnException.contains(tarif.schluessel());
            List<String> coverage = schwerpunkte.stream()
                    .map(bereich -> BeratungTexts.coverageRow(
                            leistungsbereichName(bereich), leistung(tarif, bereich)))
                    .toList();
            int platz = leitfaden.recommendationOrder().rank(tarif.schluessel());

            recommendations.add(new Empfehlung(
                    position + 1,
                    tarif.schluessel(),
                    tarif.anzeigename(),
                    targetKunde.kurzformel(),
                    platz,
                    own,
                    BeratungTexts.recommendationReason(tarif, targetKunde.kurzformel(), own,
                            platz, anliegen, coverage, ersatzOffen),
                    targetKunde.hinweispflicht()));
        }
        return List.copyOf(recommendations);
    }

    private static DecisionPath path(List<Empfehlung> empfehlungen,
                                        Map<String, List<NeedReason>> gruende) {
        if (empfehlungen.isEmpty()) {
            return DecisionPath.KEIN_TARIF;
        }
        boolean bedarfUnterscheidet = gruende.values().stream().anyMatch(liste -> !liste.isEmpty());
        return bedarfUnterscheidet ? DecisionPath.BEDARF : DecisionPath.REIHENFOLGE;
    }

    private static String summary(DecisionPath weg, List<Empfehlung> empfehlungen,
                                          List<ExcludedTarif> ausgeschlossene) {
        return switch (weg) {
            case KEIN_TARIF -> BeratungTexts.summaryNoTarif(ausgeschlossene);
            case BEDARF -> BeratungTexts.summaryNeed(
                    empfehlungen.getFirst().anzeigename(), ausgeschlossene);
            case REIHENFOLGE -> BeratungTexts.summaryOrder(
                    empfehlungen.getFirst().anzeigename(), ausgeschlossene);
        };
    }


    private List<Hinweis> hints(BeratungRequest anliegen, List<Tarif> verbleibende,
                                   List<Empfehlung> empfehlungen, List<String> schwerpunkte) {
        List<Hinweis> hints = new ArrayList<>();

        if (empfehlungen.isEmpty()) {
            hints.add(Hinweis.primary("KEIN_TARIF", BeratungTexts.hintNoTarif()));
        }

        switch (anliegen.angerateneBehandlung()) {
            case ANGERATEN -> hints.add(Hinweis.primary(EXCLUSION_ANGERATEN,
                    BeratungTexts.hintAngeraten(exclusionTextAngeraten)));
            case NICHT_ERHOBEN -> hints.add(Hinweis.primary("ANGERATEN_OFFEN",
                    BeratungTexts.hintAngeratenOpen(exclusionTextAngeraten)));
            case KEINE -> {
            }
        }

        if (anliegen.hasMissingZaehne()) {
            List<String> withException = namesWithException(verbleibende, true);
            List<String> withoutException = namesWithException(verbleibende, false);
            hints.add(Hinweis.primary(CRITERION_MISSING_ZAEHNE,
                    BeratungTexts.hintMissingZaehne(anliegen.fehlendeZaehne(), withException,
                            withoutException, exceptionTextMissingZaehne)));
        } else if (!anliegen.missingZaehneRecorded()) {
            hints.add(Hinweis.primary("FEHLENDE_ZAEHNE_OFFEN",
                    BeratungTexts.hintMissingZaehneOpen(allNamesExcept())));
        }

        nameCheaperTarif(anliegen, empfehlungen, schwerpunkte).ifPresent(hints::add);

        return List.copyOf(hints);
    }

    private Optional<Hinweis> nameCheaperTarif(BeratungRequest anliegen,
                                                      List<Empfehlung> empfehlungen,
                                                      List<String> schwerpunkte) {
        if (schwerpunkte.isEmpty() || empfehlungen.size() < 2 || anliegen.hasMissingZaehne()) {
            return Optional.empty();
        }
        Tarif last = tarif(empfehlungen.getLast().tarifschluessel());
        List<String> coverage = schwerpunkte.stream()
                .map(bereich -> BeratungTexts.coverageRow(
                        leistungsbereichName(bereich), leistung(last, bereich)))
                .toList();
        return Optional.of(Hinweis.supplementary("GUENSTIGEREN_TARIF_NENNEN",
                BeratungTexts.hintNameCheaper(last.anzeigename(), coverage)));
    }

    private List<String> namesWithException(List<Tarif> tarife, boolean mitAusnahme) {
        return tarife.stream()
                .filter(tarif -> tarifeWithZahnException.contains(tarif.schluessel()) == mitAusnahme)
                .map(Tarif::anzeigename)
                .toList();
    }

    private List<String> allNamesExcept() {
        return tarifeWithZahnException.stream().map(schluessel -> tarif(schluessel).anzeigename()).toList();
    }


    private List<String> checkedAreas(List<String> schwerpunkte) {
        for (String bereich : schwerpunkte) {
            if (!areaNames.containsKey(bereich)) {
                throw new UnknownLeistungsbereichException(
                        bereich, tarifCatalog.leistungsbereichKeys());
            }
        }
        return schwerpunkte;
    }

    private Tarif tarif(String schluessel) {
        return tarifCatalog.tarif(schluessel).orElseThrow(() -> new IllegalStateException(
                "Der Leitfaden nennt den Tarif " + schluessel + ", das Produktmodell kennt ihn nicht"));
    }

    private Leistung leistung(Tarif tarif, String bereich) {
        return tarif.findLeistung(bereich).orElseThrow(() -> new IllegalStateException("Der Tarif "
                + tarif.schluessel() + " sagt zum Leistungsbereich " + bereich + " nichts"));
    }

    private TargetKunde targetKunde(String tarifschluessel) {
        return leitfaden.targetKunde(tarifschluessel).orElseThrow(() -> new IllegalStateException(
                "Zum Tarif " + tarifschluessel + " fehlt der Zielkundeneintrag des Leitfadens"));
    }

    private Ausschluss ausschluss(String schluessel) {
        return tarifCatalog.exclusions().stream()
                .filter(entry -> entry.schluessel().equals(schluessel))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Das Produktmodell kennt den Ausschluss " + schluessel + " nicht"));
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

    private void checkFoundations() {
        Set<String> ausLeitfaden = new LinkedHashSet<>(leitfaden.order());
        Set<String> fromProduktmodell = new LinkedHashSet<>(tarifCatalog.tarifKeys());
        if (!ausLeitfaden.equals(fromProduktmodell)) {
            throw new IllegalStateException("Leitfaden und Produktmodell fuehren nicht dieselben "
                    + "Tarife: Leitfaden " + ausLeitfaden + ", Produktmodell " + fromProduktmodell);
        }
        for (String schluessel : List.of(CRITERION_EINTRITTSALTER,
                CRITERION_AREA_NOT_COVERED, CRITERION_MISSING_ZAEHNE)) {
            if (leitfaden.hardCriterion(schluessel).isEmpty()) {
                throw new IllegalStateException("Der Leitfaden kennt das harte Kriterium "
                        + schluessel + " nicht; die Empfehlung wertet es aus");
            }
        }
        for (String schluessel : tarifeWithZahnException) {
            if (tarifCatalog.tarif(schluessel).isEmpty()) {
                throw new IllegalStateException("Die Exception fuer fehlende Zaehne nennt den "
                        + "unbekannten Tarif " + schluessel);
            }
        }
        if (exceptionTextMissingZaehne == null || exclusionTextAngeraten == null) {
            throw new IllegalStateException(
                    "Das Produktmodell fuehrt einen Ausschluss ohne Text; er geht in die Hinweise ein");
        }
    }
}
