package consulting.atra.agenten.orchestrator;

import consulting.atra.agenten.mcp.ToolSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;

@TestConfiguration(proxyBeanMethods = false)
public class TestToolSource {

    @Bean
    ToolSource toolSource() {
        return allowed -> List.of();
    }
}
