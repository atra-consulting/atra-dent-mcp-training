package consulting.atra.wissen.beratung;

public record NeedReason(NeedReasonType art, String bereich, String text) {

    public NeedReason {
        if (art == null) {
            throw new IllegalArgumentException("Ein NeedReason braucht eine Art");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Ein NeedReason braucht einen Text");
        }
        boolean istSchwerpunkt = art == NeedReasonType.SCHWERPUNKT;
        if (istSchwerpunkt == (bereich == null)) {
            throw new IllegalArgumentException(
                    "Der Leistungsbereich gehoert zu SCHWERPUNKT und nur dorthin, hier: "
                            + art + " / " + bereich);
        }
    }

    static NeedReason focusArea(String bereich, String text) {
        return new NeedReason(NeedReasonType.SCHWERPUNKT, bereich, text);
    }

    static NeedReason missingZaehne(String text) {
        return new NeedReason(NeedReasonType.FEHLENDE_ZAEHNE_AUSNAHME, null, text);
    }

    static NeedReason wartezeit(String text) {
        return new NeedReason(NeedReasonType.WARTEZEIT_ENTFAELLT, null, text);
    }
}
