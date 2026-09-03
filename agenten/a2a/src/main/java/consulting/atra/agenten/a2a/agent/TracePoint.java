package consulting.atra.agenten.a2a.agent;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TracePoint(
        String text,
        String label,
        String sender,
        Protocol protocol,
        String peer,
        String operation,
        Map<String, Object> data,
        Instant timestamp) {

    public static final int MAX_LENGTH = 8_000;

    static final String TRUNCATED = "\n… gekürzt nach " + MAX_LENGTH + " Zeichen";

    public TracePoint {
        Objects.requireNonNull(protocol, "protocol");
        Objects.requireNonNull(text, "text");
        if (text.isBlank()) {
            throw new IllegalArgumentException("text darf nicht leer sein");
        }
        data = data == null ? Map.of() : unmodifiable(data);
    }

    public static TracePoint hop(String sender, String peer, String methode, String text,
                                   String label, Map<String, Object> data) {
        return new TracePoint(text, label, sender, Protocol.A2A, peer, methode,
                data, Instant.now());
    }

    public static TracePoint tool(String sender, String server, String tool, String text,
                                     String label, Map<String, Object> data) {
        return new TracePoint(text, label, sender, Protocol.MCP, server, tool,
                data, Instant.now());
    }

    public static TracePoint model(String sender, String model, String operation, String text,
                                   String label, Map<String, Object> data) {
        return new TracePoint(text, label, sender, Protocol.MODELL, model, operation,
                data, Instant.now());
    }

    public static TracePoint internal(String sender, String operation, String text,
                                   String label, Map<String, Object> data) {
        return new TracePoint(text, label, sender, Protocol.INTERNAL, null, operation,
                data, Instant.now());
    }

    public static TracePoint plain(String text) {
        return new TracePoint(text, null, null, Protocol.INTERNAL, null, null, Map.of(),
                Instant.now());
    }

    public TracePoint withSenderIfAbsent(String fallback) {
        return sender == null
                ? new TracePoint(text, label, fallback, protocol, peer, operation, data,
                        timestamp)
                : this;
    }

    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("text", text);
        if (label != null) {
            map.put("label", label);
        }
        map.put("protocol", protocol.name());
        if (timestamp != null) {
            map.put("timestamp", timestamp.toString());
        }
        if (sender != null) {
            map.put("sender", sender);
        }
        if (peer != null) {
            map.put("peer", peer);
        }
        if (operation != null) {
            map.put("operation", operation);
        }
        if (!data.isEmpty()) {
            map.put("data", data);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    public static TracePoint fromMap(Map<String, Object> map) {
        if (map == null || !(map.get("text") instanceof String text) || text.isBlank()) {
            return null;
        }
        Protocol protocol = Protocol.INTERNAL;
        if (map.get("protocol") instanceof String name) {
            try {
                protocol = Protocol.valueOf(name);
            } catch (IllegalArgumentException _) {
            }
        }
        Map<String, Object> data = map.get("data") instanceof Map<?, ?> raw
                ? (Map<String, Object>) raw
                : Map.of();
        Instant timestamp = null;
        if (map.get("timestamp") instanceof String when) {
            try {
                timestamp = Instant.parse(when);
            } catch (DateTimeParseException _) {
            }
        }
        return new TracePoint(text,
                map.get("label") instanceof String b ? b : null,
                map.get("sender") instanceof String a ? a : null,
                protocol,
                map.get("peer") instanceof String g ? g : null,
                map.get("operation") instanceof String v ? v : null,
                data,
                timestamp);
    }

    public static String truncate(String value) {
        if (value == null || value.length() <= MAX_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_LENGTH) + TRUNCATED;
    }

    private static Map<String, Object> unmodifiable(Map<String, Object> data) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }
}
