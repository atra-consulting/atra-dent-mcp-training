package consulting.atra.agenten.a2a.client;

import java.util.Objects;

public class AgentUnreachableException extends RuntimeException {

    private final transient String agent;

    public AgentUnreachableException(String agent, String message, Throwable cause) {
        super("Der Agent '" + agent + "' ist nicht erreichbar: " + message, cause);
        this.agent = Objects.requireNonNull(agent, "agent");
    }

    public AgentUnreachableException(String agent, String message) {
        this(agent, message, null);
    }

    public String agent() {
        return agent;
    }
}
