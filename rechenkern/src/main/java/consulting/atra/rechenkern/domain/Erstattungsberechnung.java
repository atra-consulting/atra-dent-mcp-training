package consulting.atra.rechenkern.domain;

import consulting.atra.produktmodell.Leistung;
import consulting.atra.produktmodell.Staffel;
import consulting.atra.produktmodell.Tarif;
import consulting.atra.produktmodell.TarifCatalog;
import consulting.atra.rechenkern.generated.model.ErstattungsberechnungRequest;
import consulting.atra.rechenkern.generated.model.ErstattungsberechnungResult;
import consulting.atra.rechenkern.generated.model.Leistungsbereich;
import consulting.atra.rechenkern.generated.model.Rechenschritt;
import consulting.atra.rechenkern.generated.model.RechenschrittResult;
import consulting.atra.rechenkern.generated.model.Schadenposition;
import consulting.atra.rechenkern.generated.model.Vorverbrauch;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.stream.Collectors;

@Service
public class Erstattungsberechnung {

    private static final List<StepRule> CHAIN = List.of(
            new Erstattungsfaehig(),
            new Quote(),
            new Sublimit(),
            new GkvVorleistung(),
            new Selbstbehalt(),
            new StaffelAndJahresgrenze());

    private final TarifCatalog catalog;

    public Erstattungsberechnung(TarifCatalog catalog) {
        this.catalog = catalog;
    }

    public ErstattungsberechnungResult calculate(ErstattungsberechnungRequest request) {
        Context context = contextFrom(request);

        SequencedMap<Leistungsbereich, BigDecimal> perLeistungsbereich = new LinkedHashMap<>();
        for (Schadenposition position : request.getPositionen()) {
            perLeistungsbereich.merge(
                    position.getLeistungsbereich(), position.getBetrag(), BigDecimal::add);
        }

        State state = State.split(perLeistungsbereich);
        BigDecimal rechnungTotal = state.amount();

        List<RechenschrittResult> steps = new ArrayList<>(CHAIN.size());
        for (StepRule rule : CHAIN) {
            Effect effect = rule.apply(state, context);
            state = effect.state();
            steps.add(new RechenschrittResult(rule.step(), rounded(state.amount()))
                    .erlaeuterung(effect.erlaeuterung()));
        }

        BigDecimal erstattung = rounded(state.amount());
        BigDecimal rechnung = rounded(rechnungTotal);
        BigDecimal ownShare = rounded(notNegative(
                rechnungTotal.subtract(context.gkvLeistung()).subtract(erstattung)));

        return new ErstattungsberechnungResult(
                erstattung, rechnung, ownShare, context.versicherungsjahr(), steps);
    }

    private Context contextFrom(ErstattungsberechnungRequest request) {
        String tarifKey = request.getTarifId().getValue();
        Tarif tarif = catalog.tarif(tarifKey)
                .orElseThrow(() -> new DomainException(
                        "Der Tarif " + tarifKey + " steht nicht im Produktmodell"));
        Staffel staffel = catalog.staffel(tarif.staffel())
                .orElseThrow(() -> new IllegalStateException("Tarif " + tarifKey
                        + " is missing Staffel " + tarif.staffel() + " in the Produktmodell"));
        int versicherungsjahr = Versicherungsjahr.derive(
                request.getVersicherungsbeginn(), request.getBehandlungsdatum());

        return new Context(tarif, staffel, versicherungsjahr,
                Objects.requireNonNullElse(request.getGkvLeistung(), BigDecimal.ZERO),
                Boolean.TRUE.equals(request.getUnfallbedingt()),
                Objects.requireNonNullElse(request.getVerbrauch(), new Vorverbrauch()));
    }


    private interface StepRule {

        Rechenschritt step();

        Effect apply(State state, Context context);
    }

    private interface LeistungsbereichRule extends StepRule {

        @Override
        default Effect apply(State state, Context context) {
            if (!(state instanceof State.Split split)) {
                throw new IllegalStateException("Step " + step() + " calculates per"
                        + " Leistungsbereich but sits in CHAIN behind a step that treats the"
                        + " Rechnung as a whole and has therefore given up the split."
                        + " Per-Leistungsbereich steps belong before " + Rechenschritt.GKV);
            }
            return apply(split, context);
        }

        Effect apply(State.Split state, Context context);
    }

    private record Effect(State state, String erlaeuterung) {
    }

    private sealed interface State {

        BigDecimal amount();

