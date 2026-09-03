package consulting.atra.agenten.orchestrator;

import consulting.atra.agenten.a2a.client.SdkAgentClient;
import consulting.atra.agenten.a2a.client.SecurityHeaders;
import consulting.atra.agenten.a2a.server.A2aAgentExecutor;
import consulting.atra.agenten.a2a.server.AgentCardLoader;
import consulting.atra.agenten.a2a.server.MandantenMessageController;
import consulting.atra.agenten.a2a.server.StreamController;
import consulting.atra.agenten.a2a.tracelog.Tracelog;
import consulting.atra.agenten.a2a.client.SubagentCatalog;
import consulting.atra.agenten.a2a.client.SubagentProperties;
import consulting.atra.agenten.mcp.McpProperties;
import consulting.atra.agenten.mcp.McpToolSource;
import consulting.atra.agenten.mcp.ToolSource;
import consulting.atra.agenten.orchestrator.agent.*;
import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.requesthandlers.RequestHandler;
import io.a2a.spec.AgentCard;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({SubagentProperties.class, McpProperties.class})
class OrchestratorConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(ToolSource.class)
    McpToolSource toolSource(McpProperties properties) {
        McpToolSource source = new McpToolSource(properties);
        source.check(Set.of(KundendatenTool.SOURCE));
        return source;
    }

    @Bean
    KundendatenTool kundendatenTool(ToolSource tools) {
        return new KundendatenTool(tools);
    }

    @Bean
    ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder().maxMessages(16).build();
    }

    @Bean
    SubagentCatalog subagentCatalog(SubagentProperties properties) {
        return new SubagentCatalog(properties.wired(), properties.cardTimeout());
    }

    @Bean
    ThreadRegistry threadRegistry() {
        return new ThreadRegistry();
    }

    @Bean
    AgentTool agentTool(SubagentCatalog catalog, ThreadRegistry threadRegistry,
                                    SubagentProperties properties) {
        return new AgentTool(catalog, threadRegistry,
                card -> new SdkAgentClient(card.name(), Orchestrator.SENDER, card,
                        properties.timeout(),
                        SecurityHeaders.forCard(card, catalog.apiKey(card.name()))));
    }

    @Bean
    ChatClient chatClient(ChatModel modell, ChatMemory chatMemory,
                          @Value("${spring.ai.google.genai.chat.model}") String modellId) {
        return ChatClient.builder(modell)
                .defaultOptions(GoogleGenAiChatOptions.builder().model(modellId))
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    @Bean
    Orchestrator orchestrator(ChatClient chatClient, AgentTool tool,
                          KundendatenTool kundendatenTool, ThreadRegistry threadRegistry,
                          SubagentCatalog catalog,
                          @Value("${spring.ai.google.genai.chat.model}") String modellId) {
        return new Orchestrator(chatClient, tool, kundendatenTool, threadRegistry, catalog,
                modellId);
    }


    @Bean
    AgentCard agentCard(@Value("${agenten.card}") String card,
                        @Value("${agenten.base-url}") String basisUrl) {
        return AgentCardLoader.load(Path.of(card), basisUrl);
    }

    @Bean
    Tracelog tracelog() {
        return Tracelog.fromEnvironment(Orchestrator.SENDER);
    }

    @Bean
    AgentExecutor agentExecutor(Orchestrator orchestrator, Tracelog tracelog,
                                @Value("${agenten.max-length:2000}") int maxLength) {
        return new A2aAgentExecutor(orchestrator, maxLength, tracelog);
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
