package consulting.atra.agenten.a2a.server;

import consulting.atra.agenten.a2a.agent.AgentResult;
import consulting.atra.agenten.a2a.agent.Agent;
import consulting.atra.agenten.a2a.agent.TracePoint;
import io.a2a.server.ServerCallContext;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.events.EventQueue;
import io.a2a.server.events.EventQueueItem;
import io.a2a.server.events.InMemoryQueueManager;
import io.a2a.server.tasks.TaskStateProvider;
import io.a2a.spec.Artifact;
import io.a2a.spec.DataPart;
import io.a2a.spec.Message;
import io.a2a.spec.MessageSendParams;
import io.a2a.spec.Part;
import io.a2a.spec.TaskArtifactUpdateEvent;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatusUpdateEvent;
import io.a2a.spec.TextPart;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class A2aAgentExecutorTest {

    private static final TaskStateProvider IMMER_AKTIV = new TaskStateProvider() {
        @Override
        public boolean isTaskActive(String taskId) {
            return true;
        }

        @Override
        public boolean isTaskFinalized(String taskId) {
            return false;
        }
    };

    private RequestContext context(String text, Long kundenId) {
        Message message = new Message.Builder()
                .messageId(UUID.randomUUID().toString())
                .role(Message.Role.USER)
                .parts(List.of(new TextPart(text)))
                .build();
        Map<String, Object> state = new HashMap<>();
        if (kundenId != null) {
            state.put(Headers.KUNDEN_ID_KEY, kundenId);
        }
        return new RequestContext.Builder()
                .setParams(new MessageSendParams.Builder().message(message).build())
                .setServerCallContext(new ServerCallContext(null, state, Set.of()))
                .build();
    }

    private EventQueue queue(RequestContext context) {
        return new InMemoryQueueManager(IMMER_AKTIV).createOrTap(context.getTaskId());
    }

    private List<Object> abgreifen(EventQueue queue) throws Exception {
        List<Object> events = new ArrayList<>();
        EventQueueItem entry;
        while ((entry = queue.dequeueEventItem(0)) != null) {
            events.add(entry.getEvent());
        }
        return events;
    }

    private static String text(Message message) {
        for (Part<?> part : message.getParts()) {
            if (part instanceof TextPart textteil) {
                return textteil.getText();
            }
        }
        throw new AssertionError("Nachricht ohne TextPart: " + message);
    }

    private static Map<String, Object> data(Message message) {
        for (Part<?> part : message.getParts()) {
            if (part instanceof DataPart dataPart) {
                return dataPart.getData();
            }
        }
        throw new AssertionError("Nachricht ohne DataPart: " + message);
    }

    @Test
    void completedYieldsAnArtifactAndCompleted() throws Exception {
        Agent agent = (contextId, text, data, kundenId, status) -> {
            status.report(TracePoint.plain("sehe nach"));
            return AgentResult.completed("beratung", "Antwort", Map.of("belege", List.of()));
        };
        RequestContext context = context("Frage", 10001L);
        EventQueue queue = queue(context);

        new A2aAgentExecutor(agent, 2000).execute(context, queue);

        List<Object> events = abgreifen(queue);
        assertThat(events).hasSize(5);

        assertThat(((TaskStatusUpdateEvent) events.get(0)).getStatus().state())
                .isEqualTo(TaskState.SUBMITTED);

        TaskStatusUpdateEvent working = (TaskStatusUpdateEvent) events.get(1);
        assertThat(working.getStatus().state()).isEqualTo(TaskState.WORKING);
        assertThat(working.getStatus().message()).isNull();

        TaskStatusUpdateEvent status = (TaskStatusUpdateEvent) events.get(2);
        assertThat(status.getStatus().state()).isEqualTo(TaskState.WORKING);
        assertThat(text(status.getStatus().message())).isEqualTo("sehe nach");
        assertThat(data(status.getStatus().message()))
                .containsEntry("text", "sehe nach")
                .containsEntry("protocol", "INTERNAL");

        TaskArtifactUpdateEvent artifactEvent = (TaskArtifactUpdateEvent) events.get(3);
        Artifact artifact = artifactEvent.getArtifact();
        assertThat(artifact.name()).isEqualTo("beratung");
        assertThat(artifact.parts()).hasSize(2);
        assertThat(artifact.parts().get(0)).isInstanceOf(TextPart.class);
        assertThat(((TextPart) artifact.parts().get(0)).getText()).isEqualTo("Antwort");
        assertThat(artifact.parts().get(1)).isInstanceOf(DataPart.class);
        assertThat(((DataPart) artifact.parts().get(1)).getData()).containsEntry("belege", List.of());

        TaskStatusUpdateEvent closing = (TaskStatusUpdateEvent) events.get(4);
        assertThat(closing.getStatus().state()).isEqualTo(TaskState.COMPLETED);
        assertThat(closing.isFinal()).isTrue();
    }

    @Test
    void inputRequiredYieldsInputRequiredAsFinal() throws Exception {
        Agent agent = (contextId, text, data, kundenId, status) ->
                AgentResult.inputRequired("Wie alt sind Sie?");
        RequestContext context = context("Ich möchte einen Tarif", null);
        EventQueue queue = queue(context);

        new A2aAgentExecutor(agent, 2000).execute(context, queue);

        List<Object> events = abgreifen(queue);
        assertThat(events).hasSize(3);

        TaskStatusUpdateEvent followUp = (TaskStatusUpdateEvent) events.get(2);
        assertThat(followUp.getStatus().state()).isEqualTo(TaskState.INPUT_REQUIRED);
        assertThat(followUp.isFinal()).isTrue();
        assertThat(text(followUp.getStatus().message())).isEqualTo("Wie alt sind Sie?");
    }

    @Test
    void anOverlongMessageIsRejected() throws Exception {
        Agent agent = (contextId, text, data, kundenId, status) -> {
            throw new AssertionError("Die Fachlogik haette bei zu langem Text nicht gerufen werden duerfen");
        };
        RequestContext context = context("0123456789", null);
        EventQueue queue = queue(context);

        new A2aAgentExecutor(agent, 5).execute(context, queue);

        List<Object> events = abgreifen(queue);
        assertThat(events).hasSize(3);

        TaskStatusUpdateEvent rejection = (TaskStatusUpdateEvent) events.get(2);
        assertThat(rejection.getStatus().state()).isEqualTo(TaskState.REJECTED);
        assertThat(rejection.isFinal()).isTrue();
        assertThat(text(rejection.getStatus().message())).contains("zu lang");
    }

    @Test
    void failedYieldsFailedAndNoRejection() throws Exception {
        Agent agent = (contextId, text, data, kundenId, status) ->
                AgentResult.failed("Ich kann Ihre Frage gerade nicht beantworten.");
        RequestContext context = context("Frage", 10001L);
        EventQueue queue = queue(context);

        new A2aAgentExecutor(agent, 2000).execute(context, queue);

        List<Object> events = abgreifen(queue);
        TaskStatusUpdateEvent last = (TaskStatusUpdateEvent) events.getLast();
        assertThat(last.getStatus().state()).isEqualTo(TaskState.FAILED);
        assertThat(text(last.getStatus().message()))
                .isEqualTo("Ich kann Ihre Frage gerade nicht beantworten.");
    }

    @Test
    void rejectedStaysRejected() throws Exception {
        Agent agent = (contextId, text, data, kundenId, status) ->
                AgentResult.rejected("Dafuer bin ich nicht zustaendig.");
        RequestContext context = context("Frage", 10001L);
        EventQueue queue = queue(context);

        new A2aAgentExecutor(agent, 2000).execute(context, queue);

        TaskStatusUpdateEvent last = (TaskStatusUpdateEvent) abgreifen(queue).getLast();
        assertThat(last.getStatus().state()).isEqualTo(TaskState.REJECTED);
    }

    @Test
    void kundenIdComesFromTheCallContextAndNeverFromTheText() {
        AtomicReference<Long> seen = new AtomicReference<>();
        Agent agent = (contextId, text, data, kundenId, status) -> {
            seen.set(kundenId);
            return AgentResult.completed("beratung", "Antwort", Map.of());
        };

        RequestContext withKunde = context("10001", 10001L);
        new A2aAgentExecutor(agent, 2000).execute(withKunde, queue(withKunde));
        assertThat(seen.get()).isEqualTo(10001L);

        RequestContext withoutKunde = context("Frage ohne Kundennummer im Text", null);
        new A2aAgentExecutor(agent, 2000).execute(withoutKunde, queue(withoutKunde));
        assertThat(seen.get()).isNull();
    }
}
