package consulting.atra.agenten.beratung;

import consulting.atra.agenten.beratung.tools.ToolSelection;
import consulting.atra.agenten.mcp.MandantContext;
import consulting.atra.agenten.mcp.McpWrapper;
import consulting.atra.agenten.mcp.ToolSource;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;

@TestConfiguration(proxyBeanMethods = false)
public class TestTools {

    @Bean
    ToolSource toolSource(ToolLog store) {
        return allowed -> ToolSelection.catalog().stream()
                .filter(name -> allowed.allowed(name, ToolSelection.serverOf(name)))
                .map(name -> (ToolCallback) new Testtool(name, store))
                .toList();
    }

    @Bean
    ToolLog toolBook() {
        return new ToolLog();
    }

    public static final class ToolLog {

        private final List<Aufruf> calls = new ArrayList<>();

        synchronized void record(String name, Long kundenId) {
            calls.add(new Aufruf(name, kundenId));
        }

        public synchronized List<String> calls() {
            return calls.stream().map(Aufruf::tool).toList();
        }

        public synchronized List<Long> kundenNumbers() {
            return calls.stream().map(Aufruf::kundenId).toList();
        }

        public synchronized void clear() {
            calls.clear();
        }

        public record Aufruf(String tool, Long kundenId) {
        }
    }

    private record Testtool(String name, ToolLog store) implements ToolCallback {

        @Override
        public ToolDefinition getToolDefinition() {
            return ToolDefinition.builder()
                    .name(name)
                    .description("Testtool " + name)
                    .inputSchema("{\"type\":\"object\",\"properties\":{}}")
                    .build();
        }

        @Override
        public String call(String input) {
            store.record(name, MandantContext.caller());
            return McpWrapper.um("{\"testtool\":\"" + name + "\"}");
        }

        @Override
        public String call(String input, org.springframework.ai.chat.model.ToolContext context) {
            return call(input);
        }
    }
}
