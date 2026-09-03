package consulting.atra.agenten.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.metadata.ToolMetadata;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ToolRefusals {

    public static final String ERROR = "fehler";

    private static final Logger log = LoggerFactory.getLogger(ToolRefusals.class);

    private static final ObjectMapper IMAGES = JsonMapper.builder().build();

    private static final Pattern TEXT_BLOCK =
            Pattern.compile("text=(.*?), meta=", Pattern.DOTALL);

    private static final Pattern INVOCATION_PREFIX =
            Pattern.compile("^Error invoking method: \\S+\\R?");

    private ToolRefusals() {
    }

    public static List<ToolCallback> readable(List<ToolCallback> callbacks) {
        Objects.requireNonNull(callbacks, "callbacks");
        return callbacks.stream().map(ToolRefusals::readable).toList();
    }

    public static ToolCallback readable(ToolCallback callback) {
        Objects.requireNonNull(callback, "callback");
        return new ReadableCallback(callback);
    }

    static String asJson(ToolExecutionException refused) {
        return IMAGES.writeValueAsString(Map.of(ERROR, text(refused)));
    }

    private static String text(ToolExecutionException refused) {
        String raw = String.valueOf(refused.getMessage());
        Matcher blocks = TEXT_BLOCK.matcher(raw);
        StringBuilder blown = new StringBuilder();
        while (blocks.find()) {
            if (!blown.isEmpty()) {
                blown.append('\n');
            }
            blown.append(blocks.group(1));
        }
        String content = blown.isEmpty() ? raw : blown.toString();
        return INVOCATION_PREFIX.matcher(content).replaceFirst("").trim();
    }

    private record ReadableCallback(ToolCallback original) implements ToolCallback {

        @Override
        public ToolDefinition getToolDefinition() {
            return original.getToolDefinition();
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return original.getToolMetadata();
        }

        @Override
        public String call(String toolInput) {
            return call(toolInput, null);
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            try {
                return toolContext == null
                        ? original.call(toolInput)
                        : original.call(toolInput, toolContext);
            } catch (ToolExecutionException refused) {
                String answer = asJson(refused);
                log.info("{} hat abgelehnt; das Modell bekommt {}",
                        getToolDefinition().name(), answer);
                return answer;
            }
        }
    }
}
