package consulting.atra.wissen.data;

public record GoaePosition(
        String nummer,
        String bezeichnung,
        String leistungsbereich,
        String hinweis) {

    public boolean areaDeterminable() {
        return leistungsbereich != null;
    }
}
