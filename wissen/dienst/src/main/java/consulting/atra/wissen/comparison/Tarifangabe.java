package consulting.atra.wissen.comparison;

import consulting.atra.produktmodell.Eintrittsalter;
import consulting.atra.produktmodell.Staffel;

import java.math.BigDecimal;
import java.util.List;

public record Tarifangabe(
        String schluessel,
        String anzeigename,
        String positionierung,
        Eintrittsalter eintrittsalter,
        BigDecimal selbstbehalt,
        BigDecimal jahreshoechstgrenze,
        int wartezeitMonate,
        boolean wartezeitEntfaelltBeiVorversicherung,
        List<Staffel.Stufe> zahnstaffel,
        String bedingungswerk) {

    public Tarifangabe {
        zahnstaffel = zahnstaffel == null ? List.of() : List.copyOf(zahnstaffel);
    }
}
