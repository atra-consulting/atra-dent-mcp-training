package consulting.atra.agenten.a2a.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "agenten.subagents")
public record SubagentProperties(Map<String, Subagent> catalog, Duration timeout,
                                 Duration cardTimeout) {

    static final Duration DEFAULT_CARD_TIMEOUT = Duration.ofSeconds(60);

    public SubagentProperties {
        catalog = catalog == null ? Map.of() : catalog;
        for (Map.Entry<String, Subagent> entry : catalog.entrySet()) {
            Subagent subagent = entry.getValue();
            if (subagent == null) {
                throw new IllegalStateException("agenten.subagents.catalog." + entry.getKey()
                        + " ist leer: erwartet wird mindestens eine url");
            }
            if (!subagent.wired()) {
                continue;
            }
            try {
                URI.create(subagent.url());
            } catch (IllegalArgumentException cause) {
                throw new IllegalStateException("agenten.subagents.catalog." + entry.getKey()
                        + ".url ist keine gueltige Adresse: " + subagent.url(), cause);
            }
        }
        catalog = Collections.unmodifiableMap(new LinkedHashMap<>(catalog));
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalStateException("agenten.subagents.timeout muss positiv sein");
        }
        cardTimeout = cardTimeout == null || cardTimeout.isNegative() || cardTimeout.isZero()
                ? DEFAULT_CARD_TIMEOUT
                : cardTimeout;
    }

    public List<Subagent> wired() {
        return catalog.values().stream().filter(Subagent::wired).toList();
    }
}
