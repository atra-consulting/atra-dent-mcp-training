package consulting.atra.agenten.schadensfall.check;

import consulting.atra.agenten.a2a.client.AgentLookup;
import consulting.atra.agenten.mcp.McpProperties;
import consulting.atra.agenten.mcp.McpToolSource;
import consulting.atra.agenten.mcp.ToolSource;
import consulting.atra.agenten.model.ModelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(McpProperties.class)
public class CheckConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(ToolSource.class)
    McpToolSource toolSource(McpProperties properties) {
        McpToolSource source = new McpToolSource(properties);
        source.check(ToolSelection.allExpected());
        return source;
    }

    @Bean
    ApprovalGuard freigabeGuard(SchadensfallProperties properties) {
        return new ApprovalGuard(properties.freigabeThreshold());
    }

    @Bean
    SchadensfallCheck caseCheck(ModelClient modellClient,
                              @Value("${spring.ai.google.genai.chat.model}") String modell,
                              ToolSource toolSource, AgentLookup subagents,
                              ApprovalGuard guard, ObjectMapper mapper, Clock clock) {
        return new SchadensfallCheck(modellClient, modell, toolSource, subagents, guard,
                mapper, clock);
    }

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock clock() {
        return Clock.systemUTC();
    }
}
