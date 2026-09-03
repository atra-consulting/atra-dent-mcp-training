package consulting.atra.wissen.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class IndexFile {

    private static final Logger LOG = LoggerFactory.getLogger(IndexFile.class);

    public record Content(Map<String, String> texts, int dimension) {

        public Set<String> ids() {
            return texts.keySet();
        }
    }

    private IndexFile() {
    }

    public static Optional<Content> read(Path file) {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            Map<String, JsonNode> entries = new JsonMapper()
                    .readValue(file.toFile(), new TypeReference<Map<String, JsonNode>>() { });
            if (entries.isEmpty()) {
                return Optional.empty();
            }
            JsonNode embedding = entries.values().iterator().next().get("embedding");
            if (embedding == null || !embedding.isArray() || embedding.isEmpty()) {
                return Optional.empty();
            }
            Map<String, String> texts = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> entry : entries.entrySet()) {
                JsonNode text = entry.getValue().get("text");
                if (text == null || !text.isString()) {
                    LOG.warn("Indexdatei {} hat keinen Text zu {} und ist damit unbrauchbar.",
                            file, entry.getKey());
                    return Optional.empty();
                }
                texts.put(entry.getKey(), text.asString());
            }
            return Optional.of(new Content(Map.copyOf(texts), embedding.size()));
        } catch (JacksonException cause) {
            LOG.warn("Indexdatei {} ist nicht lesbar und wird ignoriert: {}",
                    file, cause.getMessage());
            return Optional.empty();
        }
    }

    public static boolean load(SimpleVectorStore store, Path file,
                                Map<String, String> expectedTexts, EmbeddingModel model) {
        if (file == null) {
            return false;
        }
        Optional<Content> content = read(file);
        if (content.isEmpty()) {
            LOG.warn("Vektorindex {} fehlt oder ist unbrauchbar -- die Passagen werden neu "
                    + "eingebettet; ./wissen/build.sh erneuert die Datei.", file);
            return false;
        }
        if (!content.get().ids().equals(expectedTexts.keySet())) {
            LOG.warn("Vektorindex {} passt nicht zu den erzeugten Dokumenten -- die Passagen "
                    + "werden neu eingebettet; ./wissen/build.sh erneuert die Datei.", file);
            return false;
        }
        if (!content.get().texts().equals(expectedTexts)) {
            LOG.warn("Vektorindex {} enthaelt andere Texte als die erzeugten Dokumente -- die "
                    + "Passagen werden neu eingebettet; ./wissen/build.sh erneuert die Datei.", file);
            return false;
        }
        if (content.get().dimension() != model.dimensions()) {
            LOG.warn("Vektorindex {} stammt von einem anderen Einbettungsmodell ({} statt {} "
                    + "Dimensionen) -- die Passagen werden neu eingebettet.",
                    file, content.get().dimension(), model.dimensions());
            return false;
        }
        store.load(file.toFile());
        return true;
    }
}
