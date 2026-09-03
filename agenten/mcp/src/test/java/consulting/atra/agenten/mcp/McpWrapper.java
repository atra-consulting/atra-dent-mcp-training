package consulting.atra.agenten.mcp;

import tools.jackson.databind.json.JsonMapper;

public final class McpWrapper {

    private static final JsonMapper IMAGES = JsonMapper.builder().build();

    private McpWrapper() {
    }

    public static String um(String antwort) {
        return "[{\"type\":\"text\",\"text\":" + IMAGES.writeValueAsString(antwort) + "}]";
    }
}
