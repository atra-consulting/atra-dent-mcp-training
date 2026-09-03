package consulting.atra.wissen.beratung;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record BeratungRequest(
        int alter,
        List<String> behandlungsschwerpunkte,
        AngerateneBehandlung angerateneBehandlung,
        Integer fehlendeZaehne,
        Vorversicherung vorversicherung) {

    private static final int MAX_AGE = 122;

    private static final int ZAEHNE_TOTAL = 32;

    public BeratungRequest {
        if (alter < 0 || alter > MAX_AGE) {
            throw new IllegalArgumentException(
                    "Das Alter liegt ausserhalb des Moeglichen: " + alter);
        }
        if (fehlendeZaehne != null && (fehlendeZaehne < 0 || fehlendeZaehne > ZAEHNE_TOTAL)) {
            throw new IllegalArgumentException(
                    "Die Anzahl fehlender Zaehne liegt ausserhalb von 0 bis " + ZAEHNE_TOTAL
                            + ": " + fehlendeZaehne);
        }
        behandlungsschwerpunkte = normalized(behandlungsschwerpunkte);
        angerateneBehandlung = angerateneBehandlung == null
                ? AngerateneBehandlung.NICHT_ERHOBEN : angerateneBehandlung;
        vorversicherung = vorversicherung == null
                ? Vorversicherung.NICHT_ERHOBEN : vorversicherung;
    }

    public static BeratungRequest fuerAlter(int alter) {
        return new BeratungRequest(alter, List.of(),
                AngerateneBehandlung.NICHT_ERHOBEN, null, Vorversicherung.NICHT_ERHOBEN);
    }

    public BeratungRequest withFocusAreas(String... bereiche) {
        return new BeratungRequest(alter, List.of(bereiche),
                angerateneBehandlung, fehlendeZaehne, vorversicherung);
    }

    public BeratungRequest mitAngeratenerBehandlung(AngerateneBehandlung angeraten) {
        return new BeratungRequest(alter, behandlungsschwerpunkte,
                angeraten, fehlendeZaehne, vorversicherung);
    }

    public BeratungRequest mitFehlendenZaehnen(int count) {
        return new BeratungRequest(alter, behandlungsschwerpunkte,
                angerateneBehandlung, count, vorversicherung);
    }

    public BeratungRequest mitVorversicherung(Vorversicherung vorschutz) {
        return new BeratungRequest(alter, behandlungsschwerpunkte,
                angerateneBehandlung, fehlendeZaehne, vorschutz);
    }

    public boolean missingZaehneRecorded() {
        return fehlendeZaehne != null;
    }

    public boolean hasMissingZaehne() {
        return fehlendeZaehne != null && fehlendeZaehne > 0;
    }

    private static List<String> normalized(List<String> bereiche) {
        if (bereiche == null) {
            return List.of();
        }
        Set<String> clean = new LinkedHashSet<>();
        for (String bereich : bereiche) {
            if (bereich == null || bereich.isBlank()) {
                throw new IllegalArgumentException(
                        "Ein Behandlungsschwerpunkt ohne Bereichsschluessel");
            }
            clean.add(bereich.strip().toUpperCase(Locale.ROOT));
        }
        return List.copyOf(clean);
    }
}
