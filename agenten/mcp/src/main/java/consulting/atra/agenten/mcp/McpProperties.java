package consulting.atra.agenten.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "agenten.mcp")
public record McpProperties(
        Connection kernsystem, Connection rechenkern, Connection wissen, Duration timeout) {

    public McpProperties {
        if (kernsystem == null) {
            throw new IllegalStateException("agenten.mcp braucht kernsystem, rechenkern und "
                    + "wissen: Die Agenten fuehren keine Fachlogik und koennen ohne alle "
                    + "drei Server nichts beantworten");
        }
        if (kernsystem.apiKey() == null || kernsystem.apiKey().isBlank()) {
            throw new IllegalStateException("agenten.mcp.kernsystem.api-key fehlt");
        }
        timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
    }

    public record Connection(String url, String endpoint, String apiKey) {

        public Connection {
            if (url == null || url.isBlank()) {
                throw new IllegalStateException("url fehlt");
            }
            endpoint = endpoint == null || endpoint.isBlank() ? "/mcp" : endpoint;
        }
    }
}
