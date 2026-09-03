package consulting.atra.wissen.mcp;

import consulting.atra.wissen.api.generated.model.Leistungsbereich;
import consulting.atra.wissen.api.generated.model.Tarifschluessel;
import consulting.atra.wissen.comparison.TarifComparison;
import consulting.atra.wissen.comparison.ComparisonResult;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

@Service
@SuppressWarnings("unused")
public class TarifwerkTools {

    private static final String TARIFE = """
            Die zu vergleichenden Tarife, technische Schlüssel: ATRA_DENT_S \
            (atra.dent.smart), ATRA_DENT_B (atra.dent.balance), ATRA_DENT_X \
            (atra.dent.brillant), ATRA_DENT_X_SB (atra.dent.brillant mit \
            Selbstbehalt). Ohne Angabe kommen alle vier zurück. Sinnvoll ist \
            die Einschränkung auf die Tarife, um die es im Gespräch geht: Eine \
            Tabelle mit vier Spalten beantwortet eine Frage nach zweien nicht \
            besser, sondern unübersichtlicher.""";

    private static final String AREAS = """
            Die zu vergleichenden Leistungsbereiche: ZE (Zahnersatz), IMP \
            (Implantate), INL (Inlays und Onlays), ZERH (Zahnerhalt), PAR \
            (Parodontosebehandlung), PZR (Prophylaxe und professionelle \
            Zahnreinigung), KFO (Kieferorthopädie), FUN (Funktionsanalyse und \
            Aufbissschiene), NAR (Narkose und Sedierung), AKUT \
            (Schmerzausschaltung und Akutbehandlung). Ohne Angabe kommen alle \
            zehn zurück. Auch hier gilt: Wer nach Implantaten fragt, braucht \
            keine Zeile zur Prophylaxe.""";

    private final TarifComparison comparison;

    public TarifwerkTools(TarifComparison comparison) {
        this.comparison = Objects.requireNonNull(comparison, "vergleich");
    }

    @McpTool(name = "tarife_vergleichen", description = """
            Stellt Tarife von atra.dent nebeneinander: je Tarif Selbstbehalt, \
            Jahreshöchstgrenze, Wartezeit, Eintrittsalter und Zahnstaffel, je \
            Leistungsbereich Quote und Grenzen aller angefragten Tarife in \
            einer Zeile.

            Das ist das Produktmodell — dieselbe Datenbasis, aus der die \
            Bedingungswerke erzeugt werden, nur strukturiert statt als Text. \
            Es eignet sich, um Unterschiede zu FINDEN: Wo zwei Tarife dieselbe \
            Quote tragen und sich nur im Jahreslimit unterscheiden, steht das \
            hier nebeneinander und ist in vier einzelnen Textsuchen kaum zu \
            sehen.

            ES IST KEIN BELEG. Eine Quote aus diesem Ergebnis ist eine Angabe \
            des Produktmodells und keine zitierfähige Fundstelle. Wer in Prosa \
            behauptet, was ein Tarif leistet, belegt das weiterhin über \
            bedingungen_suchen und nennt die Stelle im Satz. Zu jedem Tarif \
            steht deshalb der Verweis auf sein Bedingungswerk mit im Ergebnis.

            Das Tool rechnet nicht. Beiträge liegen im Rechenkern \
            (beitrag_berechnen), und ob eine einzelne Gebührennummer enthalten \
            ist, beantwortet goz_pruefen. Was am Ende erstattet wird, hängt an \
            Vertrag und bisherigem Verbrauch und steht hier nicht.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    public ComparisonResult compareTarife(
            @McpToolParam(required = false, description = TARIFE) List<Tarifschluessel> tarife,
            @McpToolParam(required = false, description = AREAS) List<Leistungsbereich> leistungsbereiche) {

        return comparison.compare(
                key(tarife, Tarifschluessel::getValue),
                key(leistungsbereiche, Leistungsbereich::getValue));
    }

    private static <T> List<String> key(List<T> values, Function<T, String> value) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).map(value).toList();
    }
}
