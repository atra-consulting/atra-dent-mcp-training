package consulting.atra.produktmodell;

import java.math.BigDecimal;
import java.util.List;

public record Staffel(String schluessel, int dauerJahre, List<Stufe> stufen) {

    public record Stufe(Integer bisJahr, BigDecimal betrag) {
    }
}
