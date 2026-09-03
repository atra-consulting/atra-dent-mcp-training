package consulting.atra.agenten.a2a.client;

import java.util.Optional;

@FunctionalInterface
public interface AgentLookup {

    Optional<AgentClient> withSkill(String skillId);
}
