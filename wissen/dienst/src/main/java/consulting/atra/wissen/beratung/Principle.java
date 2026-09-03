package consulting.atra.wissen.beratung;

public record Principle(String vorrang, String begruendung, String pflichtZurOffenheit) {

    public Principle {
        vorrang = BeratungTexts.sentence(vorrang);
        begruendung = BeratungTexts.sentence(begruendung);
        pflichtZurOffenheit = BeratungTexts.sentence(pflichtZurOffenheit);
        if (vorrang == null || begruendung == null || pflichtZurOffenheit == null) {
            throw new IllegalArgumentException(
                    "Der Grundsatz braucht vorrang, begruendung und pflicht_zur_offenheit");
        }
    }
}
