package consulting.atra.agenten.a2a.client;

import consulting.atra.agenten.a2a.agent.StatusChannel;
import consulting.atra.agenten.a2a.agent.TracePoint;

import java.util.Objects;

public final class RelayChannel implements StatusChannel {

    private final StatusChannel delegate;

    private TracePoint last;

    private RelayChannel(StatusChannel delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public static StatusChannel wrap(StatusChannel delegate) {
        return new RelayChannel(delegate);
    }

    @Override
    public synchronized void report(TracePoint point) {
        if (point.equals(last)) {
            return;
        }
        last = point;
        delegate.report(point);
    }
}
