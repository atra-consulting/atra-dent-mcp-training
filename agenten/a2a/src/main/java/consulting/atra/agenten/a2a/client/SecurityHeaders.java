package consulting.atra.agenten.a2a.client;

import io.a2a.spec.APIKeySecurityScheme;
import io.a2a.spec.AgentCard;
import io.a2a.spec.HTTPAuthSecurityScheme;
import io.a2a.spec.SecurityScheme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class SecurityHeaders {

    public static final String AUTHORIZATION = "Authorization";

    public static final String BEARER = "Bearer";

    private static final String IN_HEADER = APIKeySecurityScheme.Location.HEADER.asString();

    private static final Logger log = LoggerFactory.getLogger(SecurityHeaders.class);

    private SecurityHeaders() {
    }

    public static Map<String, String> forCard(AgentCard card, String key) {
        Collection<SecurityScheme> declared = schemes(card);
        if (declared.isEmpty()) {
            return Map.of();
        }
        for (SecurityScheme scheme : declared) {
            String header = headerOf(scheme);
            if (header == null) {
                continue;
            }
            if (key == null || key.isBlank()) {
                log.warn("Der Agent '{}' verlangt laut seiner Karte einen Schluessel im Header"
                        + " {}, und es ist keiner hinterlegt. Die Karte ist ohne Schluessel"
                        + " lesbar, der Agent steht also im Katalog - abgewiesen wird erst"
                        + " der Aufruf.", name(card), header);
                return Map.of();
            }
            return Map.of(header, valueOf(scheme, key));
        }
        log.warn("Der Agent '{}' verlangt laut seiner Karte eine Anmeldung, die dieser Client"
                + " nicht setzen kann ({}). Jeder Aufruf an ihn wird abgewiesen.",
                name(card), types(declared));
        return Map.of();
    }

    private static Collection<SecurityScheme> schemes(AgentCard card) {
        Map<String, SecurityScheme> declared = card == null ? null : card.securitySchemes();
        return declared == null ? List.of() : declared.values();
    }

    private static String headerOf(SecurityScheme scheme) {
        if (scheme instanceof APIKeySecurityScheme apiKey
                && IN_HEADER.equalsIgnoreCase(apiKey.getIn())
                && apiKey.getName() != null && !apiKey.getName().isBlank()) {
            return apiKey.getName();
        }
        if (scheme instanceof HTTPAuthSecurityScheme http
                && BEARER.equalsIgnoreCase(http.getScheme())) {
            return AUTHORIZATION;
        }
        return null;
    }

    private static String valueOf(SecurityScheme scheme, String key) {
        return scheme instanceof HTTPAuthSecurityScheme ? BEARER + " " + key : key;
    }

    private static String name(AgentCard card) {
        return card == null || card.name() == null ? "unbekannt" : card.name();
    }

    private static List<String> types(Collection<SecurityScheme> schemes) {
        return schemes.stream()
                .map(scheme -> scheme.getClass().getSimpleName())
                .sorted()
                .toList();
    }
}