        static Split split(SequencedMap<Leistungsbereich, BigDecimal> perLeistungsbereich) {
            BigDecimal sum = perLeistungsbereich.values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new Split(notNegative(sum),
                    Collections.unmodifiableSequencedMap(new LinkedHashMap<>(perLeistungsbereich)));
        }

        static Total total(BigDecimal amount) {
            return new Total(notNegative(amount));
        }

        record Split(BigDecimal amount,
                     SequencedMap<Leistungsbereich, BigDecimal> perLeistungsbereich)
                implements State {
        }

        record Total(BigDecimal amount) implements State {
        }
    }

    private record Context(Tarif tarif, Staffel staffel, int versicherungsjahr,
                           BigDecimal gkvLeistung, boolean unfallbedingt,
                           Vorverbrauch verbrauch) {

        private static final String ALLOWED_LEISTUNGSBEREICHE =
                Arrays.stream(Leistungsbereich.values())
                        .map(Leistungsbereich::getValue)
                        .collect(Collectors.joining(", "));

        Context {
            checked("leistungsbereiche", verbrauch.getLeistungsbereiche());
            checked("leistungsbereicheSeitVersicherungsbeginn",
                    verbrauch.getLeistungsbereicheSeitVersicherungsbeginn());
        }

        private static void checked(String field, Map<String, BigDecimal> leistungsbereiche) {
            if (leistungsbereiche == null) {
                return;
            }
            for (String key : leistungsbereiche.keySet()) {
                try {
                    Leistungsbereich.fromValue(key);
                } catch (IllegalArgumentException error) {
                    throw new DomainException("Der Leistungsbereich \"" + key + "\" in "
                            + field + " des Verbrauchs ist unbekannt. Zulaessig sind: "
                            + ALLOWED_LEISTUNGSBEREICHE);
                }
            }
        }

        Leistung leistung(Leistungsbereich leistungsbereich) {
            return tarif.findLeistung(leistungsbereich.getValue())
                    .orElseThrow(() -> new DomainException("Der Leistungsbereich "
                            + leistungsbereich.getValue() + " ist im Tarif " + tarif.schluessel()
                            + " nicht vorgesehen"));
        }

        BigDecimal usageInYear(Leistungsbereich leistungsbereich) {
            return fromMap(verbrauch.getLeistungsbereiche(), leistungsbereich);
        }

        BigDecimal usageSinceStart(Leistungsbereich leistungsbereich) {
            return fromMap(verbrauch.getLeistungsbereicheSeitVersicherungsbeginn(),
                    leistungsbereich);
        }

        BigDecimal staffelUsage() {
            return Objects.requireNonNullElse(verbrauch.getStaffel(), BigDecimal.ZERO);
        }

        BigDecimal selbstbehaltUsage() {
            return Objects.requireNonNullElse(verbrauch.getSelbstbehalt(), BigDecimal.ZERO);
        }

        BigDecimal paidOutThisYear() {
            return Objects.requireNonNullElse(verbrauch.getJahr(), BigDecimal.ZERO);
        }

        private static BigDecimal fromMap(Map<String, BigDecimal> map,
                                          Leistungsbereich leistungsbereich) {
            if (map == null) {
                return BigDecimal.ZERO;
            }
            return Objects.requireNonNullElse(
                    map.get(leistungsbereich.getValue()), BigDecimal.ZERO);
        }
    }


    private record Erstattungsfaehig() implements LeistungsbereichRule {

        @Override
        public Rechenschritt step() {
            return Rechenschritt.ERSTATTUNGSFAEHIG;
        }

        @Override
        public Effect apply(State.Split state, Context context) {
            SequencedMap<Leistungsbereich, BigDecimal> versichert = new LinkedHashMap<>();
            List<String> excluded = new ArrayList<>();
            state.perLeistungsbereich().forEach((leistungsbereich, betrag) -> {
                if (context.leistung(leistungsbereich).versichert()) {
                    versichert.put(leistungsbereich, betrag);
                } else {
                    excluded.add(leistungsbereich.getValue());
                }
            });

            String sentence = excluded.isEmpty()
                    ? "Alle Positionen liegen in versicherten Leistungsbereichen"
                    : "Im Tarif " + context.tarif().schluessel() + " nicht versichert und deshalb"
                            + " unberuecksichtigt: " + String.join(", ", excluded);
            return new Effect(State.split(versichert), sentence);
        }
    }


    private record Quote() implements LeistungsbereichRule {

        @Override
        public Rechenschritt step() {
            return Rechenschritt.QUOTE;
        }

