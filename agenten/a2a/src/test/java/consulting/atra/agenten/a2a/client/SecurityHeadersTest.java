package consulting.atra.agenten.a2a.client;

import io.a2a.spec.APIKeySecurityScheme;
import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.HTTPAuthSecurityScheme;
import io.a2a.spec.SecurityScheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityHeadersTest {

    @Test
    @DisplayName("the header name comes from the card, not from this side")
    void takesTheHeaderNameFromTheCard() {
        AgentCard card = cardWith(Map.of("apiKey",
                new APIKeySecurityScheme("header", "x-api-key", null)));

        assertThat(SecurityHeaders.forCard(card, "geheim"))
                .containsExactly(Map.entry("x-api-key", "geheim"));
    }

    @Test
    @DisplayName("a card naming a different header gets that one")
    void followsWhateverHeaderTheCardNames() {
        AgentCard card = cardWith(Map.of("apiKey",
                new APIKeySecurityScheme("header", "x-praxis-token", null)));

        assertThat(SecurityHeaders.forCard(card, "geheim"))
                .containsExactly(Map.entry("x-praxis-token", "geheim"));
    }

    @Test
    @DisplayName("a bearer scheme becomes an Authorization header")
    void carriesABearerScheme() {
        AgentCard card = cardWith(Map.of("http",
                new HTTPAuthSecurityScheme(null, "bearer", null)));

        assertThat(SecurityHeaders.forCard(card, "geheim"))
                .containsExactly(Map.entry("Authorization", "Bearer geheim"));
    }

    @Test
    @DisplayName("an agent without a security scheme gets no header")
    void addsNothingWithoutAScheme() {
        assertThat(SecurityHeaders.forCard(cardWith(null), "geheim")).isEmpty();
        assertThat(SecurityHeaders.forCard(cardWith(Map.of()), "geheim")).isEmpty();
    }

    @Test
    @DisplayName("a demanded key that is not configured sets no header")
    void setsNoHeaderWithoutAKey() {
        AgentCard card = cardWith(Map.of("apiKey",
                new APIKeySecurityScheme("header", "x-api-key", null)));

        assertThat(SecurityHeaders.forCard(card, null)).isEmpty();
        assertThat(SecurityHeaders.forCard(card, "   ")).isEmpty();
    }

    @Test
    @DisplayName("a key demanded somewhere this transport cannot carry it is not invented")
    void carriesNoKeyOutsideAHeader() {
        AgentCard card = cardWith(Map.of("apiKey",
                new APIKeySecurityScheme("query", "schluessel", null)));

        assertThat(SecurityHeaders.forCard(card, "geheim")).isEmpty();
    }

    @Test
    @DisplayName("no card at all is not an error")
    void survivesAMissingCard() {
        assertThat(SecurityHeaders.forCard(null, "geheim")).isEmpty();
    }

    private static AgentCard cardWith(Map<String, SecurityScheme> schemes) {
        return new AgentCard.Builder()
                .name("Testagent")
                .description("Testkarte")
                .version("1.0.0")
                .url("http://localhost:0/")
                .protocolVersion("0.3.0")
                .capabilities(new AgentCapabilities.Builder()
                        .streaming(false).pushNotifications(false).build())
                .defaultInputModes(List.of("text/plain"))
                .defaultOutputModes(List.of("text/plain"))
                .skills(List.of())
                .securitySchemes(schemes)
                .build();
    }
}
