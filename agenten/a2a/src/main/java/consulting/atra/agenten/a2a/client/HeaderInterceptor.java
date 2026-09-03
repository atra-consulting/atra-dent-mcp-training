package consulting.atra.agenten.a2a.client;

import io.a2a.client.transport.spi.interceptors.ClientCallContext;
import io.a2a.client.transport.spi.interceptors.ClientCallInterceptor;
import io.a2a.client.transport.spi.interceptors.PayloadAndHeaders;
import io.a2a.spec.AgentCard;

import java.util.LinkedHashMap;
import java.util.Map;

public class HeaderInterceptor extends ClientCallInterceptor {

    @Override
    public PayloadAndHeaders intercept(String methode, Object nutzlast,
            Map<String, String> headers, AgentCard card, ClientCallContext context) {
        Map<String, String> alle = new LinkedHashMap<>(headers == null ? Map.of() : headers);
        if (context != null && context.getHeaders() != null) {
            alle.putAll(context.getHeaders());
        }
        return new PayloadAndHeaders(nutzlast, alle);
    }
}
