package consulting.atra.agenten.mcp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpContentTest {

    @Test
    @DisplayName("the text of a content block is the result")
    void theTextOfABlock() {
        String wrapper = McpWrapper.um("{\"treffer\":[]}");

        assertThat(McpContent.unpack(wrapper)).isEqualTo("{\"treffer\":[]}");
    }

    @Test
    @DisplayName("several blocks come out in sequence")
    void severalBlocks() {
        String wrapper = "[{\"type\":\"text\",\"text\":\"eins\"},"
                + "{\"type\":\"text\",\"text\":\"zwei\"}]";

        assertThat(McpContent.unpack(wrapper)).isEqualTo("einszwei");
    }

    @Test
    @DisplayName("what is not a wrapper passes through unchanged")
    void withoutTheWrapperUnchanged() {
        assertThat(McpContent.unpack("{\"treffer\":[]}")).isEqualTo("{\"treffer\":[]}");
    }

    @Test
    @DisplayName("a list without a text block is a result and no wrapper")
    void aListWithoutTextBlocks() {
        String list = "[{\"behandlungsdatum\":\"2026-01-02\"}]";

        assertThat(McpContent.unpack(list)).isEqualTo(list);
    }

    @Test
    @DisplayName("an unreadable result stays unreadable instead of throwing")
    void unreadableInputDoesNotThrow() {
        assertThat(McpContent.unpack("das ist kein json")).isEqualTo("das ist kein json");
    }

    @Test
    @DisplayName("without a result there is nothing to unwrap")
    void withoutAResult() {
        assertThat(McpContent.unpack(null)).isNull();
        assertThat(McpContent.unpack("")).isEmpty();
    }
}
