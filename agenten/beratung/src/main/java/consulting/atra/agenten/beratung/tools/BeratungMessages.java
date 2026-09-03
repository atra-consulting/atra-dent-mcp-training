package consulting.atra.agenten.beratung.tools;

import consulting.atra.agenten.mcp.ObservedTools;
import consulting.atra.agenten.mcp.ToolMessages;

import java.util.Map;

public final class BeratungMessages {

    private static final Map<String, ObservedTools.Meldung> MESSAGES = Map.ofEntries(
            Map.entry("mein_vertrag_lesen",
                    message("sehe in Ihrem Vertrag nach", "Vertrag nachschlagen")),
            Map.entry("mein_beitrag_berechnen",
                    message("hole den Taschenrechner raus", "Eigenen Beitrag berechnen")),
            Map.entry("beitrag_berechnen",
                    message("hole den Taschenrechner raus", "Beitrag berechnen")),
            Map.entry("tarife_auflisten",
                    message("blättere durch unsere Tarife", "Tarife auflisten")),
            Map.entry("tarif_lesen",
                    message("sehe mir den Tarif genauer an", "Tarif nachschlagen")),
            Map.entry("meine_kontaktdaten_aendern",
                    message("trage Ihre neuen Kontaktdaten ein", "Kontaktdaten ändern")),
            Map.entry("meine_schadensfaelle_auflisten",
                    message("sehe Ihre bisherigen Erstattungen durch",
                            "Erstattungen durchsehen")),
            Map.entry("bedingungen_suchen",
                    message("schlage in den Bedingungen nach", "Bedingungen durchsuchen")),
            Map.entry("goz_pruefen",
                    message("prüfe die Gebührennummern", "Gebührennummern prüfen")),
            Map.entry("tarifempfehlung",
                    message("überlege, welcher Tarif zu Ihnen passt", "Empfehlung ableiten")),
            Map.entry("tarife_vergleichen",
                    message("lege die Tarife nebeneinander", "Tarife gegenüberstellen")),
            Map.entry("beratungsleitfaden_suchen",
                    message("sehe in meinen Beratungsunterlagen nach",
                            "Beratungsunterlagen durchsuchen")),
            Map.entry(SignalTools.FOLLOW_UP,
                    message("habe noch eine Frage an Sie", "Rückfrage stellen")),
            Map.entry(SignalTools.REJECT,
                    message("sehe, dass ich hier nicht weiterhelfen kann",
                            "Anliegen ablehnen")));

    public static final ToolMessages ALLE = MESSAGES::get;

    private BeratungMessages() {
    }

    private static ObservedTools.Meldung message(String text, String label) {
        return new ObservedTools.Meldung(text, label);
    }
}
