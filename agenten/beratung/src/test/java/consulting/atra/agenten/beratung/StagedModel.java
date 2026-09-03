package consulting.atra.agenten.beratung;

import consulting.atra.agenten.model.ModelClient;
import consulting.atra.agenten.model.ChatMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;

@TestConfiguration(proxyBeanMethods = false)
public class StagedModel {

    @Bean
    ModelClient modellClient() {
        return new ModelClient() {

            @Override
            public String classify(String modell, String systemprompt, String message) {
                return "BERATUNG";
            }

            @Override
            public String respond(String modell, String systemprompt,
                                    List<ChatMessage> history, List<ToolCallback> tools) {
                ToolCallback first = tool(tools, "tarife_auflisten");
                ToolCallback second = tool(tools, "bedingungen_suchen");
                first.call("{}");
                try {
                    Thread.sleep(400);
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                }
                second.call("{}");
                return TestModel.ANSWER;
            }

            private ToolCallback tool(List<ToolCallback> tools, String name) {
                return tools.stream()
                        .filter(w -> w.getToolDefinition().name().equals(name))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "Tool " + name + " nicht angeboten"));
            }
        };
    }
}
