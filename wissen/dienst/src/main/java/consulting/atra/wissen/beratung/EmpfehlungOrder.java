package consulting.atra.wissen.beratung;

import java.util.LinkedHashSet;
import java.util.List;

public record EmpfehlungOrder(
        List<String> reihenfolge,
        String giltNurBei,
        String begruendung,
        String vorrangDesBedarfs,
        List<Exception> reihenfolgeGreiftNichtWenn) {

    public EmpfehlungOrder {
        reihenfolge = reihenfolge == null ? List.of() : List.copyOf(reihenfolge);
        giltNurBei = BeratungTexts.sentence(giltNurBei);
        begruendung = BeratungTexts.sentence(begruendung);
        vorrangDesBedarfs = BeratungTexts.sentence(vorrangDesBedarfs);
        reihenfolgeGreiftNichtWenn = reihenfolgeGreiftNichtWenn == null
                ? List.of() : List.copyOf(reihenfolgeGreiftNichtWenn);

        if (reihenfolge.isEmpty()) {
            throw new IllegalArgumentException("Die Empfehlungsreihenfolge ist leer");
        }
        if (new LinkedHashSet<>(reihenfolge).size() != reihenfolge.size()) {
            throw new IllegalArgumentException(
                    "Ein Tarif steht mehrfach in der Empfehlungsreihenfolge: " + reihenfolge);
        }
        if (giltNurBei == null || vorrangDesBedarfs == null) {
            throw new IllegalArgumentException("Die Empfehlungsreihenfolge braucht gilt_nur_bei und "
                    + "vorrang_des_bedarfs; ohne sie liest sie sich als Vertriebsvorgabe");
        }
    }

    public int rank(String tarifschluessel) {
        return reihenfolge.indexOf(tarifschluessel) + 1;
    }

    public record Exception(String schluessel, String text) {

        public Exception {
            text = BeratungTexts.sentence(text);
            if (schluessel == null || schluessel.isBlank()) {
                throw new IllegalArgumentException("Eine Exception braucht einen Schluessel");
            }
            if (text == null) {
                throw new IllegalArgumentException(
                        "Die Exception " + schluessel + " hat keinen Text");
            }
        }
    }
}
