package consulting.atra.wissen.comparison;

import java.time.LocalDate;
import java.util.List;

public record ComparisonResult(
        LocalDate stand,
        List<Tarifangabe> tarife,
        List<LeistungsbereichComparison> leistungsbereiche) {

    public ComparisonResult {
        tarife = tarife == null ? List.of() : List.copyOf(tarife);
        leistungsbereiche = leistungsbereiche == null ? List.of() : List.copyOf(leistungsbereiche);
        if (tarife.isEmpty()) {
            throw new IllegalArgumentException("Ein Vergleich ohne Tarif ist keiner");
        }
    }
}
