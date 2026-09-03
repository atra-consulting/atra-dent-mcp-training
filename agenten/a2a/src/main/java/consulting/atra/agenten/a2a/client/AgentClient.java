package consulting.atra.agenten.a2a.client;

import consulting.atra.agenten.a2a.agent.StatusChannel;

import java.util.Map;

public interface AgentClient {

    SubagentResponse send(String contextId, String taskId, String text, Map<String, Object> data,
            Long kundenId, StatusChannel status);
}
