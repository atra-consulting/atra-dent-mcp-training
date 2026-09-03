package consulting.atra.agenten.a2a.agent;

import java.util.Map;

public interface Agent {

    AgentResult run(String contextId, String text, Map<String, Object> data,
                               Long kundenId, StatusChannel status);

    default void finish(String contextId) {
    }
}
