package consulting.atra.produktmodell;

import java.util.List;

public record Ausschluss(String schluessel, String text, Exemption ausnahme) {

    public record Exemption(List<String> tarife, String text) {
    }
}
