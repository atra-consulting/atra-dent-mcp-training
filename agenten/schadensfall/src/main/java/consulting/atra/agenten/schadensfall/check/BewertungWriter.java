package consulting.atra.agenten.schadensfall.check;

import consulting.atra.agenten.mcp.MandantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class BewertungWriter {

    public static final String AGENT = "schadensfall";

    static final List<String> CONFLICT_MARKERS = List.of(
            "konflikt",
            "nur aus in_pruefung",
            "ist ein endzustand");

    private static final Logger log = LoggerFactory.getLogger(BewertungWriter.class);

    private static final String GOZ_MUSTER = "[0-9]{4}";


    private static final Set<String> LEISTUNGSBEREICHE =
            Set.of("ZE", "IMP", "INL", "ZERH", "PAR", "PZR", "KFO", "FUN", "NAR", "AKUT");

    private static final Set<String> PLAUSIBILITAETEN = Set.of("plausibel", "auffaellig");

    private static final Set<String> NOTWENDIGKEITEN = Set.of("ueblich", "fraglich");

    private final ObjectMapper mapper;

    public BewertungWriter(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "abbilder");
    }

    public void write(long fallId, long kundenId, BewertungResult result,
                          int positionenImFall, List<Map<String, Object>> protokoll, String modell,
                          ToolCallback bewerten) {
        Objects.requireNonNull(result, "ergebnis");
        Objects.requireNonNull(bewerten, "bewerten");

        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("schadensfallId", fallId);
        arguments.put("bewertung", bewertung(result, modell, positionenImFall));
        arguments.put("protokoll", protokoll == null ? List.of() : protokoll);

        String input = mapper.writeValueAsString(arguments);
        try {
            MandantContext.with(kundenId, () -> bewerten.call(input));
        } catch (RuntimeException fehlschlag) {
            if (isConflict(fehlschlag)) {
                throw new ConflictException("Fall " + fallId + " steht nicht mehr auf in_pruefung; "
                        + "die Bewertung dieses Zuges ist gegenstandslos", fehlschlag);
            }
            throw fehlschlag;
        }
        log.info("Bewertung zu Fall {} geschrieben: {}", fallId, result.empfehlung());
    }


    private Map<String, Object> bewertung(BewertungResult result, String modell,
                                          int positionenImFall) {
        Map<String, Object> bewertung = new LinkedHashMap<>();
        bewertung.put("empfehlung", result.empfehlung());
        if (result.erstattungsvorschlag() != null) {
            bewertung.put("erstattungsvorschlag", euro(result.erstattungsvorschlag()));
        }
        bewertung.put("eskalationsgruende", result.gruende().stream()
                .map(grund -> Map.of("code", grund.code(), "text", grund.text()))
                .toList());
        bewertung.put("positionen", positionen(result, positionenImFall));
        arztauskunft(result.arztauskunft())
                .ifPresent(auskunft -> bewertung.put("arztauskunft", auskunft));
        bewertung.put("begruendung", result.begruendung());
        bewertung.put("agent", AGENT);
        if (modell != null && !modell.isBlank()) {
            bewertung.put("modell", modell);
        }
        return bewertung;
    }

    private List<Map<String, Object>> positionen(BewertungResult result,
                                                 int positionenImFall) {
        List<Map<String, Object>> positionen = new ArrayList<>();
        for (Bewertungsvorschlag.Position position : result.positionen()) {
            String state = key(position.zustand());
            boolean gozUnbrauchbar =
                    position.goz() != null && !position.goz().strip().matches(GOZ_MUSTER);
            boolean ohneSchluessel = position.goz() == null && position.index() == null;
            boolean indexDaneben = position.index() != null
                    && (position.index() < 0 || position.index() >= positionenImFall);
            if (gozUnbrauchbar || ohneSchluessel || indexDaneben
                    || state == null || !Bewertungsvorschlag.STATES.contains(state)
                    || position.begruendung() == null || position.begruendung().isBlank()) {
                log.warn("Position faellt aus der Bewertung, das Kernsystem naehme sie nicht an: "
                        + "index={}, goz={}, zustand={}",
                        position.index(), position.goz(), position.zustand());
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            if (position.index() != null) {
                entry.put("index", position.index());
            }
            if (position.goz() != null) {
                entry.put("goz", position.goz().strip());
            }
            String leistungsbereich = key(position.leistungsbereich());
            if (leistungsbereich != null && LEISTUNGSBEREICHE.contains(leistungsbereich)) {
                entry.put("leistungsbereich", leistungsbereich);
            } else if (leistungsbereich != null) {
                log.warn("Leistungsbereich '{}' ist keiner der bekannten Schluessel; das Feld "
                        + "bleibt weg", position.leistungsbereich());
            }
            entry.put("zustand", state);
            entry.put("begruendung", position.begruendung());
            positionen.add(entry);
        }
        if (positionen.isEmpty() && !result.positionen().isEmpty()) {
            log.warn("Keine einzige der {} Positionen des Modells war fuer das Kernsystem "
                    + "verwertbar; die Bewertung geht ohne Positionen hinaus",
                    result.positionen().size());
        }
        return positionen;
    }

    private static String key(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip().toUpperCase(Locale.ROOT);
    }

    private static String contractValue(String value, Set<String> erlaubte) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String small = value.strip().toLowerCase(Locale.ROOT);
        return erlaubte.contains(small) ? small : null;
    }

    private Optional<Map<String, Object>> arztauskunft(JsonNode auskunft) {
        if (auskunft == null || !auskunft.isObject()) {
            return Optional.empty();
        }
        String plausibilitaet = contractValue(text(auskunft, "plausibilitaet"), PLAUSIBILITAETEN);
        String notwendigkeit = contractValue(text(auskunft, "notwendigkeit"), NOTWENDIGKEITEN);
        String content = text(auskunft, "text");
        String hint = text(auskunft, "hinweis");
        if (plausibilitaet == null || notwendigkeit == null || content == null || hint == null) {
            log.warn("Arztauskunft unvollstaendig oder mit einem Wert, den das Kernsystem nicht "
                    + "kennt; sie geht nicht mit in die Bewertung (plausibilitaet={}, "
                    + "notwendigkeit={})", text(auskunft, "plausibilitaet"),
                    text(auskunft, "notwendigkeit"));
            return Optional.empty();
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("plausibilitaet", plausibilitaet);
        input.put("notwendigkeit", notwendigkeit);
        input.put("text", content);
        input.put("hinweis", hint);
        return Optional.of(input);
    }

    private static boolean isConflict(Throwable fehlschlag) {
        for (Throwable cause = fehlschlag; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message == null) {
                continue;
            }
            String small = message.toLowerCase(Locale.ROOT);
            if (CONFLICT_MARKERS.stream().anyMatch(small::contains)) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    private static String text(JsonNode elter, String field) {
        JsonNode value = elter == null ? null : elter.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            String content = value.asString();
            return content.isBlank() ? null : content;
        } catch (RuntimeException keinText) {
            return null;
        }
    }

    private static String euro(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
