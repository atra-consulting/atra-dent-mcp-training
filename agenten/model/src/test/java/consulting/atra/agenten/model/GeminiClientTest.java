package consulting.atra.agenten.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiClientTest {

    private static final String MODELL = "gemini-test";

    @Test
    @DisplayName("an empty response is a ModelWithoutResponseException")
    void anEmptyResponseGetsItsOwnType() {
        GeminiClient client = clientWith("");

        assertThatThrownBy(() -> client.classify(MODELL, "System", "Hallo"))
                .isInstanceOf(ModelWithoutResponseException.class);
    }

    @Test
    @DisplayName("a response of nothing but whitespace likewise")
    void aBlankResponseGetsItsOwnType() {
        GeminiClient client = clientWith("   \n  ");

        assertThatThrownBy(() -> client.classify(MODELL, "System", "Hallo"))
                .isInstanceOf(ModelWithoutResponseException.class);
    }

    @Test
    @DisplayName("in a conversation with tools too -- there the turn ends with the signal")
    void anEmptyResponseAlsoInAConversation() {
        GeminiClient client = clientWith("");

        assertThatThrownBy(() -> client.respond(MODELL, "System",
                List.of(ChatMessage.fromTheKunde("Bitte pruefen")), List.of()))
                .isInstanceOf(ModelWithoutResponseException.class);
    }

    @Test
    @DisplayName("the new type is a ModelUnreachableException -- old catchers still hold")
    void oldCatchersStillHold() {
        GeminiClient client = clientWith("");

        assertThatThrownBy(() -> client.classify(MODELL, "System", "Hallo"))
                .isInstanceOf(ModelUnreachableException.class)
                .extracting(failure -> ((ModelUnreachableException) failure).modell())
                .isEqualTo(MODELL);
    }

    @Test
    @DisplayName("a provider failure stays an ordinary failure")
    void aProviderFailureIsNotAnEmptyResponse() {
        GeminiClient client = new GeminiClient(ChatClient.create(prompt -> {
            throw new IllegalStateException("Anbieter meldet 503");
        }));

        assertThatThrownBy(() -> client.classify(MODELL, "System", "Hallo"))
                .isInstanceOf(ModelUnreachableException.class)
                .isNotInstanceOf(ModelWithoutResponseException.class);
    }

    @Test
    @DisplayName("a filled response comes back truncated")
    void aFilledResponseGetsThrough() {
        GeminiClient client = clientWith("  PRUEFUNG\n");

        assertThat(client.classify(MODELL, "System", "Hallo")).isEqualTo("PRUEFUNG");
    }


    private static GeminiClient clientWith(String text) {
        ChatModel fixedAnswer = prompt -> new ChatResponse(
                List.of(new Generation(new AssistantMessage(text))));
        return new GeminiClient(ChatClient.create(fixedAnswer));
    }
}
