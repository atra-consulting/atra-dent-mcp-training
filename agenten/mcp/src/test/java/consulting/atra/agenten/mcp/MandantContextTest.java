package consulting.atra.agenten.mcp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class MandantContextTest {

    @Test
    @DisplayName("inside the call the number applies, afterwards it does not")
    void theNumberAppliesOnlyWithinTheCall() {
        assertThat(MandantContext.caller()).isNull();

        Long seen = MandantContext.with(10001L, MandantContext::caller);

        assertThat(seen).isEqualTo(10001L);
        assertThat(MandantContext.caller()).isNull();
    }

    @Test
    @DisplayName("the context is cleaned up after an exception too")
    void cleanupAlsoAfterAnException() {
        try {
            MandantContext.with(10002L, () -> {
                throw new IllegalStateException("etwas ging schief");
            });
        } catch (IllegalStateException _) {
        }
        assertThat(MandantContext.caller()).isNull();
    }

    @Test
    @DisplayName("nested calls restore the previous state")
    void nestedCalls() {
        MandantContext.with(10001L, () -> {
            assertThat(MandantContext.caller()).isEqualTo(10001L);
            MandantContext.with(10002L, () -> {
                assertThat(MandantContext.caller()).isEqualTo(10002L);
                return null;
            });
            assertThat(MandantContext.caller()).isEqualTo(10001L);
            return null;
        });
    }

    @Test
    @DisplayName("nobody logged in is a valid state, not an error")
    void nobodyLoggedIn() {
        Long seen = MandantContext.with(null, MandantContext::caller);

        assertThat(seen).isNull();
    }

    @Test
    @DisplayName("another thread does not see the number")
    void anotherThreadSeesNothing() {
        Long fremd = MandantContext.with(10001L,
                () -> CompletableFuture.supplyAsync(MandantContext::caller).join());

        assertThat(fremd).isNull();
    }
}
