package consulting.atra.wissen.search;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IndexFileTest {

    @TempDir
    Path directory;

    @Test
    void readsIdsAndDimensionFromAStoredIndex() {
        SimpleVectorStore store = SimpleVectorStore.builder(new CountingModel(8)).build();
        store.add(List.of(
                new Document("avb-s#wartezeiten", "passage: Wartezeit acht Monate", Map.of()),
                new Document("avb-s#umfang", "passage: Erstattung neunzig Prozent", Map.of())));
        Path file = directory.resolve("bedingungen.json");
        store.save(file.toFile());

        var content = IndexFile.read(file);

        assertThat(content).isPresent();
        assertThat(content.get().ids())
                .isEqualTo(Set.of("avb-s#wartezeiten", "avb-s#umfang"));
        assertThat(content.get().dimension()).isEqualTo(8);
    }

    @Test
    void aMissingFileIsNoContent() {
        assertThat(IndexFile.read(directory.resolve("gibt-es-nicht.json"))).isEmpty();
    }

    @Test
    void anUnreadableFileIsNoContent() throws Exception {
        Path file = directory.resolve("kaputt.json");
        Files.writeString(file, "kein json");
        assertThat(IndexFile.read(file)).isEmpty();
    }

    @Test
    void loadingRejectsMismatchedIds() {
        SimpleVectorStore store = SimpleVectorStore.builder(new CountingModel(8)).build();
        store.add(List.of(new Document("avb-s#wartezeiten", "passage: Wartezeit", Map.of())));
        Path file = directory.resolve("bedingungen.json");
        store.save(file.toFile());

        SimpleVectorStore target = SimpleVectorStore.builder(new CountingModel(8)).build();
        boolean geladen = IndexFile.load(target, file,
                Map.of("avb-s#umfang", "passage: Wartezeit"), new CountingModel(8));

        assertThat(geladen).isFalse();
    }

    @Test
    void loadingRejectsChangedTextOnMatchingIds() {
        SimpleVectorStore store = SimpleVectorStore.builder(new CountingModel(8)).build();
        store.add(List.of(new Document("avb-s#wartezeiten", "passage: Wartezeit acht Monate", Map.of())));
        Path file = directory.resolve("bedingungen.json");
        store.save(file.toFile());

        CountingModel model = new CountingModel(8);
        SimpleVectorStore target = SimpleVectorStore.builder(model).build();
        boolean geladen = IndexFile.load(target, file,
                Map.of("avb-s#wartezeiten", "passage: Wartezeit zwoelf Monate"), model);

        assertThat(geladen).isFalse();
        assertThat(model.calls()).isZero();
    }

    @Test
    void loadingRejectsAnEntryMissingText() throws Exception {
        Path file = directory.resolve("bedingungen.json");
        Files.writeString(file, "{\"avb-s#wartezeiten\": "
                + "{\"embedding\": [0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8]}}");

        SimpleVectorStore target = SimpleVectorStore.builder(new CountingModel(8)).build();
        boolean geladen = IndexFile.load(target, file,
                Map.of("avb-s#wartezeiten", "passage: Wartezeit"), new CountingModel(8));

        assertThat(geladen).isFalse();
    }

    @Test
    void loadingRejectsADifferentDimension() {
        SimpleVectorStore store = SimpleVectorStore.builder(new CountingModel(8)).build();
        store.add(List.of(new Document("avb-s#wartezeiten", "passage: Wartezeit", Map.of())));
        Path file = directory.resolve("bedingungen.json");
        store.save(file.toFile());

        SimpleVectorStore target = SimpleVectorStore.builder(new CountingModel(16)).build();
        boolean geladen = IndexFile.load(target, file,
                Map.of("avb-s#wartezeiten", "passage: Wartezeit"), new CountingModel(16));

        assertThat(geladen).isFalse();
    }

    @Test
    void loadingTakesOverAMatchingIndex() {
        SimpleVectorStore store = SimpleVectorStore.builder(new CountingModel(8)).build();
        store.add(List.of(new Document("avb-s#wartezeiten", "passage: Wartezeit acht Monate", Map.of())));
        Path file = directory.resolve("bedingungen.json");
        store.save(file.toFile());

        CountingModel model = new CountingModel(8);
        SimpleVectorStore target = SimpleVectorStore.builder(model).build();
        boolean geladen = IndexFile.load(target, file,
                Map.of("avb-s#wartezeiten", "passage: Wartezeit acht Monate"), model);

        assertThat(geladen).isTrue();
        assertThat(model.calls()).isZero();
    }
}
