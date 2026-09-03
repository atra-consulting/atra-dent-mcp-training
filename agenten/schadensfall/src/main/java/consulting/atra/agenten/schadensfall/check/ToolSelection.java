package consulting.atra.agenten.schadensfall.check;

import consulting.atra.agenten.mcp.McpToolSource;
import consulting.atra.agenten.mcp.ToolPermission;

import java.util.List;
import java.util.Set;

public final class ToolSelection {

    public static final String VERTRAG = "mein_vertrag_lesen";

    public static final String SCHADENSFAELLE = "meine_schadensfaelle_auflisten";

    public static final String GOZ = "goz_pruefen";

    public static final String BEDINGUNGEN = "bedingungen_suchen";

    public static final String COMPARISON = "tarife_vergleichen";

    public static final String ERSTATTUNG = "erstattung_berechnen";

    public static final String BEWERTEN = "schadensfall_bewerten";

    public static final String ARZT = "arzt_befragen";

    public static final String SIGNAL = "bewertung_abgeben";

    private static final Set<String> KERNSYSTEM_READING = Set.of(VERTRAG, SCHADENSFAELLE);

    private static final Set<String> RECHENKERN = Set.of(ERSTATTUNG);

    private static final Set<String> WISSEN = Set.of(GOZ, BEDINGUNGEN, COMPARISON);

    private ToolSelection() {
    }

    public static ToolPermission forModel() {
        return (name, verbindung) -> switch (verbindung) {
            case McpToolSource.KERNSYSTEM -> KERNSYSTEM_READING.contains(name);
            case McpToolSource.RECHENKERN -> RECHENKERN.contains(name);
            case McpToolSource.WISSEN -> WISSEN.contains(name);
            default -> false;
        };
    }

    public static ToolPermission forCode() {
        return (name, verbindung) ->
                McpToolSource.KERNSYSTEM.equals(verbindung) && BEWERTEN.equals(name);
    }

    public static List<String> namesForModel() {
        return allMcpTools().stream()
                .filter(name -> !BEWERTEN.equals(name))
                .toList();
    }

    public static Set<String> allExpected() {
        return Set.copyOf(allMcpTools());
    }

    static String serverOf(String name) {
        if (KERNSYSTEM_READING.contains(name) || BEWERTEN.equals(name)) {
            return McpToolSource.KERNSYSTEM;
        }
        if (RECHENKERN.contains(name)) {
            return McpToolSource.RECHENKERN;
        }
        if (WISSEN.contains(name)) {
            return McpToolSource.WISSEN;
        }
        return null;
    }

    private static List<String> allMcpTools() {
        return java.util.stream.Stream.of(KERNSYSTEM_READING, RECHENKERN, WISSEN,
                        Set.of(BEWERTEN))
                .flatMap(Set::stream)
                .sorted()
                .toList();
    }
}
