package consulting.atra.agenten.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.McpToolFilter;
import org.springframework.ai.mcp.McpToolNamePrefixGenerator;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

public class McpToolSource implements ToolSource, AutoCloseable {

    public static final String KERNSYSTEM = "kernsystem";

    public static final String RECHENKERN = "rechenkern";

    public static final String WISSEN = "wissen";

    private static final Logger log = LoggerFactory.getLogger(McpToolSource.class);

    private final McpSyncClient kernsystem;
    private final McpSyncClient rechenkern;
    private final McpSyncClient wissen;

    public McpToolSource(McpProperties properties) {
        this(connect(KERNSYSTEM, require(properties).kernsystem(), properties, true),
                connect(RECHENKERN, properties.rechenkern(), properties, false),
                connect(WISSEN, properties.wissen(), properties, false));
    }

    McpToolSource(McpSyncClient kernsystem, McpSyncClient rechenkern, McpSyncClient wissen) {
        this.kernsystem = kernsystem;
        this.rechenkern = rechenkern;
        this.wissen = wissen;
    }

    @Override
    public List<ToolCallback> forPermission(ToolPermission allowed) {
        Objects.requireNonNull(allowed, "erlaubt");
        McpToolFilter onlyAllowed = (verbindung, tool) ->
                allowed.allowed(tool.name(), verbindung.clientInfo().name());

        ToolCallback[] tools = SyncMcpToolCallbackProvider.builder()
                .mcpClients(connected())
                .toolFilter(onlyAllowed)
                .toolNamePrefixGenerator(McpToolNamePrefixGenerator.noPrefix())
                .build()
                .getToolCallbacks();

        return Arrays.asList(tools);
    }

    public void check(Set<String> erwarteteTools) {
        Objects.requireNonNull(erwarteteTools, "erwarteteTools");
        List<McpSyncClient> clients = connected();
        List<String> present = clients.stream()
                .flatMap(client -> names(client).stream())
                .toList();
        List<String> missing = erwarteteTools.stream()
                .filter(name -> !present.contains(name))
                .sorted()
                .toList();

        if (!missing.isEmpty()) {
            throw new IllegalStateException("Die MCP-Server bieten nicht alle Tools an, die "
                    + "der Agent braucht. Es fehlen: " + missing + ". Vorhanden sind: "
                    + present + ". Laufen die unter agenten.mcp eingetragenen Server "
                    + configured() + ", und passen ihre Adressen?");
        }
        log.info("MCP-Tools vollstaendig: {} von {} Server(n) {}",
                present.size(), clients.size(), configured());
    }

    @Override
    public void close() {
        connected().forEach(McpSyncClient::closeGracefully);
    }


    private static McpProperties require(McpProperties properties) {
        return Objects.requireNonNull(properties, "einstellungen");
    }

    private List<McpSyncClient> connected() {
        return Stream.of(kernsystem, rechenkern, wissen).filter(Objects::nonNull).toList();
    }

    private List<String> configured() {
        return Stream.of(kernsystem, rechenkern, wissen)
                .filter(Objects::nonNull)
                .map(client -> client.getClientInfo().name())
                .toList();
    }

    private static McpSyncClient connect(String name, McpProperties.Connection target,
                                           McpProperties properties, boolean mitMandant) {
        if (target == null) {
            log.info("agenten.mcp.{} ist nicht konfiguriert; dieser Agent kommt ohne ihn aus",
                    name);
            return null;
        }

        McpSyncHttpClientRequestCustomizer headers = (bauer, methode, adresse, body, context) -> {
            if (target.apiKey() != null && !target.apiKey().isBlank()) {
                bauer.header("x-api-key", target.apiKey());
            }
            Object kundenId = context.get(MandantContext.class.getName());
            if (kundenId != null) {
                bauer.header("x-kunden-id", String.valueOf(kundenId));
            }
        };

        var transport = HttpClientStreamableHttpTransport.builder(target.url())
                .endpoint(target.endpoint())
                .jsonMapper(new JacksonMcpJsonMapper(JsonMapper.builder().build()))
                .httpRequestCustomizer(headers)
                .build();

        McpSyncClient client = McpClient.sync(transport)
                .clientInfo(McpSchema.Implementation.builder(name, "1.0.0").build())
                .requestTimeout(properties.timeout())
                .transportContextProvider(() -> {
                    Long kundenId = mitMandant ? MandantContext.caller() : null;
                    return kundenId == null ? McpTransportContext.EMPTY
                            : McpTransportContext.create(
                                    Map.of(MandantContext.class.getName(), kundenId));
                })
                .build();

        client.initialize();
        return client;
    }

    private static List<String> names(McpSyncClient client) {
        return client.listTools().tools().stream().map(McpSchema.Tool::name).toList();
    }
}
