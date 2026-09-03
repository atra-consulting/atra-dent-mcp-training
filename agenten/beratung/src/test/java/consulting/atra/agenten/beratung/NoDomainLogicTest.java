package consulting.atra.agenten.beratung;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class NoDomainLogicTest {

    private static final Pattern TARIF_KEY =
            Pattern.compile("ATRA_DENT_(S|B|X|X_SB)\\b");

    private static final Pattern QUOTE =
            Pattern.compile("\\b\\d{1,3}\\s*(%|Prozent|EUR|Euro)\\b");

    private static final List<String> ALLOWED = List.of(
            "Beratungsprompt.java",
            "ToolSelection.java",
            "Beratungsmeldungen.java");

    @Test
    @DisplayName("no Tarif key and no Quote outside of prompts")
    void noDomainLogicInTheSources() throws IOException {
        List<String> findings = new ArrayList<>();

        for (Path source : sources()) {
            String filename = source.getFileName().toString();
            if (ALLOWED.contains(filename)) {
                continue;
            }
            String content = withoutComments(Files.readString(source));
            check(findings, source, content, TARIF_KEY, "Tarifschluessel");
            check(findings, source, content, QUOTE, "Quote oder Betrag");
        }

        assertThat(findings)
                .as("Fachlogik in den Agentendiensten. Was ein Tarif leistet, sagt der "
                        + "Wissensdienst; was jemand hat, das Kernsystem. Steht es hier, "
                        + "laufen die Antworten auseinander.")
                .isEmpty();
    }

    private static List<Path> sources() throws IOException {
        List<Path> alle = new ArrayList<>();
        for (String modul : List.of("a2a", "modell", "mcp", "beratung", "orchestrator")) {
            Path root = Path.of("..", modul, "src", "main", "java");
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> dateien = Files.walk(root)) {
                dateien.filter(path -> path.toString().endsWith(".java")).forEach(alle::add);
            }
        }
        assertThat(alle).as("Es wurden keine Quellen gefunden; der Pfad stimmt nicht")
                .isNotEmpty();
        return alle;
    }

    private static String withoutComments(String source) {
        return source
                .replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)//.*$", " ");
    }

    private static void check(List<String> befunde, Path source, String content,
                                Pattern muster, String was) {
        Matcher hits = muster.matcher(content);
        while (hits.find()) {
            befunde.add(source.getFileName() + ": " + was + " \"" + hits.group() + "\"");
        }
    }
}
