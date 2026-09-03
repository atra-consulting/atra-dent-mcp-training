package consulting.atra.agenten.a2a.server;

import io.a2a.server.ServerCallContext;
import io.a2a.server.requesthandlers.RequestHandler;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.SendStreamingMessageRequest;
import io.a2a.spec.SendStreamingMessageResponse;
import io.a2a.spec.StreamingEventKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Flow;

@RestController
public class StreamController {

    private static final Logger log = LoggerFactory.getLogger(StreamController.class);
    private static final long STREAM_TIMEOUT_MS = Duration.ofMinutes(2).toMillis();

    private final RequestHandler requestHandler;
    private final com.fasterxml.jackson.databind.ObjectMapper json =
            io.a2a.util.Utils.OBJECT_MAPPER;

    public StreamController(RequestHandler requestHandler) {
        this.requestHandler = Objects.requireNonNull(requestHandler, "requestHandler");
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody String body) throws Exception {
        SendStreamingMessageRequest request =
                json.readValue(body, SendStreamingMessageRequest.class);
        SseEmitter stream = new SseEmitter(STREAM_TIMEOUT_MS);

        Flow.Publisher<StreamingEventKind> events;
        try {
            ServerCallContext context = Headers.context();
            events = requestHandler.onMessageSendStream(request.getParams(), context);
        } catch (JSONRPCError error) {
            sendErrorAsSingleFrame(stream, request.getId(), error);
            return stream;
        } catch (Exception unerwartet) {
            log.error("Unerwarteter Fehler vor dem Start des Streams", unerwartet);
            sendErrorAsSingleFrame(stream, request.getId(),
                    new JSONRPCError(-32603, "Internal error: " + unerwartet.getMessage(), null));
            return stream;
        }

        Thread.ofVirtual().name("a2a-stream-" + request.getId()).start(
                () -> events.subscribe(new Flow.Subscriber<>() {
                    private Flow.Subscription subscription;

                    @Override public void onSubscribe(Flow.Subscription subscription) {
                        this.subscription = subscription;
                        subscription.request(1);
                    }

                    @Override public void onNext(StreamingEventKind event) {
                        try {
                            stream.send(SseEmitter.event().data(json.writeValueAsString(
                                    new SendStreamingMessageResponse(request.getId(), event)),
                                    MediaType.APPLICATION_JSON));
                            subscription.request(1);
                        } catch (Exception weg) {
                            log.debug("Stream-Empfaenger nicht mehr erreichbar", weg);
                            subscription.cancel();
                            stream.complete();
                        }
                    }

                    @Override public void onError(Throwable error) {
                        log.error("Fehler im Event-Stream", error);
                        stream.complete();
                    }

                    @Override public void onComplete() {
                        stream.complete();
                    }
                }));
        return stream;
    }

    private void sendErrorAsSingleFrame(SseEmitter stream, Object requestId,
                                               JSONRPCError error) {
        try {
            stream.send(SseEmitter.event().data(json.writeValueAsString(
                    new SendStreamingMessageResponse(requestId, error)),
                    MediaType.APPLICATION_JSON));
        } catch (Exception weg) {
            log.debug("Stream-Empfaenger vor der Fehlerantwort nicht mehr erreichbar", weg);
        } finally {
            stream.complete();
        }
    }
}
