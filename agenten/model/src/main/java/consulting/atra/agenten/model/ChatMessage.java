package consulting.atra.agenten.model;

import java.util.Objects;

public record ChatMessage(boolean vomKunden, String text) {

    public ChatMessage {
        Objects.requireNonNull(text, "text");
        if (text.isBlank()) {
            throw new IllegalArgumentException("Ein ChatMessage ohne Text ist keiner");
        }
    }

    public static ChatMessage fromTheKunde(String text) {
        return new ChatMessage(true, text);
    }

    public static ChatMessage fromAgent(String text) {
        return new ChatMessage(false, text);
    }
}
