package consulting.atra.wissen.mcp;

import consulting.atra.wissen.beratung.AngerateneBehandlung;
import consulting.atra.wissen.beratung.BeratungRequest;
import consulting.atra.wissen.beratung.BeratungsResult;
import consulting.atra.wissen.beratung.BeratungSearch;
import consulting.atra.wissen.beratung.BeratungHit;
import consulting.atra.wissen.beratung.LeitfadenCatalog;
import consulting.atra.wissen.beratung.Tarifempfehlung;
import consulting.atra.wissen.beratung.Vorversicherung;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@SuppressWarnings("unused")
public class BeratungTools {

    private static final String CONFIDENTIALITY = """
            INTERNES STEUERUNGSWISSEN, NICHT ZUR VORLAGE BEIM KUNDEN. Die \
            Antwort führt den Berater; sie ist kein Vertragstext. Sie wird \
            gegenüber dem Kunden nicht zitiert — weder wörtlich noch sinngemäß \
            als Auskunft darüber, was versichert ist. Was gegenüber dem Kunden \
            gilt, steht ausschließlich in den Bedingungswerken. Widerspricht \
            eine Angabe von hier einem Bedingungswerk, gilt ohne Ausnahme das \
            Bedingungswerk; dann ist die Bedingungsstelle zu nennen und zu \
            zitieren — zu finden mit bedingungen_suchen — und nicht der \
            Leitfaden.""";

    private static final String QUESTION = """
            Die Frage aus der Beratungssituation, in natürlicher Sprache, 3 bis \
            500 Zeichen. Am besten so gestellt, wie die Lage im Gespräch \
            wirklich ist: "Der Kunde sagt, es sei zu teuer", "Wie steige ich ins \
            Gespräch ein?", "Der Kunde hat schon einen Heil- und Kostenplan". \
            Ganze Sätze finden besser als einzelne Stichworte.""";

    private static final String COUNT = """
            Höchstzahl der Abschnitte, 1 bis 20. Ohne Angabe 5. Die Abschnitte \
            sind ausführlich; mehr als eine Handvoll kostet Kontext, ohne die \
            Antwort besser zu machen.""";

    private static final String ALTER = """
            Pflichtangabe. Alter der Kundin oder des Kunden bei Vertragsbeginn \
            in Jahren. Einzige Angabe, die für sich allein einen Tarif streichen \
            kann: brillant und brillant mit Selbstbehalt sind nur von 18 bis 65 \
            abschließbar. Ist das Alter nicht bekannt, muss danach gefragt \
            werden — raten ergibt eine Empfehlung, die im Antrag scheitert.""";

    private static final String FOCUS_AREAS = """
            Leistungsbereiche, in denen Bedarf absehbar ist, als Schlüssel des \
            Produktmodells: ZE (Zahnersatz), ZB (Zahnbehandlung), PZR \
            (professionelle Zahnreinigung), IMP (Implantate), KFO \
            (Kieferorthopädie), PAR (Parodontose), FAL (Funktionsanalyse), NAR \
            (Narkose). Leer lassen, solange die Bedarfsanalyse nichts ergeben \
            hat — ein erfundener Schwerpunkt verschiebt die Empfehlung. Ein \
            Schlüssel, den das Produktmodell nicht kennt, ist ein Fehler und \
            wird nicht stillschweigend übergangen.""";

    private static final String ANGERATEN = """
            Ob vor Vertragsschluss bereits eine Behandlung angeraten oder \
            begonnen wurde, etwa durch einen Heil- und Kostenplan. KEINE = \
            ausdrücklich gefragt und verneint. ANGERATEN = es liegt etwas vor. \
            NICHT_ERHOBEN = die Frage wurde noch nicht gestellt; das ist der \
            Vorgabewert und etwas anderes als KEINE — die ungestellte Frage \
            erzeugt einen vorrangigen Hinweis, weil angeratene Behandlungen in \
            allen vier Tarifen ausgeschlossen sind.""";

