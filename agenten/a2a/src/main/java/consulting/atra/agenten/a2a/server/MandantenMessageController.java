package consulting.atra.agenten.a2a.server;

import io.a2a.server.requesthandlers.RequestHandler;
import io.a2a.spec.EventKind;
import io.a2a.spec.JSONRPCError;
import io.a2a.spec.SendMessageRequest;
import io.a2a.spec.SendMessageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.a2a.server.controller.MessageController;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

public class MandantenMessageController extends MessageController {

    private static final Logger log = LoggerFactory.getLogger(MandantenMessageController.class);

    private final RequestHandler requestHandler;

    public MandantenMessageController(RequestHandler requestHandler) {
        super(requestHandler);
        this.requestHandler = requestHandler;
    }

    @Override
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public SendMessageResponse sendMessage(@RequestBody SendMessageRequest request) {
        try {
            EventKind result = requestHandler.onMessageSend(request.getParams(),
                    Headers.context());
            return new SendMessageResponse(request.getId(), result);
        } catch (JSONRPCError error) {
            return new SendMessageResponse(request.getId(), error);
        } catch (Exception exception) {
            log.error("Unerwarteter Fehler bei message/send", exception);
            return new SendMessageResponse(request.getId(),
                    new JSONRPCError(-32603, "Internal error: " + exception.getMessage(), null));
        }
    }
}
