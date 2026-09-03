package consulting.atra.wissen.beratung;

public record ExcludedTarif(
        String tarifschluessel,
        String anzeigename,
        String kriterium,
        String begruendung) {

    public ExcludedTarif {
        if (tarifschluessel == null || tarifschluessel.isBlank()) {
            throw new IllegalArgumentException("Ein Ausschluss braucht einen Tarifschluessel");
        }
        if (anzeigename == null || anzeigename.isBlank()) {
            throw new IllegalArgumentException("Ein Ausschluss braucht den Anzeigenamen des Tarifs");
        }
        if (kriterium == null || kriterium.isBlank()) {
            throw new IllegalArgumentException(
                    "Ein Ausschluss braucht das harte Kriterium, an dem der Tarif ausscheidet");
        }
        if (begruendung == null || begruendung.isBlank()) {
            throw new IllegalArgumentException(
                    "Ein Ausschluss ohne Begruendung ist im Gespraech nicht verwendbar");
        }
    }
}
