package consulting.atra.agenten.beratung.beleg;

import consulting.atra.agenten.mcp.ObservedTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Component
public class ConfidentialityGuard {

    public static final String LEITFADEN = "beratungsleitfaden_suchen";

    static final int WINDOW = 8;

    private static final Logger log = LoggerFactory.getLogger(ConfidentialityGuard.class);

    private final ObjectMapper mapper;

    public ConfidentialityGuard(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "abbilder");
    }

    public Optional<String> violation(String antwort, ObservedTools observed) {
        List<String> passages = passages(observed);
        if (passages.isEmpty() || antwort == null || antwort.isBlank()) {
            return Optional.empty();
        }

        Set<String> fromAnswer = follow(antwort);
        for (String passage : passages) {
            for (String folge : follow(passage)) {
                if (fromAnswer.contains(folge)) {
                    return Optional.of(folge);
                }
            }
        }
        return Optional.empty();
    }

    public boolean leitfadenQueried(ObservedTools observed) {
        return !observed.resultsOf(LEITFADEN).isEmpty();
    }


    private List<String> passages(ObservedTools observed) {
        List<String> texts = new ArrayList<>();
        for (String rohergebnis : observed.resultsOf(LEITFADEN)) {
            try {
                JsonNode sections = mapper.readTree(rohergebnis).get("abschnitte");
                if (sections == null || !sections.isArray()) {
                    continue;
                }
                sections.forEach(abschnitt -> {
                    JsonNode text = abschnitt.get("text");
                    if (text != null && !text.isNull()) {
                        texts.add(text.asString());
                    }
                });
            } catch (RuntimeException exception) {
                log.warn("Ergebnis von {} liess sich nicht lesen; die Pruefung faellt fuer diese "
                        + "Passage aus", LEITFADEN, exception);
            }
        }
        return texts;
    }

    private static Set<String> follow(String text) {
        String[] words = normalize(text).split(" ");
        Set<String> follow = new LinkedHashSet<>();
        for (int anfang = 0; anfang + WINDOW <= words.length; anfang++) {
            follow.add(String.join(" ", java.util.Arrays.copyOfRange(words, anfang,
                    anfang + WINDOW)));
        }
        return follow;
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.GERMAN)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .strip();
    }
}
