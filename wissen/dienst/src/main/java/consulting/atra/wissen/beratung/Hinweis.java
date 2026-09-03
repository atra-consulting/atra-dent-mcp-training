package consulting.atra.wissen.beratung;

public record Hinweis(String schluessel, Urgency dringlichkeit, String text) {

    public Hinweis {
        if (schluessel == null || schluessel.isBlank()) {
            throw new IllegalArgumentException("Ein Hinweis braucht einen Schluessel");
        }
        if (dringlichkeit == null) {
            throw new IllegalArgumentException("Ein Hinweis braucht eine Dringlichkeit");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Der Hinweis " + schluessel + " hat keinen Text");
        }
    }

    static Hinweis primary(String schluessel, String text) {
        return new Hinweis(schluessel, Urgency.VORRANGIG, text);
    }

    static Hinweis supplementary(String schluessel, String text) {
        return new Hinweis(schluessel, Urgency.ERGAENZEND, text);
    }
}
