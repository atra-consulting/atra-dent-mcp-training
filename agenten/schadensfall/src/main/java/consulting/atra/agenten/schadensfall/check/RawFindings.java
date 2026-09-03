package consulting.atra.agenten.schadensfall.check;

import consulting.atra.agenten.mcp.ObservedTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record RawFindings(Optional<JsonNode> vertrag,
                         List<JsonNode> gozBefunde, List<JsonNode> gozArgumente,
                         Optional<BigDecimal> erstattungsbetrag,
                         Optional<JsonNode> erstattungsargumente,
                         Optional<JsonNode> arztauskunft,
                         boolean arztAufgerufen, boolean arztGestoert,
                         List<JsonNode> andereFaelle, boolean schadensfaelleGelesen,
                         Optional<Integer> allgemeineWartezeitMonate,
                         boolean wartezeitEntfaelltBeiVorversicherung) {

    private static final Logger log = LoggerFactory.getLogger(RawFindings.class);

    private static final String ERROR = "fehler";

    public RawFindings {
        gozBefunde = gozBefunde == null ? List.of() : List.copyOf(gozBefunde);
        gozArgumente = gozArgumente == null ? List.of() : List.copyOf(gozArgumente);
        andereFaelle = andereFaelle == null ? List.of() : List.copyOf(andereFaelle);
    }

    public static RawFindings empty() {
        return new RawFindings(Optional.empty(), List.of(), List.of(), Optional.empty(),
                Optional.empty(), Optional.empty(),
                false, false, List.of(), false, Optional.empty(), false);
    }

    public static RawFindings aus(ObservedTools observed, ObjectMapper mapper) {
        List<String> arztResults = observed.resultsOf(ToolSelection.ARZT);
        Optional<JsonNode> arzt = lastNode(observed, mapper, ToolSelection.ARZT)
                .filter(JsonNode::isObject)
                .filter(node -> node.get(ERROR) == null);
        Optional<JsonNode> vertrag = lastNode(observed, mapper, ToolSelection.VERTRAG)
                .filter(JsonNode::isObject);
        Optional<JsonNode> tarifLine = tarifLine(observed, mapper, vertrag);
        Optional<List<JsonNode>> caseList = otherCases(observed, mapper);

        return new RawFindings(
                vertrag,
                gozFindings(observed, mapper),
                allArguments(observed, mapper, ToolSelection.GOZ),
                erstattungsbetrag(observed, mapper),
                lastArguments(observed, mapper, ToolSelection.ERSTATTUNG),
                arzt,
                !arztResults.isEmpty(),
                !arztResults.isEmpty() && arzt.isEmpty(),
                caseList.orElse(List.of()),
                caseList.isPresent(),
                tarifLine.map(line -> integer(line, "wartezeitMonate"))
                        .orElse(Optional.empty()),
                tarifLine.map(line -> booleanValue(line, "wartezeitEntfaelltBeiVorversicherung"))
                        .orElse(false));
    }

    private static Optional<JsonNode> tarifLine(ObservedTools observed, ObjectMapper mapper,
                                                 Optional<JsonNode> vertrag) {
        if (vertrag.isEmpty()) {
            return Optional.empty();
        }
        String tarifId = text(vertrag.get(), "tarifId");
        if (tarifId == null) {
            return Optional.empty();
        }
        return lastNode(observed, mapper, ToolSelection.COMPARISON)
                .map(root -> root.get("tarife"))
                .filter(tarife -> tarife != null && tarife.isArray())
                .flatMap(tarife -> {
                    for (JsonNode line : tarife) {
                        if (tarifId.equals(text(line, "schluessel"))) {
                            return Optional.of(line);
                        }
                    }
                    return Optional.empty();
                });
    }

    private static List<JsonNode> gozFindings(ObservedTools observed, ObjectMapper mapper) {
        List<JsonNode> alle = new ArrayList<>();
        for (String rohergebnis : observed.resultsOf(ToolSelection.GOZ)) {
            node(mapper, rohergebnis, ToolSelection.GOZ)
                    .map(root -> root.get("befunde"))
                    .filter(befunde -> befunde != null && befunde.isArray())
                    .ifPresent(befunde -> befunde.forEach(alle::add));
        }
        return List.copyOf(alle);
    }

    private static Optional<BigDecimal> erstattungsbetrag(ObservedTools observed,
                                                          ObjectMapper mapper) {
        return lastNode(observed, mapper, ToolSelection.ERSTATTUNG)
                .map(root -> root.get("erstattungsbetrag"))
                .flatMap(RawFindings::amount);
    }

    private static Optional<List<JsonNode>> otherCases(ObservedTools observed,
                                                         ObjectMapper mapper) {
        Optional<JsonNode> root = lastNode(observed, mapper, ToolSelection.SCHADENSFAELLE);
        if (root.isEmpty()) {
            return Optional.empty();
        }
        JsonNode node = root.get();
        JsonNode list = node.isArray() ? node : node.get("schadensfaelle");
        if (list == null || !list.isArray()) {
            return Optional.empty();
        }
        List<JsonNode> cases = new ArrayList<>();
        list.forEach(cases::add);
        return Optional.of(List.copyOf(cases));
    }

    private static List<JsonNode> allArguments(ObservedTools observed, ObjectMapper mapper,
                                                String tool) {
        List<JsonNode> alle = new ArrayList<>();
        for (ObservedTools.Toolaufruf call : observed.callsOf(tool)) {
            arguments(mapper, call.arguments(), tool).ifPresent(alle::add);
        }
        return List.copyOf(alle);
    }

    private static Optional<JsonNode> lastArguments(ObservedTools observed,
                                                      ObjectMapper mapper, String tool) {
        List<ObservedTools.Toolaufruf> calls = observed.callsOf(tool);
        return calls.isEmpty()
                ? Optional.empty()
                : arguments(mapper, calls.getLast().arguments(), tool);
    }

    private static Optional<JsonNode> arguments(ObjectMapper mapper, String input, String tool) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(mapper.readTree(input));
        } catch (RuntimeException unreadable) {
            log.warn("Argumente von {} liessen sich nicht lesen; sie bleiben ungeprueft",
                    tool, unreadable);
            return Optional.empty();
        }
    }

    private static Optional<JsonNode> lastNode(ObservedTools observed,
                                                    ObjectMapper mapper, String tool) {
        List<String> results = observed.resultsOf(tool);
        return results.isEmpty()
                ? Optional.empty()
                : node(mapper, results.getLast(), tool);
    }

    private static Optional<JsonNode> node(ObjectMapper mapper, String rohergebnis, String tool) {
        if (rohergebnis == null || rohergebnis.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(mapper.readTree(rohergebnis));
        } catch (RuntimeException unreadable) {
            log.warn("Ergebnis von {} liess sich nicht lesen; der Befund entfaellt", tool, unreadable);
            return Optional.empty();
        }
    }

    private static Optional<BigDecimal> amount(JsonNode node) {
        if (node == null || node.isNull()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(node.asString().strip()));
        } catch (RuntimeException keinBetrag) {
            log.warn("Ein Knoten vom Typ {} ist kein Geldbetrag; der Befund entfaellt",
                    node.getNodeType(), keinBetrag);
            return Optional.empty();
        }
    }

    private static String text(JsonNode elter, String field) {
        if (elter == null) {
            return null;
        }
        JsonNode value = elter.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return value.asString();
        } catch (RuntimeException keinText) {
            return null;
        }
    }

    private static Optional<Integer> integer(JsonNode elter, String field) {
        JsonNode value = elter == null ? null : elter.get(field);
        return value == null || !value.isNumber() ? Optional.empty() : Optional.of(value.asInt());
    }

    private static boolean booleanValue(JsonNode elter, String field) {
        JsonNode value = elter == null ? null : elter.get(field);
        return value != null && value.isBoolean() && value.asBoolean();
    }
}
