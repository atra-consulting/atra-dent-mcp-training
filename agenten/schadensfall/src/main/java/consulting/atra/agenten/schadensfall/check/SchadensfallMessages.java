package consulting.atra.agenten.schadensfall.check;

import consulting.atra.agenten.mcp.ObservedTools;
import consulting.atra.agenten.mcp.ToolMessages;

import java.util.Map;

public final class SchadensfallMessages {

    private static final Map<String, ObservedTools.Meldung> MESSAGES = Map.ofEntries(
            Map.entry(ToolSelection.VERTRAG,
                    message("lese den Vertrag der Kundin", "Vertrag lesen")),
            Map.entry(ToolSelection.GOZ,
                    message("prüfe die Gebührennummern dem Grunde nach", "GOZ prüfen")),
            Map.entry(ToolSelection.SCHADENSFAELLE,
                    message("sehe den bisherigen Verbrauch durch", "Verbrauch lesen")),
            Map.entry(ToolSelection.BEDINGUNGEN,
                    message("schlage im Bedingungswerk nach", "Bedingungen nachschlagen")),
            Map.entry(ToolSelection.COMPARISON,
                    message("lege die Tarife nebeneinander", "Tarife vergleichen")),
            Map.entry(ToolSelection.ERSTATTUNG,
                    message("rechne die Erstattung aus", "Erstattung berechnen")),
            Map.entry(ToolSelection.ARZT,
                    message("hole die Fachauskunft des Arztservice", "Arztservice befragen")),
            Map.entry(ToolSelection.SIGNAL,
                    message("gebe meine Bewertung ab", "Bewertung abgeben")),
            Map.entry(ToolSelection.BEWERTEN,
                    message("schreibe die Bewertung in den Fall", "Bewertung schreiben")));

    public static final ToolMessages ALLE = MESSAGES::get;

    private SchadensfallMessages() {
    }

    private static ObservedTools.Meldung message(String text, String label) {
        return new ObservedTools.Meldung(text, label);
    }
}
