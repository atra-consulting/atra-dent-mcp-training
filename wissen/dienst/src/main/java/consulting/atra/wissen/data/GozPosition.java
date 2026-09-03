package consulting.atra.wissen.data;

public record GozPosition(
        String nummer,
        String bezeichnung,
        String abschnitt,
        String leistungsbereich,
        String hinweis) {

    public boolean areaDeterminable() {
        return leistungsbereich != null;
    }
}
