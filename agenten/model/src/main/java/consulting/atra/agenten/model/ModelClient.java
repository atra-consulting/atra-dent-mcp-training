package consulting.atra.agenten.model;

import org.springframework.ai.tool.ToolCallback;

import java.util.List;

public interface ModelClient {

    String classify(String modell, String systemprompt, String message);

    String respond(String modell, String systemprompt, List<ChatMessage> history,
                     List<ToolCallback> tools);
}
