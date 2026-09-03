package consulting.atra.wissen.beratung;

public record HardCriterion(String schluessel, String pruefung, String wirkung, String hinweis) {

    public HardCriterion {
        pruefung = BeratungTexts.sentence(pruefung);
        wirkung = BeratungTexts.sentence(wirkung);
        hinweis = BeratungTexts.sentence(hinweis);
        if (schluessel == null || schluessel.isBlank()) {
            throw new IllegalArgumentException("Ein hartes Kriterium braucht einen Schluessel");
        }
        if (pruefung == null || wirkung == null) {
            throw new IllegalArgumentException("Das harte Kriterium " + schluessel
                    + " braucht pruefung und wirkung");
        }
    }
}
