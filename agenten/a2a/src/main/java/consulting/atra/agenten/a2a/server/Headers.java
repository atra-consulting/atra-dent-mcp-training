package consulting.atra.agenten.a2a.server;

import io.a2a.server.ServerCallContext;
import io.a2a.spec.JSONRPCError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class Headers {

    public static final String HEADER = "x-kunden-id";
    public static final String KUNDEN_ID_KEY = "kundenId";

    private Headers() {
    }

    public static ServerCallContext context() throws JSONRPCError {
        HttpServletRequest request = ((ServletRequestAttributes)
                RequestContextHolder.currentRequestAttributes()).getRequest();
        String value = request.getHeader(HEADER);
        Map<String, Object> state = new HashMap<>();
        if (value != null && !value.isBlank()) {
            try {
                long kundenId = Long.parseLong(value.strip());
                if (kundenId < 1) {
                    throw new NumberFormatException("nicht positiv");
                }
                state.put(KUNDEN_ID_KEY, kundenId);
            } catch (NumberFormatException cause) {
                throw new JSONRPCError(-32600, "Der Header " + HEADER
                        + " muss eine positive Kundennummer sein. Ohne sie antwortet der"
                        + " Dienst als waere niemand angemeldet; mit einem unlesbaren Wert"
                        + " antwortet er nicht.", null);
            }
        }
        return new ServerCallContext(null, state, Set.of());
    }
}