        @Override
        public Effect apply(State.Split state, Context context) {
            LeistungsbereichState result = new LeistungsbereichState();
            state.perLeistungsbereich().forEach((leistungsbereich, betrag) -> {
                Leistung leistung = context.leistung(leistungsbereich);
                if (leistung.quote() == null) {
                    throw new IllegalStateException("Tarif " + context.tarif().schluessel()
                            + " is missing the Quote for insured Leistungsbereich "
                            + leistungsbereich.getValue());
                }
                BigDecimal share = betrag.multiply(BigDecimal.valueOf(leistung.quote()))
                        .movePointLeft(2);
                result.betraege().put(leistungsbereich, share);
                result.sentences().add(leistungsbereich.getValue() + " " + leistung.quote()
                        + " Prozent auf " + euro(betrag) + " EUR");
            });

            String sentence = result.sentences().isEmpty()
                    ? "Kein erstattungsfaehiger Betrag, auf den eine Quote anzuwenden waere"
                    : "Quote je Leistungsbereich: " + String.join("; ", result.sentences());
            return new Effect(State.split(result.betraege()), sentence);
        }
    }


    private record Sublimit() implements LeistungsbereichRule {

        @Override
        public Rechenschritt step() {
            return Rechenschritt.SUBLIMIT;
        }

        @Override
        public Effect apply(State.Split state, Context context) {
            LeistungsbereichState result = new LeistungsbereichState();
            state.perLeistungsbereich().forEach((leistungsbereich, betrag) -> {
                Leistung leistung = context.leistung(leistungsbereich);
                BigDecimal capped = betrag;

                if (leistung.limitProJahr() != null) {
                    BigDecimal remaining = notNegative(leistung.limitProJahr()
                            .subtract(context.usageInYear(leistungsbereich)));
                    if (capped.compareTo(remaining) > 0) {
                        capped = remaining;
                        result.sentences().add(leistungsbereich.getValue() + " auf "
                                + euro(remaining) + " EUR begrenzt (Jahreslimit "
                                + euro(leistung.limitProJahr()) + " EUR, davon "
                                + euro(context.usageInYear(leistungsbereich))
                                + " EUR verbraucht)");
                    }
                }

                if (leistung.limitGesamt() != null) {
                    BigDecimal remaining = notNegative(leistung.limitGesamt()
                            .subtract(context.usageSinceStart(leistungsbereich)));
                    if (capped.compareTo(remaining) > 0) {
                        capped = remaining;
                        result.sentences().add(leistungsbereich.getValue() + " auf "
                                + euro(remaining) + " EUR begrenzt (Gesamtlimit "
                                + euro(leistung.limitGesamt()) + " EUR, davon "
                                + euro(context.usageSinceStart(leistungsbereich))
                                + " EUR verbraucht)");
                    }
                }

                result.betraege().put(leistungsbereich, capped);
            });

            String sentence = result.sentences().isEmpty()
                    ? "Kein Sublimit greift"
                    : String.join("; ", result.sentences());
            return new Effect(State.split(result.betraege()), sentence);
        }
    }


    private record GkvVorleistung() implements StepRule {

        @Override
        public Rechenschritt step() {
            return Rechenschritt.GKV;
        }

        @Override
        public Effect apply(State state, Context context) {
            BigDecimal deducted = smaller(context.gkvLeistung(), state.amount());
            String sentence = context.gkvLeistung().signum() == 0
                    ? "Keine GKV-Vorleistung angerechnet"
                    : deducted.compareTo(context.gkvLeistung()) < 0
                            ? "GKV-Vorleistung " + euro(context.gkvLeistung()) + " EUR, davon "
                                    + euro(deducted) + " EUR abgezogen -- mehr liess der"
                                    + " erstattungsfaehige Betrag nicht mehr zu; die Quote"
                                    + " versteht sich einschliesslich dieser Vorleistung"
                            : "GKV-Vorleistung " + euro(context.gkvLeistung())
                                    + " EUR abgezogen; die Quote versteht sich einschliesslich"
                                    + " dieser Vorleistung";
            return new Effect(
                    State.total(state.amount().subtract(context.gkvLeistung())), sentence);
        }
    }


    private record Selbstbehalt() implements StepRule {

        @Override
        public Rechenschritt step() {
            return Rechenschritt.SELBSTBEHALT;
        }

