package consulting.atra.agenten.orchestrator.agent;

import consulting.atra.agenten.a2a.agent.StatusChannel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class TaskContext {

    public record Auftrag(Long kundenId, String contextId, StatusChannel status,
                          ArztauskunftCollector auskuenfte, Routings weiterleitungen,
                          Map<String, Object> data) {

        public Auftrag {
            data = data == null ? Map.of() : unmodifiable(data);
        }

        public Auftrag(Long kundenId, String contextId, StatusChannel status) {
            this(kundenId, contextId, status, new ArztauskunftCollector());
        }

        public Auftrag(Long kundenId, String contextId, StatusChannel status,
                       ArztauskunftCollector auskuenfte) {
            this(kundenId, contextId, status, auskuenfte, new Routings());
        }

        public Auftrag(Long kundenId, String contextId, StatusChannel status,
                       ArztauskunftCollector auskuenfte, Routings weiterleitungen) {
            this(kundenId, contextId, status, auskuenfte, weiterleitungen, Map.of());
        }

        private static Map<String, Object> unmodifiable(Map<String, Object> data) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(data));
        }
    }

    private static final ThreadLocal<Auftrag> CURRENT = new ThreadLocal<>();

    private TaskContext() {
    }

    public static <T> T with(Auftrag auftrag, Supplier<T> work) {
        CURRENT.set(auftrag);
        try {
            return work.get();
        } finally {
            CURRENT.remove();
        }
    }

    public static Auftrag current() {
        Auftrag task = CURRENT.get();
        if (task == null) {
            throw new IllegalStateException("Kein Auftragskontext - das Tool darf nur"
                    + " innerhalb von Orchestrator.fuehren laufen");
        }
        return task;
    }
}
