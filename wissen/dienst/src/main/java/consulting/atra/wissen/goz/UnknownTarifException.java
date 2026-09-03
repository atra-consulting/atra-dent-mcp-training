package consulting.atra.wissen.goz;

import java.util.List;

public class UnknownTarifException extends IllegalArgumentException {

    private final String tarif;
    private final List<String> validTarife;

    public UnknownTarifException(String tarif, List<String> gueltigeTarife) {
        super("Tarif '" + tarif + "' ist nicht bekannt. Gültig sind "
                + String.join(", ", gueltigeTarife));
        this.tarif = tarif;
        this.validTarife = List.copyOf(gueltigeTarife);
    }

    public String tarif() {
        return tarif;
    }

    public List<String> validTarife() {
        return validTarife;
    }
}