        @Override
        public Effect apply(State state, Context context) {
            BigDecimal agreed = Objects.requireNonNullElse(
                    context.tarif().selbstbehalt(), BigDecimal.ZERO);
            if (agreed.signum() == 0) {
                return new Effect(State.total(state.amount()),
                        "Der Tarif " + context.tarif().schluessel()
                                + " erhebt keinen Selbstbehalt");
            }

            BigDecimal remaining = notNegative(agreed.subtract(context.selbstbehaltUsage()));
            BigDecimal deducted = smaller(remaining, state.amount());
            String sentence = "Selbstbehalt " + euro(agreed) + " EUR je Versicherungsjahr, davon "
                    + euro(context.selbstbehaltUsage()) + " EUR bereits verbraucht; "
                    + euro(deducted) + " EUR abgezogen"
                    + (deducted.compareTo(remaining) < 0
                            ? " -- mehr liess der erstattungsfaehige Betrag nicht mehr zu"
                            : "");
            return new Effect(State.total(state.amount().subtract(remaining)), sentence);
        }
    }


    private record StaffelAndJahresgrenze() implements StepRule {

        @Override
        public Rechenschritt step() {
            return Rechenschritt.STAFFEL;
        }

        @Override
        public Effect apply(State state, Context context) {
            if (context.unfallbedingt()) {
                return annualLimit(state, context,
                        "Unfallbedingte Behandlung: die Zahnstaffel entfaellt");
            }
            if (context.versicherungsjahr() > context.staffel().dauerJahre()) {
                return annualLimit(state, context, "Die Zahnstaffel "
                        + context.staffel().schluessel() + " ist nach "
                        + context.staffel().dauerJahre() + " Versicherungsjahren abgelaufen");
            }

            BigDecimal tier = tierAmount(context);
            if (tier == null) {
                return new Effect(State.total(state.amount()),
                        "Die Zahnstaffel " + context.staffel().schluessel()
                                + " sieht fuer Versicherungsjahr " + context.versicherungsjahr()
                                + " keinen Hoechstbetrag vor");
            }

            BigDecimal remaining = notNegative(tier.subtract(context.staffelUsage()));
            String suffix = context.tarif().jahreshoechstgrenze() != null
                    ? " Die Jahreshoechstgrenze greift erst nach Ablauf der Staffel"
                    : " Der Tarif " + context.tarif().schluessel()
                            + " traegt keine Jahreshoechstgrenze";
            String sentence = "Zahnstaffel bis Versicherungsjahr " + context.versicherungsjahr()
                    + ": " + euro(tier) + " EUR, davon " + euro(context.staffelUsage())
                    + " EUR ausgezahlt; " + euro(remaining) + " EUR verbleiben." + suffix;
            return new Effect(State.total(smaller(state.amount(), remaining)), sentence);
        }

        private static BigDecimal tierAmount(Context context) {
            return context.staffel().stufen().stream()
                    .filter(stufe -> stufe.bisJahr() == null
                            || stufe.bisJahr() >= context.versicherungsjahr())
                    .findFirst()
                    .map(Staffel.Stufe::betrag)
                    .orElse(null);
        }

        private static Effect annualLimit(State state, Context context, String grund) {
            BigDecimal limit = context.tarif().jahreshoechstgrenze();
            if (limit == null) {
                return new Effect(State.total(state.amount()),
                        grund + "; der Tarif " + context.tarif().schluessel()
                                + " traegt keine Jahreshoechstgrenze, die Erstattung ist"
                                + " unbegrenzt");
            }
            BigDecimal remaining = notNegative(limit.subtract(context.paidOutThisYear()));
            String sentence = grund + "; es gilt die Jahreshoechstgrenze " + euro(limit)
                    + " EUR, davon " + euro(context.paidOutThisYear()) + " EUR im laufenden"
                    + " Versicherungsjahr ausgezahlt; " + euro(remaining) + " EUR verbleiben";
            return new Effect(State.total(smaller(state.amount(), remaining)), sentence);
        }
    }


    private record LeistungsbereichState(SequencedMap<Leistungsbereich, BigDecimal> betraege,
                                         List<String> sentences) {

        LeistungsbereichState() {
            this(new LinkedHashMap<>(), new ArrayList<>());
        }
    }

    private static BigDecimal notNegative(BigDecimal betrag) {
        return betrag.signum() < 0 ? BigDecimal.ZERO : betrag;
    }

    private static BigDecimal smaller(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    private static BigDecimal rounded(BigDecimal betrag) {
        return betrag.setScale(2, RoundingMode.HALF_UP);
    }

    private static String euro(BigDecimal betrag) {
        return betrag.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
