package consulting.atra.agenten.a2a.server;

import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentSkill;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class AgentCardLoader {

    private static final ObjectMapper CARD_READER = YAMLMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private record Card(String name, String description, String version,
                         Faehigkeiten capabilities, List<String> defaultInputModes,
                         List<String> defaultOutputModes, List<Fertigkeit> skills) {
    }

    private record Faehigkeiten(boolean streaming, boolean pushNotifications) {
    }

    private record Fertigkeit(String id, String name, String description,
                              List<String> tags, List<String> examples) {
    }

    private AgentCardLoader() {
    }

    public static AgentCard load(Path card, String basisUrl) {
        Card read;
        try (InputStream stream = Files.newInputStream(card)) {
            read = CARD_READER.readValue(stream, Card.class);
        } catch (IOException exception) {
            throw new UncheckedIOException("Die Agent Card ist nicht lesbar: "
                    + card.toAbsolutePath(), exception);
        }
        return new AgentCard.Builder()
                .name(read.name())
                .description(read.description())
                .version(read.version())
                .url(basisUrl + "/")
                .protocolVersion("0.3.0")
                .capabilities(new AgentCapabilities.Builder()
                        .streaming(read.capabilities().streaming())
                        .pushNotifications(false)
                        .build())
                .defaultInputModes(read.defaultInputModes())
                .defaultOutputModes(read.defaultOutputModes())
                .skills(read.skills() == null ? List.of() : read.skills().stream()
                        .map(f -> new AgentSkill.Builder()
                                .id(f.id()).name(f.name()).description(f.description())
                                .tags(f.tags() == null ? List.of() : f.tags())
                                .examples(f.examples() == null ? List.of() : f.examples())
                                .build())
                        .toList())
                .build();
    }
}
