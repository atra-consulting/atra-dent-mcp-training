package consulting.atra.agenten.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public final class McpContent {

    private static final String TEXT = "text";

    private static final Logger log = LoggerFactory.getLogger(McpContent.class);

    private static final ObjectMapper IMAGES = JsonMapper.builder().build();

    private McpContent() {
    }

    public static String unpack(String rohergebnis) {
        if (rohergebnis == null || rohergebnis.isBlank()) {
            return rohergebnis;
        }
        try {
            JsonNode root = IMAGES.readTree(rohergebnis);
            if (!root.isArray()) {
                return rohergebnis;
            }
            StringBuilder content = new StringBuilder();
            for (JsonNode block : root) {
                JsonNode text = block.get(TEXT);
                if (text != null && !text.isNull()) {
                    content.append(text.asString());
                }
            }
            return content.isEmpty() ? rohergebnis : content.toString();
        } catch (RuntimeException unreadable) {
            log.debug("Rohergebnis ist kein JSON; es geht unveraendert weiter", unreadable);
            return rohergebnis;
        }
    }
}
