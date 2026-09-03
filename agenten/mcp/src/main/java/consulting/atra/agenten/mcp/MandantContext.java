package consulting.atra.agenten.mcp;

import java.util.function.Supplier;

public final class MandantContext {

    private static final ThreadLocal<Long> CALLER = new ThreadLocal<>();

    private MandantContext() {
    }

    public static <T> T with(Long kundenId, Supplier<T> work) {
        Long previous = CALLER.get();
        set(kundenId);
        try {
            return work.get();
        } finally {
            set(previous);
        }
    }

    public static Long caller() {
        return CALLER.get();
    }

    private static void set(Long kundenId) {
        if (kundenId == null) {
            CALLER.remove();
        } else {
            CALLER.set(kundenId);
        }
    }
}
