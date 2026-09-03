package consulting.atra.agenten.beratung;

import consulting.atra.agenten.beratung.tools.SignalTools;
import consulting.atra.agenten.model.ModelClient;
import consulting.atra.agenten.model.ChatMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@TestConfiguration(proxyBeanMethods = false)
public class TestModel {

    public static final String WITHOUT_API_KEY = "spring.ai.google.genai.api-key=test-attrappe";

    public static final String ANSWER =
            "Ihr Tarif leistet für Zahnersatz 70 Prozent, in den ersten Jahren begrenzt durch"
                    + " die Zahnstaffel.";

    private static final String RECHNUNG_EINREICHEN_MARKER = "RECHNUNG EINREICHEN";

    @Bean
    ModelClient modellClient(Aufrufprotokoll protokoll) {
        return new ModelClient() {

            @Override
            public String classify(String modell, String systemprompt, String message) {
                protokoll.record(modell, systemprompt, List.of(), List.of());
                return "BERATUNG";
            }

            @Override
            public String respond(String modell, String systemprompt,
                                    List<ChatMessage> history, List<ToolCallback> tools) {
                protokoll.record(modell, systemprompt, history,
                        tools.stream().map(w -> w.getToolDefinition().name()).toList());
                boolean ersteAnnahme = systemprompt.contains(RECHNUNG_EINREICHEN_MARKER)
                        && history.size() <= 1;
                Optional<ToolCallback> followUp = tools.stream()
                        .filter(w -> SignalTools.FOLLOW_UP.equals(w.getToolDefinition().name()))
                        .findFirst();
                if (ersteAnnahme && followUp.isPresent()) {
                    followUp.get().call(
                            "{\"frage\":\"Ist die Rechnung fuer eine mitversicherte Person?\"}");
                    return "wird durch das Signal ersetzt";
                }
                if (!tools.isEmpty()) {
                    tools.getFirst().call("{}");
                }
                return ANSWER;
            }
        };
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
