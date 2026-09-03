package consulting.atra.wissen.goz;

public record GozBefund(
        String nummer,
        String bezeichnung,
        String abschnitt,
        String leistungsbereich,
        GozStatus status,
        Integer quote,
        Leistungsgrenzen grenzen,
        String begruendung) {

    public GozBefund {
        if (nummer == null || nummer.isBlank()) {
            throw new IllegalArgumentException("Ein Befund braucht die gepruefte Gebuehrennummer");
        }
        if (status == null) {
            throw new IllegalArgumentException("Ein Befund braucht einen Status");
        }
        if (begruendung == null || begruendung.isBlank()) {
            throw new IllegalArgumentException(
                    "Ein Befund braucht eine Begruendung; sie ist der Satz, mit dem beraten wird");
        }
        checkStatusPromises(status, leistungsbereich, quote, grenzen, bezeichnung, abschnitt);
    }

    public static GozBefund included(String nummer, String bezeichnung, String abschnitt,
                                      String leistungsbereich, int quote, Leistungsgrenzen grenzen,
                                      String begruendung) {
        return new GozBefund(nummer, bezeichnung, abschnitt, leistungsbereich,
                GozStatus.ENTHALTEN, quote, grenzen, begruendung);
    }

    public static GozBefund notIncluded(String nummer, String bezeichnung, String abschnitt,
                                           String leistungsbereich, String begruendung) {
        return new GozBefund(nummer, bezeichnung, abschnitt, leistungsbereich,
                GozStatus.NICHT_ENTHALTEN, null, null, begruendung);
    }

    public static GozBefund notDeterminable(String nummer, String bezeichnung, String abschnitt,
                                            String begruendung) {
        return new GozBefund(nummer, bezeichnung, abschnitt, null,
                GozStatus.NICHT_BESTIMMBAR, null, null, begruendung);
    }

    public static GozBefund unknown(String nummer, String begruendung) {
        return new GozBefund(nummer, null, null, null,
                GozStatus.UNBEKANNT, null, null, begruendung);
    }

    private static void checkStatusPromises(GozStatus status, String leistungsbereich, Integer quote,
                                            Leistungsgrenzen grenzen, String bezeichnung,
                                            String abschnitt) {
        switch (status) {
            case ENTHALTEN -> {
                require(leistungsbereich != null, "ENTHALTEN ohne Leistungsbereich");
                require(quote != null, "ENTHALTEN ohne Quote");
                require(quote == null || (quote >= 0 && quote <= 100),
                        "Quote ausserhalb von 0 bis 100: " + quote);
                require(grenzen != null, "ENTHALTEN ohne Grenzen; ohne eigene Grenze gehoert "
                        + "Leistungsgrenzen.keine() in den Befund, nicht null");
            }
            case NICHT_ENTHALTEN -> {
                require(leistungsbereich != null, "NICHT_ENTHALTEN ohne Leistungsbereich");
                requireWithoutZusage(quote, grenzen, status);
            }
            case NICHT_BESTIMMBAR -> {
                require(leistungsbereich == null,
                        "NICHT_BESTIMMBAR mit Leistungsbereich " + leistungsbereich);
                requireWithoutZusage(quote, grenzen, status);
            }
            case UNBEKANNT -> {
                require(leistungsbereich == null,
                        "UNBEKANNT mit Leistungsbereich " + leistungsbereich);
                require(bezeichnung == null && abschnitt == null,
                        "UNBEKANNT mit Angaben aus der GOZ, die es fuer diese Nummer nicht gibt");
                requireWithoutZusage(quote, grenzen, status);
            }
        }
    }

    private static void requireWithoutZusage(Integer quote, Leistungsgrenzen grenzen, GozStatus status) {
        require(quote == null, status + " mit Quote");
        require(grenzen == null, status + " mit Grenzen");
    }

    private static void require(boolean bedingung, String message) {
        if (!bedingung) {
            throw new IllegalArgumentException(message);
        }
    }
}
