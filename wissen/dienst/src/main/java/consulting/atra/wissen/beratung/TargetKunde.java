package consulting.atra.wissen.beratung;

import java.util.List;

public record TargetKunde(
        String tarif,
        String kurzformel,
        List<String> passtWenn,
        List<String> passtNichtWenn,
        String hinweispflicht) {

    public TargetKunde {
        kurzformel = BeratungTexts.sentence(kurzformel);
        hinweispflicht = BeratungTexts.sentence(hinweispflicht);
        passtWenn = BeratungTexts.sentences(passtWenn);
        passtNichtWenn = BeratungTexts.sentences(passtNichtWenn);

        if (tarif == null || tarif.isBlank()) {
            throw new IllegalArgumentException("Ein Zielkundeneintrag braucht einen Tarifschluessel");
        }
        if (kurzformel == null) {
            throw new IllegalArgumentException("Der Zielkundeneintrag zu " + tarif
                    + " hat keine Kurzformel");
        }
        if (hinweispflicht == null) {
            throw new IllegalArgumentException("Der Zielkundeneintrag zu " + tarif
                    + " hat keine Hinweispflicht; sie geht mit jeder Empfehlung mit und darf "
                    + "deshalb nicht fehlen");
        }
    }
}
