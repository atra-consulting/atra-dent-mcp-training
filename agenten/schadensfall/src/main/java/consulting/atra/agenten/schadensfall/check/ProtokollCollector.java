package consulting.atra.agenten.schadensfall.check;

import consulting.atra.agenten.mcp.ObservedTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProtokollCollector {

    public static final String GUARD = "Freigabewächter";

    private static final String AGENT = "agent";

    static final int MAX_CALL_TEXT = 200;

    private static final Logger log = LoggerFactory.getLogger(ProtokollCollector.class);

    private static final Map<String, String> STEPS = Map.of(
            ToolSelection.VERTRAG, "Vertrag gelesen",
            ToolSelection.GOZ, "GOZ-Prüfung",
            ToolSelection.SCHADENSFAELLE, "Verbrauch gelesen",
            ToolSelection.BEDINGUNGEN, "Bedingungen nachgeschlagen",
            ToolSelection.COMPARISON, "Tarife verglichen",
            ToolSelection.ERSTATTUNG, "Erstattung berechnet",
            ToolSelection.ARZT, "Arztservice befragt",
            ToolSelection.SIGNAL, "Bewertung abgegeben");

    private ProtokollCollector() {
    }

    public static List<Map<String, Object>> aus(ObservedTools observed,
                                                BewertungResult result,
                                                ObjectMapper mapper, Clock clock) {
        String timestamp = now(clock);
        List<Map<String, Object>> protokoll = new ArrayList<>();
        for (ObservedTools.Toolaufruf call : observed.calls()) {
            protokoll.add(entry(call, timestamp, mapper));
        }
        protokoll.add(guardEntry(result, timestamp));
        return List.copyOf(protokoll);
    }


    private static Map<String, Object> entry(ObservedTools.Toolaufruf call,
                                               String zeitpunkt, ObjectMapper mapper) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("zeitpunkt", zeitpunkt);
        entry.put("akteur", AGENT);
        entry.put("schritt", STEPS.getOrDefault(call.tool(), call.tool()));
        String detail = detail(call, mapper);
        if (detail != null) {
            entry.put("detail", detail);
        }
        entry.put("calls", List.of(agentCall(call, zeitpunkt)));
        return entry;
    }

    private static Map<String, Object> agentCall(ObservedTools.Toolaufruf call, String zeitpunkt) {
        Map<String, Object> agentCall = new LinkedHashMap<>();
        agentCall.put("zeitpunkt", zeitpunkt);
        agentCall.put("art", ToolSelection.ARZT.equals(call.tool()) ? "a2a" : "tool");
        agentCall.put("name", call.tool());
        agentCall.put("eingabeKurz", abbreviate(call.arguments()));
        agentCall.put("ergebnisKurz", abbreviate(call.content()));
        return agentCall;
    }

    private static Map<String, Object> guardEntry(BewertungResult result,
                                                       String zeitpunkt) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("zeitpunkt", zeitpunkt);
        entry.put("akteur", AGENT);
        entry.put("schritt", GUARD);
        entry.put("detail", result.freigabe()
                ? "Freigabe"
                : "Eskalation: " + String.join(", ", result.codes()));
        return entry;
    }

    private static String detail(ObservedTools.Toolaufruf call, ObjectMapper mapper) {
        try {
            return switch (call.tool()) {
                case ToolSelection.VERTRAG -> vertragDetail(tree(mapper, call.content()));
                case ToolSelection.GOZ -> gozDetail(tree(mapper, call.content()));
                case ToolSelection.SCHADENSFAELLE -> casesDetail(tree(mapper, call.content()));
                case ToolSelection.BEDINGUNGEN -> fundstelleDetail(tree(mapper, call.content()));
                case ToolSelection.COMPARISON -> countDetail(
                        tree(mapper, call.content()), "tarife", "Tarif", "Tarife");
                case ToolSelection.ERSTATTUNG -> erstattungDetail(tree(mapper, call.content()));
                case ToolSelection.ARZT -> arztDetail(tree(mapper, call.content()));
                case ToolSelection.SIGNAL -> signalDetail(tree(mapper, call.arguments()));
                default -> null;
            };
        } catch (RuntimeException unreadable) {
            log.debug("Kein Detail zu {}; das Ergebnis liess sich nicht deuten",
                    call.tool(), unreadable);
            return null;
        }
    }

    private static String vertragDetail(JsonNode root) {
        String tarif = text(root, "tarifId");
        String status = text(root, "status");
        if (tarif == null && status == null) {
            return null;
        }
        return "Tarif " + oder(tarif, "unbekannt") + ", Vertrag " + oder(status, "ohne Status");
    }

    private static String gozDetail(JsonNode root) {
        JsonNode findings = root == null ? null : root.get("befunde");
        if (findings == null || !findings.isArray() || findings.isEmpty()) {
            return null;
        }
        int enthalten = 0;
        for (JsonNode befund : findings) {
            if ("ENTHALTEN".equals(text(befund, "status"))) {
                enthalten++;
            }
        }
        return enthalten + " von " + findings.size() + " ENTHALTEN";
    }

    private static String casesDetail(JsonNode root) {
        if (root == null) {
            return null;
        }
        JsonNode list = root.isArray() ? root : root.get("schadensfaelle");
        return list == null || !list.isArray()
                ? null
                : list.size() + (list.size() == 1 ? " Fall in der Akte" : " Fälle in der Akte");
    }

    private static String fundstelleDetail(JsonNode root) {
        return countDetail(root, "treffer", "Fundstelle", "Fundstellen");
    }

    private static String countDetail(JsonNode root, String field, String einzahl,
                                       String mehrzahl) {
        JsonNode list = root == null ? null : root.get(field);
        if (list == null || !list.isArray()) {
            return null;
        }
        return list.size() + " " + (list.size() == 1 ? einzahl : mehrzahl);
    }

    private static String erstattungDetail(JsonNode root) {
        BigDecimal amount = amount(text(root, "erstattungsbetrag"));
        return amount == null ? null : "Erstattung " + euro(amount) + " EUR";
    }

    private static String arztDetail(JsonNode root) {
        String error = text(root, ArztTool.ERROR);
        if (error != null) {
            return "keine Auskunft";
        }
        String plausibilitaet = text(root, "plausibilitaet");
        String notwendigkeit = text(root, "notwendigkeit");
        return plausibilitaet == null && notwendigkeit == null
                ? null
                : oder(plausibilitaet, "ohne Angabe") + " / " + oder(notwendigkeit, "ohne Angabe");
    }

    private static String signalDetail(JsonNode root) {
        String recommendation = text(root, "empfehlung");
        if (recommendation == null) {
            return null;
        }
        BigDecimal amount = amount(text(root, "erstattungsvorschlag"));
        return amount == null ? recommendation : recommendation + ", " + euro(amount) + " EUR";
    }


    static String abbreviate(String value) {
        if (value == null) {
            return null;
        }
        String one = value.strip();
        return one.length() <= MAX_CALL_TEXT ? one : one.substring(0, MAX_CALL_TEXT) + "…";
    }

    private static String now(Clock clock) {
        return OffsetDateTime.now(clock).withNano(0).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private static JsonNode tree(ObjectMapper mapper, String rohergebnis) {
        return rohergebnis == null || rohergebnis.isBlank() ? null : mapper.readTree(rohergebnis);
    }

    private static String text(JsonNode elter, String field) {
        JsonNode value = elter == null ? null : elter.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return value.asString();
        } catch (RuntimeException keinText) {
            return null;
        }
    }

    private static BigDecimal amount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.strip());
        } catch (NumberFormatException keinBetrag) {
            return null;
        }
    }

    private static String euro(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String oder(String value, String ersatz) {
        return value == null || value.isBlank() ? ersatz : value;
    }
}
