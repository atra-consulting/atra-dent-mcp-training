package consulting.atra.agenten.schadensfall;

import consulting.atra.agenten.a2a.client.AgentLookup;
import consulting.atra.agenten.a2a.client.SdkAgentClient;
import consulting.atra.agenten.a2a.client.SecurityHeaders;
import consulting.atra.agenten.a2a.client.SubagentCatalog;
import consulting.atra.agenten.a2a.client.SubagentProperties;
import consulting.atra.agenten.a2a.server.AgentCardLoader;
import consulting.atra.agenten.a2a.server.A2aAgentExecutor;
import consulting.atra.agenten.a2a.server.MandantenMessageController;
import consulting.atra.agenten.a2a.server.StreamController;
import consulting.atra.agenten.a2a.tracelog.Tracelog;
import consulting.atra.agenten.schadensfall.agent.Schadensfallagent;
import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.requesthandlers.RequestHandler;
import io.a2a.spec.AgentCard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SubagentProperties.class)
class A2aServerConfiguration {

    @Bean
    AgentCard agentCard(@Value("${agenten.card}") String card,
                        @Value("${agenten.base-url}") String basisUrl) {
        return AgentCardLoader.load(Path.of(card), basisUrl);
    }

    @Bean
    Tracelog tracelog() {
        return Tracelog.fromEnvironment(Schadensfallagent.SENDER);
    }

    @Bean
    AgentExecutor agentExecutor(Schadensfallagent agent, Tracelog tracelog,
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

    @Bean
    SubagentCatalog subagentCatalog(SubagentProperties properties) {
        return new SubagentCatalog(properties.wired(), properties.cardTimeout());
    }

    @Bean
    AgentLookup subagents(SubagentCatalog catalog, SubagentProperties properties) {
        return skillId -> catalog.withSkill(skillId)
                .map(card -> new SdkAgentClient(card.name(), Schadensfallagent.SENDER, card,
                        properties.timeout(),
                        SecurityHeaders.forCard(card, catalog.apiKey(card.name()))));
    }
}
