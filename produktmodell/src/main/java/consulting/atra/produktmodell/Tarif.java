package consulting.atra.produktmodell;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

public record Tarif(
        String schluessel,
        String anzeigename,
        String positionierung,
        Eintrittsalter eintrittsalter,
        BigDecimal selbstbehalt,
        BigDecimal jahreshoechstgrenze,
        int wartezeitMonate,
        boolean wartezeitEntfaelltBeiVorversicherung,
        String staffel,
        Map<String, Leistung> leistungen,
        String leistungenWie) {

    public Optional<Leistung> findLeistung(String leistungsbereich) {
        return Optional.ofNullable(leistungen.get(leistungsbereich));
    }

    Tarif withLeistungen(Map<String, Leistung> resolved) {
        return new Tarif(schluessel, anzeigename, positionierung, eintrittsalter,
                selbstbehalt, jahreshoechstgrenze, wartezeitMonate,
                wartezeitEntfaelltBeiVorversicherung, staffel, resolved, leistungenWie);
    }
}
