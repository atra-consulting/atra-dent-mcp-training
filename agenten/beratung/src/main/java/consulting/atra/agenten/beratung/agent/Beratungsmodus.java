package consulting.atra.agenten.beratung.agent;

import java.util.Map;

public enum Beratungsmodus {

    NEUBERATUNG,

    BESTANDSBERATUNG,

    RECHNUNG_EINREICHEN;

    public static final String BELEG = "rechnung";

    public static Beratungsmodus determine(boolean angemeldet, Map<String, Object> data) {
        if (!angemeldet) {
            return NEUBERATUNG;
        }
        return data != null && data.get(BELEG) != null ? RECHNUNG_EINREICHEN : BESTANDSBERATUNG;
    }

    public boolean loggedIn() {
        return this != NEUBERATUNG;
    }
}
