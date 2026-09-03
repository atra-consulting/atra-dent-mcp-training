package consulting.atra.agenten.a2a.client;

import io.a2a.client.transport.spi.interceptors.ClientCallContext;
import io.a2a.client.transport.spi.interceptors.PayloadAndHeaders;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HeaderInterceptorTest {

    private final HeaderInterceptor interceptor = new HeaderInterceptor();

    @Test
    void takesHeadersFromTheContextAndLeavesExistingOnes() {
        Map<String, String> headers = Map.of("Content-Type", "application/json");
        ClientCallContext context = new ClientCallContext(Map.of(), Map.of("x-kunden-id", "4711"));

        PayloadAndHeaders result = interceptor.intercept("message/send", "nutzlast", headers, null, context);

        assertThat(result.getPayload()).isEqualTo("nutzlast");
        assertThat(result.getHeaders())
                .containsEntry("Content-Type", "application/json")
                .containsEntry("x-kunden-id", "4711");
    }

    @Test
    void handlesANullContext() {
        Map<String, String> headers = Map.of("Content-Type", "application/json");

        PayloadAndHeaders result = interceptor.intercept("message/send", "nutzlast", headers, null, null);

        assertThat(result.getHeaders()).containsExactly(Map.entry("Content-Type", "application/json"));
    }

    @Test
    void handlesNullHeadersWithAPopulatedContext() {
        ClientCallContext context = new ClientCallContext(Map.of(), Map.of("x-kunden-id", "4711"));

        PayloadAndHeaders result = interceptor.intercept("message/send", "nutzlast", null, null, context);

        assertThat(result.getHeaders()).containsExactly(Map.entry("x-kunden-id", "4711"));
    }

    @Test
    void handlesANullContextAndNullHeaders() {
        PayloadAndHeaders result = interceptor.intercept("message/send", "nutzlast", null, null, null);

        assertThat(result.getHeaders()).isEmpty();
    }
}
