package consulting.atra.wissen.beratung;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public final class LeitfadenCatalog {

    private final LocalDate stand;
    private final String confidentiality;
    private final String giltFuer;
    private final Principle principle;
    private final EmpfehlungOrder recommendationOrder;
    private final Map<String, TargetKunde> targetKunden;
    private final Map<String, HardCriterion> hardCriteria;
    private final List<ConversationReason> conversationReasons;
    private final List<ComplianceLimit> complianceLimits;

    private LeitfadenCatalog(Datei file) {
        this.stand = file.stand();
        this.confidentiality = BeratungTexts.sentence(file.vertraulichkeit());
        this.giltFuer = BeratungTexts.sentence(file.giltFuer());
        this.principle = file.grundsatz();
        this.recommendationOrder = file.empfehlungsreihenfolge();
        this.targetKunden = byKey(file.zielkunden(), TargetKunde::tarif, "Zielkunde");
        this.hardCriteria = byKey(
                file.harteKriterien(), HardCriterion::schluessel, "Hartes Kriterium");
        this.conversationReasons = List.copyOf(file.gespraechsanlaesse());
        this.complianceLimits = List.copyOf(file.complianceGrenzen());
        checkCompleteness();
    }

    public static LeitfadenCatalog read(Path file) throws IOException {
        return new LeitfadenCatalog(LeitfadenReader.read(file, Datei.class));
    }

    public static LeitfadenCatalog read(InputStream stream) throws IOException {
        return new LeitfadenCatalog(LeitfadenReader.read(stream, Datei.class));
    }

    public LocalDate stand() {
        return stand;
    }

    public String confidentiality() {
        return confidentiality;
    }

    public String giltFuer() {
        return giltFuer;
    }

    public Principle principle() {
        return principle;
    }

    public EmpfehlungOrder recommendationOrder() {
        return recommendationOrder;
    }

    public List<String> order() {
        return recommendationOrder.reihenfolge();
    }

    public Optional<TargetKunde> targetKunde(String tarifschluessel) {
        return Optional.ofNullable(targetKunden.get(tarifschluessel));
    }

    public List<TargetKunde> targetKunden() {
        return List.copyOf(targetKunden.values());
    }

    public Optional<HardCriterion> hardCriterion(String schluessel) {
        return Optional.ofNullable(hardCriteria.get(schluessel));
    }

    public List<HardCriterion> hardCriteria() {
        return List.copyOf(hardCriteria.values());
    }

    public List<ConversationReason> conversationReasons() {
        return conversationReasons;
    }

    public Optional<ConversationReason> conversationReason(String schluessel) {
        return conversationReasons.stream()
                .filter(anlass -> anlass.schluessel().equals(schluessel))
                .findFirst();
    }

    public List<ComplianceLimit> complianceLimits() {
        return complianceLimits;
    }

    private void checkCompleteness() {
        if (stand == null) {
            throw new IllegalStateException("Der Leitfaden nennt keinen Stand");
        }
        if (confidentiality == null) {
            throw new IllegalStateException("Der Leitfaden nennt keine Vertraulichkeit; ohne sie "
                    + "laesst sich internes Steuerungswissen nicht von einem Bedingungswerk "
                    + "unterscheiden");
        }
        if (giltFuer == null) {
            throw new IllegalStateException("Der Leitfaden nennt nicht, fuer welche Beratung er gilt");
        }
        if (principle == null) {
            throw new IllegalStateException("Der Leitfaden hat keinen Grundsatz; ohne ihn bliebe von "
                    + "der Empfehlung nur die Reihenfolge uebrig");
        }
        if (recommendationOrder == null) {
            throw new IllegalStateException("Der Leitfaden hat keine Empfehlungsreihenfolge");
        }
        if (complianceLimits.isEmpty()) {
            throw new IllegalStateException("Der Leitfaden nennt keine Compliance-Grenzen; sie gehen "
                    + "mit jeder Empfehlung mit und duerfen deshalb nicht fehlen");
        }
        if (hardCriteria.isEmpty()) {
            throw new IllegalStateException("Der Leitfaden nennt keine harten Kriterien");
        }
        checkUnique(complianceLimits.stream().map(ComplianceLimit::schluessel).toList(),
                "Compliance-Grenze");
        checkUnique(conversationReasons.stream().map(ConversationReason::schluessel).toList(),
                "ConversationReason");

        Set<String> fromOrder = new LinkedHashSet<>(order());
        if (!fromOrder.equals(targetKunden.keySet())) {
            throw new IllegalStateException("Empfehlungsreihenfolge und Zielkunden decken nicht "
                    + "dieselben Tarife ab: Reihenfolge " + fromOrder
                    + ", Zielkunden " + targetKunden.keySet());
        }
    }

    private static void checkUnique(List<String> schluessel, String was) {
        Set<String> seen = new LinkedHashSet<>();
        for (String entry : schluessel) {
            if (!seen.add(entry)) {
                throw new IllegalStateException(was + " doppelt vergeben: " + entry);
            }
        }
    }

    private static <T> Map<String, T> byKey(
            List<T> entries, Function<T, String> schluessel, String was) {
        Map<String, T> map = new LinkedHashMap<>();
        for (T entry : entries) {
            if (map.put(schluessel.apply(entry), entry) != null) {
                throw new IllegalStateException(was + " doppelt vergeben: " + schluessel.apply(entry));
            }
        }
        return Collections.unmodifiableMap(map);
    }

    private record Datei(
            LocalDate stand,
            String vertraulichkeit,
            String giltFuer,
            Principle grundsatz,
            EmpfehlungOrder empfehlungsreihenfolge,
            List<TargetKunde> zielkunden,
            List<HardCriterion> harteKriterien,
            List<ConversationReason> gespraechsanlaesse,
            List<ComplianceLimit> complianceGrenzen) {

        private Datei {
            zielkunden = zielkunden == null ? List.of() : new ArrayList<>(zielkunden);
            harteKriterien = harteKriterien == null ? List.of() : new ArrayList<>(harteKriterien);
            gespraechsanlaesse = gespraechsanlaesse == null
                    ? List.of() : new ArrayList<>(gespraechsanlaesse);
            complianceGrenzen = complianceGrenzen == null
                    ? List.of() : new ArrayList<>(complianceGrenzen);
        }
    }
}
