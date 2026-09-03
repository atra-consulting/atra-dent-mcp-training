package de.atra.kernsystem.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStatelessServerTransport;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.ServerRequest;

import java.util.Map;

import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
class McpConfiguration {

    private static final Logger log = LoggerFactory.getLogger(McpConfiguration.class);

    static final String HEADER = "x-kunden-id";

    static final String KUNDEN_ID_KEY = "kundenId";

    @Bean
    WebMvcStatelessServerTransport webMvcStatelessServerTransport(
            @Qualifier("mcpServerJsonMapper") JsonMapper jsonMapper,
            McpServerStreamableHttpProperties properties) {

        return WebMvcStatelessServerTransport.builder()
                .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
                .messageEndpoint(properties.getMcpEndpoint())
                .contextExtractor(kundennummerFromHeader())
                .build();
    }

    static McpTransportContextExtractor<ServerRequest> kundennummerFromHeader() {
        return request -> {
            String raw = request.servletRequest().getHeader(HEADER);
            if (raw == null || raw.isBlank()) {
                return McpTransportContext.EMPTY;
            }
            try {
                long kundenId = Long.parseLong(raw.trim());
                if (kundenId < 1) {
                    log.debug("Header {} ist keine gueltige Kundennummer: {}", HEADER, raw);
                    return McpTransportContext.EMPTY;
                }
                return McpTransportContext.create(Map.of(KUNDEN_ID_KEY, kundenId));
            } catch (NumberFormatException _) {
                log.debug("Header {} ist keine Zahl: {}", HEADER, raw);
                return McpTransportContext.EMPTY;
            }
        };
    }
}
