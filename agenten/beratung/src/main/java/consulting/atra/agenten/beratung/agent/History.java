package consulting.atra.agenten.beratung.agent;

import consulting.atra.agenten.model.ChatMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class History {

    static final int MAX_COUNT = 16;

    private final Map<String, List<ChatMessage>> conversations = new ConcurrentHashMap<>();

    public List<ChatMessage> history(String contextId) {
        return List.copyOf(conversations.getOrDefault(contextId, List.of()));
    }

    public void record(String contextId, ChatMessage beitrag) {
        conversations.compute(contextId, (id, sofar) -> {
            List<ChatMessage> amended =
                    new ArrayList<>(sofar == null ? List.<ChatMessage>of() : sofar);
            amended.add(beitrag);
            while (amended.size() > MAX_COUNT) {
                amended.removeFirst();
            }
            return List.copyOf(amended);
        });
    }

    public void forgotten(String contextId) {
        conversations.remove(contextId);
    }
}
