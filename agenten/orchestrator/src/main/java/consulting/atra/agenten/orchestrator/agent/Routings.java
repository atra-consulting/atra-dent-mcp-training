package consulting.atra.agenten.orchestrator.agent;

import java.util.ArrayList;
import java.util.List;

public final class Routings {

    private record Routing(String agent, String anliegen) {
    }

    private final List<Routing> chosen = new ArrayList<>();

    public synchronized void add(String agent, String anliegen) {
        chosen.add(new Routing(agent, anliegen));
    }

    public synchronized List<String> names() {
        return chosen.stream().map(Routing::agent).toList();
    }

    public synchronized int count() {
        return chosen.size();
    }

    public synchronized boolean alreadyAsked(String agent, String anliegen) {
        return chosen.contains(new Routing(agent, anliegen));
    }
}
