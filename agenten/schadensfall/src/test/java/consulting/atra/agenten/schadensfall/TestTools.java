package consulting.atra.agenten.schadensfall;

import consulting.atra.agenten.mcp.MandantContext;
import consulting.atra.agenten.mcp.McpWrapper;
import consulting.atra.agenten.mcp.ToolSource;
import consulting.atra.agenten.schadensfall.check.SchadensfallMapping;
import consulting.atra.agenten.schadensfall.check.ToolSelection;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@TestConfiguration(proxyBeanMethods = false)
public class TestTools {

    private static final List<String> KNOWN_TOOLS =
            ToolSelection.allExpected().stream().sorted().toList();

    @Bean
    ToolSource toolSource(ToolLog store) {
        return source(store);
    }

    public static ToolSource source(ToolLog store) {
        return allowed -> KNOWN_TOOLS.stream()
                .filter(name -> allowed.allowed(name, SchadensfallMapping.ALLE.serverOf(name)))
                .map(name -> (ToolCallback) new Testtool(name, store))
                .toList();
    }

    @Bean
    ToolLog toolBook() {
        return new ToolLog();
    }

    public static final class ToolLog {

        private final List<Aufruf> calls = new ArrayList<>();
        private final Map<String, String> respond = new LinkedHashMap<>(ANSWERS);
        private final Map<String, RuntimeException> stoerungen = new LinkedHashMap<>();

        synchronized void record(String name, Long kundenId, String arguments) {
            calls.add(new Aufruf(name, kundenId, arguments));
        }

        public synchronized List<String> calls() {
            return calls.stream().map(Aufruf::tool).toList();
        }

        public synchronized List<Long> kundenNumbers() {
            return calls.stream().map(Aufruf::kundenId).toList();
        }

        public synchronized String argumentsOf(String tool) {
            return calls.stream().filter(call -> call.tool().equals(tool))
                    .map(Aufruf::arguments).reduce((erster, last) -> last).orElse(null);
        }

        public synchronized void responds(String tool, String json) {
            respond.put(tool, json);
        }

        public synchronized void disturbs(String tool, RuntimeException failure) {
            stoerungen.put(tool, failure);
        }

        public synchronized void clear() {
            calls.clear();
            respond.clear();
            respond.putAll(ANSWERS);
            stoerungen.clear();
        }

        private synchronized String answerTo(String tool) {
            RuntimeException failure = stoerungen.get(tool);
            if (failure != null) {
                throw failure;
            }
            return respond.getOrDefault(tool, "{}");
        }

        public record Aufruf(String tool, Long kundenId, String arguments) {
        }
    }

    private record Testtool(String name, ToolLog store) implements ToolCallback {

        @Override
        public ToolDefinition getToolDefinition() {
            return ToolDefinition.builder()
                    .name(name)
                    .description("Testtool " + name)
                    .inputSchema("{\"type\":\"object\",\"properties\":{}}")
                    .build();
        }

        @Override
        public String call(String input) {
            store.record(name, MandantContext.caller(), input);
            return McpWrapper.um(store.answerTo(name));
        }

        @Override
        public String call(String input, org.springframework.ai.chat.model.ToolContext context) {
            return call(input);
        }
    }


    public static final String VERTRAG = """
            {"id":4711,"vorname":"Anna","nachname":"Mueller","geburtsdatum":"1990-04-12",
             "tarifId":"ATRA_DENT_B","versicherungsbeginn":"2025-01-01","status":"aktiv",
             "vorversicherung":false}
            """;

    public static final String GOZ = """
            {"tarif":"ATRA_DENT_B","befunde":[
              {"nummer":"2197","bezeichnung":"Adhaesive Befestigung","leistungsbereich":"ZERH",
               "status":"ENTHALTEN","quote":85,"begruendung":"Zahnerhalt laut B.2.1",
               "grenzen":{"wartezeitMonate":null}},
              {"nummer":"2150","bezeichnung":"Aufbaufuellung","leistungsbereich":"ZERH",
               "status":"ENTHALTEN","quote":85,"begruendung":"Zahnerhalt laut B.2.1",
               "grenzen":{"wartezeitMonate":null}}]}
            """;

    public static final String GOZ_UNCLEAR = """
            {"tarif":"ATRA_DENT_B","befunde":[
              {"nummer":"2197","bezeichnung":"Adhaesive Befestigung","leistungsbereich":"ZERH",
               "status":"ENTHALTEN","quote":85,"begruendung":"Zahnerhalt laut B.2.1"},
              {"nummer":"2150","bezeichnung":"Aufbaufuellung","leistungsbereich":null,
               "status":"UNBEKANNT","quote":null,
               "begruendung":"Die Nummer steht nicht in der Zuordnung."}]}
            """;

    public static final String GOZ_NICHT_BESTIMMBAR = """
            {"tarif":"ATRA_DENT_B","befunde":[
              {"nummer":"2197","bezeichnung":"Adhaesive Befestigung","leistungsbereich":"ZERH",
               "status":"ENTHALTEN","quote":85,"begruendung":"Zahnerhalt laut B.2.1"},
              {"nummer":"2150","bezeichnung":"Aufbaufuellung","leistungsbereich":null,
               "status":"NICHT_BESTIMMBAR","quote":null,
               "begruendung":"Keinem Leistungsbereich fest zugeordnet."}]}
            """;

    public static final String SCHADENSFAELLE = """
            [{"id":50071,"behandlungsdatum":"2026-01-08","rechnungsbetrag":"395.00",
              "status":"in_pruefung","erstattungsbetrag":null},
             {"id":50012,"behandlungsdatum":"2025-06-02","rechnungsbetrag":"120.00",
              "status":"ausgezahlt","erstattungsbetrag":"102.00"}]
            """;

    public static final String ERSTATTUNG = """
            {"erstattungsbetrag":"336.00","rechnungsbetrag":"395.00","eigenanteil":"59.00",
             "versicherungsjahr":2,"schritte":[]}
            """;

    public static final String COMPARISON = """
            {"stand":"2026-01-01","tarife":[
              {"schluessel":"ATRA_DENT_B","anzeigename":"atra.dent.balance","wartezeitMonate":8,
               "wartezeitEntfaelltBeiVorversicherung":true,"zahnstaffel":[]}],
             "leistungsbereiche":[]}
            """;

    public static final String BEDINGUNGEN = """
            {"treffer":[{"fundstelle":"B.2.1","text":"Zahnerhalt wird zu 85 Prozent erstattet."}]}
            """;

    public static final String BEWERTET = """
            {"id":50071,"status":"geprueft_freigabe"}
            """;

    private static final Map<String, String> ANSWERS = Map.of(
            ToolSelection.VERTRAG, VERTRAG,
            ToolSelection.GOZ, GOZ,
            ToolSelection.SCHADENSFAELLE, SCHADENSFAELLE,
            ToolSelection.ERSTATTUNG, ERSTATTUNG,
            ToolSelection.COMPARISON, COMPARISON,
            ToolSelection.BEDINGUNGEN, BEDINGUNGEN,
            ToolSelection.BEWERTEN, BEWERTET);
}
