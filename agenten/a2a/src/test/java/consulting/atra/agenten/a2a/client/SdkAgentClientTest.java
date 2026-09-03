package consulting.atra.agenten.a2a.client;

import consulting.atra.agenten.a2a.agent.TracePoint;
import consulting.atra.agenten.a2a.server.AgentCardLoader;
import io.a2a.client.ClientEvent;
import io.a2a.client.MessageEvent;
import io.a2a.client.TaskEvent;
import io.a2a.client.TaskUpdateEvent;
import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.Artifact;
import io.a2a.spec.DataPart;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.Task;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatus;
import io.a2a.spec.TaskStatusUpdateEvent;
import io.a2a.spec.TextPart;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SdkAgentClientTest {

    private static final AgentCard CARD = AgentCardLoader.load(
            Path.of("src/test/resources/card.yaml"), "http://localhost:8084");

    private final SdkAgentClient client = new SdkAgentClient("Beratungsagent", "orchestrator", CARD, Duration.ofSeconds(5));

    @Test
    void aTaskEventWithANonFinalTaskDoesNotCloseTheFuture() {
        CompletableFuture<Task> completed = new CompletableFuture<>();
        Task notFinal = new Task.Builder()
                .id("aufgabe-1").contextId("kontext-1")
                .status(new TaskStatus(TaskState.SUBMITTED))
                .build();

        client.handle(new TaskEvent(notFinal), completed, message -> { });

        assertThat(completed).isNotDone();
    }

    @Test
    void aTaskEventWithAFinalTaskClosesTheFuture() {
        CompletableFuture<Task> completed = new CompletableFuture<>();
        Task finishedTask = new Task.Builder()
                .id("aufgabe-2").contextId("kontext-2")
                .status(new TaskStatus(TaskState.COMPLETED))
                .build();

        client.handle(new TaskEvent(finishedTask), completed, message -> { });

        assertThat(completed).isCompletedWithValue(finishedTask);
    }

    @Test
    void aTaskUpdateEventWithANonFinalStateDoesNotCloseTheFuture() {
        CompletableFuture<Task> completed = new CompletableFuture<>();
        Task task = new Task.Builder()
                .id("aufgabe-3").contextId("kontext-3")
                .status(new TaskStatus(TaskState.WORKING))
                .build();
        TaskStatusUpdateEvent update = new TaskStatusUpdateEvent.Builder()
                .taskId(task.getId()).contextId(task.getContextId())
                .status(new TaskStatus(TaskState.WORKING))
                .isFinal(false)
                .build();

        client.handle(new TaskUpdateEvent(task, update), completed, message -> { });

        assertThat(completed).isNotDone();
    }

    @Test
    void aTaskUpdateEventWithAFinalStateClosesTheFuture() {
        CompletableFuture<Task> completed = new CompletableFuture<>();
        Task task = new Task.Builder()
                .id("aufgabe-4").contextId("kontext-4")
                .status(new TaskStatus(TaskState.COMPLETED))
                .build();
        TaskStatusUpdateEvent update = new TaskStatusUpdateEvent.Builder()
                .taskId(task.getId()).contextId(task.getContextId())
                .status(new TaskStatus(TaskState.COMPLETED))
                .isFinal(true)
                .build();

        client.handle(new TaskUpdateEvent(task, update), completed, message -> { });

        assertThat(completed).isCompletedWithValue(task);
    }

    @Test
    void aStatusDeliveredTwiceIsRelayedOnce() {
        TracePoint point = TracePoint.tool("beratung", "wissen", "bedingungen_suchen",
                "schlage in den Bedingungen nach", "Bedingungen durchsuchen",
                Map.of("call", 1));
        Message message = new Message.Builder()
                .role(Message.Role.AGENT)
                .messageId("nachricht-2")
                .parts(List.of(new TextPart(point.text()), new DataPart(point.asMap())))
                .build();
        Task task = new Task.Builder()
                .id("aufgabe-5").contextId("kontext-5")
                .status(new TaskStatus(TaskState.WORKING, message, null))
                .build();
        TaskStatusUpdateEvent update = new TaskStatusUpdateEvent.Builder()
                .taskId(task.getId()).contextId(task.getContextId())
                .status(task.getStatus())
                .isFinal(false)
                .build();
        List<TracePoint> reported = new ArrayList<>();
        BiConsumer<ClientEvent, AgentCard> consumer =
                client.consumer(new CompletableFuture<>(), reported::add);

        consumer.accept(new TaskUpdateEvent(task, update), CARD);
        consumer.accept(new TaskUpdateEvent(task, update), CARD);

        assertThat(reported).containsExactly(point);
    }

    @Test
    void aMessageEventClosesTheFutureWithTheRealAgentName() {
        CompletableFuture<Task> completed = new CompletableFuture<>();
        Message message = new Message.Builder()
                .role(Message.Role.AGENT)
                .messageId("nachricht-1")
                .parts(List.of(new TextPart("nur eine Nachricht, kein Task")))
                .build();

        client.handle(new MessageEvent(message), completed, point -> { });

        assertThat(completed).isCompletedExceptionally();
        assertThatThrownBy(() -> completed.getNow(null))
                .isInstanceOf(CompletionException.class)
                .cause()
                .isInstanceOf(AgentUnreachableException.class)
                .extracting(cause -> ((AgentUnreachableException) cause).agent())
                .isEqualTo("Beratungsagent");
    }

    @Test
    void theTimeoutMessageNamesTheTimeoutInsteadOfNull() {
        String message = SdkAgentClient.timeoutMessage(Duration.ofMillis(300));

        assertThat(message).contains("300").doesNotContain("null");
    }

    @Test
    void theOutboundHopCarriesTheHeaderWhenSomeoneIsLoggedIn() {
        Map<String, Object> data = SdkAgentClient.across("k1", null, "Frage", null, 7L);

        assertThat(data)
                .containsEntry("loggedIn", true)
                .containsEntry("header x-kunden-id", 7L);
        assertThat(data.keySet()).containsExactly(
                "message", "belegAttached", "loggedIn", "header x-kunden-id",
                "contextId", "newContext");
    }

    @Test
    void theOutboundHopWithoutLoginCarriesNoHeader() {
        Map<String, Object> data = SdkAgentClient.across("k1", null, "Frage", null, null);

        assertThat(data)
                .containsEntry("loggedIn", false)
                .doesNotContainKey("header x-kunden-id");
    }

    @Test
    void theOutboundHopWithoutDataReportsNoBeleg() {
        Map<String, Object> data = SdkAgentClient.across("k1", null, "Frage", Map.of(), 7L);

        assertThat(data).containsEntry("belegAttached", false);
    }

    @Test
    void theOutboundHopWithDataReportsTheBeleg() {
        Map<String, Object> data = SdkAgentClient.across("k1", null, "Frage",
                Map.of("rechnung", Map.of("rechnungsnummer", "2026-4711")), 7L);

        assertThat(data).containsEntry("belegAttached", true);
    }

    @Test
    void theOutboundHopWithANullEntryReportsNoBeleg() {
        Map<String, Object> data = new HashMap<>();
        data.put("rechnung", null);

        Map<String, Object> fields = SdkAgentClient.across("k1", null, "Frage", data, 7L);

        assertThat(fields).containsEntry("belegAttached", false);
    }

    @Test
    void aMessageWithoutDataCarriesOnlyTheText() {
        Message message = SdkAgentClient.message("Frage", Map.of(), "kontext", "task");

        assertThat(message.getParts()).hasSize(1);
        assertThat(message.getParts().getFirst()).isInstanceOf(TextPart.class);
        assertThat(((TextPart) message.getParts().getFirst()).getText()).isEqualTo("Frage");
    }

    @Test
    void aMessageWithDataCarriesTextAndADataPart() {
        Map<String, Object> data = Map.of("rechnung", Map.of("rechnungsnummer", "2026-4711"));

        Message message = SdkAgentClient.message("Frage", data, "kontext", "task");

        assertThat(message.getParts()).hasSize(2);
        assertThat(message.getParts().get(0)).isInstanceOf(TextPart.class);
        assertThat(message.getParts().get(1)).isInstanceOf(DataPart.class);
        assertThat(((DataPart) message.getParts().get(1)).getData()).isEqualTo(data);
        assertThat(message.getContextId()).isEqualTo("kontext");
        assertThat(message.getTaskId()).isEqualTo("task");
    }

    @Test
    void aMessageWithANullEntryCarriesOnlyTheText() {
        Map<String, Object> data = new HashMap<>();
        data.put("rechnung", null);

        Message message = SdkAgentClient.message("Frage", data, "kontext", "task");

        assertThat(message.getParts()).hasSize(1);
        assertThat(message.getParts().getFirst()).isInstanceOf(TextPart.class);
    }

    @Test
    void theExtraHeaderGoesOutAlongsideTheKundennummer() {
        SdkAgentClient withKey = new SdkAgentClient("Arztservice", "schadensfall",
                CARD, Duration.ofSeconds(5), Map.of("x-api-key", "geheim"));

        assertThat(outgoingHeaders(withKey, 4711L))
                .containsEntry("x-api-key", "geheim")
                .containsEntry("x-kunden-id", "4711");
    }

    @Test
    void theExtraHeaderGoesOutEvenWithoutAKundennummer() {
        SdkAgentClient withKey = new SdkAgentClient("Arztservice", "schadensfall",
                CARD, Duration.ofSeconds(5), Map.of("x-api-key", "geheim"));

        assertThat(outgoingHeaders(withKey, null))
                .containsExactly(Map.entry("x-api-key", "geheim"));
    }

    @Test
    void withoutTheExtraHeaderOnlyTheKundennummerGoesOut() {
        assertThat(outgoingHeaders(client, 4711L))
                .containsExactly(Map.entry("x-kunden-id", "4711"));
    }

    @Test
    void withoutAnythingThereIsNoCallContext() {
        assertThat(client.callContext(null)).isNull();
    }

    @Test
    void aStreamingCardNamesMessageStreamOnEveryTracePoint() {
        SdkAgentClient streaming = new SdkAgentClient("Beratungsagent", "orchestrator",
                CARD, Duration.ofSeconds(5));

        assertThat(streaming.outboundHop("k", null, "Frage", null, null).operation())
                .isEqualTo("message/stream");
        assertThat(streaming.returnHop(completedTask(null)).operation())
                .isEqualTo("message/stream");
        assertThat(streaming.unreachable().operation()).isEqualTo("message/stream");
    }

    @Test
    void aNonStreamingCardNamesMessageSendOnEveryTracePoint() {
        SdkAgentClient blocking = new SdkAgentClient("Arztservice", "orchestrator",
                cardWith(new AgentCapabilities.Builder()
                        .streaming(false).pushNotifications(false).build()),
                Duration.ofSeconds(5));

        assertThat(blocking.outboundHop("k", null, "Frage", null, null).operation())
                .isEqualTo("message/send");
        assertThat(blocking.returnHop(completedTask(null)).operation())
                .isEqualTo("message/send");
        assertThat(blocking.unreachable().operation()).isEqualTo("message/send");
    }

    @Test
    void capabilitiesMissingFromTheCardCountAsNonStreaming() {
        assertThat(SdkAgentClient.method(null)).isEqualTo("message/send");
    }

    @Test
    void theReturnHopNamesTheArtifact() {
        assertThat(client.returnHop(completedTask("terminbestaetigung")).data())
                .containsEntry("artifact", "terminbestaetigung");
    }

    @Test
    void theReturnHopWithoutANamedArtifactCarriesNoArtifactKey() {
        assertThat(client.returnHop(completedTask(null)).data())
                .doesNotContainKey("artifact");
    }

    private static Task completedTask(String artifactName) {
        Task.Builder task = new Task.Builder()
                .id("aufgabe-9").contextId("kontext-9")
                .status(new TaskStatus(TaskState.COMPLETED));
        if (artifactName != null) {
            task.artifacts(List.of(new Artifact.Builder()
                    .artifactId("artefakt-9")
                    .name(artifactName)
                    .parts(List.<Part<?>>of(new TextPart("Der Termin steht.")))
                    .build()));
        }
        return task.build();
    }

    private static AgentCard cardWith(AgentCapabilities capabilities) {
        return new AgentCard.Builder()
                .name("Arztservice")
                .description("Ein fremder Agent")
                .version("3.1.0")
                .url("http://localhost:0/")
                .protocolVersion("0.3.0")
                .capabilities(capabilities)
                .defaultInputModes(List.of("text/plain"))
                .defaultOutputModes(List.of("text/plain"))
                .skills(List.of())
                .build();
    }

    private static Map<String, String> outgoingHeaders(SdkAgentClient client, Long kundenId) {
        return new HeaderInterceptor()
                .intercept("message/stream", "nutzlast", Map.of(), CARD,
                        client.callContext(kundenId))
                .getHeaders();
    }
}
