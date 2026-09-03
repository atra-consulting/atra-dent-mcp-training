package consulting.atra.wissen.beratung;

import java.util.List;

public class UnknownLeistungsbereichException extends IllegalArgumentException {

    private final String area;
    private final List<String> knownAreas;

    public UnknownLeistungsbereichException(String bereich, List<String> bekannteBereiche) {
        super("Der Leistungsbereich " + bereich + " gehoert nicht zum Produktmodell; bekannt sind "
                + String.join(", ", bekannteBereiche));
        this.area = bereich;
        this.knownAreas = List.copyOf(bekannteBereiche);
    }

    public String area() {
        return area;
    }

    public List<String> knownAreas() {
        return knownAreas;
    }
}
