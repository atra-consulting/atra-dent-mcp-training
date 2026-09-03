package consulting.atra.agenten.schadensfall.check;

import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class ApprovalGuard {

    static final String NO_ASSESSMENT = "Das Modell hat keine Bewertung abgegeben.";

    private static final String ENTHALTEN = "ENTHALTEN";
    private static final String NICHT_ENTHALTEN = "NICHT_ENTHALTEN";
    private static final String NICHT_BESTIMMBAR = "NICHT_BESTIMMBAR";
    private static final String ACTIVE = "aktiv";

    private static final String[][] UMLAUT_FOLDING = {
            {"ä", "ae"}, {"ö", "oe"}, {"ü", "ue"}, {"ß", "ss"}};

    private static final Pattern SEPARATORS = Pattern.compile("[\\s.,;\\-]+");

    private static final Set<String> SALUTATIONS = Set.of(
            "frau", "herr", "herrn", "fraeulein", "frl", "familie", "fam", "eheleute",
            "dr", "prof", "med", "dent", "dipl");

    private final BigDecimal approvalThreshold;

    public ApprovalGuard(BigDecimal approvalThreshold) {
        this.approvalThreshold = Objects.requireNonNull(approvalThreshold, "approvalThreshold");
    }

    public BewertungResult check(JsonNode schadensfall, Bewertungsvorschlag proposal,
                                     RawFindings findings) {
        Objects.requireNonNull(findings, "findings");

        List<Eskalationsgrund> reasons = new ArrayList<>();
        gozUnclear(schadensfall, proposal, findings).ifPresent(reasons::add);
        gozForForeignTarif(findings).ifPresent(reasons::add);
        amountDeviates(proposal, findings).ifPresent(reasons::add);
        erstattungWithForeignTarif(findings).ifPresent(reasons::add);
        erstattungWithForeignVersicherungsbeginn(findings).ifPresent(reasons::add);
        erstattungWithForeignDate(schadensfall, findings).ifPresent(reasons::add);
        erstattungWithForeignPositionen(schadensfall, findings).ifPresent(reasons::add);
        erstattungWithForeignLeistungsbereich(schadensfall, proposal, findings)
                .ifPresent(reasons::add);
        erstattungAsUnfall(findings).ifPresent(reasons::add);
        erstattungExceedsRechnungsbetrag(schadensfall, findings).ifPresent(reasons::add);
        arztReportsAnomaly(findings).ifPresent(reasons::add);
        arztUnavailable(findings).ifPresent(reasons::add);
        amountAboveThreshold(schadensfall).ifPresent(reasons::add);
        vertragInactive(findings).ifPresent(reasons::add);
        wartezeit(schadensfall, findings).ifPresent(reasons::add);
        patientUnclear(schadensfall, findings).ifPresent(reasons::add);
        duplicate(schadensfall, findings).ifPresent(reasons::add);
        extractionIncomplete(schadensfall).ifPresent(reasons::add);
        modelWithoutResult(proposal).ifPresent(reasons::add);
        modelRecommendsReturn(proposal).ifPresent(reasons::add);

        return new BewertungResult(
                reasons.isEmpty() ? Bewertungsvorschlag.FREIGABE : Bewertungsvorschlag.ESKALATION,
                findings.erstattungsbetrag().orElse(null),
                merged(reasons),
                positionen(proposal, findings),
                findings.arztauskunft().orElse(null),
                reasoning(proposal));
    }


    private Optional<Eskalationsgrund> modelWithoutResult(Bewertungsvorschlag proposal) {
        return proposal == null
                ? reason(Eskalationsgrund.MODELL_WITHOUT_RESULT,
                        "Das Modell hat keine Bewertung abgegeben; der Fall ist ungeprueft.")
                : Optional.empty();
    }

    private Optional<Eskalationsgrund> modelRecommendsReturn(Bewertungsvorschlag proposal) {
        if (proposal == null || !Bewertungsvorschlag.ESKALATION.equals(proposal.empfehlung())) {
            return Optional.empty();
        }
        String reasoning = proposal.begruendung();
        return reason(Eskalationsgrund.MODELL_ESCALATION,
                "Der pruefende Agent hat selbst eine Eskalation empfohlen"
                        + (reasoning == null || reasoning.isBlank()
                                ? "." : ": " + shortened(reasoning)));
    }

    private Optional<Eskalationsgrund> gozUnclear(JsonNode schadensfall,
                                                Bewertungsvorschlag proposal,
                                                RawFindings findings) {
        List<String> defects = new ArrayList<>();
        List<JsonNode> schadensfallPositionen = positionen(schadensfall);
        for (JsonNode finding : findings.gozBefunde()) {
            String status = text(finding, "status");
            if (ENTHALTEN.equals(status) || NICHT_ENTHALTEN.equals(status)) {
                continue;
            }
            String number = text(finding, "nummer");
            if (NICHT_BESTIMMBAR.equals(status)) {
                if (number != null && indexesWithNumber(schadensfall, number).isEmpty()) {
                    defects.add("GOZ-Nummer " + number + " ist " + NICHT_BESTIMMBAR
                            + " und steht nicht auf dieser Rechnung; geprueft wurde damit "
                            + "etwas, das niemand in Rechnung gestellt hat");
                }
                continue;
            }
            defects.add("GOZ-Nummer " + orElse(number, "ohne Nummer")
                    + " ist " + orElse(status, "ohne Status"));
        }
        for (int index = 0; index < schadensfallPositionen.size(); index++) {
            String goz = text(schadensfallPositionen.get(index), "goz");
            if (goz == null) {
                unanchored(schadensfall, proposal, findings, index).ifPresent(defects::add);
                continue;
            }
            if (!hasFinding(findings, goz)) {
                defects.add("zu GOZ-Nummer " + goz + " liegt kein Befund vor");
                continue;
            }
            if (isIndeterminate(findings, goz)) {
                unanchored(schadensfall, proposal, findings, index).ifPresent(defects::add);
            }
        }
        return defects.isEmpty()
                ? Optional.empty()
                : reason(Eskalationsgrund.GOZ_UNCLEAR,
                        "Nicht jede Position ist dem Grunde nach geklaert: "
                                + String.join("; ", defects) + ".");
    }

    private static Optional<String> unanchored(JsonNode schadensfall, Bewertungsvorschlag proposal,
                                               RawFindings findings, int index) {
        JsonNode position = positionen(schadensfall).get(index);
        String goz = text(position, "goz");
        String label = label(position, index)
                + (goz == null ? " traegt keine Gebuehrennummer" : " ist " + NICHT_BESTIMMBAR);
        String assigned = assignedLeistungsbereich(proposal, index);
        if (assigned == null && goz != null) {
            assigned = assignedLeistungsbereich(proposal, goz);
        }
        if (assigned == null) {
            return Optional.of(label + "; eine Behandlung, der sie folgt, ist nicht genannt");
        }
        List<JsonNode> candidates = anchorCandidates(schadensfall, index);
        String disputed = uncoveredNumberAtAnchor(candidates, findings);
        if (disputed != null) {
            return Optional.of(label + "; " + anchorPhrase(schadensfall, index)
                    + " steht mit GOZ-Nummer " + disputed
                    + " zugleich eine nicht versicherte Behandlung, und welcher von "
                    + "beiden die Position folgt, entscheidet ein Mensch");
        }
        if (coveredLeistungsbereicheAtAnchor(candidates, findings).contains(normalized(assigned))) {
            return Optional.empty();
        }
        return Optional.of(label + "; zugeordnet wurde " + assigned + ", aber "
                + anchorPhrase(schadensfall, index) + " steht keine Position, die "
                + ENTHALTEN + " ist");
    }

    private static List<Integer> indexesWithNumber(JsonNode schadensfall, String number) {
        List<JsonNode> all = positionen(schadensfall);
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            if (number.equals(text(all.get(i), "goz"))) {
                indexes.add(i);
            }
        }
        return indexes;
    }

    private static boolean isIndeterminate(RawFindings findings, String goz) {
        return findings.gozBefunde().stream()
                .anyMatch(finding -> goz.equals(text(finding, "nummer"))
                        && NICHT_BESTIMMBAR.equals(text(finding, "status")));
    }

    private static List<JsonNode> anchorCandidates(JsonNode schadensfall, int index) {
        List<JsonNode> all = positionen(schadensfall);
        String tooth = tooth(all.get(index));
        List<JsonNode> candidates = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            if (i == index || text(all.get(i), "goz") == null) {
                continue;
            }
            if (tooth == null || tooth.equals(tooth(all.get(i)))) {
                candidates.add(all.get(i));
            }
        }
        return candidates;
    }

    private static String tooth(JsonNode position) {
        String raw = text(position, "zahn");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9]", "").replaceFirst("^0+(?=.)", "");
        return digits.isEmpty() ? raw.strip() : digits;
    }

    private static List<String> coveredLeistungsbereicheAtAnchor(List<JsonNode> candidates,
                                                                 RawFindings findings) {
        List<String> numbers = candidates.stream()
                .map(position -> text(position, "goz")).toList();
        return findings.gozBefunde().stream()
                .filter(finding -> ENTHALTEN.equals(text(finding, "status")))
                .filter(finding -> numbers.contains(text(finding, "nummer")))
                .map(finding -> text(finding, "leistungsbereich"))
                .filter(Objects::nonNull)
                .map(ApprovalGuard::normalized)
                .toList();
    }

    private static String uncoveredNumberAtAnchor(List<JsonNode> candidates,
                                                  RawFindings findings) {
        List<String> numbers = candidates.stream()
                .map(position -> text(position, "goz")).toList();
        return findings.gozBefunde().stream()
                .filter(finding -> NICHT_ENTHALTEN.equals(text(finding, "status")))
                .map(finding -> text(finding, "nummer"))
                .filter(Objects::nonNull)
                .filter(numbers::contains)
                .findFirst()
                .orElse(null);
    }

    private static String anchorPhrase(JsonNode schadensfall, int index) {
        String tooth = tooth(positionen(schadensfall).get(index));
        return tooth == null ? "auf dieser Rechnung" : "an Zahn " + tooth;
    }

    private static String label(JsonNode position, int index) {
        String goz = text(position, "goz");
        if (goz != null && !goz.isBlank()) {
            return "GOZ-Nummer " + goz;
        }
        String description = text(position, "beschreibung");
        return description == null || description.isBlank()
                ? "Position " + (index + 1)
                : "Position " + (index + 1) + " (" + shortened(description) + ")";
    }

    private static String assignedLeistungsbereich(Bewertungsvorschlag proposal, int index) {
        if (proposal == null) {
            return null;
        }
        return proposal.positionen().stream()
                .filter(position -> position.index() != null && position.index() == index)
                .filter(position -> Bewertungsvorschlag.isGozState(position.zustand()))
                .map(Bewertungsvorschlag.Position::leistungsbereich)
                .filter(leistungsbereich -> leistungsbereich != null
                        && !leistungsbereich.isBlank())
                .findFirst()
                .orElse(null);
    }

    private static String assignedLeistungsbereich(Bewertungsvorschlag proposal, String number) {
        if (proposal == null || number == null) {
            return null;
        }
        return proposal.positionen().stream()
                .filter(position -> number.equals(position.goz()))
                .filter(position -> Bewertungsvorschlag.isGozState(position.zustand()))
                .map(Bewertungsvorschlag.Position::leistungsbereich)
                .filter(leistungsbereich -> leistungsbereich != null
                        && !leistungsbereich.isBlank())
                .findFirst()
                .orElse(null);
    }

    private static String normalized(String leistungsbereich) {
        return leistungsbereich.strip().toUpperCase(Locale.ROOT);
    }

    private Optional<Eskalationsgrund> gozForForeignTarif(RawFindings findings) {
        if (findings.vertrag().isEmpty()) {
            return Optional.empty();
        }
        String contracted = text(findings.vertrag().get(), "tarifId");
        if (contracted == null) {
            return Optional.empty();
        }
        List<String> foreign = new ArrayList<>();
        for (JsonNode arguments : findings.gozArgumente()) {
            String asked = orElse(text(arguments, "tarif"), "ohne Angabe");
            if (!contracted.equals(asked) && !foreign.contains(asked)) {
                foreign.add(asked);
            }
        }
        return foreign.isEmpty()
                ? Optional.empty()
                : reason(Eskalationsgrund.GOZ_UNCLEAR,
                        ToolSelection.GOZ + " wurde fuer den Tarif " + String.join(", ", foreign)
                                + " gefragt; der Vertrag laeuft auf " + contracted
                                + " -- die Befunde gelten nicht fuer diesen Fall.");
    }

    private Optional<Eskalationsgrund> amountDeviates(Bewertungsvorschlag proposal,
                                                    RawFindings findings) {
        if (proposal == null || !proposal.recommendsFreigabe()) {
            return Optional.empty();
        }
        BigDecimal stated = proposal.erstattungsvorschlag();
        Optional<BigDecimal> calculated = findings.erstattungsbetrag();

        if (calculated.isEmpty()) {
            return reason(Eskalationsgrund.BETRAG_DEVIATES,
                    "Das Modell empfiehlt Freigabe"
                            + (stated == null ? "" : " mit " + euro(stated) + " EUR")
                            + ", aber erstattung_berechnen hat in diesem Zug kein Ergebnis geliefert.");
        }
        if (stated == null) {
            return reason(Eskalationsgrund.BETRAG_DEVIATES,
                    "Das Modell nennt keinen Erstattungsbetrag; der Rechenkern hat "
                            + euro(calculated.get()) + " EUR errechnet.");
        }
        return stated.compareTo(calculated.get()) != 0
                ? reason(Eskalationsgrund.BETRAG_DEVIATES,
                        "Der Erstattungsvorschlag " + euro(stated)
                                + " EUR weicht vom Ergebnis des Rechenkerns ("
                                + euro(calculated.get()) + " EUR) ab.")
                : Optional.empty();
    }

    private Optional<Eskalationsgrund> erstattungWithForeignTarif(RawFindings findings) {
        if (findings.erstattungsargumente().isEmpty() || findings.vertrag().isEmpty()) {
            return Optional.empty();
        }
        String contracted = text(findings.vertrag().get(), "tarifId");
        if (contracted == null) {
            return Optional.empty();
        }
        String calculated = text(findings.erstattungsargumente().get(), "tarifId");
        return contracted.equals(calculated)
                ? Optional.empty()
                : reason(Eskalationsgrund.BETRAG_DEVIATES,
                        ToolSelection.ERSTATTUNG + " wurde mit dem Tarif "
                                + orElse(calculated, "ohne Angabe") + " gerechnet; der Vertrag "
                                + "laeuft auf " + contracted + ".");
    }

    private Optional<Eskalationsgrund> erstattungWithForeignVersicherungsbeginn(
            RawFindings findings) {
        if (findings.erstattungsargumente().isEmpty() || findings.vertrag().isEmpty()) {
            return Optional.empty();
        }
        JsonNode arguments = findings.erstattungsargumente().get();
        Optional<LocalDate> contracted = date(findings.vertrag().get(), "versicherungsbeginn");
        if (contracted.isEmpty()) {
            return Optional.empty();
        }
        return date(arguments, "versicherungsbeginn").filter(contracted.get()::equals).isPresent()
                ? Optional.empty()
                : reason(Eskalationsgrund.BETRAG_DEVIATES,
                        ToolSelection.ERSTATTUNG + " wurde mit dem Versicherungsbeginn "
                                + orElse(text(arguments, "versicherungsbeginn"), "ohne Angabe")
                                + " gerechnet; der Vertrag beginnt am " + contracted.get() + ".");
    }

    private Optional<Eskalationsgrund> erstattungAsUnfall(RawFindings findings) {
        if (findings.erstattungsargumente().isEmpty()) {
            return Optional.empty();
        }
        JsonNode unfall = findings.erstattungsargumente().get().get("unfallbedingt");
        return meansTrue(unfall)
                ? reason(Eskalationsgrund.BETRAG_DEVIATES,
                        ToolSelection.ERSTATTUNG + " wurde als unfallbedingt gerechnet; das laesst "
                                + "die Zahnstaffel entfallen und hebt den Betrag. Im Fall steht "
                                + "dazu nichts -- das Kennzeichen hat keine Quelle in der Akte.")
                : Optional.empty();
    }

    private static boolean meansTrue(JsonNode value) {
        if (value == null || value.isNull() || !value.isValueNode()) {
            return false;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            return value.asDouble() != 0;
        }
        String word = value.asString().strip().toLowerCase(Locale.ROOT);
        return "true".equals(word) || "1".equals(word) || "ja".equals(word);
    }

    private Optional<Eskalationsgrund> erstattungWithForeignDate(JsonNode schadensfall,
                                                               RawFindings findings) {
        if (findings.erstattungsargumente().isEmpty()) {
            return Optional.empty();
        }
        JsonNode arguments = findings.erstattungsargumente().get();
        Optional<LocalDate> inSchadensfall = date(schadensfall, "behandlungsdatum");
        if (inSchadensfall.isEmpty()) {
            return Optional.empty();
        }
        return date(arguments, "behandlungsdatum").filter(inSchadensfall.get()::equals).isPresent()
                ? Optional.empty()
                : reason(Eskalationsgrund.BETRAG_DEVIATES,
                        ToolSelection.ERSTATTUNG + " wurde zum Behandlungsdatum "
                                + orElse(text(arguments, "behandlungsdatum"), "ohne Angabe")
                                + " gerechnet; der Fall nennt " + inSchadensfall.get() + ".");
    }

    private Optional<Eskalationsgrund> erstattungWithForeignPositionen(JsonNode schadensfall,
                                                                     RawFindings findings) {
        if (findings.erstattungsargumente().isEmpty()) {
            return Optional.empty();
        }
        List<JsonNode> calculatedPositionen = positionen(findings.erstattungsargumente().get());
        List<Integer> indexes = calculatedIndexes(schadensfall,
                findings.erstattungsargumente().get());
        List<String> foreign = new ArrayList<>();
        for (int i = 0; i < calculatedPositionen.size(); i++) {
            if (indexes.get(i) != null) {
                continue;
            }
            JsonNode calculated = calculatedPositionen.get(i);
            String goz = text(calculated, "goz");
            foreign.add(labelFor(calculated, goz) + " ueber "
                    + amount(calculated, "betrag").map(ApprovalGuard::euro)
                            .orElse("ohne Betrag") + " EUR");
        }
        return foreign.isEmpty()
                ? Optional.empty()
                : reason(Eskalationsgrund.BETRAG_DEVIATES,
                        ToolSelection.ERSTATTUNG + " wurde ueber Positionen gerechnet, die so nicht "
                                + "auf der Rechnung stehen: " + String.join("; ", foreign) + ".");
    }

    private Optional<Eskalationsgrund> erstattungWithForeignLeistungsbereich(
            JsonNode schadensfall, Bewertungsvorschlag proposal, RawFindings findings) {
        if (findings.erstattungsargumente().isEmpty()) {
            return Optional.empty();
        }
        List<JsonNode> calculatedPositionen = positionen(findings.erstattungsargumente().get());
        List<Integer> indexes = calculatedIndexes(schadensfall,
                findings.erstattungsargumente().get());
        List<String> foreign = new ArrayList<>();
        for (int i = 0; i < calculatedPositionen.size(); i++) {
            JsonNode calculated = calculatedPositionen.get(i);
            String leistungsbereich = text(calculated, "leistungsbereich");
            if (leistungsbereich == null || leistungsbereich.isBlank()) {
                continue;
            }
            String goz = text(calculated, "goz");
            String proven = provenLeistungsbereich(proposal, findings, goz, indexes.get(i));
            if (proven == null || normalized(proven).equals(normalized(leistungsbereich))) {
                continue;
            }
            foreign.add(labelFor(calculated, goz) + " wurde als " + leistungsbereich
                    + " gerechnet, belegt ist " + proven);
        }
        return foreign.isEmpty()
                ? Optional.empty()
                : reason(Eskalationsgrund.BETRAG_DEVIATES,
                        ToolSelection.ERSTATTUNG + " wurde mit Leistungsbereichen gerechnet, die "
                                + "zu diesen Positionen nicht belegt sind: "
                                + String.join("; ", foreign) + ".");
    }

    private static String provenLeistungsbereich(Bewertungsvorschlag proposal,
                                                 RawFindings findings,
                                                 String goz, Integer index) {
        if (goz == null) {
            return index == null ? null : assignedLeistungsbereich(proposal, index);
        }
        Optional<JsonNode> finding = findings.gozBefunde().stream()
                .filter(entry -> goz.equals(text(entry, "nummer")))
                .findFirst();
        if (finding.isEmpty()) {
            return null;
        }
        String fromFinding = text(finding.get(), "leistungsbereich");
        return fromFinding != null ? fromFinding : assignedLeistungsbereich(proposal, goz);
    }

    private Optional<Eskalationsgrund> erstattungExceedsRechnungsbetrag(JsonNode schadensfall,
                                                                      RawFindings findings) {
        Optional<BigDecimal> erstattung = findings.erstattungsbetrag();
        Optional<BigDecimal> rechnung = amount(schadensfall, "rechnungsbetrag");
        if (erstattung.isEmpty() || rechnung.isEmpty()
                || erstattung.get().compareTo(rechnung.get()) <= 0) {
            return Optional.empty();
        }
        return reason(Eskalationsgrund.BETRAG_DEVIATES,
                "Der Rechenkern hat " + euro(erstattung.get())
                        + " EUR errechnet; die Rechnung lautet nur ueber "
                        + euro(rechnung.get()) + " EUR.");
    }

    private Optional<Eskalationsgrund> arztUnavailable(RawFindings findings) {
        if (!findings.arztAufgerufen()) {
            return reason(Eskalationsgrund.ARZT_UNAVAILABLE,
                    "Der Arztservice wurde in diesem Zug nicht befragt.");
        }
        if (findings.arztGestoert()) {
            return reason(Eskalationsgrund.ARZT_UNAVAILABLE,
                    "Der Arztservice hat auf die Anfrage nicht verwertbar geantwortet.");
        }
        return findings.arztauskunft().isEmpty()
                ? reason(Eskalationsgrund.ARZT_UNAVAILABLE,
                        "Der Arztservice hat keine Auskunft geliefert.")
                : Optional.empty();
    }

    private Optional<Eskalationsgrund> arztReportsAnomaly(RawFindings findings) {
        if (findings.arztauskunft().isEmpty()) {
            return Optional.empty();
        }
        JsonNode info = findings.arztauskunft().get();
        List<String> anomalies = new ArrayList<>();
        String plausibilitaet = text(info, "plausibilitaet");
        String notwendigkeit = text(info, "notwendigkeit");
        if ("auffaellig".equals(plausibilitaet)) {
            anomalies.add("die Plausibilitaet als auffaellig");
        }
        if ("fraglich".equals(notwendigkeit)) {
            anomalies.add("die Notwendigkeit als fraglich");
        }
        return anomalies.isEmpty()
                ? Optional.empty()
                : reason(Eskalationsgrund.ARZT_FLAGGED,
                        "Der Arztservice bewertet " + String.join(" und ", anomalies)
                                + " (Sprachmodell-Auskunft, kein Beleg).");
    }

    private Optional<Eskalationsgrund> amountAboveThreshold(JsonNode schadensfall) {
        Optional<BigDecimal> rechnungTotal = amount(schadensfall, "rechnungsbetrag");
        return rechnungTotal.filter(value -> value.compareTo(approvalThreshold) > 0)
                .flatMap(value -> reason(Eskalationsgrund.BETRAG_ABOVE_THRESHOLD,
                        "Rechnungsbetrag " + euro(value) + " EUR liegt ueber der Freigabeschwelle "
                                + euro(approvalThreshold) + " EUR."));
    }

    private Optional<Eskalationsgrund> vertragInactive(RawFindings findings) {
        if (findings.vertrag().isEmpty()) {
            return reason(Eskalationsgrund.VERTRAG_INACTIVE,
                    "Der Vertrag der Kundin wurde in diesem Zug nicht gelesen.");
        }
        String status = text(findings.vertrag().get(), "status");
        return ACTIVE.equals(status)
                ? Optional.empty()
                : reason(Eskalationsgrund.VERTRAG_INACTIVE,
                        "Der Vertrag ist nicht aktiv (Status: " + orElse(status, "ohne Angabe") + ").");
    }

    private Optional<Eskalationsgrund> wartezeit(JsonNode schadensfall, RawFindings findings) {
        Optional<JsonNode> vertrag = findings.vertrag();
        Optional<LocalDate> start = vertrag.flatMap(node -> date(node, "versicherungsbeginn"));
        Optional<LocalDate> treatment = date(schadensfall, "behandlungsdatum");
        boolean waived = findings.wartezeitEntfaelltBeiVorversicherung()
                && vertrag.isPresent() && flag(vertrag.get(), "vorversicherung");

        List<String> pending = new ArrayList<>();
        if (!waived && findings.allgemeineWartezeitMonate().isEmpty()) {
            pending.add("die allgemeine Wartezeit des Tarifs wurde in diesem Zug nicht geprueft "
                    + "(kein Ergebnis von " + ToolSelection.COMPARISON + " zum Tarif des Vertrags)");
        }
        if (start.isPresent() && treatment.isPresent()) {
            pending.addAll(pendingDeadlines(findings, start.get(), treatment.get(), waived));
        }
        if (pending.isEmpty()) {
            return Optional.empty();
        }
        String prefix = treatment.map(day -> "Behandelt wurde am " + day + ", ")
                .orElse("Die Wartezeit ist nicht geklaert: ");
        return reason(Eskalationsgrund.WARTEZEIT, prefix + String.join("; ", pending) + ".");
    }

    private static List<String> pendingDeadlines(RawFindings findings, LocalDate start,
                                                 LocalDate treatment, boolean waived) {
        List<String> pending = new ArrayList<>();
        if (!waived && findings.allgemeineWartezeitMonate().isPresent()) {
            int months = findings.allgemeineWartezeitMonate().get();
            LocalDate end = start.plusMonths(months);
            if (treatment.isBefore(end)) {
                pending.add("die allgemeine Wartezeit des Tarifs von " + months
                        + " Monaten endet am " + end);
            }
        }
        for (JsonNode finding : findings.gozBefunde()) {
            if (!ENTHALTEN.equals(text(finding, "status"))) {
                continue;
            }
            JsonNode limits = finding.get("grenzen");
            Integer months = integerValue(limits, "wartezeitMonate");
            if (months == null) {
                continue;
            }
            LocalDate end = start.plusMonths(months);
            if (treatment.isBefore(end)) {
                pending.add("die Wartezeit von " + months + " Monaten fuer GOZ-Nummer "
                        + orElse(text(finding, "nummer"), "ohne Nummer") + " endet am " + end);
            }
        }
        return pending;
    }

    private Optional<Eskalationsgrund> patientUnclear(JsonNode schadensfall, RawFindings findings) {
        String patient = text(node(schadensfall, "rechnung"), "patient");
        if (patient == null || patient.isBlank() || findings.vertrag().isEmpty()) {
            return Optional.empty();
        }
        String lastName = text(findings.vertrag().get(), "nachname");
        List<NamePart> expectedLastName = nameParts(lastName);
        if (expectedLastName.isEmpty()) {
            return Optional.empty();
        }
        List<NamePart> parts = nameParts(patient);
        int index = indexOfSequence(parts, expectedLastName);
        if (index < 0) {
            return reason(Eskalationsgrund.PATIENT_UNCLEAR,
                    "Die Rechnung nennt als Patient \"" + patient
                            + "\"; der Vertrag lautet auf " + lastName + ".");
        }
        return givenNameDiffers(patient,
                givenNamesFrom(parts, index, index + expectedLastName.size()),
                text(findings.vertrag().get(), "vorname"), lastName);
    }

    private static List<NamePart> givenNamesFrom(List<NamePart> parts, int index, int end) {
        List<NamePart> before = withoutSalutations(parts.subList(0, index));
        if (!before.isEmpty() || !parts.get(end - 1).commaAfter()) {
            return before;
        }
        return withoutSalutations(parts.subList(end, parts.size()));
    }

    private static List<NamePart> withoutSalutations(List<NamePart> parts) {
        return parts.stream()
                .filter(part -> part.forms().stream().noneMatch(SALUTATIONS::contains))
                .toList();
    }

    private Optional<Eskalationsgrund> givenNameDiffers(String patient, List<NamePart> stated,
                                                      String givenName, String lastName) {
        List<NamePart> expected = nameParts(givenName);
        if (stated.isEmpty() || expected.isEmpty()) {
            return Optional.empty();
        }
        boolean matches = stated.stream().anyMatch(part -> expected.stream()
                .anyMatch(other -> part.matches(other) || part.abbreviates(other)));
        if (matches) {
            return Optional.empty();
        }
        String joined = stated.stream().map(NamePart::raw).collect(Collectors.joining(" "));
        return reason(Eskalationsgrund.PATIENT_UNCLEAR,
                "Die Rechnung nennt als Patient \"" + patient + "\": derselbe Nachname, aber ein "
                        + "anderer Vorname. Die Rechnung nennt " + joined
                        + ", der Vertrag lautet auf " + givenName + " " + lastName
                        + ". Ein mitversichertes Kind oder ein Ehepartner ist damit nicht "
                        + "ausgeschlossen, aber nachzufragen.");
    }

    private record NamePart(String raw, Set<String> forms, boolean commaAfter) {

        boolean matches(NamePart other) {
            return !Collections.disjoint(forms, other.forms());
        }

        boolean abbreviates(NamePart full) {
            return forms.stream().anyMatch(form -> form.length() == 1
                    && full.forms().stream().anyMatch(whole -> whole.startsWith(form)));
        }
    }

    private static List<NamePart> nameParts(String name) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        List<NamePart> parts = new ArrayList<>();
        Matcher separator = SEPARATORS.matcher(name);
        int start = 0;
        while (start <= name.length()) {
            boolean hasMore = separator.find(start);
            int end = hasMore ? separator.start() : name.length();
            String raw = name.substring(start, end);
            Set<String> forms = comparisonForms(raw);
            if (!forms.stream().allMatch(String::isEmpty)) {
                parts.add(new NamePart(raw.replaceAll("^\\p{Punct}+|\\p{Punct}+$", ""), forms,
                        hasMore && separator.group().indexOf(',') >= 0));
            }
            if (!hasMore) {
                break;
            }
            start = separator.end();
        }
        return List.copyOf(parts);
    }

    private static Set<String> comparisonForms(String part) {
        String lower = part.toLowerCase(Locale.GERMAN);
        String folded = lower;
        for (String[] pair : UMLAUT_FOLDING) {
            folded = folded.replace(pair[0], pair[1]);
        }
        return Set.copyOf(List.of(lettersOnly(folded), lettersOnly(lower)));
    }

    private static String lettersOnly(String word) {
        StringBuilder filtered = new StringBuilder(word.length());
        Normalizer.normalize(word, Normalizer.Form.NFD).codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(filtered::appendCodePoint);
        return filtered.toString();
    }

    private static int indexOfSequence(List<NamePart> parts, List<NamePart> wanted) {
        for (int i = 0; i + wanted.size() <= parts.size(); i++) {
            boolean all = true;
            for (int j = 0; j < wanted.size(); j++) {
                if (!parts.get(i + j).matches(wanted.get(j))) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return i;
            }
        }
        return -1;
    }

    private Optional<Eskalationsgrund> duplicate(JsonNode schadensfall, RawFindings findings) {
        if (!findings.schadensfaelleGelesen()) {
            return reason(Eskalationsgrund.DUPLICATE,
                    "Die uebrigen Faelle der Kundin wurden in diesem Zug nicht gelesen; ein "
                            + "Duplikat ist damit nicht ausgeschlossen.");
        }
        String ownId = text(schadensfall, "id");
        String treatmentDate = text(schadensfall, "behandlungsdatum");
        Optional<BigDecimal> rechnungTotal = amount(schadensfall, "rechnungsbetrag");
        if (treatmentDate == null || rechnungTotal.isEmpty()) {
            return Optional.empty();
        }
        for (JsonNode other : findings.andereFaelle()) {
            String id = text(other, "id");
            if (id == null || id.equals(ownId)
                    || !treatmentDate.equals(text(other, "behandlungsdatum"))) {
                continue;
            }
            Optional<BigDecimal> otherAmount = amount(other, "rechnungsbetrag");
            if (otherAmount.isPresent()
                    && otherAmount.get().compareTo(rechnungTotal.get()) == 0) {
                return reason(Eskalationsgrund.DUPLICATE,
                        "Fall " + id + " hat dasselbe Behandlungsdatum " + treatmentDate
                                + " und denselben Rechnungsbetrag "
                                + euro(rechnungTotal.get()) + " EUR.");
            }
        }
        return Optional.empty();
    }

    private Optional<Eskalationsgrund> extractionIncomplete(JsonNode schadensfall) {
        List<String> defects = new ArrayList<>();
        List<JsonNode> positionen = positionen(schadensfall);
        if (positionen.isEmpty()) {
            defects.add("die Rechnung hat keine Positionen");
        }
        if (amount(schadensfall, "rechnungsbetrag").isEmpty()) {
            defects.add("der Rechnungsbetrag fehlt");
        }
        int number = 0;
        boolean allWithAmount = true;
        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode position : positionen) {
            number++;
            Optional<BigDecimal> positionAmount = amount(position, "betrag");
            if (positionAmount.isEmpty()) {
                defects.add("Position " + number + " hat keinen Betrag");
                allWithAmount = false;
            } else {
                sum = sum.add(positionAmount.get());
            }
        }
        JsonNode rechnung = node(schadensfall, "rechnung");
        Optional<BigDecimal> total = amount(rechnung, "gesamtbetrag");
        if (rechnung != null && total.isEmpty()) {
            defects.add("zur Rechnung fehlt der Gesamtbetrag, gegen den sich die Summe der "
                    + "Positionen halten liesse");
        } else if (allWithAmount && !positionen.isEmpty() && total.isPresent()
                && sum.compareTo(total.get()) != 0) {
            defects.add("die " + positionen.size() + " Positionen ergeben " + euro(sum)
                    + " EUR, die Rechnung lautet ueber " + euro(total.get()) + " EUR");
        }
        return defects.isEmpty()
                ? Optional.empty()
                : reason(Eskalationsgrund.EXTRACTION_INCOMPLETE,
                        "Die Rechnung ist nicht vollstaendig erfasst: "
                                + String.join("; ", defects) + ".");
    }


    private static List<Bewertungsvorschlag.Position> positionen(Bewertungsvorschlag proposal,
                                                                 RawFindings findings) {
        if (proposal == null) {
            return findings.gozBefunde().stream()
                    .map(finding -> new Bewertungsvorschlag.Position(
                            text(finding, "nummer"),
                            text(finding, "leistungsbereich"),
                            text(finding, "status"),
                            text(finding, "begruendung")))
                    .toList();
        }
        return proposal.positionen().stream()
                .map(position -> position.leistungsbereich() != null
                        ? position
                        : new Bewertungsvorschlag.Position(position.index(), position.goz(),
                                leistungsbereichFrom(findings, position.goz()),
                                position.zustand(), position.begruendung()))
                .toList();
    }

    private static String leistungsbereichFrom(RawFindings findings, String goz) {
        return findings.gozBefunde().stream()
                .filter(finding -> goz != null && goz.equals(text(finding, "nummer")))
                .map(finding -> text(finding, "leistungsbereich"))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static String reasoning(Bewertungsvorschlag proposal) {
        return proposal != null && proposal.begruendung() != null
                && !proposal.begruendung().isBlank()
                ? proposal.begruendung()
                : NO_ASSESSMENT;
    }


    private static Optional<Eskalationsgrund> reason(String code, String text) {
        return Optional.of(new Eskalationsgrund(code, text));
    }

    private static List<Eskalationsgrund> merged(List<Eskalationsgrund> reasons) {
        List<Eskalationsgrund> unique = new ArrayList<>();
        for (Eskalationsgrund reason : reasons) {
            int existing = -1;
            for (int i = 0; i < unique.size(); i++) {
                if (unique.get(i).code().equals(reason.code())) {
                    existing = i;
                    break;
                }
            }
            if (existing < 0) {
                unique.add(reason);
            } else {
                unique.set(existing, new Eskalationsgrund(reason.code(),
                        unique.get(existing).text() + " " + reason.text()));
            }
        }
        return List.copyOf(unique);
    }

    private static List<Integer> calculatedIndexes(JsonNode schadensfall, JsonNode arguments) {
        List<JsonNode> schadensfallPositionen = positionen(schadensfall);
        List<Integer> open = new ArrayList<>();
        for (int i = 0; i < schadensfallPositionen.size(); i++) {
            open.add(i);
        }
        List<Integer> indexes = new ArrayList<>();
        for (JsonNode calculated : positionen(arguments)) {
            String goz = text(calculated, "goz");
            Optional<BigDecimal> calculatedAmount = amount(calculated, "betrag");
            Integer hit = null;
            for (int k = 0; k < open.size(); k++) {
                JsonNode candidate = schadensfallPositionen.get(open.get(k));
                Optional<BigDecimal> candidateAmount = amount(candidate, "betrag");
                if (Objects.equals(goz, text(candidate, "goz"))
                        && calculatedAmount.isPresent() && candidateAmount.isPresent()
                        && calculatedAmount.get().compareTo(candidateAmount.get()) == 0) {
                    hit = open.remove(k);
                    break;
                }
            }
            indexes.add(hit);
        }
        return indexes;
    }

    private static String labelFor(JsonNode calculated, String goz) {
        if (goz != null && !goz.isBlank()) {
            return "GOZ-Nummer " + goz;
        }
        String description = text(calculated, "beschreibung");
        return description == null || description.isBlank()
                ? "die Position ohne Gebuehrennummer"
                : "die Position ohne Gebuehrennummer (" + shortened(description) + ")";
    }

    private static boolean hasFinding(RawFindings findings, String goz) {
        return findings.gozBefunde().stream()
                .anyMatch(finding -> goz.equals(text(finding, "nummer")));
    }

    private static List<JsonNode> positionen(JsonNode schadensfall) {
        JsonNode positionen = node(schadensfall, "positionen");
        if (positionen == null || !positionen.isArray()) {
            return List.of();
        }
        List<JsonNode> all = new ArrayList<>();
        positionen.forEach(all::add);
        return all;
    }

    private static JsonNode node(JsonNode parent, String field) {
        if (parent == null) {
            return null;
        }
        JsonNode value = parent.get(field);
        return value == null || value.isNull() ? null : value;
    }

    private static String text(JsonNode parent, String field) {
        JsonNode value = node(parent, field);
        if (value == null) {
            return null;
        }
        try {
            return value.asString();
        } catch (RuntimeException notText) {
            return null;
        }
    }

    private static Optional<BigDecimal> amount(JsonNode parent, String field) {
        JsonNode value = node(parent, field);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(value.asString().strip()));
        } catch (RuntimeException notAnAmount) {
            return Optional.empty();
        }
    }

    private static boolean flag(JsonNode parent, String field) {
        JsonNode value = node(parent, field);
        return value != null && value.isBoolean() && value.asBoolean();
    }

    private static String shortened(String text) {
        String single = text.strip();
        return single.length() <= 200 ? single : single.substring(0, 200) + "…";
    }

    private static Optional<LocalDate> date(JsonNode parent, String field) {
        String value = text(parent, field);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(value.strip()));
        } catch (DateTimeParseException notADate) {
            return Optional.empty();
        }
    }

    private static Integer integerValue(JsonNode parent, String field) {
        JsonNode value = node(parent, field);
        return value == null || !value.isNumber() ? null : value.asInt();
    }

    private static String euro(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String orElse(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
