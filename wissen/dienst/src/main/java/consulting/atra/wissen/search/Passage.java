package consulting.atra.wissen.search;

public record Passage(
        String dokumentId,
        String abschnittId,
        String ueberschrift,
        String text,
        int teil,
        int parts) {

    public String id() {
        return parts == 1
                ? dokumentId + "#" + abschnittId
                : dokumentId + "#" + abschnittId + "/" + teil;
    }

    public String embeddingText() {
        return ueberschrift + "\n" + text;
    }
}
