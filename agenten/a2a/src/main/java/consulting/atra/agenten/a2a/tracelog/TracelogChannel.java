package consulting.atra.agenten.a2a.tracelog;

import consulting.atra.agenten.a2a.agent.StatusChannel;
import consulting.atra.agenten.a2a.agent.TracePoint;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TracelogChannel {

    private TracelogChannel() {
    }

    public static StatusChannel wrap(StatusChannel delegate, Tracelog tracelog,
                                     String context) {
        if (tracelog == null || !tracelog.isEnabled()) {
            return delegate;
        }
        return point -> {
            tracelog.write("trace", context, fields(point));
            delegate.report(point);
        };
    }

    private static Map<String, Object> fields(TracePoint point) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("protocol", point.protocol().name());
        fields.put("sender", point.sender());
        fields.put("peer", point.peer());
        fields.put("operation", point.operation());
        fields.put("label", point.label());
        fields.put("text", point.text());
        fields.put("data", point.data());
        return fields;
    }
}
