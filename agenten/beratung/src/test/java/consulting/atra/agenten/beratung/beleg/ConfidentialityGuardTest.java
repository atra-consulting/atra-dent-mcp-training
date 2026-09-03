package consulting.atra.agenten.beratung.beleg;

import consulting.atra.agenten.a2a.agent.StatusChannel;
import consulting.atra.agenten.beratung.tools.BeratungMessages;
import consulting.atra.agenten.beratung.tools.ToolSelection;
import consulting.atra.agenten.mcp.ObservedTools;
import consulting.atra.agenten.mcp.McpWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfidentialityGuardTest {

    private static final String LEITFADEN_PASSAGE =
            "Wird der Einwand zu teuer vorgebracht, stellen Sie dem Monatsbeitrag den "
                    + "Eigenanteil einer einzelnen Krone gegenüber und fragen Sie, ob dieser "
                    + "Betrag im Ernstfall bereitstünde.";

    private final ConfidentialityGuard guard =
            new ConfidentialityGuard(JsonMapper.builder().build());

    @Test
    @DisplayName("a passage quoted verbatim is a violation")
    void aVerbatimQuoteIsAViolation() {
        var observed = withLeitfaden(LEITFADEN_PASSAGE);

        String answer = "Dazu ein Gedanke: Stellen Sie dem Monatsbeitrag den Eigenanteil einer "
                + "einzelnen Krone gegenüber und fragen Sie, ob dieser Betrag im Ernstfall "
                + "bereitstünde.";

        assertThat(guard.violation(answer, observed)).isPresent();
    }

    @Test
    @DisplayName("different punctuation and capitalisation do not help")
    void normalizationApplies() {
        var observed = withLeitfaden(LEITFADEN_PASSAGE);

        String answer = "STELLEN SIE dem Monatsbeitrag - den Eigenanteil einer einzelnen Krone - "
                + "gegenüber, und fragen Sie...";

        assertThat(guard.violation(answer, observed)).isPresent();
    }

    @Test
    @DisplayName("the same statement in one's own words is no violation")
    void ownWordsAreNoViolation() {
        var observed = withLeitfaden(LEITFADEN_PASSAGE);

        String answer = "Ob sich der Beitrag lohnt, hängt davon ab, was Sie im Ernstfall selbst "
                + "tragen müssten. Für Zahnersatz sieht Ihr Tarif eine Quote von 70 Prozent vor "
                + "(§ 3 Leistungsumfang).";

        assertThat(guard.violation(answer, observed)).isEmpty();
    }

    @Test
    @DisplayName("short generic phrases do not trigger")
    void shortPhrasesDoNotTrigger() {
        var observed = withLeitfaden(
                "In den ersten Jahren gilt die Zahnstaffel, und der Kunde sollte das wissen.");

        String answer = "In den ersten Jahren gilt die Zahnstaffel (§ 5 Staffelung).";

        assertThat(guard.violation(answer, observed)).isEmpty();
    }

    @Test
    @DisplayName("without a Leitfaden call there is nothing to check")
    void withoutTheLeitfadenNoViolation() {
        var observed = new ObservedTools(trace -> { }, "beratung",
                ToolSelection.MAPPING, BeratungMessages.ALLE);

        assertThat(guard.violation("Beliebiger Text", observed)).isEmpty();
        assertThat(guard.leitfadenQueried(observed)).isFalse();
    }

    @Test
    @DisplayName("an unreadable result is no violation, but it is noticed")
    void anUnreadableResultIsNoViolation() {
        var observed = new ObservedTools(trace -> { }, "beratung",
                ToolSelection.MAPPING, BeratungMessages.ALLE);
        observed.wrap(List.of(tool(ConfidentialityGuard.LEITFADEN, "kein json")))
                .getFirst().call("{}");

        assertThat(guard.violation("Irgendeine Antwort", observed)).isEmpty();
        assertThat(guard.leitfadenQueried(observed)).isTrue();
    }


    private static ObservedTools withLeitfaden(String passage) {
        var observed = new ObservedTools(StatusChannel.discarded(), "beratung",
                        ToolSelection.MAPPING, BeratungMessages.ALLE);
        String result = """
                {"frage":"zu teuer","vertraulichkeit":"intern",
                 "vertraulichkeitshinweis":"nicht zur Vorlage beim Kunden",
                 "abschnitte":[{"dokumentId":"beratungshandbuch","abschnittId":"einwand-teuer",
                                "ueberschrift":"Einwand: zu teuer","text":"%s","bewertung":0.9}]}
                """.formatted(passage);
        observed.wrap(List.of(tool(ConfidentialityGuard.LEITFADEN, result)))
                .getFirst().call("{}");
        return observed;
    }

    private static ToolCallback tool(String name, String result) {
        String onTheWire = McpWrapper.um(result);
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name(name).description("Test")
                        .inputSchema("{\"type\":\"object\",\"properties\":{}}").build();
            }

            @Override
            public String call(String input) {
                return onTheWire;
            }

            @Override
            public String call(String input, ToolContext context) {
                return onTheWire;
            }
        };
    }
}
