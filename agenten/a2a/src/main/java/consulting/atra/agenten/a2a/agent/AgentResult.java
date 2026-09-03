package consulting.atra.agenten.a2a.agent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record AgentResult(
        Outcome outcome,
        String answerText,
        String artifactName,
        Map<String, Object> content) {

    public AgentResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(answerText, "answerText");
        if (answerText.isBlank()) {
            throw new IllegalArgumentException("answerText must not be blank");
        }
        content = content == null ? Map.of() : unmodifiable(content);
        if (outcome != Outcome.COMPLETED && (artifactName != null || !content.isEmpty())) {
            throw new IllegalArgumentException(
                    "Only a COMPLETED outcome carries an artifact, " + outcome + " does not");
        }
        if (outcome == Outcome.COMPLETED && artifactName == null) {
            throw new IllegalArgumentException("A COMPLETED outcome needs an artifact name");
        }
    }

    public static AgentResult completed(String artifactName, String answerText,
                                            Map<String, Object> content) {
        return new AgentResult(Outcome.COMPLETED, answerText, artifactName, content);
    }

    public static AgentResult inputRequired(String answerText) {
        return new AgentResult(Outcome.INPUT_REQUIRED, answerText, null, Map.of());
    }

    public static AgentResult rejected(String answerText) {
        return new AgentResult(Outcome.REJECTED, answerText, null, Map.of());
    }

    public static AgentResult failed(String answerText) {
        return new AgentResult(Outcome.FAILED, answerText, null, Map.of());
    }

    private static Map<String, Object> unmodifiable(Map<String, Object> content) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(content));
    }
}
