package consulting.atra.agenten.model;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Objects;

public class GeminiClient implements ModelClient {

    private final ChatClient chatClient;

    public GeminiClient(ChatClient chatClient) {
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient");
    }

    @Override
    public String classify(String modell, String systemprompt, String message) {
        Objects.requireNonNull(message, "nachricht");
        return call(modell, systemprompt, spec -> spec.user(message));
    }

    @Override
    public String respond(String modell, String systemprompt, List<ChatMessage> history,
                            List<ToolCallback> tools) {
        Objects.requireNonNull(history, "verlauf");
        Objects.requireNonNull(tools, "tools");
        if (history.isEmpty()) {
            throw new IllegalArgumentException("Ein Gespraech ohne Wortmeldung ist keines");
        }

        List<ToolCallback> providerReady =
                ToolRefusals.readable(ToolSchemaInliner.resolve(tools));

        return call(modell, systemprompt, spec -> {
            for (ChatMessage beitrag : history) {
                if (beitrag.vomKunden()) {
                    spec.user(beitrag.text());
                } else {
                    spec.messages(new org.springframework.ai.chat.messages.AssistantMessage(
                            beitrag.text()));
                }
            }
            return providerReady.isEmpty() ? spec : spec.tools(providerReady.toArray());
        });
    }

    private String call(String modell, String systemprompt,
                            java.util.function.UnaryOperator<ChatClient.ChatClientRequestSpec> ausbau) {
        Objects.requireNonNull(modell, "modell");
        Objects.requireNonNull(systemprompt, "systemprompt");

        try {
            ChatClient.ChatClientRequestSpec spec = chatClient.prompt()
                    .system(systemprompt)
                    .options(GoogleGenAiChatOptions.builder().model(modell));

            String text = ausbau.apply(spec).call().content();

            if (text == null || text.isBlank()) {
                throw new ModelWithoutResponseException(modell);
            }
            return text.strip();
        } catch (ModelUnreachableException weiterreichen) {
            throw weiterreichen;
        } catch (RuntimeException exception) {
            throw new ModelUnreachableException(modell, exception);
        }
    }
}