    private static final String MISSING_ZAEHNE = """
            Anzahl der bei Vertragsschluss fehlenden, nicht ersetzten Zähne, 0 \
            bis 32. Weglassen heißt "nicht erhoben" und ist etwas anderes als 0. \
            Fehlende Zähne streichen keinen Tarif, sie verschieben die \
            Empfehlung: nur brillant und brillant mit Selbstbehalt sehen dafür \
            eine Exception vor.""";

    private static final String VORVERSICHERUNG = """
            Bestehender Vorversicherungsschutz. KEINE = ausdrücklich gefragt, es \
            besteht keiner. LUECKENLOS = lückenloser Vorschutz; dann entfällt \
            bei den Tarifen, die das vorsehen, die allgemeine Wartezeit. \
            NICHT_ERHOBEN = noch nicht gefragt, Vorgabewert.""";

    private static final String DESCRIPTION_SEARCH = """
            Sucht im internen Beratungshandbuch zu atra.dent und liefert die \
            Abschnitte, die eine Frage aus der Beratungspraxis behandeln — \
            absteigend nach Ähnlichkeit.

            Das Handbuch führt durch das Gespräch und behandelt drei Kapitel: \
            Gesprächsführung (Eröffnung, Überleitung zur Bedarfsermittlung, \
            Pflichtangaben, Bedenkzeit, Abschluss, Nachbereitung), \
            Bedarfsanalyse (Fragenkatalog, angeratene Behandlungen, fehlende \
            Zähne, Vorversicherung, Eintrittsalter, Implantat- und KFO-Bedarf, \
            Budget, Ableitungsschema) und Einwandbehandlung (zu teuer, kein \
            Bedarf, Wartezeit, Rücksprache mit dem Partner, "die Krankenkasse \
            zahlt doch", schlechte Erfahrungen, zu alt, Selbstbehalt, \
            Kündigung, Einwand nach einer Diagnose).

            Kein Tarifparameter, und das ist kein Versehen: Gesprächsführung, \
            Bedarfsanalyse und Einwandbehandlung gelten für alle vier Tarife \
            gleichermaßen. Wo ein Abschnitt Tarifwerte nennt, nennt er sie für \
            alle vier nebeneinander.
            """
            + "\n" + CONFIDENTIALITY + "\n\n"
            + """
            Das Tool beantwortet keine Vertragsfrage. Was ein Tarif \
            leistet, steht in den Bedingungswerken und wird mit \
            bedingungen_suchen gesucht; ob eine Gebührennummer dem Grunde nach \
            versichert ist, sagt goz_pruefen. Welcher Tarif zu einer Bedarfslage \
            passt, rechnet tarifempfehlung aus Regeln aus, statt es hier zu \
            suchen. Eine leere Trefferliste ist ein gültiges Ergebnis und heißt: \
            dazu steht im Handbuch nichts.""";

