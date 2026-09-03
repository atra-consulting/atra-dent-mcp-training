package consulting.atra.agenten.schadensfall.check;

import java.util.List;
import java.util.Objects;

public record Eskalationsgrund(String code, String text) {

    public static final String MODELL_WITHOUT_RESULT = "MODELL_WITHOUT_RESULT";

    public static final String GOZ_UNCLEAR = "GOZ_UNCLEAR";

    public static final String BETRAG_DEVIATES = "BETRAG_DEVIATES";

    public static final String ARZT_UNAVAILABLE = "ARZT_UNAVAILABLE";

    public static final String ARZT_FLAGGED = "ARZT_FLAGGED";

    public static final String BETRAG_ABOVE_THRESHOLD = "BETRAG_ABOVE_THRESHOLD";

    public static final String VERTRAG_INACTIVE = "VERTRAG_INACTIVE";

    public static final String WARTEZEIT = "WARTEZEIT";

    public static final String PATIENT_UNCLEAR = "PATIENT_UNCLEAR";

    public static final String DUPLICATE = "DUPLICATE";

    public static final String EXTRACTION_INCOMPLETE = "EXTRACTION_INCOMPLETE";

    public static final String MODELL_ESCALATION = "MODELL_ESCALATION";

    public static final List<String> ALLE = List.of(
            GOZ_UNCLEAR, BETRAG_DEVIATES, ARZT_FLAGGED, ARZT_UNAVAILABLE,
            BETRAG_ABOVE_THRESHOLD, VERTRAG_INACTIVE, WARTEZEIT, PATIENT_UNCLEAR,
            DUPLICATE, EXTRACTION_INCOMPLETE, MODELL_WITHOUT_RESULT, MODELL_ESCALATION);

    public Eskalationsgrund {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(text, "text");
    }
}
