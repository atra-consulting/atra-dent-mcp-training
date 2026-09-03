package consulting.atra.agenten.schadensfall.check;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record Bewertungsvorschlag(String empfehlung, BigDecimal erstattungsvorschlag,
                                  List<Position> positionen, String begruendung) {

    public static final String FREIGABE = "freigabe";

    public static final String ESKALATION = "eskalation";

    public static final Set<String> STATES =
            Set.of("ENTHALTEN", "NICHT_ENTHALTEN", "NICHT_BESTIMMBAR", "UNBEKANNT");

    public static boolean isGozState(String zustand) {
        return zustand != null
                && STATES.contains(zustand.strip().toUpperCase(Locale.ROOT));
    }

    public Bewertungsvorschlag {
        positionen = positionen == null ? List.of() : List.copyOf(positionen);
    }

    public boolean recommendsFreigabe() {
        return FREIGABE.equals(empfehlung);
    }

    public record Position(Integer index, String goz, String leistungsbereich, String zustand,
                           String begruendung) {

        public Position(String goz, String leistungsbereich, String zustand, String begruendung) {
            this(null, goz, leistungsbereich, zustand, begruendung);
        }
    }
}
