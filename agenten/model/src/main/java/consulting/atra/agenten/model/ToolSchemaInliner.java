package consulting.atra.agenten.model;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ToolSchemaInliner {

    private static final String DEFS = "$defs";

    private static final String REF = "$ref";

    private static final String REF_PREFIX = "#/" + DEFS + "/";

    private static final ObjectMapper IMAGES = JsonMapper.builder().build();

    private ToolSchemaInliner() {
    }

    public static List<ToolCallback> resolve(List<ToolCallback> callbacks) {
        Objects.requireNonNull(callbacks, "callbacks");
        return callbacks.stream().map(ToolSchemaInliner::resolve).toList();
    }

    public static ToolCallback resolve(ToolCallback callback) {
        Objects.requireNonNull(callback, "callback");

        ToolDefinition definition = callback.getToolDefinition();
        String schema = definition.inputSchema();
        if (schema == null || !schema.contains("\"" + DEFS + "\"")) {
            return callback;
        }

        JsonNode root;
        try {
            root = IMAGES.readTree(schema);
        } catch (RuntimeException unreadable) {
            throw new IllegalStateException("Das inputSchema von " + definition.name()
                    + " laesst sich nicht als JSON lesen; ein Tool mit unlesbarem Schema "
                    + "laesst sich weder anbieten noch stillschweigend uebergehen.", unreadable);
        }
        JsonNode definitions = root.get(DEFS);
        if (!root.isObject() || definitions == null || !definitions.isObject()) {
            return callback;
        }

        ObjectNode ohneDefs = IMAGES.getNodeFactory().objectNode();
        for (Map.Entry<String, JsonNode> field : ((ObjectNode) root).properties()) {
            if (!DEFS.equals(field.getKey())) {
                ohneDefs.set(field.getKey(), field.getValue());
            }
        }
        JsonNode result =
                inserted(ohneDefs, definitions, new ArrayDeque<>(), definition.name());

        return new AufgeloesterCallback(callback, DefaultToolDefinition.builder()
                .name(definition.name())
                .description(definition.description())
                .inputSchema(IMAGES.writeValueAsString(result))
                .build());
    }

    private static JsonNode inserted(JsonNode node, JsonNode definitionen,
                                       Deque<String> path, String tool) {
        if (node.isArray()) {
            ArrayNode list = IMAGES.getNodeFactory().arrayNode();
            for (JsonNode element : node) {
                list.add(inserted(element, definitionen, path, tool));
            }
            return list;
        }
        if (!node.isObject()) {
            return node;
        }

        ObjectNode object = (ObjectNode) node;
        JsonNode reference = object.get(REF);
        if (reference == null || !reference.isString()) {
            ObjectNode copy = IMAGES.getNodeFactory().objectNode();
            for (Map.Entry<String, JsonNode> field : object.properties()) {
                copy.set(field.getKey(), inserted(field.getValue(), definitionen, path, tool));
            }
            return copy;
        }

        String target = reference.stringValue();
        String name = target.startsWith(REF_PREFIX) ? target.substring(REF_PREFIX.length()) : null;
        JsonNode definition = name == null ? null : definitionen.get(name);
        if (definition == null) {
            throw new IllegalStateException("Das Schema von " + tool + " verweist auf " + target
                    + ", wozu es keine Definition gibt. Ein solcher Verweis laesst sich nicht "
                    + "aufloesen, und weglassen wuerde das Tool still verstuemmeln.");
        }
        if (path.contains(name)) {
            throw new IllegalStateException("Das Schema von " + tool
                    + " enthaelt einen Ringschluss: " + cycle(path, name)
                    + ". Google kennt keine Definitionen, also laesst sich der Ring nicht "
                    + "aufloesen -- und ein abgeschnittener Zweig waere ein Tool, dessen "
                    + "Eingabeform nicht mehr stimmt.");
        }

        path.addLast(name);
        JsonNode resolved = inserted(definition, definitionen, path, tool);
        path.removeLast();

        if (!resolved.isObject()) {
            if (object.size() > 1) {
                throw new IllegalStateException("Das Schema von " + tool + " loest " + target
                        + " zu etwas auf, das kein Objekt ist -- daneben stehen aber noch "
                        + "Angaben (etwa eine description), die sich damit nicht "
                        + "zusammenfuehren lassen. Sie weglassen hiesse, dem Modell ein Feld "
                        + "ohne seine Beschreibung anzubieten.");
            }
            return resolved;
        }
        ObjectNode merged = ((ObjectNode) resolved).deepCopy();
        for (Map.Entry<String, JsonNode> field : object.properties()) {
            if (!REF.equals(field.getKey())) {
                merged.set(field.getKey(),
                        inserted(field.getValue(), definitionen, path, tool));
            }
        }
        return merged;
    }

    private static String cycle(Deque<String> path, String name) {
        StringBuilder kette = new StringBuilder();
        boolean imRing = false;
        for (String station : path) {
            if (station.equals(name)) {
                imRing = true;
            }
            if (imRing) {
                kette.append(station).append(" -> ");
            }
        }
        return kette.append(name).toString();
    }

    private record AufgeloesterCallback(ToolCallback original, ToolDefinition definition)
            implements ToolCallback {

        @Override
        public ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return original.getToolMetadata();
        }

        @Override
        public String call(String toolInput) {
            return original.call(toolInput);
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            return original.call(toolInput, toolContext);
        }
    }
}
