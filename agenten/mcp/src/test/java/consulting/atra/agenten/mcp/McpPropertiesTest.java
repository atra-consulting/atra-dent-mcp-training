package consulting.atra.agenten.mcp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpPropertiesTest {

    private static final String KEY = "atra-lab-2026";

    @Test
    @DisplayName("an agent may configure the Kernsystem alone")
    void theKernsystemAloneIsEnough() {
        McpProperties properties = new McpProperties(kernsystem(), null, null, null);

        assertThat(properties.kernsystem().url()).isEqualTo("http://localhost:8080");
        assertThat(properties.rechenkern()).isNull();
        assertThat(properties.wissen()).isNull();
    }

    @Test
    @DisplayName("a missing Kernsystem still fails with the known message")
    void aMissingKernsystemFails() {
        assertThatThrownBy(() -> new McpProperties(null, connection(), connection(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agenten.mcp braucht kernsystem");
    }

    @Test
    @DisplayName("the Kernsystem without an api-key fails")
    void theKernsystemNeedsItsKey() {
        assertThatThrownBy(() -> new McpProperties(connection(), null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agenten.mcp.kernsystem.api-key fehlt");
    }

    @Test
    @DisplayName("a connection without a url fails")
    void aConnectionNeedsItsUrl() {
        assertThatThrownBy(() -> new McpProperties.Connection(" ", "/mcp", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("url fehlt");
    }

    @Test
    @DisplayName("the timeout falls back to thirty seconds and the endpoint to /mcp")
    void theDefaultsHold() {
        McpProperties properties = new McpProperties(
                new McpProperties.Connection("http://localhost:8080", null, KEY),
                null, null, null);

        assertThat(properties.timeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.kernsystem().endpoint()).isEqualTo("/mcp");
    }

    private static McpProperties.Connection kernsystem() {
        return new McpProperties.Connection("http://localhost:8080", "/mcp", KEY);
    }

    private static McpProperties.Connection connection() {
        return new McpProperties.Connection("http://localhost:8086", "/mcp", null);
    }
}
