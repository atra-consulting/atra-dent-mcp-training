package consulting.atra.agenten.model;

public final class ApiKeyCheck {

    private static final String TEMPLATE = """

            ===========================================================
            Der %s braucht einen Gemini-Schlüssel.

            Setzen Sie GEMINI_API_KEY (ersatzweise GOOGLE_API_KEY) in der
            Umgebung und starten Sie erneut:

                cp .env.beispiel .env    # Schlüssel eintragen
                ./start.sh

            Ohne Modell kann der Dienst nichts von dem, was seine Agent
            Card zusagt: Jede eingehende Nachricht läuft durch einen
            Modellaufruf.
            ===========================================================
            """;

    private ApiKeyCheck() {
    }

    public static void checkOrStop(String service) {
        if (key() == null) {
            System.err.println(TEMPLATE.formatted(service));
            System.exit(1);
        }
    }

    public static String key() {
        String gemini = System.getenv("GEMINI_API_KEY");
        if (gemini != null && !gemini.isBlank()) {
            return gemini;
        }
        String google = System.getenv("GOOGLE_API_KEY");
        return google == null || google.isBlank() ? null : google;
    }
}
