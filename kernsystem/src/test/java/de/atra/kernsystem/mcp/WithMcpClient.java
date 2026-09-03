package de.atra.kernsystem.mcp;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import de.atra.kernsystem.persistence.JsonRepository;
import de.atra.kernsystem.rechenkern.RechenkernStub;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class WithMcpClient {

    static final String API_KEY = "atra-lab-2026";

    static final long ANNA = 10001L;

    static final long CLARA = 10003L;

    static final long CLARA_CASE = 50009L;

    private static Path workingCopy;
    private static RechenkernStub rechenkern;

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private de.atra.kernsystem.domain.SchadensfallService schadensfallService;

    private final List<McpSyncClient> open = new ArrayList<>();

    de.atra.kernsystem.domain.SchadensfallService service() {
        return schadensfallService;
    }

    @DynamicPropertySource
    static void environment(DynamicPropertyRegistry registry) throws Exception {
        workingCopy = Files.createTempDirectory("kernsystem-mcp-test");
        copyFiles();
        registry.add("kernsystem.data-directory", workingCopy::toString);

        rechenkern = new RechenkernStub();
        Runtime.getRuntime().addShutdownHook(new Thread(rechenkern::close));
        registry.add("kernsystem.rechenkern.url", rechenkern::url);
    }

    static RechenkernStub rechenkern() {
        return rechenkern;
    }

    @BeforeEach
    void resetStore() throws Exception {
        copyFiles();
        applicationContext.getBeansOfType(JsonRepository.class).values().forEach(JsonRepository::reload);
        rechenkern.reset();
    }

    @AfterEach
    void closeSessions() {
        open.forEach(McpSyncClient::closeGracefully);
        open.clear();
    }

    private static void copyFiles() throws Exception {
        if (workingCopy != null) {
            for (String file : new String[]{"kunden.json", "schadensfaelle.json"}) {
                Files.copy(Path.of("data", file), workingCopy.resolve(file),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    McpSyncClient clientFor(long kundenId) {
        return client(() -> String.valueOf(kundenId));
    }

    McpSyncClient clientWithoutCustomer() {
        return client(() -> null);
    }

    McpSyncClient client(Supplier<String> kundennummer) {
        McpSyncClient client = McpClient.sync(HttpClientStreamableHttpTransport
                        .builder("http://localhost:" + port)
                        .endpoint("/mcp")
                        .httpRequestCustomizer((builder, method, target, body, context) -> {
                            builder.header("x-api-key", API_KEY);
                            String kunde = kundennummer.get();
                            if (kunde != null) {
                                builder.header("x-kunden-id", kunde);
                            }
                        })
                        .build())
                .requestTimeout(Duration.ofSeconds(30))
                .build();
        client.initialize();
        open.add(client);
        return client;
    }

    static DocumentContext result(McpSyncClient client, String tool, Map<String, Object> arguments) {
        McpSchema.CallToolResult response = client.callTool(
                McpSchema.CallToolRequest.builder(tool).arguments(arguments).build());
        assertThat(response.isError())
                .withFailMessage("Tool %s hat einen Fehler gemeldet: %s", tool, text(response))
                .isNotEqualTo(Boolean.TRUE);
        return JsonPath.parse(text(response));
    }

    static String exception(McpSyncClient client, String tool, Map<String, Object> arguments) {
        McpSchema.CallToolResult response = client.callTool(
                McpSchema.CallToolRequest.builder(tool).arguments(arguments).build());
        assertThat(response.isError())
                .withFailMessage("Tool %s hat kein Fehlerergebnis geliefert: %s",
                        tool, text(response))
                .isEqualTo(Boolean.TRUE);
        return text(response);
    }

    static String text(McpSchema.CallToolResult response) {
        return response.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(McpSchema.TextContent.class::cast)
                .map(McpSchema.TextContent::text)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Das Ergebnis enthaelt keinen Text: " + response));
    }
}
