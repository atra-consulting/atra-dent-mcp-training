package consulting.atra.agenten.a2a.client;

import consulting.atra.agenten.a2a.agent.TracePoint;
import consulting.atra.agenten.a2a.agent.StatusChannel;
import io.a2a.A2A;
import io.a2a.client.Client;
import io.a2a.client.ClientEvent;
import io.a2a.client.MessageEvent;
import io.a2a.client.TaskEvent;
import io.a2a.client.TaskUpdateEvent;
import io.a2a.client.config.ClientConfig;
import io.a2a.client.transport.jsonrpc.JSONRPCTransport;
import io.a2a.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import io.a2a.client.transport.spi.interceptors.ClientCallContext;
import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.DataPart;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.Task;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatus;
import io.a2a.spec.TextPart;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

public class SdkAgentClient implements AgentClient {

    static final String STREAMING_METHOD = "message/stream";

    static final String BLOCKING_METHOD = "message/send";

    private static final String MANDANTEN_HEADER = "x-kunden-id";

    private final String name;
    private final String sender;
    private final AgentCard card;
    private final Duration timeout;
    private final Map<String, String> extraHeaders;
    private final String method;

    public SdkAgentClient(String name, String sender, AgentCard card, Duration timeout) {
        this(name, sender, card, timeout, Map.of());
    }

    public SdkAgentClient(String name, String sender, AgentCard card, Duration timeout,
                          Map<String, String> zusatzHeader) {
        this.name = Objects.requireNonNull(name, "name");
        this.sender = Objects.requireNonNull(sender, "absender");
        this.card = Objects.requireNonNull(card, "card");
        this.timeout = Objects.requireNonNull(timeout, "frist");
        this.extraHeaders = Map.copyOf(Objects.requireNonNull(zusatzHeader, "zusatzHeader"));
        this.method = method(card.capabilities());
    }

    static String method(AgentCapabilities capabilities) {
        return capabilities != null && capabilities.streaming()
                ? STREAMING_METHOD : BLOCKING_METHOD;
    }

