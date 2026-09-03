package consulting.atra.agenten.beratung.tools;

import consulting.atra.agenten.beratung.agent.Beratungsmodus;
import consulting.atra.agenten.mcp.McpToolSource;
import consulting.atra.agenten.mcp.ToolPermission;
import consulting.atra.agenten.mcp.ToolMapping;

import java.util.List;
import java.util.Set;

public final class ToolSelection {

    public static final String VERTRAG_READ = "mein_vertrag_lesen";

    static final Set<String> MANDANT_REQUIRED = Set.of(
            VERTRAG_READ,
            //TODO Workshop (Aufgabe: Adressänderung)
            "mein_beitrag_berechnen",
            "meine_schadensfaelle_auflisten",
            "schadensfall_einreichen");

    static final Set<String> KERNSYSTEM = Set.of(
            VERTRAG_READ,
            //TODO Workshop (Aufgabe: Adressänderung)
            "mein_beitrag_berechnen",
            "meine_schadensfaelle_auflisten",
            "schadensfall_einreichen");

    static final Set<String> ASSUMPTION = Set.of(VERTRAG_READ, "schadensfall_einreichen");

    static final Set<String> RECHENKERN = Set.of(
            "tarife_auflisten",
            "tarif_lesen",
            "beitrag_berechnen");

    static final Set<String> WISSEN = Set.of(
            "bedingungen_suchen",
            "tarifempfehlung",
            "beratungsleitfaden_suchen",
            "goz_pruefen",
            "tarife_vergleichen");

    public static final ToolMapping MAPPING = name -> {
        try {
            return serverOf(name);
        } catch (IllegalArgumentException _) {
            return null;
        }
    };

    private ToolSelection() {
    }

    public static ToolPermission permission(Beratungsmodus modus) {
        return (name, verbindung) -> allowed(name, verbindung, modus);
    }

    public static boolean allowed(String name, String verbindung, Beratungsmodus modus) {
        if (modus == Beratungsmodus.RECHNUNG_EINREICHEN) {
            return McpToolSource.KERNSYSTEM.equals(verbindung) && ASSUMPTION.contains(name);
        }
        if ("schadensfall_einreichen".equals(name)) {
            return false;
        }
        if (!modus.loggedIn() && MANDANT_REQUIRED.contains(name)) {
            return false;
        }
        return switch (verbindung) {
            case McpToolSource.KERNSYSTEM -> KERNSYSTEM.contains(name);
            case McpToolSource.RECHENKERN -> RECHENKERN.contains(name);
            case McpToolSource.WISSEN -> WISSEN.contains(name);
            default -> false;
        };
    }

    public static List<String> allowedFor(Beratungsmodus modus) {
        return catalog().stream()
                .filter(name -> allowed(name, serverOf(name), modus))
                .toList();
    }

    public static List<String> allExpected() {
        return java.util.stream.Stream.of(Beratungsmodus.values())
                .flatMap(modus -> allowedFor(modus).stream())
                .distinct()
                .sorted()
                .toList();
    }

    public static List<String> catalog() {
        return java.util.stream.Stream.of(KERNSYSTEM, RECHENKERN, WISSEN)
                .flatMap(Set::stream)
                .sorted()
                .toList();
    }

    public static String serverOf(String name) {
        if (KERNSYSTEM.contains(name)) {
            return McpToolSource.KERNSYSTEM;
        }
        if (RECHENKERN.contains(name)) {
            return McpToolSource.RECHENKERN;
        }
        if (WISSEN.contains(name)) {
            return McpToolSource.WISSEN;
        }
        throw new IllegalArgumentException("Kein erlaubtes Tool: " + name);
    }
}
