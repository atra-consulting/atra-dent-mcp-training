package consulting.atra.wissen.mcp;

import consulting.atra.wissen.api.Mapping;
import consulting.atra.wissen.api.generated.model.GozPruefungResult;
import consulting.atra.wissen.api.generated.model.SucheResult;
import consulting.atra.wissen.api.generated.model.Tarifschluessel;
import consulting.atra.wissen.goz.GozBefund;
import consulting.atra.wissen.goz.GozPruefung;
import consulting.atra.wissen.search.BedingungenSearch;
import consulting.atra.wissen.search.Hit;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@SuppressWarnings("unused")
public class WissenTools {

    private static final String TARIF = """
            Pflichtangabe. Technischer Schlüssel des Tarifs, einer von vier: \
            ATRA_DENT_S (atra.dent.smart), ATRA_DENT_B (atra.dent.balance), \
            ATRA_DENT_X (atra.dent.brillant), ATRA_DENT_X_SB (atra.dent.brillant \
            mit Selbstbehalt). Die vier Tarife unterscheiden sich in Quoten, \
            Wartezeiten und Ausschlüssen und widersprechen einander \
            entsprechend; ohne Tarif wäre eine Antwort nicht ungenau, sondern \
            beliebig. Ist der Tarif des Kunden nicht bekannt, muss danach \
            gefragt werden — raten ist hier falsch.""";

    private static final String QUESTION = """
            Die Frage in natürlicher Sprache, 3 bis 500 Zeichen, am besten so \
            gestellt, wie der Kunde sie stellt. Ganze Fragen finden besser als \
            einzelne Stichworte.""";

    private static final String COUNT = """
            Höchstzahl der Treffer, 1 bis 20. Ohne Angabe 5. Das reicht für \
            eine belegte Antwort; mehr Treffer kosten Kontext, ohne die Antwort \
            besser zu machen.""";

    private static final String NUMBERS = """
            Die zu prüfenden Gebührennummern, üblicherweise die Positionen \
            einer Zahnarztrechnung. Jede Nummer ist vierstellig und wird als \
            String übergeben, mit führenden Nullen ("0010") — als Zahl \
            gingen sie verloren. Doppelte Nummern sind zulässig, weil eine \
            Rechnung dieselbe Leistung mehrfach ausweisen kann; sie werden \
            einzeln beantwortet. Die Befunde kommen in der Reihenfolge der \
            Nummern zurück.""";

    private final BedingungenSearch search;
    private final GozPruefung pruefung;

    public WissenTools(BedingungenSearch suche, GozPruefung pruefung) {
        this.search = Objects.requireNonNull(suche, "suche");
        this.pruefung = Objects.requireNonNull(pruefung, "pruefung");
    }

