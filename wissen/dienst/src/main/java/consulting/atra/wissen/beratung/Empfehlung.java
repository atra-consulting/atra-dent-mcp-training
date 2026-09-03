package consulting.atra.wissen.beratung;

import java.util.List;

public record Empfehlung(
        int rang,
        String tarifschluessel,
        String anzeigename,
        String kurzformel,
        int reihenfolgeplatz,
        List<NeedReason> needReasons,
        String begruendung,
        String hinweispflicht) {

    public Empfehlung {
        needReasons = needReasons == null ? List.of() : List.copyOf(needReasons);
        if (rang < 1) {
            throw new IllegalArgumentException("Der Rang beginnt bei 1, hier: " + rang);
        }
        if (reihenfolgeplatz < 1) {
            throw new IllegalArgumentException(
                    "Der Platz in der Empfehlungsreihenfolge beginnt bei 1, hier: " + reihenfolgeplatz);
        }
        if (tarifschluessel == null || tarifschluessel.isBlank()) {
            throw new IllegalArgumentException("Eine Empfehlung braucht einen Tarifschluessel");
        }
        if (anzeigename == null || anzeigename.isBlank()) {
            throw new IllegalArgumentException("Eine Empfehlung braucht den Anzeigenamen des Tarifs");
        }
        if (kurzformel == null || kurzformel.isBlank()) {
            throw new IllegalArgumentException("Eine Empfehlung braucht die Kurzformel des Tarifs");
        }
        if (begruendung == null || begruendung.isBlank()) {
            throw new IllegalArgumentException("Eine Empfehlung ohne Begruendung ist eine Behauptung");
        }
        if (hinweispflicht == null || hinweispflicht.isBlank()) {
            throw new IllegalArgumentException("Der Tarif " + tarifschluessel + " wird ohne seine "
                    + "Hinweispflicht empfohlen; genau das sind die Luecken, die spaeter enttaeuschen");
        }
    }

    public boolean hasNeedReasons() {
        return !needReasons.isEmpty();
    }
}
