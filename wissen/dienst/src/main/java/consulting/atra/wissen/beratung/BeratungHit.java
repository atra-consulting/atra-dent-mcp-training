package consulting.atra.wissen.beratung;

import consulting.atra.wissen.search.Passage;

public record BeratungHit(
        String dokumentId,
        String abschnittId,
        String ueberschrift,
        String text,
        double bewertung) {

    static BeratungHit aus(Passage passage, double bewertung) {
        return new BeratungHit(
                passage.dokumentId(),
                passage.abschnittId(),
                passage.ueberschrift(),
                passage.text(),
                bewertung);
    }
}
