package consulting.atra.agenten.schadensfall.check;

import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;

public record BewertungResult(String empfehlung, BigDecimal erstattungsvorschlag,
                                 List<Eskalationsgrund> gruende,
                                 List<Bewertungsvorschlag.Position> positionen,
                                 JsonNode arztauskunft, String begruendung) {

    public BewertungResult {
        gruende = gruende == null ? List.of() : List.copyOf(gruende);
        positionen = positionen == null ? List.of() : List.copyOf(positionen);
    }

    public boolean freigabe() {
        return Bewertungsvorschlag.FREIGABE.equals(empfehlung);
    }

    public List<String> codes() {
        return gruende.stream().map(Eskalationsgrund::code).toList();
    }
}
