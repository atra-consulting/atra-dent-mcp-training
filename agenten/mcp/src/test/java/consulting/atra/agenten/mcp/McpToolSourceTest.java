package consulting.atra.agenten.mcp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpToolSourceTest {

    @Test
    @DisplayName("an unconfigured connection is skipped instead of being iterated")
    void anAbsentConnectionOffersNoTools() {
        try (McpToolSource source = new McpToolSource(null, null, null)) {
            assertThat(source.forPermission((tool, server) -> true)).isEmpty();
        }
    }

    @Test
    @DisplayName("closing a source without any connection stays quiet")
    void closingWithoutConnectionsIsHarmless() {
        assertThatCode(() -> new McpToolSource(null, null, null).close())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the startup check expecting nothing passes without any connection")
    void theCheckPassesWhenNothingIsExpected() {
        try (McpToolSource source = new McpToolSource(null, null, null)) {
            assertThatCode(() -> source.check(Set.of())).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("the startup check names the tools an absent connection cannot deliver")
    void theCheckNamesTheMissingTools() {
        try (McpToolSource source = new McpToolSource(null, null, null)) {
            assertThatThrownBy(() -> source.check(Set.of("mein_vertrag_lesen")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("mein_vertrag_lesen");
        }
    }
}
