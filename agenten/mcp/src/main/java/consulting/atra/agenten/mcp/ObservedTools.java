package consulting.atra.agenten.mcp;

import consulting.atra.agenten.a2a.agent.TracePoint;
import consulting.atra.agenten.a2a.agent.StatusChannel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ObservedTools {

    public static final int MAX_COUNT = 12;

    public record Meldung(String text, String label) {
    }

    private final StatusChannel channel;
    private final String sender;
    private final ToolMapping mapping;
    private final ToolMessages messages;
    private final List<Toolaufruf> calls = new ArrayList<>();

    private int attempts;

    public ObservedTools(StatusChannel channel, String sender,
                            ToolMapping mapping, ToolMessages meldungen) {
        this.channel = Objects.requireNonNull(channel, "channel");
        this.sender = Objects.requireNonNull(sender, "absender");
        this.mapping = Objects.requireNonNull(mapping, "zuordnung");
        this.messages = Objects.requireNonNull(meldungen, "meldungen");
    }

    public List<ToolCallback> wrap(List<ToolCallback> tools) {
        return tools.stream().map(delegate -> (ToolCallback) new Wrapper(delegate, this)).toList();
    }

    public synchronized List<Toolaufruf> calls() {
        return List.copyOf(calls);
    }

    public synchronized int attempts() {
        return attempts;
    }

    public synchronized List<String> resultsOf(String tool) {
        return callsOf(tool).stream().map(Toolaufruf::content).toList();
    }

    public synchronized List<Toolaufruf> callsOf(String tool) {
        return calls.stream().filter(call -> call.tool().equals(tool)).toList();
    }

    private synchronized void record(String tool, String arguments, String result) {
        calls.add(new Toolaufruf(tool, arguments, result));
    }

    private synchronized int checkLimit(String tool) {
        if (attempts >= MAX_COUNT) {
            throw new ToolschleifeAusgeufertException(MAX_COUNT, tool);
        }
        return ++attempts;
    }

    private void report(String tool, int number, String input) {
        Map<String, Object> data = new LinkedHashMap<>();
        Long kundenId = MandantContext.caller();
        if (kundenId != null && McpToolSource.KERNSYSTEM.equals(server(tool))) {
            data.put("header x-kunden-id", kundenId);
        }
        data.put("call", number);
        data.put("arguments", TracePoint.truncate(input));
        Meldung message = message(tool);
        channel.report(point(tool, message, data));
    }

    private void answered(String tool, int number, String result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("call", number);
        data.put("result", TracePoint.truncate(result));
        Meldung message = message(tool);
        channel.report(point(tool, message, data));
    }

    private void failed(String tool, int number, RuntimeException failure) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("call", number);
        data.put("failed", TracePoint.truncate(String.valueOf(failure.getMessage())));
        Meldung message = message(tool);
        channel.report(point(tool, message, data));
    }

    private TracePoint point(String tool, Meldung message, Map<String, Object> data) {
        String server = server(tool);
        return server != null
                ? TracePoint.tool(sender, server, tool, message.text(), message.label(), data)
                : TracePoint.internal(sender, tool, message.text(), message.label(), data);
    }

    private Meldung message(String tool) {
        Meldung message = messages.forTool(tool);
        return message != null ? message : new Meldung("rufe " + tool + " auf", null);
    }

    private String server(String tool) {
        return mapping.serverOf(tool);
    }

    public record Toolaufruf(String tool, String arguments, String result) {

        public String content() {
            return McpContent.unpack(result);
        }
    }

    public static class ToolschleifeAusgeufertException extends RuntimeException {

        public ToolschleifeAusgeufertException(int grenze, String tool) {
            super("Die Tool-Schleife hat " + grenze + " Aufrufe erreicht und wurde bei '"
                    + tool + "' abgebrochen. Ein belegter Zug braucht typisch drei bis "
                    + "sechs Aufrufe; wer hier ankommt, hat sich verrannt.");
        }
    }

    private record Wrapper(ToolCallback delegate, ObservedTools store) implements ToolCallback {

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public String call(String input) {
            return call(input, null);
        }

        @Override
        public String call(String input, ToolContext context) {
            String name = delegate.getToolDefinition().name();
            int number = store.checkLimit(name);
            store.report(name, number, input);
            String result;
            try {
                result = context == null ? delegate.call(input) : delegate.call(input, context);
            } catch (RuntimeException failure) {
                store.failed(name, number, failure);
                throw failure;
            }
            store.record(name, input, result);
            store.answered(name, number, result);
            return result;
        }
    }
}
