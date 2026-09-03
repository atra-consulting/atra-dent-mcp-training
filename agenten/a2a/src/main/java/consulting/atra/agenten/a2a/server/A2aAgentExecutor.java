package consulting.atra.agenten.a2a.server;

import consulting.atra.agenten.a2a.agent.AgentResult;
import consulting.atra.agenten.a2a.agent.Agent;
import consulting.atra.agenten.a2a.agent.StatusChannel;
import consulting.atra.agenten.a2a.tracelog.TracelogChannel;
import consulting.atra.agenten.a2a.tracelog.Tracelog;
import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.events.EventQueue;
import io.a2a.server.tasks.TaskUpdater;
import io.a2a.spec.DataPart;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.TaskState;
import io.a2a.spec.TextPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class A2aAgentExecutor implements AgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(A2aAgentExecutor.class);

    private final Agent agent;
    private final int maxLength;
    private final Tracelog tracelog;

    public A2aAgentExecutor(Agent agent, int maxLength) {
        this(agent, maxLength, Tracelog.OFF);
    }

    public A2aAgentExecutor(Agent agent, int maxLength,
                              Tracelog tracelog) {
        this.agent = Objects.requireNonNull(agent, "agent");
        this.maxLength = maxLength;
        this.tracelog = Objects.requireNonNull(tracelog, "tracelog");
    }

    @Override
    public void execute(RequestContext context, EventQueue eventQueue) throws JSONRPCError {
        TaskUpdater updater = new TaskUpdater(context, eventQueue);
        if (context.getTask() == null) {
            updater.submit();
        }
        updater.startWork();

        String text = context.getUserInput("\n").strip();
        if (text.length() > maxLength) {
            finish(updater, AgentResult.rejected("Die Nachricht ist mit "
                    + text.length() + " Zeichen zu lang. Ausgewertet werden höchstens "
                    + maxLength + " Zeichen; bitte fassen Sie sich kürzer."),
                    context.getContextId());
            return;
        }

        StatusChannel channel = point -> updater.updateStatus(TaskState.WORKING,
                updater.newAgentMessage(
                        List.of(new TextPart(point.text()), new DataPart(point.asMap())),
                        null),
                false);

        channel = TracelogChannel.wrap(channel, tracelog, context.getContextId());
        tracelog.write("request", context.getContextId(),
                Map.of("text", text, "data", data(context.getMessage())));

        AgentResult result;
        try {
            result = agent.run(context.getContextId(), text, data(context.getMessage()),
                    kundenId(context), channel);
        } catch (RuntimeException exception) {
            log.error("Agent step failed", exception);
            tracelog.write("error", context.getContextId(),
                    Map.of("exception", exception.getClass().getName(),
                            "message", String.valueOf(exception.getMessage())));
            updater.fail(updater.newAgentMessage(
                    List.of(new TextPart("Der Dienst konnte die Nachricht nicht bearbeiten.")), null));
            return;
        }
        finish(updater, result, context.getContextId());
    }

    @Override
    public void cancel(RequestContext context, EventQueue eventQueue) throws JSONRPCError {
        new TaskUpdater(context, eventQueue).cancel();
        agent.finish(context.getContextId());
    }

    private void finish(TaskUpdater updater, AgentResult result, String context) {
        tracelog.write("outcome", context,
                Map.of("outcome", result.outcome().name(),
                        "answer", String.valueOf(result.answerText()),
                        "content", result.content()));
        switch (result.outcome()) {
            case COMPLETED -> {
                List<Part<?>> parts = new ArrayList<>();
                parts.add(new TextPart(result.answerText()));
                if (!result.content().isEmpty()) {
                    parts.add(new DataPart(result.content()));
                }
                updater.addArtifact(parts, null, result.artifactName(), null);
                updater.complete();
            }
            case INPUT_REQUIRED -> updater.requiresInput(updater.newAgentMessage(
                    List.of(new TextPart(result.answerText())), null), true);
            case REJECTED -> updater.reject(updater.newAgentMessage(
                    List.of(new TextPart(result.answerText())), null));
            case FAILED -> updater.fail(updater.newAgentMessage(
                    List.of(new TextPart(result.answerText())), null));
        }
    }

    private static Long kundenId(RequestContext context) {
        if (context.getCallContext() == null) {
            return null;
        }
        Object value = context.getCallContext().getState().get(Headers.KUNDEN_ID_KEY);
        return value instanceof Long kundenId ? kundenId : null;
    }

    private static Map<String, Object> data(Message message) {
        if (message == null || message.getParts() == null) {
            return Map.of();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        for (Part<?> part : message.getParts()) {
            if (part instanceof DataPart dataPart && dataPart.getData() != null) {
                data.putAll(dataPart.getData());
            }
        }
        return data;
    }
}
