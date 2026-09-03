package consulting.atra.agenten.orchestrator.agent;

import consulting.atra.agenten.a2a.agent.Outcome;
import consulting.atra.agenten.a2a.client.SubagentResponse;

import java.util.concurrent.ConcurrentHashMap;

public class ThreadRegistry {

    public record Thread(String contextId, String taskId) { }

    private final ConcurrentHashMap<String, Thread> threads = new ConcurrentHashMap<>();

    public Thread thread(String contextId, String agent) {
        return threads.get(contextId + "/" + agent);
    }

    public boolean isOpen(String contextId) {
        return threads.keySet().stream().anyMatch(s -> s.startsWith(contextId + "/"));
    }

    public void record(String contextId, String agent, SubagentResponse response) {
        String key = contextId + "/" + agent;
        if (response.outcome() == Outcome.INPUT_REQUIRED) {
            threads.put(key, new Thread(response.contextId(), response.taskId()));
        } else {
            threads.remove(key);
        }
    }

    public void clear(String contextId) {
        threads.keySet().removeIf(key -> key.startsWith(contextId + "/"));
    }
}