    @McpTool(name = "bedingungen_suchen", description = """
            Sucht in den Versicherungsbedingungen eines Tarifs der \
            Zahnzusatzversicherung atra.dent und liefert die Passagen, die eine \
            Frage behandeln — absteigend nach Ähnlichkeit.

            Jeder Treffer nennt seine Fundstelle: Dokument, Abschnittskennung \
            und zitierfähige Überschrift ("§ 4 Wartezeiten") sowie einen \
            Verweis auf die HTML-Fassung mit Sprungmarke und auf das PDF. Damit \
            lässt sich belegen, worauf eine Antwort sich stützt; ohne Beleg \
            sollte aus diesen Texten nicht geantwortet werden.

            Durchsucht werden Absätze und Tabellen gleichermaßen, das \
            Leistungsverzeichnis (Anlage A) eingeschlossen — es trägt die Quoten \
            und Grenzen und ist damit die häufigste Fundstelle überhaupt. \
            Gesucht wird nur in den Dokumenten, die für den angegebenen Tarif \
            gelten: sein Bedingungswerk, der Tarifvergleich und die \
            GOZ-Zuordnung.

            Das Tool findet Textstellen. Es rechnet nicht, entscheidet nicht \
            über Erstattungen und ersetzt keine Leistungsprüfung. Ob eine \
            einzelne Gebührennummer im Tarif enthalten ist, beantwortet \
            goz_pruefen. Eine leere Trefferliste ist ein gültiges Ergebnis und \
            heißt: dazu steht dort nichts.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    public SucheResult searchBedingungswerk(
            @McpToolParam(required = true, description = TARIF) Tarifschluessel tarif,
            @McpToolParam(required = true, description = QUESTION) String frage,
            @McpToolParam(required = false, description = COUNT) Integer anzahl) {

        Objects.requireNonNull(tarif, "tarif ist Pflicht und muss einer der vier Tarifschluessel sein");
        int desired = anzahl == null ? BedingungenSearch.DEFAULT_COUNT : anzahl;
        if (desired < 1 || desired > 20) {
            throw new IllegalArgumentException(
                    "anzahl muss zwischen 1 und 20 liegen, war " + desired);
        }

        List<Hit> hits = search.search(tarif.getValue(), frage, desired);
        return new SucheResult(tarif, frage, hits.stream().map(Mapping::toApi).toList());
    }

    @McpTool(name = "goz_pruefen", description = """
            Prüft Gebührennummern nach der Gebührenordnung für Zahnärzte (GOZ) \
            gegen einen Tarif von atra.dent. Der Regelfall ist eine ganze \
            Zahnarztrechnung, deshalb nimmt das Tool eine Liste und nicht \
            eine einzelne Nummer.

            Je Nummer kommt ein Befund zurück, mit einem von vier Zuständen:

            ENTHALTEN — die Nummer gehört zu einem Leistungsbereich, der in \
            diesem Tarif versichert ist. Quote und Grenzen stehen im Befund.

            NICHT_ENTHALTEN — der Leistungsbereich ist in diesem Tarif nicht \
            versichert. Eine klare Absage.

            NICHT_BESTIMMBAR — die Nummer steht in der GOZ, lässt sich aber \
            keinem Leistungsbereich zuordnen, weil sie einer Hauptbehandlung \
            dient (Untersuchung, Planung, Abformung, Zuschlag) oder in mehreren \
            Bereichen vorkommen kann. Das ist KEINE Ablehnung. Richtig ist die \
            Rückfrage, zu welcher Behandlung die Position gehört; erst daraus \
            ergibt sich, ob der Tarif dafür leistet. Der Fall ist häufig und \
            fachlich gewollt.

            UNBEKANNT — die Nummer steht nicht in Anlage 1 zur GOZ. Das \
            betrifft vor allem Leistungen, die der Zahnarzt über die \
            Gebührenordnung für Ärzte abrechnet, darunter Vollnarkose, \
            Sedierung und Röntgenaufnahmen. Auch hier ist die Rückfrage die \
            richtige Reaktion, keine Ablehnung.

            Wer NICHT_BESTIMMBAR oder UNBEKANNT gegenüber einem Kunden als \
            "nicht versichert" wiedergibt, antwortet falsch. Das Feld \
            begruendung enthält zu jedem Befund einen fertigen Satz und nennt \
            bei diesen beiden Zuständen, was zu klären ist.

            Die Prüfung sagt, OB eine Leistung dem Grunde nach versichert ist. \
            Sie berechnet keine Erstattung: Selbstbehalt, Zahnstaffel, \
            Jahreshöchstgrenze und die Vorleistung der gesetzlichen \
            Krankenversicherung hängen am Vertrag und am bisherigen Verbrauch \
            und liegen bei erstattung_berechnen im Rechenkern -- der Bereich \
            aus diesem Befund ist dessen Eingabe. Die genannte Quote versteht \
            sich einschließlich der Vorleistung der gesetzlichen Krankenkasse.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    public GozPruefungResult checkGoz(
            @McpToolParam(required = true, description = TARIF) Tarifschluessel tarif,
            @McpToolParam(required = true, description = NUMBERS) List<String> nummern) {

        Objects.requireNonNull(tarif, "tarif ist Pflicht und muss einer der vier Tarifschluessel sein");
        if (nummern == null || nummern.isEmpty()) {
            throw new IllegalArgumentException(
                    "nummern darf nicht leer sein: ohne Gebuehrennummer gibt es nichts zu pruefen");
        }
        List<String> unsuitable = nummern.stream().filter(n -> n == null || !n.matches("[0-9]{4}")).toList();
        if (!unsuitable.isEmpty()) {
            throw new IllegalArgumentException("Gebuehrennummern sind vierstellig und werden als "
                    + "String angegeben, mit fuehrenden Nullen. Nicht verwertbar: " + unsuitable);
        }

        List<GozBefund> findings = pruefung.check(tarif.getValue(), nummern);
        return new GozPruefungResult(tarif, findings.stream().map(Mapping::toApi).toList());
    }
}
