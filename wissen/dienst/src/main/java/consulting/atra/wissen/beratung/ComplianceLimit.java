package consulting.atra.wissen.beratung;

public record ComplianceLimit(String schluessel, String text) {

    public ComplianceLimit {
        text = BeratungTexts.sentence(text);
        if (schluessel == null || schluessel.isBlank()) {
            throw new IllegalArgumentException("Eine Compliance-Grenze braucht einen Schluessel");
        }
        if (text == null) {
            throw new IllegalArgumentException(
                    "Die Compliance-Grenze " + schluessel + " hat keinen Text");
        }
    }
}
