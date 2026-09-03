package consulting.atra.agenten.a2a.client;

import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentSkill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public class SubagentCatalog {

    private static final Logger log = LoggerFactory.getLogger(SubagentCatalog.class);

    private static final Duration RELOAD_INTERVAL = Duration.ofSeconds(5);

    private final Map<String, AgentCard> cards = new ConcurrentHashMap<>();
    private final Map<String, String> keys = new ConcurrentHashMap<>();
    private final List<Subagent> open;
    private final Duration reloadInterval;
    private final Duration cardTimeout;
    private final AtomicReference<Instant> nextAttempt = new AtomicReference<>(Instant.MIN);

    public SubagentCatalog(List<Subagent> subagents) {
        this(subagents, RELOAD_INTERVAL, AgentCardFetcher.REQUEST_TIMEOUT);
    }

    public SubagentCatalog(List<Subagent> subagents, Duration kartenFrist) {
        this(subagents, RELOAD_INTERVAL, kartenFrist);
    }

    SubagentCatalog(List<Subagent> subagents, Duration nachladeAbstand, Duration kartenFrist) {
        this.open = new CopyOnWriteArrayList<>(subagents);
        this.reloadInterval = nachladeAbstand;
        this.cardTimeout = kartenFrist;
        nextAttempt.set(Instant.now().plus(nachladeAbstand));
        attempt();
    }

    public SubagentCatalog(Map<String, AgentCard> vorgegebeneKarten) {
        cards.putAll(vorgegebeneKarten);
        this.open = new CopyOnWriteArrayList<>();
        this.reloadInterval = RELOAD_INTERVAL;
        this.cardTimeout = AgentCardFetcher.REQUEST_TIMEOUT;
    }

    public AgentCard card(String name) {
        AgentCard card = cards.get(name);
        if (card == null) {
            reloadWhenDue();
            card = cards.get(name);
        }
        return card;
    }

    public String apiKey(String agentName) {
        String key = keys.get(agentName);
        return key == null ? "" : key;
    }

    public Set<String> names() {
        reloadWhenDue();
        return cards.keySet();
    }

    public Optional<AgentCard> withSkill(String skillId) {
        reloadWhenDue();
        return cards.values().stream()
                .sorted(Comparator.comparing(AgentCard::name))
                .filter(card -> offers(card, skillId))
                .findFirst();
    }

    private static boolean offers(AgentCard card, String skillId) {
        if (card.skills() == null) {
            return false;
        }
        return card.skills().stream().anyMatch(skill -> skill.id() != null
                && skill.id().equals(skillId));
    }

    private void reloadWhenDue() {
        if (open.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        Instant sofar = nextAttempt.get();
        if (now.isBefore(sofar)) {
            return;
        }
        if (nextAttempt.compareAndSet(sofar, now.plus(reloadInterval))) {
            attempt();
        }
    }

    private void attempt() {
        for (Subagent subagent : open) {
            try {
                AgentCard card = fetchCard(subagent.url());
                keys.put(card.name(), subagent.apiKey());
                cards.put(card.name(), card);
                open.remove(subagent);
                log.info("Agent entdeckt: '{}' unter {}", card.name(), subagent.url());
            } catch (Exception exception) {
                log.warn("Agent Card unter {}{} nicht abrufbar (Frist {} s) - der Agent fehlt"
                                + " im Prompt und wird alle {} s erneut versucht: {}",
                        subagent.url(), AgentCardFetcher.CARD_PATH, cardTimeout.toSeconds(),
                        reloadInterval.toSeconds(), exception.getMessage());
            }
        }
    }

    private AgentCard fetchCard(String basisUrl) {
        return AgentCardFetcher.fetch(basisUrl, cardTimeout);
    }

    public String descriptions() {
        reloadWhenDue();
        if (cards.isEmpty()) {
            return "Derzeit ist kein Fachagent erreichbar.";
        }
        List<AgentCard> sorted = cards.values().stream()
                .sorted(Comparator.comparing(AgentCard::name))
                .toList();
        StringBuilder text = new StringBuilder();
        for (AgentCard card : sorted) {
            if (!text.isEmpty()) {
                text.append("\n\n");
            }
            text.append("### ").append(card.name()).append('\n')
                    .append(card.description()).append('\n')
                    .append("Fertigkeiten:\n");
            for (AgentSkill fertigkeit : card.skills()) {
                text.append("- ").append(fertigkeit.name()).append(": ")
                        .append(fertigkeit.description());
                List<String> examples = fertigkeit.examples();
                if (examples != null && !examples.isEmpty()) {
                    text.append(" (Beispiele: ").append(String.join("; ", examples)).append(')');
                }
                text.append('\n');
            }
        }
        return text.toString().strip();
    }
}
