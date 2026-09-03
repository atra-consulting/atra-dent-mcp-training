package de.atra.kernsystem.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonRepositoryTest {

    public static class Posten {
        public long id;
        public String bezeichnung;

        public Posten() {
        }

        public Posten(long id, String bezeichnung) {
            this.id = id;
            this.bezeichnung = bezeichnung;
        }
    }

    private final ObjectMapper mapper = JsonMapper.builder().build();

    private JsonRepository<Posten> repository(Path file) {
        return new JsonRepository<>(file, Posten.class, p -> p.id, 100L, mapper);
    }

    @Test
    void an_empty_file_is_created_on_first_access(@TempDir Path directory) {
        Path file = directory.resolve("posten.json");

        JsonRepository<Posten> repository = repository(file);

        assertThat(repository.all()).isEmpty();
        assertThat(file).exists();
    }

    @Test
    void a_missing_directory_aborts_pointing_at_the_working_directory(
            @TempDir Path directory) {
        Path mismatched = directory.resolve("gibt-es-nicht").resolve("posten.json");

        assertThatThrownBy(() -> repository(mismatched))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Data directory")
                .hasMessageContaining("gibt-es-nicht")
                .hasMessageContaining("Working directory")
                .hasMessageContaining("kernsystem.data-directory");

        assertThat(mismatched.getParent()).doesNotExist();
    }

    @Test
    void adding_assigns_consecutive_ids_from_the_start_value(@TempDir Path directory) {
        JsonRepository<Posten> repository = repository(directory.resolve("posten.json"));

        Posten first = repository.add(id -> new Posten(id, "erster"));
        Posten second = repository.add(id -> new Posten(id, "zweiter"));

        assertThat(first.id).isEqualTo(100L);
        assertThat(second.id).isEqualTo(101L);
    }

    @Test
    void what_was_written_survives_a_reload(@TempDir Path directory) {
        Path file = directory.resolve("posten.json");
        repository(file).add(id -> new Posten(id, "bleibt"));

        JsonRepository<Posten> updated = repository(file);

        assertThat(updated.all()).hasSize(1);
        assertThat(updated.all().getFirst().bezeichnung).isEqualTo("bleibt");
    }

    @Test
    void the_next_id_continues_from_the_highest_entry_after_a_reload(@TempDir Path directory)
            throws Exception {
        Path file = directory.resolve("posten.json");
        Files.writeString(file, "[{\"id\":540,\"bezeichnung\":\"aus dem Bestand\"}]");

        Posten amended = repository(file).add(id -> new Posten(id, "neu"));

        assertThat(amended.id).isEqualTo(541L);
    }

    @Test
    void replacing_swaps_the_entry_in_place(@TempDir Path directory) {
        Path file = directory.resolve("posten.json");
        JsonRepository<Posten> repository = repository(file);
        repository.add(id -> new Posten(id, "alt"));

        repository.replace(100L, new Posten(100L, "neu"));

        assertThat(repository(file).all().getFirst().bezeichnung).isEqualTo("neu");
    }

    @Test
    void replacing_an_unknown_id_fails(@TempDir Path directory) {
        JsonRepository<Posten> repository = repository(directory.resolve("posten.json"));

        assertThatThrownBy(() -> repository.replace(999L, new Posten(999L, "x")))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void update_applies_the_change_under_the_lock_and_writes(@TempDir Path directory) {
        Path file = directory.resolve("posten.json");
        JsonRepository<Posten> repository = repository(file);
        repository.add(id -> new Posten(id, "alt"));

        Posten result = repository.update(100L, p -> new Posten(p.id, "neu"));

        assertThat(result.bezeichnung).isEqualTo("neu");
        assertThat(repository.find(100L)).get().extracting(p -> p.bezeichnung).isEqualTo("neu");
        assertThat(repository(file).find(100L)).get().extracting(p -> p.bezeichnung).isEqualTo("neu");
    }

    @Test
    void update_leaves_the_store_unchanged_when_the_change_throws(@TempDir Path directory) {
        Path file = directory.resolve("posten.json");
        JsonRepository<Posten> repository = repository(file);
        repository.add(id -> new Posten(id, "alt"));

        assertThatThrownBy(() -> repository.update(100L, p -> { throw new IllegalStateException("nein"); }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(repository.find(100L)).get().extracting(p -> p.bezeichnung).isEqualTo("alt");
    }

    @Test
    void update_on_an_unknown_id_throws(@TempDir Path directory) {
        JsonRepository<Posten> repository = repository(directory.resolve("posten.json"));
        repository.add(id -> new Posten(id, "alt"));

        assertThatThrownBy(() -> repository.update(999L, p -> p))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void find_returns_empty_for_an_unknown_id(@TempDir Path directory) {
        JsonRepository<Posten> repository = repository(directory.resolve("posten.json"));

        assertThat(repository.find(4711L)).isEmpty();
    }

    @Test
    void no_temp_file_is_left_behind_after_writing(@TempDir Path directory)
            throws Exception {
        JsonRepository<Posten> repository = repository(directory.resolve("posten.json"));
        repository.add(id -> new Posten(id, "eins"));

        try (var entries = Files.list(directory)) {
            List<String> names = entries.map(p -> p.getFileName().toString()).toList();
            assertThat(names).containsExactly("posten.json");
        }
    }

    @Test
    void the_written_file_is_indented_and_therefore_diff_friendly(@TempDir Path directory)
            throws Exception {
        Path file = directory.resolve("posten.json");
        repository(file).add(id -> new Posten(id, "eins"));

        assertThat(Files.readString(file)).contains("\n");
    }

    @Test
    void serialization_errors_leave_no_temp_file(@TempDir Path directory)
            throws Exception {
        Path file = directory.resolve("posten.json");
        JsonRepository<Posten> repository = repository(file);
        repository.add(id -> new Posten(id, "original"));
        String contentBefore = Files.readString(file);

        class BrokenPost extends Posten {
            public BrokenPost(long id, String bezeichnung) {
                super(id, bezeichnung);
            }

            public Object getBroken() {
                throw new UnsupportedOperationException("Serialisierung unmoeglich");
            }
        }

        assertThatThrownBy(() -> repository.add(id -> new BrokenPost(id, "kaputt")))
                .isInstanceOf(IllegalStateException.class);

        try (var entries = Files.list(directory)) {
            List<String> names = entries.map(p -> p.getFileName().toString()).toList();
            assertThat(names).containsExactly("posten.json");
        }
        assertThat(Files.readString(file)).isEqualTo(contentBefore);
    }

    @Test
    void kaputtes_json_beim_laden_wirft_exception_mit_dateinamen(@TempDir Path directory)
            throws Exception {
        Path file = directory.resolve("posten.json");
        Files.writeString(file, "{kaputt}");

        assertThatThrownBy(() -> repository(file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(file.toString());
    }

    @Test
    void objects_from_all_four_paths_resist_mutation(@TempDir Path directory) {
        JsonRepository<Posten> repository = repository(directory.resolve("posten.json"));

        Posten fromAmend = repository.add(id -> new Posten(id, "original"));
        fromAmend.bezeichnung = "veraendert";

        assertThat(repository.find(100L).orElseThrow().bezeichnung).isEqualTo("original");

        Posten fromFind = repository.find(100L).orElseThrow();
        fromFind.bezeichnung = "veraendert";

        assertThat(repository.find(100L).orElseThrow().bezeichnung).isEqualTo("original");

        List<Posten> ausAlle = repository.all();
        ausAlle.getFirst().bezeichnung = "veraendert";

        assertThat(repository.all().getFirst().bezeichnung).isEqualTo("original");

        Posten fromReplace = repository.replace(100L, new Posten(100L, "ersetzt"));
        fromReplace.bezeichnung = "veraendert";

        assertThat(repository.find(100L).orElseThrow().bezeichnung).isEqualTo("ersetzt");
    }

    @Test
    void changes_to_handed_out_objects_do_not_reach_the_file(@TempDir Path directory) {
        Path file = directory.resolve("posten.json");
        JsonRepository<Posten> repository = repository(file);

        repository.add(id -> new Posten(id, "original")).bezeichnung = "veraendert";
        repository.replace(100L, new Posten(100L, "ersetzt")).bezeichnung = "veraendert";

        assertThat(repository(file).all().getFirst().bezeichnung).isEqualTo("ersetzt");
    }

    @Test
    void the_written_file_ends_with_a_newline(@TempDir Path directory)
            throws Exception {
        Path file = directory.resolve("posten.json");
        repository(file).add(id -> new Posten(id, "eins"));

        assertThat(Files.readString(file)).endsWith("\n");
    }

    @Test
    void reload_breaks_the_store_out_of_the_in_memory_cache(@TempDir Path directory)
            throws Exception {
        Path file = directory.resolve("posten.json");
        JsonRepository<Posten> repository = repository(file);
        repository.add(id -> new Posten(id, "aus speicher"));

        Files.writeString(file, "[{\"id\":999,\"bezeichnung\":\"aus datei\"}]");

        repository.reload();

        assertThat(repository.all()).hasSize(1);
        assertThat(repository.all().getFirst().id).isEqualTo(999L);
        assertThat(repository.all().getFirst().bezeichnung).isEqualTo("aus datei");

        Posten updated = repository.add(id -> new Posten(id, "nach reload"));
        assertThat(updated.id).isEqualTo(1000L);
    }
}
