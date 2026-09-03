package consulting.atra.agenten.beratung;

import consulting.atra.agenten.a2a.server.A2aAgentExecutor;
import consulting.atra.agenten.a2a.server.AgentCardLoader;
import consulting.atra.agenten.a2a.server.MandantenMessageController;
import consulting.atra.agenten.a2a.server.StreamController;
import consulting.atra.agenten.a2a.tracelog.Tracelog;
import consulting.atra.agenten.beratung.agent.Beratungsagent;
import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.requesthandlers.RequestHandler;
import io.a2a.spec.AgentCard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
class A2aServerConfiguration {

    @Bean
    AgentCard agentCard(@Value("${agenten.card}") String card,
                        @Value("${agenten.base-url}") String basisUrl) {
        return AgentCardLoader.load(Path.of(card), basisUrl);
    }

    @Bean
    Tracelog tracelog() {
        return Tracelog.fromEnvironment(Beratungsagent.SENDER);
    }

    @Bean
    AgentExecutor agentExecutor(Beratungsagent agent, Tracelog tracelog,
                                @Value("${agenten.max-length:2000}") int maxLength) {
        return new A2aAgentExecutor(agent, maxLength, tracelog);
    }

    @Bean
    MandantenMessageController mandantenMessageController(RequestHandler handler) {
        return new MandantenMessageController(handler);
    }

    @Bean
    StreamController streamController(RequestHandler handler) {
        return new StreamController(handler);
    }
}
