package consulting.atra.wissen.beratung;

public record ConversationReason(String schluessel, String text) {

    public ConversationReason {
        text = BeratungTexts.sentence(text);
        if (schluessel == null || schluessel.isBlank()) {
            throw new IllegalArgumentException("Ein ConversationReason braucht einen Schluessel");
        }
        if (text == null) {
            throw new IllegalArgumentException(
                    "Der ConversationReason " + schluessel + " hat keinen Text");
        }
    }
}