    @Override
    public SubagentResponse send(String contextId, String taskId, String text, Map<String, Object> data,
                                    Long kundenId, StatusChannel status) {
        status.report(outboundHop(contextId, taskId, text, data, kundenId));

        CompletableFuture<Task> completed = new CompletableFuture<>();
        BiConsumer<ClientEvent, AgentCard> consumer = consumer(completed, status);

        Client client = null;
        try {
            client = Client.builder(card)
                    .clientConfig(new ClientConfig.Builder().build())
                    .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfigBuilder()
                            .addInterceptor(new HeaderInterceptor()))
                    .addConsumer(consumer)
                    .streamingErrorHandler(completed::completeExceptionally)
                    .build();

            Message message = message(text, data, contextId, taskId);
            client.sendMessage(message, callContext(kundenId));
            Task task = completed.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            status.report(returnHop(task));
            return SubagentResponse.of(task, name);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AgentUnreachableException(name, "der Aufruf wurde interrupted",
                    interrupted);
        } catch (TimeoutException expired) {
            throw new AgentUnreachableException(name, timeoutMessage(timeout),
                    expired);
        } catch (AgentUnreachableException rethrown) {
            throw rethrown;
        } catch (Exception exception) {
            if (exception.getCause() instanceof AgentUnreachableException cause) {
                status.report(unreachable());
                throw cause;
            }
            status.report(unreachable());
            throw new AgentUnreachableException(name, exception.getMessage(), exception);
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    TracePoint outboundHop(String contextId, String taskId, String text,
                           Map<String, Object> data, Long kundenId) {
        return TracePoint.hop(sender, name, method,
                "vertiefe mich in Ihr Anliegen", "Anliegen weiterreichen",
                across(contextId, taskId, text, data, kundenId));
    }

    ClientCallContext callContext(Long kundenId) {
        if (kundenId == null && extraHeaders.isEmpty()) {
            return null;
        }
        Map<String, String> headers = new LinkedHashMap<>(extraHeaders);
        if (kundenId != null) {
            headers.put(MANDANTEN_HEADER, String.valueOf(kundenId));
        }
        return new ClientCallContext(Map.of(), headers);
    }

    BiConsumer<ClientEvent, AgentCard> consumer(CompletableFuture<Task> completed,
                                                StatusChannel status) {
        StatusChannel relay = RelayChannel.wrap(status);
        return (event, _) -> handle(event, completed, relay);
    }

    void handle(ClientEvent event, CompletableFuture<Task> completed,
                     StatusChannel status) {
        if (event instanceof TaskUpdateEvent aktualisierung) {
            Task task = aktualisierung.getTask();
            TaskStatus taskStatus = task.getStatus();
            if (taskStatus.state() == TaskState.WORKING && taskStatus.message() != null) {
                report(taskStatus.message(), status);
            }
            if (endgueltig(taskStatus.state())) {
                completed.complete(task);
            }
        } else if (event instanceof TaskEvent taskEvent) {
            Task task = taskEvent.getTask();
            if (endgueltig(task.getStatus().state())) {
                completed.complete(task);
            }
        } else if (event instanceof MessageEvent _) {
            completed.completeExceptionally(new AgentUnreachableException(name,
                    "das Gegenueber antwortete mit einer Message statt einem Task"));
        }
    }

    private void report(Message message, StatusChannel status) {
        for (Part<?> part : message.getParts() == null ? List.<Part<?>>of() : message.getParts()) {
            if (part instanceof DataPart data) {
                TracePoint reported = TracePoint.fromMap(data.getData());
                if (reported != null) {
                    status.report(reported.withSenderIfAbsent(name));
                    return;
                }
            }
        }
        String text = SubagentResponse.text(message).strip();
        if (!text.isBlank()) {
            status.report(TracePoint.internal(name, null, text, null, Map.of()));
        }
    }

    static Message message(String text, Map<String, Object> data, String contextId, String taskId) {
        Map<String, Object> gefiltert = withoutNulls(data);
        if (gefiltert.isEmpty()) {
            return A2A.createUserTextMessage(text, contextId, taskId);
        }
        List<Part<?>> parts = List.of(new TextPart(text), new DataPart(gefiltert));
        Message.Builder builder = new Message.Builder()
                .role(Message.Role.USER)
                .parts(parts)
                .messageId(UUID.randomUUID().toString());
        if (contextId != null) {
            builder.contextId(contextId);
        }
        if (taskId != null) {
            builder.taskId(taskId);
        }
        return builder.build();
    }

    static Map<String, Object> across(String contextId, String taskId, String text,
                                      Map<String, Object> data, Long kundenId) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("message", TracePoint.truncate(text));
        fields.put("belegAttached", !withoutNulls(data).isEmpty());
        fields.put("loggedIn", kundenId != null);
        if (kundenId != null) {
            fields.put("header x-kunden-id", kundenId);
        }
        if (contextId != null) {
            fields.put("contextId", contextId);
        }
        if (taskId == null) {
            fields.put("newContext", true);
        } else {
            fields.put("taskId", taskId);
        }
        return fields;
    }

    private static Map<String, Object> withoutNulls(Map<String, Object> data) {
        if (data == null) {
            return Map.of();
        }
        Map<String, Object> gefiltert = new LinkedHashMap<>();
        data.forEach((key, value) -> {
            if (value != null) {
                gefiltert.put(key, value);
            }
        });
        return gefiltert;
    }

    TracePoint returnHop(Task task) {
        Map<String, Object> data = new LinkedHashMap<>();
        TaskState state = task.getStatus().state();
        data.put("state", state.name());

        SubagentResponse answer = SubagentResponse.of(task, name);
        if (!answer.text().isBlank()) {
            data.put("answer", TracePoint.truncate(answer.text()));
        }
        if (answer.artifactName() != null) {
            data.put("artifact", answer.artifactName());
        }
        data.put("taskId", task.getId());

        boolean rueckfrage = state == TaskState.INPUT_REQUIRED;
        String text = rueckfrage ? "habe noch eine Frage an Sie" : "formuliere meine Antwort";
        String label = rueckfrage ? "Rückfrage empfangen" : "Antwort empfangen";
        return TracePoint.hop(name, sender, method, text, label, data);
    }

    TracePoint unreachable() {
        return TracePoint.hop(sender, name, method,
                "komme an meine Unterlagen gerade nicht heran", "Agent nicht erreichbar",
                Map.of("reachable", false));
    }

    static String timeoutMessage(Duration timeout) {
        return "der Agent hat innerhalb von " + timeout.toMillis() + " ms nicht geantwortet";
    }

    private static boolean endgueltig(TaskState state) {
        return state == TaskState.COMPLETED || state == TaskState.FAILED
                || state == TaskState.REJECTED || state == TaskState.CANCELED
                || state == TaskState.INPUT_REQUIRED;
    }
}
