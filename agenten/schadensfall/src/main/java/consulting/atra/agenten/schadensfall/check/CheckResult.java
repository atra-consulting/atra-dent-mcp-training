package consulting.atra.agenten.schadensfall.check;

import java.util.List;
import java.util.Map;

public record CheckResult(BewertungResult bewertung, List<Map<String, Object>> protokoll,
                            boolean geschrieben, String hinweis) {

    public CheckResult {
        protokoll = protokoll == null ? List.of() : List.copyOf(protokoll);
    }
}
