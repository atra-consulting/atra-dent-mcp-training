package consulting.atra.agenten.beratung.tools;

import consulting.atra.agenten.mcp.McpProperties;
import consulting.atra.agenten.mcp.McpToolSource;
import consulting.atra.agenten.mcp.ToolSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(McpProperties.class)
public class ToolConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(ToolSource.class)
    McpToolSource toolSource(McpProperties properties) {
        McpToolSource source = new McpToolSource(properties);
        source.check(Set.copyOf(ToolSelection.allExpected()));
        return source;
    }
}