    private static final String DESCRIPTION_EMPFEHLUNG = """
            Leitet aus einer strukturierten Bedarfslage her, welche Tarife von \
            atra.dent in Betracht kommen, in welcher Reihenfolge und warum. Die \
            Antwort ist gerechnet und nicht gesucht: Sie folgt den Regeln des \
            Beratungsleitfadens und den Werten des Produktmodells und fällt bei \
            gleicher Eingabe immer gleich aus.

            DER BEDARF GEHT VOR. Die Empfehlungsreihenfolge des Leitfadens \
            (brillant, brillant mit Selbstbehalt, balance, smart) greift \
            ausschließlich bei gleichwertiger Eignung — sie entscheidet den \
            Zweifelsfall, nicht den Bedarfsfall. Sobald eine Angabe die \
            verbliebenen Tarife fachlich unterscheidet, ordnet der Bedarf, auch \
            gegen die Reihenfolge. Welche Regel getragen hat, steht im Feld weg: \
            BEDARF (der Bedarf hat geordnet), REIHENFOLGE (nichts unterschied \
            die Tarife, erst dann galt die Reihenfolge), KEIN_TARIF (alle Tarife \
            scheiden an harten Kriterien aus). Die Reihenfolge ist keine \
            Vertriebsvorgabe und darf nicht als solche wiedergegeben werden.

            Das Ergebnis führt getrennt: empfehlungen (geordnet, mit Rang, \
            Begründung, Bedarfsgründen und der Hinweispflicht des Tarifs), \
            ausgeschlossene (Tarife, die an einem harten Kriterium ausscheiden — \
            keine schwächeren Empfehlungen, sondern gar keine), hinweise (was \
            ungefragt anzusprechen ist, Vorrangiges zuerst), grundsatz und \
            complianceGrenzen. Hinweise und Compliance-Grenzen gehören zur \
            Antwort und werden nicht weggelassen: Eine ungestellte Frage nach \
            angeratenen Behandlungen oder fehlenden Zähnen erscheint dort als \
            eigener Punkt.
            """
            + "\n" + CONFIDENTIALITY + "\n\n"
            + """
            Das Tool nennt keine Beiträge und rechnet keine Erstattung: \
            Beiträge sind nicht Teil des Produktmodells, sie führt \
            beitrag_berechnen im Rechenkern. Ein genannter Budgetrahmen ist \
            deshalb kein Parameter — er wäre eine Prüfung, die nicht \
            stattfindet. Wie hoch eine Erstattung im Einzelfall ausfällt, \
            beantwortet weder dieses Tool noch der Leitfaden, sondern \
            erstattung_berechnen im Rechenkern.""";

    private final BeratungSearch search;
    private final Tarifempfehlung recommendation;
    private final LeitfadenCatalog leitfaden;

    public BeratungTools(BeratungSearch suche, Tarifempfehlung empfehlung,
                             LeitfadenCatalog leitfaden) {
        this.search = Objects.requireNonNull(suche, "suche");
        this.recommendation = Objects.requireNonNull(empfehlung, "empfehlung");
        this.leitfaden = Objects.requireNonNull(leitfaden, "leitfaden");
    }

    @McpTool(name = "beratungsleitfaden_suchen", description = DESCRIPTION_SEARCH,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    public LeitfadenResult searchBeratungsleitfaden(
            @McpToolParam(required = true, description = QUESTION) String frage,
            @McpToolParam(required = false, description = COUNT) Integer anzahl) {

        int desired = anzahl == null ? BeratungSearch.DEFAULT_COUNT : anzahl;
        if (desired < 1 || desired > 20) {
            throw new IllegalArgumentException(
                    "anzahl muss zwischen 1 und 20 liegen, war " + desired);
        }

        List<BeratungHit> hits = search.search(frage, desired);
        return new LeitfadenResult(frage, leitfaden.confidentiality(), CONFIDENTIALITY, hits);
    }

    @McpTool(name = "tarifempfehlung", description = DESCRIPTION_EMPFEHLUNG,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    openWorldHint = false))
    public BeratungsResult tarifempfehlung(
            @McpToolParam(required = true, description = ALTER) Integer alter,
            @McpToolParam(required = false, description = FOCUS_AREAS) List<String> behandlungsschwerpunkte,
            @McpToolParam(required = false, description = ANGERATEN) AngerateneBehandlung angerateneBehandlung,
            @McpToolParam(required = false, description = MISSING_ZAEHNE) Integer fehlendeZaehne,
            @McpToolParam(required = false, description = VORVERSICHERUNG) Vorversicherung vorversicherung) {

        if (alter == null) {
            throw new IllegalArgumentException("alter ist Pflicht: ohne das Eintrittsalter laesst "
                    + "sich nicht sagen, welche Tarife ueberhaupt abschliessbar sind");
        }

        BeratungRequest anliegen = new BeratungRequest(
                alter,
                behandlungsschwerpunkte == null ? List.of() : behandlungsschwerpunkte,
                angerateneBehandlung,
                fehlendeZaehne,
                vorversicherung);
        return recommendation.recommend(anliegen);
    }
}
