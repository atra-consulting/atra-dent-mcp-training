package consulting.atra.agenten.a2a.agent;

@FunctionalInterface
public interface StatusChannel {

    void report(TracePoint tracePoint);

    static StatusChannel discarded() {
        return _ -> {
        };
    }
}
