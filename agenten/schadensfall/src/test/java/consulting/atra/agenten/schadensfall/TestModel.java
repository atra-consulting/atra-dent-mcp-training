package consulting.atra.agenten.schadensfall;

import consulting.atra.agenten.model.ModelClient;
import consulting.atra.agenten.model.ChatMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;

@TestConfiguration(proxyBeanMethods = false)
public class TestModel {

    public static final String WITHOUT_API_KEY = "spring.ai.google.genai.api-key=test-attrappe";

    public static final String ANSWER =
            "Die Positionen sind der GOZ nach zuzuordnen; ich empfehle die Freigabe.";

    @Bean
    ModelClient modellClient(Aufrufprotokoll protokoll) {
        return new ModelClient() {

            @Override
            public String classify(String modell, String systemprompt, String message) {
                protokoll.record(modell, systemprompt, List.of(), List.of());
                return "PRUEFUNG";
            }

            @Override
            public String respond(String modell, String systemprompt,
                                    List<ChatMessage> history, List<ToolCallback> tools) {
                protokoll.record(modell, systemprompt, history,
                        tools.stream().map(w -> w.getToolDefinition().name()).toList());
                if (!tools.isEmpty()) {
                    tools.getFirst().call("{}");
                }
                return ANSWER;
            }
        };
    }

    public record Schritt(String tool, String arguments) {

        public static Schritt withoutArguments(String tool) {
            return new Schritt(tool, "{}");
        }
    }

    public static ModelClient script(Aufrufprotokoll protokoll, List<Schritt> schritte,
                                      String antwort) {
        return new ModelClient() {

            @Override
            public String classify(String modell, String systemprompt, String message) {
                protokoll.record(modell, systemprompt, List.of(), List.of());
                return "PRUEFUNG";
            }

            @Override
            public String respond(String modell, String systemprompt,
                                    List<ChatMessage> history, List<ToolCallback> tools) {
                protokoll.record(modell, systemprompt, history,
                        tools.stream().map(w -> w.getToolDefinition().name()).toList());
                for (Schritt schritt : schritte) {
                    tools.stream()
                            .filter(w -> w.getToolDefinition().name().equals(schritt.tool()))
                            .findFirst()
                            .orElseThrow(() -> new AssertionError(
                                    "Das Modell bekam kein Tool namens " + schritt.tool()
                                            + "; angeboten waren " + tools.stream()
                                            .map(w -> w.getToolDefinition().name()).toList()))
                            .call(schritt.arguments());
                }
                return antwort;
            }
        };
    }

    public static ModelClient script(Aufrufprotokoll protokoll, List<Schritt> schritte) {
        return script(protokoll, schritte, ANSWER);
    }

    @Bean
    Aufrufprotokoll callLog() {
        return new Aufrufprotokoll();
    }

    public static final class Aufrufprotokoll {

        private final List<Aufruf> calls = new ArrayList<>();

        synchronized void record(String modell, String systemprompt, List<ChatMessage> history,
                                 List<String> tools) {
            calls.add(new Aufruf(modell, systemprompt, tools,
                    history.stream().map(ChatMessage::text)
                            .collect(java.util.stream.Collectors.joining("\n"))));
        }

        public synchronized List<Aufruf> calls() {
            return List.copyOf(calls);
        }

        public synchronized Aufruf last() {
            return calls.getLast();
        }

        public synchronized void clear() {
            calls.clear();
        }

        public record Aufruf(String modell, String systemprompt, List<String> tools, String history) {
        }
    }
}
