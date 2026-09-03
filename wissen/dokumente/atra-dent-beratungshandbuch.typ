// Beratungshandbuch atra.dent
//
// Internes Steuerungswissen fuer die Beratung, in drei Kapiteln:
// Gespraechsfuehrung, Bedarfsanalyse, Einwandbehandlung.
//
// Dieses Dokument gehoert zur Dokumentfamilie der Bedingungswerke — gleiche
// Vorlage, gleiches Corporate Design — steht aber auf der anderen Seite einer
// Grenze, die das ganze Haus traegt: Die Bedingungswerke sind veroeffentlichte
// Verbraucherdokumente und zitierfaehig. Dieses Handbuch ist es nicht. Es wird
// dem Kunden nicht vorgelegt, und wo es einem Bedingungswerk widerspricht,
// gilt das Bedingungswerk. Deshalb traegt es als einziges Dokument der
// Familie eine Vertraulichkeitskennzeichnung: auf der Titelseite als Hinweis,
// auf jeder weiteren Seite als Vermerk in der Fusszeile.
//
// Diese Datei ist die einzige Quelle des Handbuchtextes. Die frueheren
// Markdown-Kapitel unter wissen/daten/beratung/gespraeche/ sind hier
// aufgegangen und entfallen — zwei Quellen fuer denselben Text laufen
// auseinander, und zwar immer zuungunsten der Fassung, die ausgeliefert wird.
//
// Der Regelteil bleibt getrennt: wissen/daten/beratung/leitfaden.yaml enthaelt,
// was sich maschinell auswerten laesst (Empfehlungsreihenfolge, harte
// Kriterien, Compliance-Grenzen); hier steht die Prosa, die sich nicht in
// Regeln fassen laesst. Das Handbuch widerspricht dem Regelteil nicht,
// sondern setzt ihn in Sprache um.
//
// Zahlen: Tabellen und Aufzaehlungen lesen das Produktmodell aus
// wissen/daten/tarife.yaml, wie die Bedingungswerke auch. Ausgenommen sind
// die Musterformulierungen. Sie stehen in woertlicher Rede und werden genau
// so wiedergegeben, wie sie am Telefon gesprochen werden — ein interpolierter
// Betrag mitten im Satz waere kein Zitat mehr, sondern eine Vorlage, die bei
// der naechsten Modellaenderung stillschweigend eine andere Aussage macht.
// Was sich am Modell aendert, faellt in den Tabellen daneben sofort auf.

#import "lib/vorlage.typ": *

// --- Tarife in der Empfehlungsreihenfolge ------------------------------
//
// Die Reihenfolge steht im Regelteil (leitfaden.yaml) und lautet brillant,
// brillant mit Selbstbehalt, balance, smart. Sie wird hier als Anordnung der
// Tabellenzeilen wiederholt, damit das Handbuch die Reihenfolge zeigt, ueber
// die es spricht.

#let smart = tarif("ATRA_DENT_S")
#let balance = tarif("ATRA_DENT_B")
#let brillant = tarif("ATRA_DENT_X")
#let brillant-sb = tarif("ATRA_DENT_X_SB")
#let alle-tarife = (brillant, brillant-sb, balance, smart)

// --- Bausteine dieses Dokuments ----------------------------------------

// Ein Stichwort am Absatzanfang. Die Kapitel sind nach Situationen
// gegliedert; das Stichwort ersetzt eine weitere Ueberschriftenebene, die
// den Suchindex mit Fragmenten fluten wuerde.
#let stichwort(body) = text(weight: "medium", fill: dunkelblau, body)

// Musterformulierung: woertliche Rede, die uebernommen werden kann.
//
// Der praktische Wert des Handbuchs steckt in diesen Saetzen, deshalb sind sie
// abgesetzt und nicht in den Fliesstext eingebettet. Zu jeder gehoert, wann
// sie passt und wann nicht — eine Formulierung am falschen Ort richtet mehr
// Schaden an als gar keine, und wer nur den Satz mitnimmt, nimmt die
// gefaehrlichere Haelfte mit.
//
// Umbrechbar, weil manche Muster laenger als ein halber Seitenrest sind; ein
// bindender Kasten erzwaenge dort eine halbleere Seite.
#let muster(anlass: none, passt: none, passt-nicht: none, body) = block(
  width: 100%,
  fill: orange-kasten,
  stroke: (left: 2.5pt + dunkelorange),
  inset: (x: 12pt, y: 10pt),
  above: 1.2em,
  below: 1.2em,
  radius: (right: 2pt),
  breakable: true,
  {
    text(
      size: 8pt,
      weight: "bold",
      fill: dunkelorange,
      tracking: 0.5pt,
      upper(if anlass == none { "Musterformulierung" } else {
        "Musterformulierung — " + anlass
      }),
    )
    v(-0.3em)
    set text(size: 9.4pt)
    body
    if passt != none {
      parbreak()
      set text(size: 8.5pt, fill: dunkelgrau)
      stichwort[Passt] + [ ] + passt
    }
    if passt-nicht != none {
      parbreak()
      set text(size: 8.5pt, fill: dunkelgrau)
      stichwort[Passt nicht] + [ ] + passt-nicht
    }
  },
)

// Die Leistungsbereiche, die smart nicht versichert. Sie sind die
// Hinweispflicht dieses Tarifs und kommen im Handbuch an drei Stellen vor —
// einmal abgeleitet statt dreimal abgeschrieben. Als eigener Wert und nicht
// als Kette im Fliesstext: In der Auszeichnungssprache endet ein Ausdruck am
// Zeilenende, eine ueber mehrere Zeilen fortgesetzte Methodenkette landet
// dort als Text im Dokument.
#let smart-luecken = {
  bereich-schluessel
    .filter(k => not leistungen(smart).at(k).versichert)
    .map(k => bereich(k).name)
    .join(", ", last: " sowie ")
}

// Querverweis auf einen Abschnitt dieses Handbuchs.
//
// Nicht `@marke`: Die Abschnittsnummerierung traegt einen Punkt ("3."), und
// der Standardverweis zieht ihn mit in den Satz — vor einem Satzpunkt stuenden
// dann zwei. Der Verweis liest die Nummer deshalb selbst aus dem
// Ueberschriftenzaehler. Die Marke bleibt der Anker; laeuft sie ins Leere,
// bricht der Build, und genau das soll ein toter Verweis tun.
#let verweis-auf(marke) = context {
  let ziel = query(marke)
  assert(ziel.len() == 1, message: "Querverweis ohne eindeutiges Ziel: " + str(marke))
  [Abschnitt #counter(heading).at(ziel.first().location()).first()]
}

// Kapitelauftakt. Traegt in der HTML-Fassung ein eigenes `section` mit
// sprechender Kennung, weil der Kapitelname und seine Abgrenzung Inhalt sind
// und nicht Seitenschmuck. Die Auszeichnung im PDF — Seitenwechsel und
// Orangelinie wie auf der Titelseite — greift nur dort.
#let kapitel(nummer, titel, id, inhalt) = {
  context if target() != "html" {
    pagebreak(weak: true)
    v(4mm)
    line(length: 28mm, stroke: 2.5pt + dunkelorange)
    v(2mm)
  }
  abschnitt(id, {
    heading(level: 1, numbering: none)[Kapitel #nummer · #titel]
    inhalt
  })
}

// Ein Leistungsbereich in allen vier Tarifen, aus dem Produktmodell gelesen.
// Quote und Begrenzung stehen nebeneinander, weil die Quote allein nicht
// ueber die Erstattung entscheidet und im Gespraech genau dieser Kurzschluss
// entsteht.
#let bereichstabelle(k, wartezeit: false) = {
  let spalten = if wartezeit { (1fr, 17mm, 1.3fr, 22mm) } else { (1fr, 17mm, 1.5fr) }
  let ausrichtung = if wartezeit { (left, right, left, left) } else { (left, right, left) }
  let kopf = if wartezeit {
    ("Tarif", "Quote", "Begrenzung", "Wartezeit")
  } else { ("Tarif", "Quote", "Begrenzung") }

  tabelle(
    spalten: spalten,
    ausrichtung: ausrichtung,
    klein: true,
    kopf: kopf,
    ..alle-tarife
      .map(t => {
        let e = leistungen(t).at(k)
        let zeile = (
          t.anzeigename,
          if e.versichert { text(weight: "medium", prozent(e.quote)) } else {
            text(fill: dunkelgrau)[—]
          },
          if not e.versichert { text(fill: dunkelgrau)[nicht versichert] } else {
            let g = begrenzungen(e, kurz: true)
            if g.len() == 0 { text(fill: dunkelgrau)[keine] } else { g.join("; ") }
          },
        )
        if wartezeit {
          zeile += (
            if not e.versichert { text(fill: dunkelgrau)[—] } else if (
              "wartezeit_monate" in e
            ) { monate(e.wartezeit_monate) } else { monate(t.wartezeit_monate) },
          )
        }
        zeile
      })
      .flatten()
  )
}

// =======================================================================

#show: avb-dokument.with(
  titel: "Beratungshandbuch",
  untertitel: "Gesprächsführung, Bedarfsanalyse, Einwandbehandlung",
  tarifname: "atra.dent",
  bedingungsnummer: "BHB 01/2026",
  stand: stand-text,
  paragraphen: false,
  vertraulichkeitsvermerk: "Intern — nicht zur Vorlage beim Kunden",
  vertraulichkeitshinweis: [
    Dieses Handbuch steuert die Beratung. Es wird Kundinnen und Kunden nicht
    vorgelegt, nicht vorgelesen und nicht zitiert. Was gegenüber dem Kunden
    gilt, steht ausschließlich in den Allgemeinen Versicherungsbedingungen und
    im Versicherungsschein. Wo dieses Handbuch und ein Bedingungswerk sich
    widersprechen, gilt das Bedingungswerk — ohne Ausnahme. Auf Nachfrage wird
    die Bedingungsstelle genannt, nicht dieses Handbuch.
  ],
)

#abschnitt("uebersicht")[
  #heading(level: 1, numbering: none, outlined: false)[Inhaltsübersicht]

  #outline(title: none, depth: 1, indent: 0pt)
]

#abschnitt("zweck-und-geltung")[
  = Zweck, Geltung und Vertraulichkeit dieses Handbuchs <marke-zweck-und-geltung>

  Dieses Handbuch beschreibt, wie ein Beratungsgespräch zu atra.dent geführt
  wird: wie es aufgebaut ist, wie der tatsächliche Bedarf ermittelt wird und
  wie auf Einwände geantwortet wird. Es ergänzt den Regelteil des
  Beratungsleitfadens um die Prosa, die sich nicht in Regeln fassen lässt. Wo
  der Regelteil eine Regel setzt, setzt dieses Handbuch sie in Sprache um; es
  widerspricht ihm nicht.

  #stichwort[Vertraulichkeit.] Das Handbuch ist internes Steuerungsmaterial.
  Es wird der Kundin oder dem Kunden nicht vorgelegt, nicht vorgelesen und
  nicht zitiert. Was gegenüber dem Kunden gilt, steht ausschließlich in den
  Bedingungswerken und im Versicherungsschein. Wo dieses Handbuch und ein
  Bedingungswerk sich widersprechen, gilt das Bedingungswerk — ohne Ausnahme.
  Bei Nachfragen zu Bedingungsinhalten wird die Bedingungsstelle genannt, nie
  dieses Handbuch: Interne Steuerungsunterlagen werden gegenüber Kunden weder
  zitiert noch erwähnt.

  #stichwort[Geltungsbereich.] Das Handbuch gilt für die Telefonberatung und
  für das persönliche Gespräch, für Erwachsene. Alle Leistungsangaben folgen
  dem Produktmodell atra.dent im Stand #stand-text: vier Tarife
  (#alle-tarife.map(t => t.anzeigename).join(", ", last: " und ")), Eintritt ab
  #str(smart.eintrittsalter.von) Jahren. Beiträge sind nicht Teil des
  Produktmodells; die Beitragshöhe steht im konkreten Angebot und im
  Versicherungsschein, nicht in diesem Handbuch. Wer hier eine Beitragszahl
  sucht, sucht an der falschen Stelle.

  #stichwort[Musterformulierungen.] Die abgesetzten Kästen enthalten wörtliche
  Rede. Sie sind Vorlagen, keine Skripte, und zu jeder gehört der Hinweis, wann
  sie passt und wann nicht. Eine Formulierung am falschen Ort richtet mehr
  Schaden an als gar keine.

  #hinweis(titel: "Fiktives Muster")[
    Alle Angaben sind erfunden. Es besteht kein Bezug zu realen Produkten,
    Anbietern oder Vertriebsvorgaben.
  ]
]

#abschnitt("grundsatz-bedarf-entscheidet")[
  = Grundsatz: Der Bedarf entscheidet, die Reihenfolge nur den Zweifelsfall <marke-grundsatz-bedarf-entscheidet>

  Der tragende Grundsatz der atra.dent-Beratung lautet: Maßgeblich ist der
  Bedarf. Die Beratung ermittelt zuerst, was gebraucht wird, und wählt danach
  den Tarif — nicht umgekehrt. Dieser Grundsatz trägt alle drei Kapitel dieses
  Handbuchs, und deshalb steht er vor ihnen.

  Die Empfehlungsreihenfolge des Vertriebs —
  #alle-tarife.map(t => t.anzeigename.replace("atra.dent.", "")).join(", ", last: " und ")
  — gilt ausschließlich bei gleichwertiger Eignung. Sie entscheidet den
  Zweifelsfall, nicht den Bedarfsfall. Ergibt das Gespräch, dass ein Tarif
  weiter hinten in der Reihenfolge besser passt, wird dieser empfohlen. Eine
  solche Abweichung braucht keine Begründung gegenüber dem Vertrieb. Eine
  Empfehlung gegen den erkennbaren Bedarf braucht sie sehr wohl.

  Der Grund für die Reihenfolge ist sachlich: Bei Zahnzusatzversicherungen ist
  die Nachversicherung der Regelfall, in dem etwas schiefgeht. Wer klein
  einsteigt und später aufstocken will, trifft auf eine erneute
  Gesundheitsprüfung, eine erneut beginnende Wartezeit und eine erneut
  beginnende Zahnstaffel. Deshalb wird bei sonst gleicher Eignung der
  umfassendere Tarif zuerst dargestellt.

  Daraus folgt die Pflicht zur Offenheit: Passt ein günstigerer Tarif ebenso
  gut, wird er genannt — auch ungefragt. Die Entscheidung trifft die Kundin
  oder der Kunde, nicht der Berater.

  Der Hintergrund ist nicht Moral, sondern Erfahrung. Ein Tarif, der nicht
  passt, wird storniert, sobald der erste Leistungsfall enttäuscht. Wer smart
  verkauft, obwohl ein Implantat absehbar ist, hat keinen Abschluss gemacht,
  sondern eine Beschwerde terminiert — in smart sind Implantate nicht
  versichert. Eine Empfehlung gegen den erkennbaren Bedarf schadet damit auch
  dem Vertrieb, nicht nur der Kundin oder dem Kunden.

  Daraus folgt die Haltung, die dieses Handbuch trägt: Die Beratung ermittelt,
  was da ist; sie erzeugt es nicht. Es wird kein Bedarf geweckt und keine Sorge
  gestreut, die vor dem Gespräch nicht bestand. Wer ein Risiko ausmalt, das der
  Kunde nicht sieht, verkauft vielleicht einen Vertrag und erzeugt sicher eine
  Stornoquote.

  Die praktische Konsequenz: Ein Gespräch, das mit einer ehrlichen Absage
  endet, ist ein gutes Gespräch. Es ist kein gescheitertes Gespräch, das man
  mit mehr Druck hätte retten können. Wer das innerlich nicht akzeptiert, hört
  im Gespräch weg, wenn der Bedarf nicht zur gewünschten Empfehlung passt —
  und genau dieses Weghören ist am Telefon hörbar.
]

// =======================================================================
// Kapitel 1 — Gespraechsfuehrung
// =======================================================================

#kapitel(1, "Gesprächsführung", "kapitel-gespraechsfuehrung")[
  Dieses Kapitel beschreibt, wie ein Beratungsgespräch geführt wird — mit
  Schwerpunkt auf dem Telefonat, weil dort die meisten Gespräche stattfinden
  und die Fehler die teuersten sind. Es behandelt die Führung des Gesprächs,
  nicht seinen fachlichen Inhalt.

  #hinweis(titel: "Abgrenzung")[
    Welche Fragen in der Bedarfsermittlung konkret gestellt werden, in welcher
    Reihenfolge und mit welchen Anschlussfragen, steht in Kapitel 2. Wie auf
    einen Einwand gegen eine bereits ausgesprochene Empfehlung geantwortet
    wird, steht in Kapitel 3. Der Grundsatz, der über allem steht, ist in
    #verweis-auf(<marke-grundsatz-bedarf-entscheidet>) beschrieben.
  ]
]

#abschnitt("telefon-besonderheiten")[
  = Was am Telefon anders ist als im persönlichen Gespräch <marke-telefon-besonderheiten>

  Am Telefon fehlt der gesamte visuelle Kanal. Das hat fünf konkrete Folgen für
  die Gesprächsführung.

  #stichwort[Pausen wirken länger.] Eine Denkpause von drei Sekunden fühlt sich
  am Telefon wie zehn an. Der Reflex, sie zuzureden, ist der häufigste Fehler.
  Wer eine Frage gestellt hat, wartet. Wer nachschaut oder rechnet, sagt das
  an: „Ich schaue kurz nach, einen Moment.“ Stille ohne Ankündigung wird als
  Verbindungsabbruch gedeutet.

  #stichwort[Zahlen kommen nicht an.] Im persönlichen Gespräch liegt ein Blatt
  auf dem Tisch. Am Telefon gilt: höchstens drei Zahlen pro Gedanke, und jede
  Zahl bekommt eine Einheit und einen Bezug. Nicht „85, 100, 4500“, sondern
  „85 Prozent beim Zahnersatz — und einmal im Jahr sind 100 Euro Selbstbehalt
  zu tragen.“ Alles, was über drei Zahlen hinausgeht, gehört in die
  schriftliche Zusammenfassung.

  #stichwort[Verstehen muss hörbar gemacht werden.] Nicken ist unsichtbar.
  Deshalb wird häufiger zurückgespiegelt als im persönlichen Gespräch: „Also:
  eine Krone ist angeraten, ein Termin steht noch nicht.“ Das ist keine
  Floskel, sondern der einzige Beleg, dass beide vom Gleichen reden.

  #stichwort[Ablenkung ist unsichtbar.] Der Kunde kocht, fährt, hat ein Kind
  auf dem Arm. Wer merkt, dass die Antworten kürzer und unspezifischer werden,
  fragt nach.

  #muster(
    anlass: "vermutete Ablenkung",
    passt: [wenn die Aufmerksamkeit erkennbar nachlässt.],
    passt-nicht: [als Vorwurf und nicht bei jemandem, der einfach knapp antwortet.],
  )[
    „Ich habe den Eindruck, es ist gerade ungünstig — soll ich lieber später
    noch einmal anrufen?“
  ]

  #stichwort[Der Abbruch ist billiger, das Dokument fehlt.] Auflegen kostet
  nichts — deshalb trägt am Telefon nur, was in den ersten dreißig Sekunden an
  Rahmen gesetzt wird. Und es gibt kein gemeinsames Blatt: Die Bedingungsstelle
  muss benannt und die Unterlage nachgereicht werden. Was nicht schriftlich
  nachkommt, gilt als nicht gesagt.
]

#abschnitt("gespraechseroeffnung")[
  = Die Gesprächseröffnung: Anlass, Rahmen und Einverständnis <marke-gespraechseroeffnung>

  Die Eröffnung hat vier Aufgaben, und zwar in dieser Reihenfolge: sagen, wer
  anruft; sagen, warum; den Zeitrahmen abstimmen; das Einverständnis einholen,
  jetzt zu sprechen. Erst danach beginnt Beratung.

  #muster(anlass: "eingehender Anruf")[
    „atra.dent-Beratung, mein Name ist [Name]. Womit kann ich Ihnen helfen?“
  ]

  Kurz, weil der Anrufende ein Anliegen hat und es loswerden will.

  #muster(
    anlass: "Rückruf auf eine Anfrage",
    passt-nicht: [wenn keine Anfrage vorliegt — dann darf auch keine behauptet
      werden.],
  )[
    „Guten Tag, [Name] von der atra.dent-Beratung. Sie hatten über das Formular
    um einen Rückruf zur Zahnzusatzversicherung gebeten — passt es Ihnen
    gerade, oder rufe ich zu einem besseren Zeitpunkt an?“
  ]

  Der Bezug auf die eigene Anfrage ist wesentlich: Er erklärt den Anruf, ohne
  dass ein Vorwand konstruiert wird.

  Der Zeitrahmen wird aktiv gesetzt. Diese Frage kostet dreißig Sekunden und
  rettet Gespräche, die sonst nach acht Minuten unter Zeitdruck kollabieren.

  #muster(anlass: "Zeitrahmen")[
    „Für einen ersten Überblick brauche ich etwa fünfzehn Minuten. Haben Sie
    die jetzt, oder machen wir lieber einen Termin aus?“
  ]

  Zur Eröffnung gehört ein Satz zur Rolle. Diese Offenheit kostet nichts und
  verhindert eine falsche Erwartung, die später als Vertrauensbruch
  zurückkommt.

  #muster(anlass: "eigene Rolle")[
    „Ich berate zu den atra.dent-Tarifen, also zu unseren eigenen Produkten —
    einen Marktvergleich kann ich Ihnen nicht anbieten.“
  ]

  Was in der Eröffnung nichts zu suchen hat: jede Andeutung von Dringlichkeit,
  jeder Hinweis auf angeblich auslaufende Konditionen, jede Aussage, die
  Beitragsvorteile an ein Datum knüpft. Künstliche Verknappung ist untersagt —
  sie verstößt gegen die Compliance-Grenzen und liefert der Kundin obendrein
  ein gutes Argument, sofort aufzulegen.
]

#abschnitt("ueberleitung-bedarfsermittlung")[
  = Die Überleitung von der Eröffnung zur Bedarfsermittlung <marke-ueberleitung-bedarfsermittlung>

  Hier entgleisen die meisten Gespräche: Der Berater springt vom Anlass direkt
  zum Tarif. Damit ist genau die Reihenfolge umgedreht, gegen die der Grundsatz
  schützt — erst der Bedarf, dann der Tarif.

  Sauber ist eine angekündigte Überleitung. Sie leistet drei Dinge: Sie
  begründet die Fragen, sie kündigt ihren Charakter an, und sie holt Zustimmung
  ein.

  #muster(
    anlass: "angekündigte Überleitung",
    passt: [in nahezu jedem Gespräch.],
    passt-nicht: [wenn die Kundin bereits von sich aus detailliert über ihre
      Zahnsituation gesprochen hat — dann wirkt sie wie ein Formular.],
  )[
    „Damit ich Ihnen nicht irgendetwas anbiete, sondern das Passende, würde ich
    Ihnen erst ein paar Fragen zu Ihrer Zahnsituation stellen. Manche davon
    sind etwas persönlich — Sie müssen nichts beantworten, was Sie nicht
    möchten. Ist das in Ordnung?“
  ]

  Hat die Kundin schon von sich aus erzählt, steigt man stattdessen mit einer
  Rückspiegelung ein.

  #muster(anlass: "Einstieg über eine Rückspiegelung")[
    „Sie hatten die Wurzelbehandlung erwähnt — da hake ich gleich noch einmal
    nach.“
  ]

  Ein zweiter Baustein ist der Hinweis auf die Gesundheitsangaben. Damit wird
  die Bedarfsermittlung von der Antragsaufnahme getrennt, was Rückfragen
  erspart.

  #muster(anlass: "Trennung von der Antragsaufnahme")[
    „Zum Gesundheitszustand frage ich später beim Antrag noch genauer — im
    Moment reicht mir ein grobes Bild.“
  ]

  An einer Stelle ist die Überleitung hart: Angaben zum Gesundheitszustand
  werden nur mit der versicherten Person selbst besprochen, nicht mit
  Angehörigen am Telefon. Meldet sich der Partner und will „für meine Frau“
  abschließen, wird die Bedarfsermittlung nicht begonnen, sondern verlegt.
]

#abschnitt("zuhoeren-und-rueckspiegeln")[
  = Zuhören, Rückspiegeln und Mitschreiben während der Bedarfsermittlung <marke-zuhoeren-und-rueckspiegeln>

  Während der Bedarfsermittlung führt der Berater das Gespräch, redet aber
  wenig. Faustregel: höchstens ein Drittel Redeanteil. Wer mehr redet, erfährt
  weniger. Drei Techniken tragen die Phase.

  #stichwort[Rückspiegeln in eigenen Worten.]

  #muster(
    anlass: "Rückspiegelung",
    passt: [wenn eine Aussage Folgen für die Empfehlung hat.],
    passt-nicht: [als Dauerbegleitung jedes Halbsatzes — dann wirkt es
      mechanisch.],
  )[
    „Verstehe ich das richtig: Sie haben seit Jahren keine größere Behandlung
    gehabt, aber Ihre Zahnärztin hat beim letzten Termin von einer Krone
    gesprochen?“
  ]

  #stichwort[Nachfassen bei Vagem.] „Von einer Krone gesprochen“ kann heißen:
  beiläufig erwähnt — oder es liegt ein Heil- und Kostenplan vor. Der
  Unterschied entscheidet, denn vor Vertragsschluss angeratene oder begonnene
  Behandlungen sind in allen Tarifen ausgeschlossen.

  #muster(anlass: "Nachfassen bei Vagem")[
    „Gibt es dazu schon einen Heil- und Kostenplan oder einen Termin?“
  ]

  #stichwort[Notieren, was zitiert werden muss.] Was später als Begründung
  auftaucht, muss wörtlich genug notiert sein, um es zurückgeben zu können.
  „Sie hatten gesagt, mehr als etwa dreißig Euro im Monat sollen es nicht
  werden“ ist stark, wenn es die tatsächliche Aussage ist, und peinlich, wenn
  nicht.

  Zwei Dinge geschehen hier nicht. Erstens wird nicht bewertet, ob eine
  Behandlung medizinisch notwendig oder sinnvoll ist — das entscheidet die
  Zahnärztin oder der Zahnarzt. Auf „Brauche ich das Implantat überhaupt?“
  lautet die Antwort:

  #muster(anlass: "Frage nach medizinischer Notwendigkeit")[
    „Das kann ich Ihnen nicht sagen, das gehört in die Praxis. Ich kann Ihnen
    sagen, was versichert wäre, wenn Sie sich dafür entscheiden.“
  ]

  Zweitens wird noch kein Tarif genannt — auch dann nicht, wenn nach zwei
  Minuten klar ist, welcher es sein wird.
]

#abschnitt("empfehlung-herleiten")[
  = Vom Bedarf zur Empfehlung: die Auswahl begründet vortragen <marke-empfehlung-herleiten>

  Zwischen Bedarfsermittlung und Angebot steht ein eigener, kurzer Schritt: die
  Zusammenfassung des Bedarfs und die Herleitung der Empfehlung. Er dauert eine
  Minute und entscheidet, ob das Angebot als Antwort oder als Verkaufsversuch
  gehört wird.

  #muster(anlass: "Zusammenfassung vor der Empfehlung")[
    „Ich fasse zusammen: Sie sind 41, das Gebiss ist bis auf zwei Füllungen
    unauffällig, angeraten ist derzeit nichts, und Implantate hatten Sie als
    Thema genannt, weil es in der Familie öfter vorkam. Passt das so?“
  ]

  Erst die Bestätigung abwarten, dann weiter: „Auf dieser Grundlage würde ich
  Ihnen zwei Tarife zeigen — und dazu sagen, was der jeweils nicht kann.“

  Die Herleitung nennt aktiv den ausgeschlossenen Tarif und den Grund. Das ist
  der wirksamste Vertrauensbeweis im Gespräch.

  #muster(anlass: "ausgeschlossener Tarif")[
    „atra.dent.smart lasse ich weg, obwohl er der günstigste wäre. Er
    versichert keine Implantate — bei Ihrem Thema wäre das genau die Lücke, die
    Sie nicht wollen.“
  ]

  Wo nach Bedarf mehrere Tarife gleichwertig geeignet sind, greift die
  Empfehlungsreihenfolge. Der Grund darf offen genannt werden.

  #muster(anlass: "Begründung der Reihenfolge")[
    „Ich fange bewusst mit dem größeren an. Aufstocken ist später der
    schwierige Weg — da stehen eine neue Gesundheitsprüfung, eine neue
    Wartezeit und eine wieder von vorn beginnende Zahnstaffel.“
  ]

  Die Reihenfolge greift ausdrücklich nicht, wenn ein Budgetrahmen genannt
  wurde, den der vorrangige Tarif überschreitet. Dann wird der Rahmen
  respektiert, nicht in Frage gestellt. Sie greift auch nicht bei einem
  Eintrittsalter über #str(brillant.eintrittsalter.bis) — brillant und brillant
  mit Selbstbehalt sind dann nicht mehr abschließbar, smart und balance bis
  #str(smart.eintrittsalter.bis) — und nicht, wenn der Bedarf erkennbar auf
  einen Bereich begrenzt ist, den ein kleinerer Tarif abdeckt. Die vollständige
  Aufzählung dieser Fälle steht in #verweis-auf(<marke-reihenfolge-greift-nicht>).
]

#abschnitt("angebot-darstellen")[
  = Das Angebot am Telefon darstellen: Struktur, Sprache, Dosierung von Zahlen <marke-angebot-darstellen>

  Ein Tarif wird am Telefon in vier Blöcken dargestellt, immer in derselben
  Reihenfolge:

  #buchstaben(
    [wofür er gedacht ist,],
    [was er im Bedarfsbereich der Kundin leistet,],
    [wo seine Grenzen liegen,],
    [was er kostet.],
  )

  Die Grenzen vor dem Beitrag — wer zuerst den Beitrag nennt, verhandelt danach
  über Geld statt über Passung.

  #muster(anlass: "balance, Leistungsblock")[
    „atra.dent.balance deckt alle Bereiche ab — Zahnersatz, Implantate,
    Zahnerhalt, Parodontose, Inlays, Prophylaxe, auch Kieferorthopädie und
    Aufbissschienen. Beim Zahnersatz sind es 85 Prozent, beim Zahnerhalt 90.
    Implantate ebenfalls 85 Prozent, bis zu vier Fälle in fünf Jahren. Die
    Wartezeit sind drei Monate.“
  ]

  Dann, ohne Übergangspause, der Grenzenblock.

  #muster(anlass: "balance, Grenzenblock")[
    „Zwei Dinge müssen Sie dazu wissen. Erstens ein Selbstbehalt von 100 Euro —
    der gilt einmal im Versicherungsjahr über alle Bereiche zusammen, nicht je
    Bereich. Zweitens die Zahnstaffel: im ersten Jahr sind insgesamt 1.000 Euro
    Erstattung möglich, im zweiten kumuliert 2.000, im dritten 3.000, im
    vierten 4.500 — ab dem fünften Jahr fällt die Begrenzung weg.“
  ]

  #muster(anlass: "brillant")[
    „Bei atra.dent.brillant sind es in fast allen Bereichen 100 Prozent, kein
    Selbstbehalt, Implantate bis zu acht Fälle in fünf Jahren, Wartezeit drei
    Monate. Zwei Einschränkungen gehören dazu: Die Zahnstaffel läuft über drei
    Jahre — 1.500 Euro im ersten Jahr, kumuliert 3.000 im zweiten, 5.000 im
    dritten. Und danach gilt eine Jahreshöchstgrenze von 5.000 Euro.
    Vollschutz heißt also nicht unbegrenzt.“
  ]

  Der letzte Satz ist Pflicht, nicht Stil: Bei brillant sind Jahreshöchstgrenze
  und Zahnstaffel aktiv zu nennen, weil die Erwartung „unbegrenzt“ sonst
  zuverlässig entsteht.

  #muster(
    anlass: "brillant mit Selbstbehalt",
    passt: [für jemanden, der kleine Rechnungen ohnehin selbst trägt.],
    passt-nicht: [bei absehbar regelmäßigen kleineren Behandlungen, weil der
      Selbstbehalt dann jedes Jahr greift.],
  )[
    „Leistungsseitig ist der identisch mit brillant — wirklich identisch. Der
    einzige Unterschied sind 250 Euro Selbstbehalt im Versicherungsjahr, dafür
    ist der Beitrag niedriger.“
  ]

  Diese Variante darf nie als „kleinerer Schutz“ dargestellt werden.

  #muster(anlass: "smart")[
    „atra.dent.smart ist der Einstieg: 60 Prozent bei Zahnersatz und Inlays, 70
    beim Zahnerhalt, Prophylaxe einmal im Jahr bis 80 Euro, kein Selbstbehalt,
    keine Jahreshöchstgrenze.“
  ]

  Direkt anschließend, ohne Nachfrage, der Lückenblock.

  #muster(anlass: "smart, nicht versicherte Bereiche")[
    „Wichtig ist, was smart nicht enthält — Implantate, Parodontose,
    Kieferorthopädie, Funktionsanalyse und Narkose sind nicht versichert. Und
    die Wartezeit beträgt acht Monate, deutlich länger als bei den anderen
    Tarifen.“
  ]

  Die Beitragshöhe stammt aus dem Rechenkern und wird zum konkreten
  Eintrittsalter berechnet, nicht aus dem Kopf geschätzt. „Ich rechne Ihnen das
  für Ihr Alter genau aus und schicke es mit“ ist die richtige Antwort, wenn
  keine berechnete Zahl vorliegt.
]

#abschnitt("pflichtangaben")[
  = Pflichtangaben, die ungefragt in jede Tarifempfehlung gehören <marke-pflichtangaben>

  Diese Angaben werden aktiv genannt, nicht auf Nachfrage. Ein Berater, der sie
  nur nennt, wenn gefragt wird, hat sie nicht genannt.

  #stichwort[Wartezeit.] Die Wartezeit des empfohlenen Tarifs wird ungefragt
  genannt.

  #tabelle(
    spalten: (1fr, 26mm, 1.1fr),
    ausrichtung: (left, left, left),
    klein: true,
    kopf: ("Tarif", "Allgemeine Wartezeit", "Besonderheit"),
    ..alle-tarife
      .map(t => (
        t.anzeigename,
        monate(t.wartezeit_monate),
        {
          let b = ()
          if t.at("wartezeit_entfaellt_bei_vorversicherung", default: false) {
            b.push("entfällt bei lückenlosem Vorversicherungsschutz")
          }
          let kfo = leistungen(t).KFO
          if kfo.versichert and "wartezeit_monate" in kfo {
            b.push("Kieferorthopädie " + monate(kfo.wartezeit_monate))
          }
          if b.len() == 0 { text(fill: dunkelgrau)[keine] } else { b.join("; ") }
        },
      ))
      .flatten()
  )

  Der Entfall bei Vorversicherung ist aktiv anzusprechen, wenn eine
  Vorversicherung im Gespräch war. Für Kieferorthopädie gelten eigene, längere
  Wartezeiten; sie entfallen auch dann nicht, wenn die allgemeine Wartezeit
  entfällt.

  #stichwort[Angeratene und begonnene Behandlungen.] Wer schon einen Heil- und
  Kostenplan hat, muss das wissen, bevor er unterschreibt, nicht danach. Diese
  Ansprache ist unangenehm und trotzdem verpflichtend; sie verhindert die
  teuerste Form der Enttäuschung.

  #muster(anlass: "Ausschluss angeratener Behandlungen")[
    „Wenn Ihre Zahnärztin Ihnen etwas bereits angeraten hat oder eine
    Behandlung begonnen ist, wird dafür nicht geleistet — das gilt in allen
    Tarifen.“
  ]

  #stichwort[Fehlende Zähne.] Bei Vertragsschluss fehlende Zähne sind
  ausgeschlossen. Nur brillant und brillant mit Selbstbehalt sehen eine
  Ausnahme vor: für bis zu zwei bei Vertragsschluss fehlende Zähne, wenn die
  Behandlung nach Ablauf von zwölf Monaten seit Versicherungsbeginn beginnt.
  Diese Ausnahme wird genau so wiedergegeben und nicht großzügiger; ausführlich
  steht sie in #verweis-auf(<marke-fehlende-zaehne>).

  #stichwort[Selbstbehalt, wo einer besteht.]
  #eur0(balance.selbstbehalt) bei balance,
  #eur0(brillant-sb.selbstbehalt) bei brillant mit Selbstbehalt, jeweils einmal
  im Versicherungsjahr über alle Leistungsbereiche zusammen. Dass es nicht je
  Bereich gilt, ist erfahrungsgemäß der am häufigsten missverstandene Punkt und
  wird deshalb explizit gesagt.

  #stichwort[Staffel und Höchstgrenze.] Die Zahnstaffel des empfohlenen Tarifs
  und, bei brillant und brillant mit Selbstbehalt, die Jahreshöchstgrenze von
  #eur0(brillant.jahreshoechstgrenze). Ergänzend, wenn es zur Situation passt:
  Bei unfallbedingten Behandlungen entfällt die Staffel vollständig.

  #stichwort[Weitere allgemeine Ausschlüsse.] Rein kosmetische Leistungen wie
  Bleaching oder Veneers ohne medizinische Indikation sowie Behandlungen durch
  nicht approbierte Behandler.
]

#abschnitt("erstattungshoehen-ohne-zusage")[
  = Über Erstattungshöhen sprechen, ohne eine Zusage zu geben <marke-erstattungshoehen-ohne-zusage>

  Die häufigste Kundenfrage lautet: „Was bekomme ich denn nun raus?“ Die Grenze
  ist eindeutig: Es wird keine Zusage über die Höhe einer konkreten Erstattung
  gegeben. Quote, Zahnstaffel, Selbstbehalt, Sublimits und die Vorleistung der
  gesetzlichen Krankenversicherung wirken zusammen; verbindlich rechnet allein
  die Leistungsabteilung.

  Die brauchbare Formulierung trennt Mechanik von Betrag.

  #muster(
    anlass: "Frage nach der Erstattungshöhe",
    passt: [wenn nach einer konkreten Behandlung gefragt wird.],
    passt-nicht: [als Antwort auf eine allgemeine Verständnisfrage — dort
      genügt die Quote.],
  )[
    „Ich kann Ihnen sagen, wie gerechnet wird, aber keine Summe zusagen — dafür
    spielen zu viele Dinge zusammen, unter anderem was Ihre gesetzliche Kasse
    vorher übernimmt. Bei balance wären es 85 Prozent des Betrags, der nach der
    Vorleistung Ihrer Kasse übrig bleibt, davon einmal im Jahr 100 Euro
    Selbstbehalt ab, und im ersten Jahr deckelt die Staffel die Summe aller
    Erstattungen auf 1.000 Euro. Wie das bei Ihrer konkreten Rechnung ausgeht,
    rechnet die Leistungsabteilung, wenn der Heil- und Kostenplan vorliegt.“
  ]

  Unzulässig sind Formulierungen wie „da kriegen Sie ungefähr 3.000 zurück“
  oder „bei so etwas zahlen wir eigentlich immer“. Auch abgeschwächte Varianten
  mit „ungefähr“ oder „erfahrungsgemäß“ sind Zusagen, sobald die Kundin sie als
  solche hört — und sie hört sie so.

  Hilft eine Beispielrechnung wirklich, wird sie als Beispiel markiert und mit
  runden Fantasiezahlen geführt: „Nur zur Mechanik: Nehmen wir 1.000 Euro
  Restbetrag …“
]

#abschnitt("abschweifen-steuern")[
  = Gesprächssteuerung, wenn die Kundin oder der Kunde abschweift <marke-abschweifen-steuern>

  Abschweifen ist normal und nicht per se ein Problem. Bei Zahnthemen ist es
  oft sogar funktional: Die Geschichte über den schlechten Zahnarzt enthält
  häufig genau die Information, nach der man sonst mühsam fragen müsste.
  Deshalb gilt: erst zuhören, dann bewerten, ob es zum Thema gehört. Drei
  Situationen, drei Techniken.

  #stichwort[Der Ausflug trägt zur Sache bei.] Nicht unterbrechen, sondern am
  Ende einsammeln.

  #muster(anlass: "Ausflug einsammeln")[
    „Das ist ein wichtiger Punkt — dass Sie schlechte Erfahrungen mit
    Zuzahlungen gemacht haben, erklärt einiges. Genau da schaue ich gleich
    hin.“
  ]

  #stichwort[Der Ausflug ist thematisch fremd, aber der Kunde ist engagiert.]
  Anerkennen, brücken, weitergehen. Die Anerkennung muss echt sein und darf
  keine Standardformel werden; zweimal derselbe Satz im selben Gespräch wird
  bemerkt.

  #muster(anlass: "Brücke zurück zum Thema")[
    „Da haben Sie recht, das ist ärgerlich. Damit ich Ihre Zeit gut nutze, gehe
    ich noch einmal zurück zu einer Sache, die für die Auswahl wichtig ist: …“
  ]

  #stichwort[Es driftet dauerhaft.] Den Rahmen offen ansprechen.

  #muster(
    anlass: "Rahmen neu setzen",
    passt: [wenn die Zeit knapp wird.],
    passt-nicht: [als frühes Disziplinierungsmittel — dann wirkt es abweisend
      und die Kundin macht dicht.],
  )[
    „Wir haben noch etwa zehn Minuten. Ich stelle Ihnen zuerst die drei Fragen,
    die ich für eine saubere Empfehlung brauche, danach ist Zeit für alles
    andere. Einverstanden?“
  ]

  Eine geschlossene Frage holt zuverlässiger zurück als eine offene. Nach einem
  langen Ausflug also nicht „Wie sieht es denn sonst bei Ihnen aus?“, sondern
  „Liegt bei Ihnen aktuell ein Heil- und Kostenplan vor — ja oder nein?“

  Führt der Ausflug in ein Beschwerdethema, wird es weitergeleitet statt
  nebenbei behandelt. Nur zusagen, was tatsächlich veranlasst wird.

  #muster(anlass: "Beschwerdethema")[
    „Das gehört in die Leistungsabteilung, dort kann ich nicht für Sie
    entscheiden. Ich notiere es und sorge dafür, dass sich jemand meldet.“
  ]
]

#abschnitt("zeitdruck")[
  = Umgang mit Zeitdruck auf Kundenseite und auf Beraterseite <marke-zeitdruck>

  Zeitdruck kommt aus zwei Richtungen und verlangt zwei verschiedene
  Reaktionen.

  #stichwort[Zeitdruck beim Kunden.] Reicht die Zeit erkennbar nicht, wird das
  Gespräch nicht beschleunigt, sondern geteilt. Eine gestraffte Beratung ist in
  der Praxis eine, bei der die Pflichtangaben unter den Tisch fallen.

  #muster(anlass: "geteiltes Gespräch")[
    „Ich merke, Sie müssen gleich los. Ich mache das nicht im Schnelldurchlauf,
    dazu gehören ein paar Punkte, die Sie wirklich gehört haben sollten. Zwei
    Möglichkeiten: Ich schicke Ihnen jetzt die Unterlagen und wir telefonieren
    in Ruhe — oder ich nenne Ihnen in zwei Minuten die grobe Richtung und wir
    gehen den Rest beim nächsten Mal durch.“
  ]

  Bleiben nur wenige Minuten und der Kunde will abschließen: Ein Abschluss ohne
  genannte Wartezeit, ohne genannten Selbstbehalt und ohne Hinweis auf
  angeratene Behandlungen findet nicht statt. Diese vier bis fünf Sätze sind
  nicht verhandelbar; reicht die Zeit dafür nicht, wird der Abschluss verlegt.

  #stichwort[Zeitdruck beim Berater.] Der eigene Zeitdruck — Warteschleife
  voll, Tagesziel offen, Feierabend — ist kein Grund, das Gespräch zu
  verkürzen, und schon gar keiner, ihn weiterzugeben. Er ist am Telefon hörbar:
  schnelleres Sprechen, kürzere Pausen, weniger Rückfragen. Wer merkt, dass er
  drängt, bremst bewusst ab. Reicht die eigene Zeit wirklich nicht, wird das
  gesagt statt versteckt.

  #muster(anlass: "eigene Zeit reicht nicht")[
    „Bei mir wird es gleich knapp, und ich möchte den Teil zu den Ausschlüssen
    nicht kürzen. Dürfte ich Sie morgen Vormittag noch einmal anrufen?“
  ]

  In beiden Richtungen verboten bleibt jede Andeutung, ein Angebot gelte nur
  heute, die Konditionen änderten sich demnächst, oder der Kunde solle sich
  „jetzt schnell“ entscheiden. Sachliche Hinweise zu Fristen gehören
  schriftlich in die Unterlagen, nicht als Druckmittel ins Gespräch.
]

#abschnitt("bedenkzeit")[
  = Bedenkzeit: annehmen, strukturieren, nicht bekämpfen <marke-bedenkzeit>

  Der Wunsch nach Bedenkzeit oder nach Rücksprache wird ohne Gegenrede
  akzeptiert. Das ist keine Höflichkeit, sondern Vorgabe. „Ich möchte darüber
  nachdenken“ ist kein Einwand, der behandelt wird, sondern eine legitime
  Entscheidung über den Zeitpunkt.

  Die richtige erste Reaktion ist Zustimmung, nicht Nachfassen.

  #muster(anlass: "Zustimmung zur Bedenkzeit")[
    „Das ist vernünftig. Eine Zahnzusatzversicherung schließt man einmal ab und
    behält sie lange — da lohnt es sich, draufzuschlafen.“
  ]

  Danach, und erst danach, wird die Bedenkzeit strukturiert. Drei Schritte.

  #stichwort[Klären, was noch fehlt.] Eine konkrete Unsicherheit, die sich in
  zwei Sätzen klären lässt, wird geklärt. Entpuppt sie sich als inhaltlicher
  Einwand — zu teuer, kein Bedarf, Misstrauen — gehört die Behandlung nach
  Kapitel 3, und auch dort gilt: höchstens eine sachliche Erwiderung, kein
  Nachsetzen.

  #muster(anlass: "offene Punkte klären")[
    „Damit Sie das gut abwägen können: Gibt es etwas, das Sie noch nicht
    wissen? Ich schicke Ihnen ohnehin die Bedingungen mit.“
  ]

  #stichwort[Unterlagen zusagen und liefern.] Nur zusagen, was auch geschickt
  wird, und nichts mitschicken, was im Gespräch nicht erwähnt wurde.

  #muster(anlass: "Unterlagen zusagen")[
    „Ich schicke Ihnen die Bedingungen und eine Übersicht zu den zwei Tarifen
    heute per Mail.“
  ]

  #stichwort[Einen Rückruf vereinbaren, wenn er gewünscht ist] — nicht
  ungefragt. Beide Antworten sind gleich richtig, und das muss im Tonfall auch
  so klingen.

  #muster(anlass: "Rückruf anbieten")[
    „Möchten Sie, dass ich mich in ein paar Tagen noch einmal melde, oder rufen
    Sie lieber selbst an, wenn Sie so weit sind?“
  ]

  Was hier nicht geschieht: kein „Was hält Sie denn noch ab?“ als drittes
  Nachhaken, kein Hinweis auf steigendes Eintrittsalter als Druckmittel, keine
  Andeutung, die Gesundheitsfragen könnten in ein paar Wochen ungünstiger
  ausfallen. Der sachliche Hinweis, dass sich der Beitrag nach dem
  Eintrittsalter richtet, ist zulässig — einmal, ruhig und ohne
  Handlungsaufforderung.
]

#abschnitt("rueckrufvereinbarung")[
  = Rückrufvereinbarungen verbindlich treffen und einhalten <marke-rueckrufvereinbarung>

  Ein Rückruf ist eine Zusage — und die Zuverlässigkeit dabei sagt oft mehr
  über die Beratung aus als der Inhalt des ersten Gesprächs.

  Eine belastbare Rückrufvereinbarung enthält vier Angaben: Tag, Zeitfenster,
  Nummer und Zweck.

  #muster(anlass: "Rückruf vereinbaren")[
    „Dann rufe ich Sie am Donnerstag zwischen 17 und 18 Uhr an, auf dieser
    Nummer — und wir gehen die Fragen durch, die sich beim Lesen ergeben haben.
    Passt das so?“
  ]

  Ein bloßes „Ich melde mich nächste Woche“ ist keine Vereinbarung, sondern
  eine Absichtserklärung, und wird entsprechend selten erwartet.

  Der Zweck gehört dazu, weil er den Rückruf von einem Nachfassanruf
  unterscheidet. „Ich frage dann nach Ihrer Entscheidung“ ist ein zulässiger
  Zweck, wenn die Kundin ihn so vereinbart hat — aufgedrängt wird er nicht.

  Der Rückruf findet im vereinbarten Fenster statt. Wer es nicht schafft,
  meldet sich trotzdem und sagt es. Ein unkommentiert verpasster Rückruf, der
  drei Tage später nachgeholt wird, kostet mehr Vertrauen als eine ehrliche
  Verspätung.

  #muster(anlass: "verpasster Rückruf")[
    „Ich hatte Donnerstag zugesagt und es nicht geschafft — das tut mir leid.
    Passt es Ihnen jetzt?“
  ]

  Ist niemand erreichbar, gilt: höchstens zwei Versuche im vereinbarten
  Fenster, dann eine kurze Nachricht mit Namen, Anlass und Erreichbarkeit — und
  danach kein weiterer Versuch, solange keine Reaktion kommt. Wiederholtes
  Anrufen ohne Reaktion ist Drängen, auch wenn kein Wort fällt.

  Kommt ein Angehöriger ans Telefon, wird der Anlass nur allgemein benannt. Zum
  Gesundheitszustand oder zu Vertragsinhalten wird gegenüber Dritten nichts
  gesagt.

  #muster(anlass: "Angehöriger am Telefon")[
    „Es geht um eine Rückfrage zu einem Beratungsgespräch, ich versuche es
    später noch einmal.“
  ]
]

#abschnitt("abschluss-feststellen")[
  = Der Abschluss: die Entscheidung feststellen, nicht herbeireden <marke-abschluss-feststellen>

  Der Abschluss ist kein eigener Überzeugungsschritt, sondern die Feststellung
  einer bereits getroffenen Entscheidung. War die Bedarfsermittlung sauber und
  die Darstellung ehrlich, ist die Entscheidung an dieser Stelle meistens schon
  gefallen — in die eine oder die andere Richtung.

  Die passende Frage ist offen und ergebnisneutral. Beide Optionen werden
  gleichwertig betont.

  #muster(anlass: "Abschlussfrage")[
    „Wie klingt das für Sie?“ — oder: „Möchten Sie das so beantragen, oder
    wollen Sie erst noch in die Unterlagen schauen?“
  ]

  Unzulässig sind Techniken, die nur eine Richtung offen lassen: Die
  Alternativfrage zwischen zwei Ja-Varianten („Lieber zum Ersten oder zum
  Fünfzehnten?“) gehört nicht in dieses Gespräch, solange die
  Grundsatzentscheidung nicht gefallen ist. Nach einem klaren Ja ist sie eine
  praktische Frage und in Ordnung.

  Vor der Antragsaufnahme wird ein letztes Mal zusammengefasst, was gilt.

  #muster(
    anlass: "Zusammenfassung vor dem Antrag",
    passt: [vor jedem Abschluss.],
    passt-nicht: [in verkürzter Form „damit es schneller geht“ — sie ist der
      Kern der Beratungsdokumentation.],
  )[
    „Bevor wir den Antrag aufnehmen, noch einmal die vier Punkte, die Sie
    kennen sollten: drei Monate Wartezeit, in den ersten drei Jahren begrenzt
    die Zahnstaffel die Erstattungen — 1.500, dann kumuliert 3.000, dann 5.000
    Euro —, danach gilt eine Jahreshöchstgrenze von 5.000 Euro im Jahr, und für
    Behandlungen, die Ihnen schon angeraten wurden, wird nicht geleistet. Ist
    das alles klar, oder sollen wir zu einem Punkt noch einmal zurück?“
  ]

  Danach folgt die Antragsaufnahme mit den Gesundheitsfragen. Sie werden
  vorgelesen wie sie stehen, nicht sinngemäß zusammengefasst, und nichts wird
  so kommentiert, dass es zu einer bestimmten Antwort einlädt. Ein „das kann
  man auch weglassen“ ist ein schwerer Fehler, egal wie harmlos der Sachverhalt
  scheint. Gesundheitsangaben werden nur mit der versicherten Person selbst
  aufgenommen.
]

#abschnitt("gespraech-ohne-abschluss-beenden")[
  = Ein Gespräch sauber beenden, das zu keinem Abschluss führt <marke-gespraech-ohne-abschluss-beenden>

  Ein Gespräch ohne Abschluss ist ein normales Ergebnis. Wenn der Bedarf nicht
  zu den Tarifen passt, ist eine Absage die richtige Beratung — und in diesem
  Fall geht sie vom Berater aus, nicht vom Kunden.

  #muster(
    anlass: "ehrlicher Abbruch durch den Berater",
    passt: [wenn der Hauptbedarf sicher nicht gedeckt ist.],
    passt-nicht: [als vorschnelles Aufgeben bei einem Bedarf, der nur teilweise
      nicht gedeckt ist — dann wird der gedeckte Teil dargestellt und die Lücke
      benannt, und die Entscheidung bleibt beim Kunden.],
  )[
    „Ich sage es Ihnen offen: Für das, was Sie brauchen, habe ich nichts
    Passendes. Sie haben einen Heil- und Kostenplan für die Brücke — dafür wird
    in keinem unserer Tarife geleistet, weil die Behandlung schon angeraten
    ist. Ich könnte Ihnen jetzt etwas verkaufen, aber Sie wären enttäuscht,
    sobald die erste Rechnung kommt.“
  ]

  Bei einer Absage darf ein sachlicher Ausblick folgen, wenn er zutrifft —
  ohne Terminvereinbarung und ohne Nachfassliste, wenn der Kunde das nicht
  will.

  #muster(anlass: "Ausblick nach einer Absage")[
    „Wenn die Brücke gemacht ist, sieht die Lage anders aus — für alles, was
    danach kommt, wäre eine Absicherung möglich. Melden Sie sich dann einfach.“
  ]

  Der Abbruch durch den Kunden: „Ich habe kein Interesse.“ Die Antwort ist
  kurz, freundlich und ohne zweiten Anlauf.

  #muster(anlass: "klares Desinteresse")[
    „Alles klar, dann will ich Sie nicht weiter aufhalten. Danke für Ihre Zeit,
    und falls das Thema später aktuell wird, wissen Sie ja, wo Sie uns finden.“
  ]

  Kein „Darf ich nur noch kurz…“, keine letzte Frage, kein zweiter Versuch
  nach einem klaren Nein. Einen echten Einwand, hinter dem eine Frage steckt,
  behandelt Kapitel 3; ein klares Desinteresse ist kein Einwand.

  Zum sauberen Beenden gehört die Absprache über weiteren Kontakt: „Soll ich
  Sie aus dem Verteiler nehmen?“ Wird das bejaht, wird es umgesetzt, nicht nur
  notiert. Ein Gespräch ohne Abschluss wird genauso dokumentiert wie eines mit
  — mit dem Grund. Der Grund ist der wertvollere Teil: Er verhindert, dass beim
  nächsten Kontakt wieder bei null angefangen wird.
]

#abschnitt("nachbereitung-und-dokumentation")[
  = Nachbereitung: Dokumentation, Zusagen und Übergaben <marke-nachbereitung-und-dokumentation>

  Die Nachbereitung beginnt unmittelbar nach dem Auflegen, nicht am Ende des
  Tages. Was nach zwei Stunden aus dem Gedächtnis rekonstruiert wird, ist
  unbrauchbar für die Beratungsdokumentation. Festgehalten wird:

  #stichwort[Der ermittelte Bedarf] in der Formulierung des Kunden, nicht in
  der eigenen Interpretation — insbesondere genannte Budgetrahmen, absehbare
  Behandlungen und vorliegende Heil- und Kostenpläne.

  #stichwort[Die Empfehlung mit Begründung] — und, wo von der
  Empfehlungsreihenfolge abgewichen wurde, der Grund. Abweichungen zugunsten
  eines kleineren Tarifs brauchen gegenüber dem Vertrieb keine Rechtfertigung,
  aber die Nachvollziehbarkeit gehört in die Akte.

  #stichwort[Die genannten Pflichtangaben:] welche Wartezeit, welcher
  Selbstbehalt, welche Staffel und welche Ausschlüsse genannt wurden. Das ist
  der Teil, der im Streitfall zählt.

  #stichwort[Offene Zusagen] mit Frist: versandte Unterlagen, vereinbarter
  Rückruf, weitergeleitete Beschwerden. Jede Zusage bekommt einen Termin, sonst
  ist sie keine.

  #stichwort[Bei Absagen der Grund.] „Kein Interesse“ ist keine Dokumentation.
  „Heil- und Kostenplan liegt vor, Hauptbedarf ausgeschlossen“ ist eine.

  Fragen zur Höhe einer konkreten Erstattung und zur Auslegung einer Bedingung
  gehen an die Leistungsabteilung. Bei Nachfragen zu Bedingungsinhalten wird
  die Bedingungsstelle genannt, nie dieses Handbuch — interne
  Steuerungsunterlagen werden gegenüber Kunden weder zitiert noch erwähnt. Die
  schriftliche Zusammenfassung an den Kunden bleibt bei dem, was besprochen
  wurde: keine Erstattungszusagen, keine Beispielrechnungen, die als Zusage
  gelesen werden können, keine Formulierung, die im Gespräch nicht gefallen
  ist.

  Welche Angaben die Bedarfsermittlung im Einzelnen hinterlässt, steht in
  #verweis-auf(<marke-dokumentation-bedarfsermittlung>).
]

#abschnitt("unzulaessige-formulierungen")[
  = Formulierungen, die in keinem atra.dent-Gespräch verwendet werden <marke-unzulaessige-formulierungen>

  Die Beispiele sind Muster, keine abschließende Liste. Im Zweifel gilt: Wenn
  eine Formulierung ihre Wirkung daraus bezieht, dass sie den Kunden schneller
  entscheiden lässt, als er es informiert könnte, wird sie nicht verwendet.

  #tabelle(
    spalten: (34mm, 1fr),
    ausrichtung: (left, left),
    klein: true,
    bindend: false,
    kopf: ("Nicht verwendet wird", "Beispiele und Begründung"),
    [Verknappung],
    [„Das Angebot gilt nur heute“, „bis Monatsende bekommen wir das noch
     durch“, „ich halte Ihnen den Platz bis morgen frei“. Unzulässig auch in
     abgeschwächter Form und auch dann, wenn eine echte Frist im Hintergrund
     steht.],

    [Erstattungszusagen],
    [„Da bekommen Sie ungefähr X zurück“, „das ist praktisch komplett
     abgedeckt“, „so etwas zahlen wir eigentlich immer“. Verbindlich rechnet
     nur die Leistungsabteilung.],

    [Medizinische Aussagen],
    [„Das brauchen Sie sowieso“, „ein Implantat ist da die bessere Lösung“. Was
     medizinisch notwendig oder sinnvoll ist, entscheidet die Zahnärztin oder
     der Zahnarzt.],

    [Angstaufbau],
    [„Bei Ihrem Alter kommt da bald einiges.“ Ein sachlicher Hinweis auf
     tatsächliche Eigenanteile ist zulässig, eine Prognose über die
     Zahngesundheit des Kunden nicht.],

    [Verharmlosung von Grenzen],
    [„Der Selbstbehalt ist vernachlässigbar“, „die Staffel merken Sie gar
     nicht“, „die Wartezeit ist nur Formsache“. Selbstbehalt, Zahnstaffel und
     Wartezeit sind genau die Punkte, an denen später gestritten wird.],

    [Falsche Gleichsetzungen],
    [brillant mit Selbstbehalt als „die kleinere Variante“ zu bezeichnen ist
     sachlich falsch — die Leistungen sind identisch, es unterscheidet sich
     allein der Selbstbehalt von #eur0(brillant-sb.selbstbehalt). Ebenso falsch
     ist smart als „Grundschutz für alles“: Implantate, Parodontose,
     Kieferorthopädie, Funktionsanalyse und Narkose sind dort nicht
     versichert.],

    [Druck nach einem Nein],
    [„Darf ich nur noch eine einzige Frage stellen“, „geben Sie mir noch
     dreißig Sekunden“. Ein klares Nein wird beim ersten Mal akzeptiert.],
  )
]

// =======================================================================
// Kapitel 2 — Bedarfsanalyse
// =======================================================================

#kapitel(2, "Bedarfsanalyse", "kapitel-bedarfsanalyse")[
  Dieses Kapitel beschreibt, wie der tatsächliche Bedarf ermittelt wird und wie
  daraus eine Tarifempfehlung entsteht: welche Fragen gestellt werden, in
  welcher Reihenfolge, mit welchen Anschlussfragen — und was aus den Antworten
  folgt.

  #hinweis(titel: "Abgrenzung")[
    Gesprächsaufbau, Phasen und die Besonderheiten der Telefonberatung stehen
    in Kapitel 1. Einwände gegen eine bereits ausgesprochene Empfehlung stehen
    in Kapitel 3. Der Unterschied liegt in Zeitpunkt und Funktion: Die
    Bedarfsanalyse ermittelt die Lage, die Einwandbehandlung reagiert auf
    Widerspruch. Wer beides vermischt, behandelt Antworten der Bedarfsanalyse
    als Einwände und beginnt zu überzeugen, wo er noch zuhören sollte.
  ]
]

#abschnitt("fragenkatalog-aufbau")[
  = Aufbau des Fragenkatalogs: Reihenfolge der Fragen und warum sie so liegt <marke-fragenkatalog-aufbau>

  Der Fragenkatalog der Bedarfsanalyse folgt einer festen Ordnung, weil die
  späteren Fragen ohne die früheren nicht auswertbar sind.

  #tabelle(
    spalten: (8mm, 1fr, 1.2fr),
    ausrichtung: (right, left, left),
    klein: true,
    kopf: ("", "Frage", "Wozu"),
    [1.], [Anlass und Ausgangslage], [Warum jetzt?],
    [2.], [Zahnärztliche Vorgeschichte], [Letzter Besuch, Befund, laufende Behandlung.],
    [3.], [Angeratene oder begonnene Behandlungen], [Die Frage mit der größten Tragweite.],
    [4.], [Fehlende Zähne], [Harte Ausschlussfrage mit genau einer Ausnahme.],
    [5.], [Bestehender Vorversicherungsschutz], [Entscheidet über die Wartezeit bei brillant.],
    [6.], [Alter], [Entscheidet, welche Tarife offenstehen.],
    [7.], [Absehbarer Bedarf], [Implantate, Kieferorthopädie, Parodontose, Schiene, Narkose.],
    [8.], [Erwartung und Rahmen], [Selbstbehalt, Budget, Zeithorizont.],
  )

  Die Begründung ist wichtiger als die Reihenfolge selbst. Anlass und
  Vorgeschichte stehen vorn, weil sie das Gespräch öffnen, ohne sensibel zu
  sein — wer mit der Frage nach angeratenen Behandlungen einsteigt, wirkt wie
  eine Prüfstelle. Die Ausschlussfragen 3 bis 5 stehen in der Mitte, weil sie
  den Lösungsraum verkleinern: Ergibt sich hier ein Ausschluss, ändert das die
  gesamte spätere Empfehlung. Der Rahmen steht am Ende, weil eine Budgetfrage
  am Anfang wie eine Bonitätsprüfung klingt: Wer zuerst nach dem Preis gefragt
  wird, nennt eine Zahl, bevor er weiß, wofür.

  Jede Frage wird nur einmal gestellt, und so, dass ein „nein“ eine vollwertige
  Antwort ist.
]

#abschnitt("einstiegsfragen-ausgangslage")[
  = Einstiegsfragen zur Ausgangslage: Anlass, letzter Besuch, Zustand des Gebisses <marke-einstiegsfragen-ausgangslage>

  Am Anfang der Bedarfsanalyse stehen drei offene Fragen ohne jede Wertung,
  deren Antworten alles Weitere steuern.

  #stichwort[Die Anlassfrage.] Ihr Zweck ist nicht Small Talk, sondern
  Einordnung: Ein Berufseinstieg ist ein anderer Anlass als eine gerade
  erhaltene Rechnung. Der günstigste Anlass überhaupt ist die Beratung, bevor
  eine Behandlung angeraten wurde, weil dann der Ausschluss angeratener
  Behandlungen noch nicht greift.

  #muster(anlass: "Anlassfrage", passt: [immer.])[
    „Was hat Sie dazu gebracht, sich gerade jetzt mit dem Thema zu befassen?“
  ]

  #stichwort[Die Vorgeschichtsfrage.] Sie liefert den Zeitbezug — wer seit fünf
  Jahren nicht dort war, kann über angeratene Behandlungen nichts sagen — und
  schafft den Übergang zur Ausschlussfrage, ohne dass diese wie ein Verhör
  wirkt.

  #muster(anlass: "Vorgeschichtsfrage")[
    „Wann waren Sie zuletzt beim Zahnarzt, und was war das Ergebnis?“
  ]

  #stichwort[Die Zustandsfrage.] Sie lässt dem Kunden die Deutungshoheit.

  #muster(
    anlass: "Zustandsfrage",
    passt-nicht: [wenn jemand erkennbar unangenehm berührt ist; dann wird sie
      ersatzlos gestrichen.],
  )[
    „Wie würden Sie den Zustand Ihrer Zähne selbst einschätzen — eher
    unauffällig, oder gibt es Stellen, um die Sie sich Sorgen machen?“
  ]

  Wichtig für alle drei: Die Antworten sind Selbstauskunft, keine Diagnose. Ob
  eine Behandlung notwendig ist, entscheidet die Zahnärztin oder der Zahnarzt,
  nie die Beratung.
]

#abschnitt("angeratene-behandlungen-wirkung")[
  = Angeratene und begonnene Behandlungen: warum dieser Ausschluss die häufigste Enttäuschung auslöst <marke-angeratene-behandlungen-wirkung>

  Zu den Ausschlüssen aller vier Tarife gehören vor Vertragsschluss angeratene
  oder begonnene Behandlungen. Es gibt keinen Tarif und keine Option, die
  diesen Ausschluss aufhebt.

  Er ist der häufigste Grund für spätere Enttäuschung, und das strukturell:
  Genau die Situation, die Menschen zum Abschluss bewegt — der Zahnarzt hat
  etwas gesagt, es wird teuer —, ist die Situation, in der der Schutz für eben
  diese Maßnahme nicht greift. Wer das im Beratungsgespräch nicht hört, hört es
  im Leistungsfall, und dann ist der Vertrag verloren und der Ruf dazu. Deshalb
  ist der Ausschluss aktiv anzusprechen: Wer bereits einen Heil- und
  Kostenplan hat, muss vor der Unterschrift wissen, dass dafür nicht geleistet
  wird.

  Praktisch bedeutet „angeraten“ mehr als „schriftlich geplant“: Auch eine
  mündliche Empfehlung, ein vermerkter Befund oder eine terminierte Maßnahme
  fällt darunter. „Begonnen“ heißt, dass die Behandlung eingeleitet wurde, etwa
  ein Zahn beschliffen oder ein Abdruck genommen. Wo die Grenze im Einzelfall
  liegt, entscheidet die Leistungsabteilung anhand des Bedingungswerks; der
  Berater sagt daher nie zu, dass etwas noch nicht als angeraten gilt.

  Die Reichweite ist zugleich begrenzt, und auch das gehört gesagt:
  Ausgeschlossen ist die angeratene Maßnahme, nicht der ganze Vertrag. Wer eine
  angeratene Krone hat, ist nicht generell unversicherbar — alles, was danach
  neu entsteht, ist vom Ausschluss nicht berührt.
]

#abschnitt("angeratene-behandlungen-frage")[
  = Wie man nach angeratenen Behandlungen fragt, ohne etwas zu unterstellen <marke-angeratene-behandlungen-frage>

  Die Frage nach vor Vertragsschluss angeratenen oder begonnenen Behandlungen
  ist die heikelste der Bedarfsanalyse, weil sie leicht wie ein
  Misstrauensvotum klingt. Sie wird deshalb als Sachfrage gestellt, mit dem
  Grund davor und ohne Konjunktiv.

  #muster(
    anlass: "Regelfall",
    passt: [sobald die Vorgeschichte erfragt ist.],
    passt-nicht: [als Einstiegsfrage, weil der Begründungssatz dann ins Leere
      läuft.],
  )[
    „Ich muss Sie das fragen, weil es für den Schutz entscheidend ist: Hat Ihre
    Zahnärztin oder Ihr Zahnarzt Ihnen zuletzt zu einer Behandlung geraten,
    oder ist bereits eine begonnen worden? Das ist keine Fangfrage — solche
    Behandlungen sind in allen unseren Tarifen ausgeschlossen. Ich möchte nur,
    dass Sie das vorher wissen und nicht erst mit der Rechnung.“
  ]

  #muster(
    anlass: "Kostenvoranschlag wurde erwähnt",
    passt: [zwingend, sobald ein Kostenvoranschlag im Gespräch ist.],
    passt-nicht: [solange unklar ist, ob dahinter eine Behandlung oder nur eine
      Prophylaxeübersicht steht.],
  )[
    „Dazu muss ich offen sein: Für eine Behandlung, die vor Vertragsschluss
    angeraten wurde, leistet keiner unserer Tarife. Ein Vertrag kann für alles
    Weitere trotzdem sinnvoll sein — aber diese eine Maßnahme würde nicht
    darunterfallen.“
  ]

  Was nicht gesagt wird: keine Andeutung, der Kunde könne mit dem Abschluss
  „noch schnell“ einer Diagnose zuvorkommen; keine Frage in der Form „Da ist
  doch sicher nichts angeraten, oder?“, weil sie die gewünschte Antwort
  mitliefert; keine Einschätzung, ob das Gesagte medizinisch geboten war.
]

#abschnitt("angeratene-behandlungen-auswertung")[
  = Was aus der Antwort zu angeratenen Behandlungen folgt <marke-angeratene-behandlungen-auswertung>

  Auf die Frage nach vor Vertragsschluss angeratenen oder begonnenen
  Behandlungen gibt es drei Antworttypen mit je eigenem Vorgehen.

  #stichwort[Nichts angeraten, letzter Besuch liegt nicht lange zurück.] Die
  günstigste Ausgangslage, weil der Ausschluss nicht greift; die
  Empfehlungsreihenfolge gilt ungeschmälert. Zur Sprache kommt die Wartezeit:
  #monate(balance.wartezeit_monate) bei balance, brillant und brillant mit
  Selbstbehalt, #monate(smart.wartezeit_monate) bei smart. Der Hinweis, dass
  eine Zahnzusatzversicherung im gesunden Zustand abgeschlossen wird und nicht
  im Schadensfall, ist hier sachlich richtig — als Feststellung, nicht als
  Drohung.

  #stichwort[Ja, es ist etwas angeraten.] Dann ist zu klären, was und in
  welchem Umfang. Die angeratene Maßnahme ist ausgeschlossen, alles Übrige
  nicht; die Empfehlung richtet sich danach, was daneben noch zu erwarten ist.
  Ein Vertrag kann sinnvoll bleiben, aber ohne die Erwartung, die
  ausgeschlossene Maßnahme mitzufinanzieren. Ausdrücklich gesagt wird: „Für
  diese eine Behandlung wird nicht geleistet.“ Nicht gesagt wird: „Da findet
  sich schon eine Lösung.“

  #stichwort[Unklar, weiß nicht, lange nicht dort gewesen.] Diese Antwort wird
  nicht umgedeutet, sondern als das dokumentiert, was sie ist: ungeklärt.

  #muster(
    anlass: "ungeklärte Lage festhalten",
    passt: [bei erkennbarer Unsicherheit.],
    passt-nicht: [wenn der Kunde bereits konkret von einer Empfehlung des
      Zahnarztes berichtet hat — dann ist die Lage klar.],
  )[
    „Dann halten wir fest: Nach Ihrem Kenntnisstand ist nichts angeraten.
    Sollte sich beim nächsten Termin etwas ergeben, fällt es unter den
    Ausschluss, wenn es vor Vertragsschluss angeraten wurde.“
  ]

  In keinem Fall entscheidet der Berater, ob eine Äußerung des Zahnarztes
  bereits ein Anraten war; diese Bewertung trifft die Leistungsabteilung.
]

#abschnitt("fehlende-zaehne")[
  = Fehlende Zähne: die Ausnahme, die es nur in brillant und brillant mit Selbstbehalt gibt <marke-fehlende-zaehne>

  Bei Vertragsschluss fehlende Zähne sind in allen vier Tarifen ausgeschlossen.
  Genau eine Ausnahme besteht, und sie gilt nur für brillant und brillant mit
  Selbstbehalt: Aufwendungen für den Ersatz von bis zu zwei bei Vertragsschluss
  fehlenden Zähnen sind erstattungsfähig, wenn die Behandlung nach Ablauf von
  zwölf Monaten seit Versicherungsbeginn begonnen wird. In smart und balance
  gibt es diese Ausnahme nicht.

  Abzufragen sind drei Größen: ob Zähne fehlen, wie viele, und ob Ersatz
  geplant ist. Bereits ersetzte Zähne — durch Brücke, Prothese oder Implantat
  mit Krone — gelten nicht als Lücke. Weisheitszähne werden regelmäßig
  vergessen und sind ausdrücklich mitzufragen.

  #muster(
    anlass: "Frage nach fehlenden Zähnen",
    passt: [sobald die Vorgeschichte erfragt ist.],
    passt-nicht: [bei jemandem, der gerade von einer frischen Extraktion
      berichtet hat.],
  )[
    „Eine Frage, die viele überrascht: Fehlen Ihnen Zähne, die nicht ersetzt
    sind? Weisheitszähne mitgerechnet. Der Hintergrund ist, dass bei
    Vertragsschluss fehlende Zähne grundsätzlich ausgeschlossen sind — nur in
    brillant und brillant mit Selbstbehalt gibt es für bis zu zwei Zähne eine
    Ausnahme, wenn die Behandlung erst nach zwölf Monaten beginnt.“
  ]

  Die Auswertung:

  #tabelle(
    spalten: (1fr, 1.5fr),
    ausrichtung: (left, left),
    klein: true,
    kopf: ("Befund", "Folge für die Empfehlung"),
    [Keine Lücke],
    [Kein Einfluss auf die Tarifwahl.],

    [Ein oder zwei Lücken, Ersatz absehbar],
    [Nur brillant und brillant mit Selbstbehalt kommen in Betracht, dort erst
     bei Behandlungsbeginn nach zwölf Monaten. Wird trotzdem balance oder smart
     gewünscht, ist festzuhalten, dass der Ersatz dieser Zähne dort nicht
     versichert ist.],

    [Ein oder zwei Lücken, Ersatz nicht geplant],
    [Wird dokumentiert, prägt die Empfehlung aber nicht allein.],

    [Mehr als zwei Lücken],
    [Für die überzähligen Zähne greift die Ausnahme auch in brillant nicht. Das
     ist vor dem Antrag zu sagen, nicht danach.],
  )

  Die Ausnahme ist kein allgemeiner Einschluss: Sie deckt bis zu zwei Zähne und
  verlangt den späteren Behandlungsbeginn — beides gehört in dieselbe Aussage,
  sonst bleibt die Hälfte hängen.
]

#abschnitt("vorversicherungsschutz")[
  = Bestehender Vorversicherungsschutz: was er bewirkt und wonach zu fragen ist <marke-vorversicherungsschutz>

  Ein lückenloser Vorversicherungsschutz wirkt bei brillant und brillant mit
  Selbstbehalt unmittelbar: Die Wartezeit von
  #monaten(brillant.wartezeit_monate) entfällt. Für smart
  (#monate(smart.wartezeit_monate)) und balance
  (#monate(balance.wartezeit_monate)) sieht das Produktmodell diesen Entfall
  nicht vor.

  #muster(anlass: "Frage nach der Vorversicherung")[
    „Haben oder hatten Sie bereits eine Zahnzusatzversicherung — und läuft die
    noch, oder ist sie beendet?“
  ]

  Der Zusatz zur Lückenlosigkeit ist entscheidend, denn eine vor zwei Jahren
  beendete Vorversicherung ist etwas anderes als ein nahtloser Übergang. Ist
  eine im Spiel, gehören Gesellschaft, Zeitraum und bezogene Leistungen ins
  Protokoll.

  #tabelle(
    spalten: (1fr, 1.5fr),
    ausrichtung: (left, left),
    klein: true,
    kopf: ("Antwort", "Folge"),
    [Lückenloser Vorversicherungsschutz vorhanden],
    [Ein sachliches Argument für brillant oder brillant mit Selbstbehalt, weil
     dort die Wartezeit entfällt. Kein Argument gegen einen anderen Tarif, wenn
     der Bedarf woanders liegt.],

    [Früher versichert, inzwischen beendet],
    [Die Wartezeit gilt wie bei einem Neuabschluss. Zu fragen ist, warum der
     Vertrag endete — weil eine Kündigung nach einem enttäuschenden
     Leistungsfall die Erwartungshaltung prägt.],

    [Noch laufender Vertrag],
    [Dann geht es um einen Wechsel. Behandlungen, die im laufenden Vertrag
     bereits angeraten wurden, sind im neuen ausgeschlossen, und die
     Zahnstaffel beginnt neu. Beides gehört vor die Entscheidung.],

    [Nie versichert],
    [Keine Besonderheit.],
  )

  Die Wartezeit des empfohlenen Tarifs wird in jedem Fall genannt, auch
  ungefragt.
]

#abschnitt("eintrittsalter")[
  = Eintrittsalter: welche Tarife ab welchem Alter noch offenstehen <marke-eintrittsalter>

  Das Eintrittsalter ist ein hartes Kriterium: Es entscheidet nicht, was
  empfehlenswert, sondern was überhaupt abschließbar ist. Es schlägt damit jede
  Empfehlungsreihenfolge.

  #tabelle(
    spalten: (1fr, 30mm),
    ausrichtung: (left, left),
    klein: true,
    kopf: ("Tarif", "Eintrittsalter"),
    ..alle-tarife
      .map(t => (
        t.anzeigename,
        [#str(t.eintrittsalter.von) bis #str(t.eintrittsalter.bis) Jahre],
      ))
      .flatten()
  )

  Praktisch heißt das: Ab #str(brillant.eintrittsalter.bis + 1) entfallen
  brillant und brillant mit Selbstbehalt vollständig aus der Empfehlung. In der
  Altersgruppe #str(brillant.eintrittsalter.bis + 1) bis
  #str(balance.eintrittsalter.bis) ist balance der umfassendste noch
  erreichbare Tarif und damit in aller Regel der erste, der dargestellt wird —
  nicht weil die Reihenfolge das sagt, sondern weil sie dort nicht mehr greift.
  Ab #str(balance.eintrittsalter.bis + 1) ist keiner der vier Tarife mehr
  abschließbar; das wird ohne Umschweife gesagt.

  Das Alter wird sachlich erhoben, meist ohnehin über das Geburtsdatum im
  Antrag. Ein entfallender Tarif wird dabei als Faktum genannt („brillant ist
  ab 66 nicht mehr abschließbar“) und nicht als Verlust inszeniert („da hätten
  Sie früher kommen müssen“).

  Umgekehrt hat, wer früh einsteigt, Zeit, die Zahnstaffel zu durchlaufen —
  #vjahre(staffel-von(smart).dauer_jahre) bei smart und balance,
  #zahlwort(staffel-von(brillant).dauer_jahre) bei brillant und brillant mit
  Selbstbehalt. Ein sachliches Argument für einen frühen Abschluss, aber keines
  für Druck.
]

#abschnitt("implantatbedarf")[
  = Absehbarer Implantatbedarf: Fallzahlen, Quoten und die Abgrenzung zur Krone <marke-implantatbedarf>

  Implantate sind der Leistungsbereich mit der größten Spreizung zwischen den
  Tarifen, und deshalb die Frage mit dem höchsten Trennwert.

  #bereichstabelle("IMP")

  #muster(
    anlass: "Frage nach Implantaten",
    passt: [weil sie drei Abstufungen anbietet und keine bewertet.],
    passt-nicht: [in der Variante „Implantate braucht früher oder später fast
      jeder“, denn das ist Bedarfsweckung und keine Analyse.],
  )[
    „Ist bei Ihnen jemals von einem Implantat die Rede gewesen — als
    Möglichkeit, als Empfehlung oder als etwas, das Sie sich selbst überlegt
    haben?“
  ]

  Die Auswertung:

  #tabelle(
    spalten: (1fr, 1.5fr),
    ausrichtung: (left, left),
    klein: true,
    kopf: ("Antwort", "Folge"),
    [Implantat absehbar oder ausdrücklich gewünscht],
    [smart scheidet aus, weil der Hauptbedarf dort nicht versichert ist.
     Zwischen balance und brillant entscheidet die erwartete Fallzahl: Sind
     mehr als vier Implantate in fünf Jahren absehbar, reicht balance nicht.],

    [Kein Implantatthema, aber Zahnersatz absehbar],
    [Dann ist die Implantatfrage nicht ausschlaggebend, und smart bleibt im
     Spiel — mit #prozent(leistungen(smart).ZE.quote) auf Zahnersatz gegenüber
     #prozent(leistungen(balance).ZE.quote) bei balance und
     #prozent(leistungen(brillant).ZE.quote) bei brillant.],

    [Bereits angeratenes Implantat],
    [Dann greift der Ausschluss angeratener Behandlungen, unabhängig vom Tarif;
     die Fallzahlen sind für diese Maßnahme ohne Bedeutung.],
  )

  Eine Abgrenzung, die regelmäßig Verwirrung stiftet und deshalb aktiv erklärt
  wird: Zum Leistungsbereich Implantate zählen die künstliche Zahnwurzel, der
  Aufbau und der nötige Knochenaufbau; die aufgesetzte Krone zählt zum
  Zahnersatz. Eine Implantatversorgung verteilt sich also auf zwei
  Leistungsbereiche mit zwei Quoten: bei balance
  #prozent(leistungen(balance).IMP.quote) auf beides, bei brillant
  #prozent(leistungen(brillant).IMP.quote) auf beides; bei smart ist der
  Implantatteil gar nicht versichert, während die Krone zu
  #prozent(leistungen(smart).ZE.quote) unter Zahnersatz fällt. Was dabei nicht
  fällt, ist eine Zusage über die Höhe der Erstattung — verbindlich rechnet nur
  die Leistungsabteilung.
]

#abschnitt("kieferorthopaedie")[
  = Kieferorthopädie bei Erwachsenen: Limits, Wartezeiten und was daraus folgt <marke-kieferorthopaedie>

  Kieferorthopädie bei Erwachsenen ist in den Tarifen sehr unterschiedlich
  geregelt und wird fast nie von sich aus angesprochen. Deshalb gehört sie fest
  in den Fragenkatalog.

  #bereichstabelle("KFO", wartezeit: true)

  Die Limits sind Gesamtlimits über die Vertragsdauer, nicht Jahreslimits. Das
  ist der Punkt, an dem die Erwartung am häufigsten auseinandergeht, und er
  wird ausdrücklich gesagt.

  #muster(
    anlass: "Frage nach Kieferorthopädie",
    passt: [weil sie den Bereich benennt, ohne eine Absicht zu unterstellen.],
    passt-nicht: [in der Form „Sind Sie mit der Stellung Ihrer Zähne
      zufrieden?“ — das ist eine Bewertung des Aussehens und in einer
      Bedarfsanalyse fehl am Platz.],
  )[
    „Ist eine Zahn- oder Kieferregulierung für Sie ein Thema — eine Schiene,
    eine feste Spange, eine Korrektur?“
  ]

  Die Auswertung ist einfach: Ist Kieferorthopädie ein Thema, scheidet smart
  aus. Zwischen balance und brillant entscheidet die Größenordnung — die
  Differenz zwischen #eur0(leistungen(balance).KFO.limit_gesamt) und
  #eur0(leistungen(brillant).KFO.limit_gesamt) Gesamtlimit ist bei einer
  Erwachsenenbehandlung erheblich. Ist Kieferorthopädie kein Thema, wird sie
  nicht zum Argument gemacht.

  #hinweis(titel: "Die besondere Wartezeit entfällt nicht mit der allgemeinen")[
    Die allgemeine Wartezeit von #monaten(brillant.wartezeit_monate) entfällt
    bei brillant und brillant mit Selbstbehalt bei lückenlosem
    Vorversicherungsschutz. Die besondere Wartezeit für Kieferorthopädie
    entfällt dadurch nicht — sie bleibt bei
    #monaten(leistungen(brillant).KFO.wartezeit_monate) bestehen. Das
    Bedingungswerk sagt das ausdrücklich. Wer hier vorschnell „dann entfällt
    alles“ sagt, erzeugt eine Erwartung, die im Leistungsfall enttäuscht wird.
  ]
]

#abschnitt("weitere-leistungsbereiche")[
  = Weitere bedarfsbestimmende Fragen: Parodontose, Schiene, Narkose, Prophylaxe, Inlays <marke-weitere-leistungsbereiche>

  Neben den großen Trennfragen geben diese Bereiche je nach Lage den Ausschlag
  und werden deshalb kurz abgefragt.

  #stichwort[Parodontose.] Ist der Bereich ein Thema, scheidet smart aus.

  #bereichstabelle("PAR")

  #muster(anlass: "Parodontose")[
    „Ist bei Ihnen schon einmal von Zahnfleischproblemen oder einer
    Parodontosebehandlung gesprochen worden?“
  ]

  #stichwort[Funktionsanalyse und Aufbissschiene.] Die zweite Hälfte der Frage
  berührt den Ausschluss angeratener Behandlungen.

  #bereichstabelle("FUN")

  #muster(anlass: "Funktionsanalyse und Schiene")[
    „Knirschen Sie nachts, oder ist Ihnen zu einer Schiene geraten worden?“
  ]

  #stichwort[Narkose und Sedierung.] Nur fragen, wenn ausgeprägte
  Behandlungsangst zur Sprache kommt. Die örtliche Betäubung gehört zur
  jeweiligen Behandlung und ist kein eigener Leistungsbereich.

  #bereichstabelle("NAR")

  #stichwort[Prophylaxe.] In allen vier Tarifen versichert, aber mit sehr
  unterschiedlichem Deckel. Ein ehrliches Unterscheidungsmerkmal, aber kein
  Hauptargument für einen Vollschutztarif.

  #bereichstabelle("PZR")

  #stichwort[Inlays und Zahnerhalt.] Bei laborgefertigten Inlays leistet smart
  #prozent(leistungen(smart).INL.quote), balance
  #prozent(leistungen(balance).INL.quote), brillant und brillant mit
  Selbstbehalt #prozent(leistungen(brillant).INL.quote); direkt gelegte
  Füllungen zählen zum Zahnerhalt mit
  #prozent(leistungen(smart).ZERH.quote),
  #prozent(leistungen(balance).ZERH.quote) beziehungsweise
  #prozent(leistungen(brillant).ZERH.quote).
]

#abschnitt("budget-und-rahmen")[
  = Budget, Selbstbehalt und Erwartung: den Rahmen erfragen, ohne ihn zu bewerten <marke-budget-und-rahmen>

  Der Rahmen wird am Ende der Bedarfsanalyse erhoben, nachdem der Bedarf steht.
  Drei Punkte gehören dazu.

  #stichwort[Der Beitragsrahmen.] Eine genannte Zahl wird respektiert und nicht
  in Frage gestellt; ein Budgetrahmen, den der vorrangige Tarif überschreitet,
  ist einer der Fälle, in denen die Empfehlungsreihenfolge nicht greift. Wird
  keine Zahl genannt, wird nicht nachgebohrt.

  #muster(anlass: "Beitragsrahmen")[
    „Haben Sie eine Vorstellung, was Ihnen der Schutz im Monat wert wäre?“
  ]

  #stichwort[Die Haltung zum Selbstbehalt.] balance hat
  #eur0(balance.selbstbehalt) Selbstbehalt, brillant mit Selbstbehalt
  #eur0(brillant-sb.selbstbehalt), smart und brillant keinen. Der Selbstbehalt
  gilt einmal im Versicherungsjahr über alle Leistungsbereiche zusammen, nicht
  je Bereich — der am häufigsten missverstandene Punkt, ungefragt zu erklären.
  Wer regelmäßige kleinere Behandlungen erwartet, für den greift er jedes Jahr
  aufs Neue; wer selten, dann aber größere Leistungen erwartet, fährt mit ihm
  gut.

  #muster(anlass: "Haltung zum Selbstbehalt")[
    „Wäre es für Sie in Ordnung, kleinere Beträge selbst zu tragen, wenn der
    Beitrag dafür niedriger ist?“
  ]

  #stichwort[Der Zeithorizont.] Die Frage entscheidet über die Bedeutung der
  Wartezeit — #monate(smart.wartezeit_monate) bei smart,
  #monate(balance.wartezeit_monate) bei balance, brillant und brillant mit
  Selbstbehalt, wobei sie bei den brillant-Tarifen bei lückenlosem
  Vorversicherungsschutz entfällt. Wer kurzfristig Schutz braucht, für den ist
  smart nicht die passende Wahl.

  #muster(anlass: "Zeithorizont")[
    „Ab wann soll der Schutz greifen?“
  ]

  Zeitdruck geht in dieser Phase ausschließlich vom Kunden aus, nie vom
  Berater. Der Wunsch nach Bedenkzeit wird ohne Gegenrede akzeptiert.
]

#abschnitt("ableitungsschema")[
  = Von den Antworten zur Empfehlung: das Ableitungsschema in fünf Schritten <marke-ableitungsschema>

  Aus den Antworten der Bedarfsanalyse entsteht die Tarifempfehlung in fester
  Abfolge. Wer sie umdreht, landet bei einer Empfehlung, die im ersten
  Leistungsfall auffliegt.

  #stichwort[Schritt 1 — harte Kriterien anwenden.] Eintrittsalter über
  #str(brillant.eintrittsalter.bis) schließt brillant und brillant mit
  Selbstbehalt aus, über #str(balance.eintrittsalter.bis) alle vier.
  Unversorgte Lücken mit geplantem Ersatz lassen nur brillant und brillant mit
  Selbstbehalt übrig, dort nur bis zu zwei Zähne bei Behandlungsbeginn nach
  zwölf Monaten. Liegt der Hauptbedarf in einem Bereich, den ein Tarif nicht
  versichert, fällt dieser Tarif aus — bei smart sind das
  #smart-luecken.

  #stichwort[Schritt 2 — Ausschlüsse markieren.] Vor Vertragsschluss angeratene
  oder begonnene Behandlungen sind in allen Tarifen ausgeschlossen. Sie
  verändern nicht die Tarifauswahl, wohl aber die Erwartung, und werden hier
  ausgesprochen, nicht erst am Ende.

  #stichwort[Schritt 3 — Bedarfsschwerpunkt bestimmen.] Gesucht wird der Tarif,
  der den erkennbaren Schwerpunkt am besten abdeckt — das, was die Kundin oder
  der Kunde selbst genannt hat, nicht das, was theoretisch eintreten könnte.

  #stichwort[Schritt 4 — Rahmen anlegen.] Beitragsrahmen, Haltung zum
  Selbstbehalt und Zeithorizont auf die verbliebenen Tarife anlegen.

  #stichwort[Schritt 5 — erst jetzt die Reihenfolge.] Bleiben zwei oder mehr
  Tarife gleichwertig geeignet, entscheidet die Empfehlungsreihenfolge:
  #alle-tarife.map(t => t.anzeigename.replace("atra.dent.", "")).join(", ", last: ", ").
  Nur dann. Ist ein günstigerer Tarif ebenso geeignet, wird er genannt.

  Zu jeder Empfehlung gehören unaufgefordert Wartezeit, Zahnstaffel, etwaiger
  Selbstbehalt und die tarifspezifischen Hinweispflichten:

  #tabelle(
    spalten: (1fr, 1.6fr),
    ausrichtung: (left, left),
    klein: true,
    kopf: ("Tarif", "Ungefragt zu nennen"),
    [#smart.anzeigename],
    [Die nicht versicherten Bereiche:
     #smart-luecken.],

    [#balance.anzeigename],
    [Der Selbstbehalt von #eur0(balance.selbstbehalt), der einmal jährlich über
     alle Leistungsbereiche zusammen gilt, nicht je Bereich.],

    [#brillant.anzeigename],
    [Die Jahreshöchstgrenze von #eur0(brillant.jahreshoechstgrenze) und die
     Zahnstaffel — Vollschutz heißt nicht unbegrenzt.],

    [#brillant-sb.anzeigename],
    [Dass der Leistungsumfang mit brillant identisch ist und sich nur der
     Selbstbehalt von #eur0(brillant-sb.selbstbehalt) unterscheidet. Diese
     Variante darf nie als kleinerer Schutz dargestellt werden.],
  )
]

#abschnitt("reihenfolge-greift-nicht")[
  = Wenn die Empfehlungsreihenfolge nicht greift: die Fälle im Einzelnen <marke-reihenfolge-greift-nicht>

  Die Empfehlungsreihenfolge gilt ausschließlich bei gleichwertiger Eignung. In
  folgenden Lagen greift sie nicht.

  #stichwort[Alter über #str(brillant.eintrittsalter.bis).] brillant und
  brillant mit Selbstbehalt sind nicht abschließbar; die Reihenfolge beginnt
  faktisch bei balance. Ab #str(balance.eintrittsalter.bis + 1) ist keiner der
  Tarife mehr möglich.

  #stichwort[Ein Budgetrahmen wurde genannt, den der vorrangige Tarif
  überschreitet.] Dann wird der Rahmen respektiert und der passende Tarif
  empfohlen, statt den Rahmen in Frage zu stellen. Zulässig ist, den
  umfassenderen Tarif einmal zu nennen, damit die Entscheidung informiert
  fällt.

  #stichwort[Der Bedarf ist erkennbar eng.] Deckt ein kleinerer Tarif den
  gesamten erkennbaren Bedarf ab, wird er empfohlen. Ein junger Versicherter
  ohne Vorschäden, ohne Implantatthema, ohne Kieferorthopädie und mit Wunsch
  nach niedrigem Beitrag ist ein smart-Kunde, auch wenn smart zuletzt steht.

  #stichwort[Regelmäßige kleinere Behandlungen sind absehbar.] Dann ist
  brillant mit Selbstbehalt trotz seines Rangs die schlechtere Wahl, weil der
  Selbstbehalt von #eur0(brillant-sb.selbstbehalt) jedes Jahr aufs Neue greift.

  #stichwort[Der Selbstbehalt wird grundsätzlich abgelehnt.] Dann scheiden
  balance und brillant mit Selbstbehalt aus; es bleiben brillant und smart,
  zwischen denen der Bedarf entscheidet.

  #stichwort[Mehr als vier Implantate in fünf Jahren sind absehbar.] Dann
  reicht balance nicht, so gut es sonst passen mag.

  In all diesen Fällen braucht die Abweichung keine Begründung gegenüber dem
  Vertrieb — eine Empfehlung gegen den erkennbaren Bedarf braucht sie.
]

#abschnitt("unklare-antworten")[
  = Umgang mit unklaren, unvollständigen oder ausweichenden Antworten <marke-unklare-antworten>

  Nicht jede Frage der Bedarfsanalyse wird beantwortet, und nicht jede Antwort
  ist eindeutig. Der Umgang damit folgt drei Regeln.

  #stichwort[Erstens: Eine Frage wird einmal gestellt.] Wer ausweicht, weicht
  mit Absicht aus. Dieselbe Frage in anderer Verpackung erneut zu stellen, ist
  Bohren, und beschädigt das Gespräch mehr, als die fehlende Antwort schadet.
  Zulässig ist eine Präzisierung, wenn die Frage erkennbar missverstanden wurde
  — nicht, wenn die Antwort missfällt.

  #stichwort[Zweitens: Die Lücke wird benannt, nicht gefüllt.]

  #muster(
    anlass: "Lücke benennen",
    passt: [bei erkennbarem Unbehagen.],
    passt-nicht: [wenn der Kunde die Frage schlicht überhört hat; dann wird sie
      wiederholt.],
  )[
    „Das ist in Ordnung, wir lassen das offen. Ich sage Ihnen nur, was daran
    hängt: Wenn bei Vertragsschluss bereits etwas angeraten war, ist das
    ausgeschlossen. Sie müssen mir das nicht sagen — Sie sollten nur wissen,
    dass es so ist.“
  ]

  #stichwort[Drittens: Die Empfehlung stützt sich auf den gesicherten Teil.]
  Aus einer Nichtantwort wird keine Annahme gebaut, in keine Richtung. Bleibt
  der Implantatbedarf unklar, wird nicht vorsorglich brillant empfohlen, weil
  damit sicher nichts falsch sei — das wäre eine Empfehlung ins Blaue. Ist der
  übrige Bedarf eng, bleibt die Empfehlung eng, und die offene Frage wird
  benannt.

  Gesundheitsangaben werden zudem nur mit der versicherten Person selbst
  besprochen, nicht mit Angehörigen. Meldet sich jemand für eine dritte Person,
  endet die Bedarfsanalyse an dieser Stelle.

  Bei widersprüchlichen Angaben — etwa „nichts angeraten“ bei vorliegendem
  Kostenvoranschlag — wird der Widerspruch einmal sachlich und ohne Vorwurf
  angesprochen. Bleibt es widersprüchlich, wird beides protokolliert und nichts
  geglättet.

  #muster(anlass: "Widerspruch ansprechen")[
    „Damit ich es richtig festhalte: Steht hinter dem Kostenvoranschlag eine
    Behandlung, zu der Ihnen geraten wurde?“
  ]
]

#abschnitt("kein-bedarf")[
  = Woran man erkennt, dass kein Bedarf besteht, und was dann zu tun ist <marke-kein-bedarf>

  Es gibt Gespräche, in denen die Bedarfsanalyse zu dem Ergebnis kommt, dass
  kein Bedarf besteht. Dieses Ergebnis ist zulässig und wird nicht
  wegverhandelt. Anzeichen sind unter anderem: ein bestehender, gleichwertiger
  Vertrag ohne erkennbare Lücke; ein Alter über
  #str(balance.eintrittsalter.bis), in dem keiner der vier Tarife mehr
  abschließbar ist; die klare Aussage, dass nur ein Angebot verglichen werden
  sollte; oder eine Lage, in der der gesamte Anlass unter den Ausschluss
  angeratener Behandlungen fällt und daneben kein weiterer Bedarf erkennbar
  ist.

  Der letzte Fall ist verführerisch: Wer wegen eines Heil- und Kostenplans
  anruft, hat einen starken Abschlusswunsch — aber genau dafür wird nicht
  geleistet. Ein Vertrag kann sinnvoll sein, wenn daneben ein Bedarf steht.
  Steht dort nichts, wird das gesagt.

  #muster(
    anlass: "kein Bedarf erkennbar",
    passt: [wenn der Bedarf tatsächlich fehlt.],
    passt-nicht: [als bequemer Ausweg aus einem anstrengenden Gespräch und
      nicht bei jemandem, der schlicht unentschlossen ist; Unentschlossenheit
      ist kein fehlender Bedarf, sondern ein Thema der Einwandbehandlung.],
  )[
    „Ehrlich gesagt sehe ich für Ihre jetzige Situation keinen Vorteil. Die
    Behandlung, um die es geht, wäre ausgeschlossen, und darüber hinaus sehe
    ich im Moment nichts, was einen Vertrag rechtfertigt. Wenn sich das ändert,
    melden Sie sich gern.“
  ]

  Was hier nicht getan wird: kein Suchen nach einem Ersatzbedarf, kein Ausmalen
  künftiger Behandlungen, kein Hinweis darauf, dass „irgendwann jeder“ etwas
  braucht. Ein Nichtbedarfsgespräch wird sauber beendet und dokumentiert: Es
  kostet einen Abschluss und erspart eine Stornierung.
]

#abschnitt("dokumentation-bedarfsermittlung")[
  = Dokumentation der Bedarfsermittlung: was festgehalten wird und warum <marke-dokumentation-bedarfsermittlung>

  Die Bedarfsermittlung wird so dokumentiert, dass ein Dritter Monate später
  nachvollziehen kann, warum genau dieser Tarif empfohlen wurde. Das dient der
  Rekonstruktion im Streitfall — und der ist typischerweise der erste
  enttäuschende Leistungsfall. Festgehalten werden:

  #tabelle(
    spalten: (1fr, 1.5fr),
    ausrichtung: (left, left),
    klein: true,
    bindend: false,
    kopf: ("Angabe", "Ausprägung"),
    [Datum, Kanal, Gesprächspartner],
    [Bei Telefonberatung auch, ob die versicherte Person selbst gesprochen hat.
     Dazu der Anlass in ihren Worten.],

    [Angeratene oder begonnene Behandlungen],
    [Wortnah und ungeglättet, einschließlich der Angabe „unklar“. Dazu der
     Vermerk, dass der Ausschluss angesprochen wurde.],

    [Fehlende Zähne],
    [Anzahl, ob versorgt, ob Ersatz geplant.],

    [Vorversicherungsschutz],
    [Ob vorhanden, lückenlos, Gesellschaft, Zeitraum.],

    [Alter],
    [Beziehungsweise Geburtsdatum.],

    [Genannte Bedarfsschwerpunkte],
    [Implantate, Kieferorthopädie, Parodontose, Funktionsanalyse, Narkose,
     Prophylaxe, Zahnersatz.],

    [Genannter Rahmen],
    [Budget, Haltung zum Selbstbehalt, gewünschter Beginn.],

    [Empfohlener Tarif und tragende Begründung],
    [In ein bis zwei Sätzen; bei Abweichung von der Empfehlungsreihenfolge,
     welcher Fall vorlag.],

    [Erteilte Pflichthinweise],
    [Wartezeit, Zahnstaffel, Selbstbehalt, nicht versicherte Bereiche,
     Jahreshöchstgrenze bei den brillant-Tarifen, Ausschluss angeratener
     Behandlungen, Ausschluss fehlender Zähne.],

    [Offen gebliebene Punkte],
    [Als solche, nicht als Annahme.],
  )

  Zwei Regeln zur Form. Erstens: Antworten und Schlussfolgerungen werden
  getrennt notiert. „Kunde gibt an, es sei nichts angeraten“ ist etwas anderes
  als „es ist nichts angeraten“. Zweitens: keine medizinischen Bewertungen.
  „Sanierungsbedarf erkennbar“ ist eine Diagnose und steht dem Berater nicht
  zu; „Kunde berichtet von mehreren Füllungen in den letzten Jahren“ ist eine
  Wiedergabe und ist richtig.

  Wird ein günstigerer Tarif genannt und abgelehnt, gehört auch das ins
  Protokoll: Dass er offenstand, muss belegbar sein.
]

#abschnitt("compliance-bedarfsanalyse")[
  = Compliance-Grenzen speziell in der Bedarfsanalyse <marke-compliance-bedarfsanalyse>

  Die Compliance-Grenzen des Beratungsleitfadens gelten in jeder Phase. In der
  Bedarfsanalyse sind diese besonders leicht zu verletzen, weil die Fragen nahe
  an Gesundheit und Geld liegen.

  #stichwort[Keine Aussage zur medizinischen Notwendigkeit.] Ob eine Behandlung
  notwendig oder sinnvoll ist, entscheidet die Zahnärztin oder der Zahnarzt.
  Zulässig ist allein die Auskunft, welcher Leistungsbereich betroffen wäre und
  wie der Tarif dort steht.

  #stichwort[Keine Zusage über die Höhe einer Erstattung.] Quote, Zahnstaffel,
  Selbstbehalt, Sublimit und die Vorleistung der gesetzlichen
  Krankenversicherung wirken zusammen; verbindlich rechnet nur die
  Leistungsabteilung. Besonders die Rückfrage „Was bekomme ich denn dann für
  meine Krone?“ verführt zu einer Zahl.

  #stichwort[Ausschlüsse und Wartezeit aktiv nennen], nicht erst auf Nachfrage
  — angeratene und begonnene Behandlungen, bei Vertragsschluss fehlende Zähne,
  Wartezeit des empfohlenen Tarifs.

  #stichwort[Kein Drängen] und keine künstliche Verknappung.

  #stichwort[Keine Gesundheitsdaten über Dritte.] Solche Angaben werden nur mit
  der versicherten Person selbst besprochen, nicht mit Angehörigen am Telefon.

  Und über allem: Bei Widerspruch zwischen diesem Handbuch und einem
  Bedingungswerk gilt das Bedingungswerk; auf Nachfrage wird die
  Bedingungsstelle genannt, nicht das interne Material.
]

// =======================================================================
// Kapitel 3 — Einwandbehandlung
// =======================================================================
//
// Die Einwaende sind gleich aufgebaut: Was dahintersteckt, klaerende
// Rueckfrage, sachliche Antwort, Musterformulierung, und wann der Einwand
// berechtigt ist. Der letzte Block traegt das Kapitel — ein Einwand, der
// zutrifft, wird bestaetigt und nicht behandelt.

#kapitel(3, "Einwandbehandlung", "kapitel-einwandbehandlung")[
  Dieses Kapitel behandelt die Situation, in der eine Kundin oder ein Kunde
  einen Einwand ausspricht — also nach der Empfehlung, nicht davor. Jeder
  Einwand ist nach demselben Schema dargestellt: was dahintersteckt, welche
  Rückfrage das klärt, wie die sachliche Antwort lautet, wie sie formuliert
  werden kann und wann der Einwand berechtigt ist.

  #hinweis(titel: "Abgrenzung")[
    Der Aufbau des Gesprächs, seine Phasen und die Besonderheiten am Telefon
    stehen in Kapitel 1. Die Fragenkataloge zur Bedarfsermittlung stehen in
    Kapitel 2. Antworten aus der Bedarfsanalyse sind keine Einwände: Wer beides
    vermischt, beginnt zu überzeugen, wo er noch zuhören sollte.
  ]
]

#abschnitt("einwand-grundhaltung")[
  = Grundhaltung: Ein Einwand ist eine Information, kein Widerstand <marke-einwand-grundhaltung>

  Einwandbehandlung bedeutet nicht, einen Einwand wegzuverhandeln. Sie
  bedeutet: den Einwand verstehen, ihn sachlich beantworten — und ihm recht
  geben, wenn er zutrifft. Der Grundsatz aus #verweis-auf(<marke-grundsatz-bedarf-entscheidet>) gilt
  auch hier, und gerade hier: Maßgeblich ist der Bedarf, und passt ein
  günstigerer Tarif ebenso gut, wird er genannt.

  Genau deshalb ist ein Einwand wertvoll: Er zeigt entweder, dass eine
  Information gefehlt hat, oder dass die Empfehlung nicht gepasst hat, oder
  dass es schlicht keinen Bedarf gibt. Jeder Einwand hat damit drei mögliche
  Ausgänge, und alle drei sind zulässige Gesprächsergebnisse.

  #tabelle(
    spalten: (30mm, 1fr),
    ausrichtung: (left, left),
    klein: true,
    kopf: ("Ausgang", "Vorgehen"),
    [Missverständnis],
    [Der Einwand beruht auf einer falschen Annahme über das Produkt. Dann wird
     sachlich richtiggestellt — mit den Zahlen des Tarifs, nicht mit
     Beteuerungen.],

    [Falsche Empfehlung],
    [Der Einwand zeigt, dass ein anderer Tarif besser passt. Dann wird der
     andere Tarif empfohlen, auch wenn er der kleinere ist.],

    [Zutreffender Einwand],
    [Es gibt keinen Bedarf, oder der Bedarf lässt sich mit diesem Produkt nicht
     decken. Dann wird das gesagt und das Gespräch endet ohne Abschluss.],
  )

  Der dritte Ausgang ist kein Scheitern. Ein Gespräch, das ehrlich ohne
  Abschluss endet, kostet weniger als eine Police, die im ersten Leistungsfall
  storniert wird.
]

#abschnitt("einwand-grenzen")[
  = Die Grenzen: Was in der Einwandbehandlung ausdrücklich unzulässig ist <marke-einwand-grenzen>

  Diese Grenzen gelten in jedem Gespräch und ausnahmslos. Sie sind gerade bei
  Einwänden wichtig, weil dort die Versuchung am größten ist, sie zu
  überschreiten.

  #tabelle(
    spalten: (40mm, 1fr),
    ausrichtung: (left, left),
    klein: true,
    bindend: false,
    kopf: ("Grenze", "Bedeutung"),
    [Kein Drängen],
    [Kein Zeitdruck, keine künstliche Verknappung, keine Andeutung, ein Angebot
     gelte nur heute oder nur in diesem Gespräch. Der Wunsch nach Bedenkzeit
     oder Rücksprache wird ohne Gegenrede akzeptiert.],

    [Kein zweiter Anlauf gegen ein klares Nein],
    [Ein Nein wird einmal verstanden, nicht zweimal getestet. Das Angebot wird
     nach einer Ablehnung nicht wiederholt und nicht umformuliert erneut
     vorgelegt.],

    [Keine Erstattungszusage],
    [Keine Zusage über die Höhe einer konkreten Erstattung. Quote, Zahnstaffel,
     Selbstbehalt, Sublimit und die Vorleistung der gesetzlichen
     Krankenversicherung wirken zusammen; verbindlich rechnet ausschließlich
     die Leistungsabteilung. Beispielrechnungen sind als Beispiele zu
     kennzeichnen.],

    [Keine Heilversprechen],
    [Keine Aussage darüber, ob eine Behandlung medizinisch notwendig oder
     sinnvoll ist. Das entscheidet die Zahnärztin oder der Zahnarzt.],

    [Ausschlüsse aktiv nennen],
    [Vor Vertragsschluss angeratene oder begonnene Behandlungen und bei
     Vertragsschluss fehlende Zähne werden von sich aus angesprochen, nicht
     erst auf Nachfrage.],

    [Wartezeit ungefragt nennen],
    [Die Wartezeit des empfohlenen Tarifs gehört in jede Empfehlung.],

    [Keine Gesundheitsdaten über Dritte],
    [Angaben zum Gesundheitszustand werden nur mit der versicherten Person
     selbst besprochen, nicht mit Angehörigen am Telefon.],

    [Bedingungen haben Vorrang],
    [Bei Widerspruch zwischen internem Material und Bedingungswerk gilt das
     Bedingungswerk.],
  )

  Ein Verstoß gegen diese Grenzen ist kein Kavaliersdelikt und wird auch dann
  nicht toleriert, wenn er zu einem Abschluss geführt hat.
]

#abschnitt("einwand-zu-teuer")[
  = Einwand „Das ist mir zu viel Geld im Monat“ <marke-einwand-zu-teuer>

  == Was dahintersteckt

  Der Preiseinwand ist selten ein Einwand gegen den Preis. Meistens ist er
  einer von drei Dingen: Der Nutzen ist noch nicht sichtbar, weil die
  Bedarfsermittlung zu dünn war. Oder der Beitrag übersteigt tatsächlich das
  Budget. Oder — der häufigste Fall — es wurde ein Tarif angeboten, der zu groß
  für den Bedarf ist. Ein Preiseinwand ist deshalb zuerst ein Hinweis darauf,
  dass die Empfehlung nicht gepasst hat, und erst danach ein Hinweis auf das
  Budget.

  == Rückfrage, die das klärt

  „Damit ich das richtig einordne: Ist der Beitrag insgesamt zu hoch, oder ist
  es der Beitrag für genau diesen Leistungsumfang?“ Diese Frage trennt Budget
  von Nutzen. Ergänzend, wenn ein Rahmen erkennbar ist: „Haben Sie eine
  Vorstellung, in welchem Rahmen sich das bewegen sollte?“

  == Sachliche Antwort

  Wird ein Budgetrahmen genannt, wird er respektiert und nicht in Frage
  gestellt. Die Empfehlungsreihenfolge entscheidet nur den Zweifelsfall bei
  gleichwertiger Eignung. Ein genannter Rahmen schlägt sie. Der Weg nach unten
  ist im Produktmodell klar abgestuft, und jede Stufe hat einen benennbaren
  Preis in Leistung.

  #stichwort[Von brillant zu brillant mit Selbstbehalt.] Der Leistungsumfang
  ist identisch — dieselben Quoten, dieselbe Zahnstaffel über
  #vjahre(staffel-von(brillant).dauer_jahre), dieselbe Jahreshöchstgrenze von
  #eur0(brillant.jahreshoechstgrenze). Der einzige Unterschied ist ein
  Selbstbehalt von #eur0(brillant-sb.selbstbehalt) je Versicherungsjahr. Diese
  Variante darf nicht als „kleinerer Schutz“ dargestellt werden, denn sie ist
  keiner.

  #stichwort[Von brillant zu balance.] Der Selbstbehalt beträgt
  #eur0(balance.selbstbehalt) je Versicherungsjahr über alle Leistungsbereiche
  zusammen. Die Quoten sinken, und die Grenzen ändern sich:

  #tabelle(
    spalten: (1fr, 1fr, 1fr),
    ausrichtung: (left, left, left),
    klein: true,
    kopf: ("Leistungsbereich", "brillant", "balance"),
    ..("ZE", "ZERH", "PAR", "IMP", "KFO")
      .map(k => {
        let ex = leistungen(brillant).at(k)
        let eb = leistungen(balance).at(k)
        let zelle(e) = {
          let g = begrenzungen(e, kurz: true)
          [#prozent(e.quote)#if g.len() > 0 [, #g.join("; ")]]
        }
        (bereich(k).name, zelle(ex), zelle(eb))
      })
      .flatten()
  )

  Dafür gibt es bei balance keine Jahreshöchstgrenze, und die Wartezeit ist mit
  #monaten(balance.wartezeit_monate) dieselbe.

  #stichwort[Von balance zu smart.] Hier endet die reine Preisdiskussion. In
  smart sind
  #smart-luecken
  nicht versichert. Die Quoten liegen bei
  #prozent(leistungen(smart).ZE.quote) für Zahnersatz und Inlays,
  #prozent(leistungen(smart).ZERH.quote) für Zahnerhalt und Akutbehandlung. Die
  Wartezeit beträgt #monate(smart.wartezeit_monate) statt
  #monate(balance.wartezeit_monate). Diese fünf Lücken sind ausdrücklich zu
  nennen — sie sind der häufigste Grund für spätere Enttäuschung.

  #muster(
    passt: [wenn der Bedarf breit ist und nur der Beitrag klemmt.],
    passt-nicht: [wenn die Bedarfsanalyse ergeben hat, dass ein Bereich zwingend
      gebraucht wird, den der kleinere Tarif nicht abdeckt — dann ist der
      günstigere Tarif keine Lösung, sondern eine Fehlberatung.],
  )[
    „Dann drehen wir das um. Sie haben mir gesagt, was Ihnen wichtig ist —
    lassen Sie uns schauen, welcher Tarif das zu Ihrem Rahmen abbildet. Wenn
    Sie kleinere Rechnungen ohnehin selbst zahlen würden, gibt es dieselbe
    Leistung wie in brillant auch mit 250 EUR Selbstbehalt im Jahr. Wenn es
    deutlich darunter liegen soll, ist balance der nächste Schritt, und dann
    muss ich Ihnen sagen, was sich dabei ändert.“
  ]

  == Wann der Einwand berechtigt ist

  Wenn auch der kleinste passende Tarif über dem Budget liegt, ist der Einwand
  richtig und das Gespräch endet. Ebenso, wenn der Bedarf nur mit einem Tarif
  zu decken wäre, der außerhalb des Rahmens liegt: Dann wird nicht ein
  untauglicher Tarif verkauft, weil er billiger ist. „Dann passt das im Moment
  nicht, und das ist völlig in Ordnung“ ist ein zulässiger Gesprächsschluss.
]

#abschnitt("einwand-kein-bedarf")[
  = Einwand „Das brauche ich nicht, meine Zähne sind gesund“ <marke-einwand-kein-bedarf>

  == Was dahintersteckt

  Meistens eine sachlich richtige Beobachtung mit einer falschen
  Schlussfolgerung: Der aktuelle Zustand wird auf die Zukunft fortgeschrieben.
  Manchmal steckt auch nur ein Abwimmeln dahinter, etwa wenn das Gespräch
  ungelegen kommt.

  == Rückfrage, die das klärt

  „Wann waren Sie zuletzt beim Zahnarzt, und stand da etwas an?“ Und, falls das
  offen bleibt: „Wenn morgen eine größere Behandlung anstünde — hätten Sie
  dafür etwas zurückgelegt, oder wäre das ein Problem?“ Die zweite Frage
  verschiebt das Thema vom Gesundheitszustand zur Risikotragfähigkeit, und dort
  gehört es hin.

  == Sachliche Antwort

  Ein gesundes Gebiss ist der beste Zeitpunkt für einen Abschluss, nicht der
  schlechteste — und zwar aus einem Grund, der im Produktmodell steht und nicht
  in der Verkaufslogik: Vor Vertragsschluss angeratene oder begonnene
  Behandlungen sind in allen vier Tarifen ausgeschlossen, ebenso bei
  Vertragsschluss fehlende Zähne. Wer noch keine Diagnose hat, hat noch alle
  Möglichkeiten offen. Wer eine hat, hat sie für diesen Befund nicht mehr.

  Dazu kommt die Zahnstaffel: In den ersten Jahren ist die Summe der
  Erstattungen begrenzt. Wer früh einsteigt, hat diese Jahre hinter sich, bevor
  er sie braucht.

  #let staffeltabelle = {
    let max-jahr = calc.max(..alle-tarife.map(t => staffel-von(t).dauer_jahre))
    tabelle(
      spalten: (1fr,) + range(max-jahr).map(_ => 1fr),
      ausrichtung: (left,) + range(max-jahr).map(_ => right),
      klein: true,
      kopf: ("Tarif",) + range(1, max-jahr + 1).map(j => [nach #str(j) Jahren]),
      ..alle-tarife
        .map(t => (
          t.anzeigename,
          ..range(1, max-jahr + 1).map(j => {
            let stufe = staffel-von(t).stufen.find(s => s.bis_jahr == j)
            if stufe == none { text(fill: dunkelgrau)[—] } else { eur0(stufe.betrag) }
          }),
        ))
        .flatten()
    )
  }
  #staffeltabelle

  Die Beträge sind kumuliert und gelten für die Summe aller Erstattungen seit
  Versicherungsbeginn.

  Was hier ausdrücklich nicht passiert: Es wird nicht mit Krankheitsbildern
  Angst gemacht und es wird keine Aussage darüber getroffen, was medizinisch
  auf jemanden zukommt. Das entscheidet die Zahnärztin oder der Zahnarzt, nicht
  der Vertrieb.

  #muster(
    passt: [wenn tatsächlich kein Befund vorliegt.],
    passt-nicht: [als Antwort auf jemanden, der bereits einen Heil- und
      Kostenplan in der Hand hält — dort gilt #verweis-auf(<marke-einwand-nach-diagnose>).],
  )[
    „Das ist ein guter Ausgangspunkt, und ehrlich gesagt der einzige, an dem so
    ein Vertrag überhaupt uneingeschränkt funktioniert: Was der Zahnarzt
    bereits angeraten hat, ist in jedem unserer Tarife ausgeschlossen. Solange
    nichts ansteht, entscheiden Sie frei. Die Frage ist deshalb nicht, ob Ihre
    Zähne heute in Ordnung sind, sondern ob Sie eine vierstellige Rechnung in
    ein paar Jahren aus eigener Tasche zahlen wollen oder nicht.“
  ]

  == Wann der Einwand berechtigt ist

  Wenn jemand das Risiko bewusst selbst tragen will und die Mittel dafür hat,
  ist das eine legitime Entscheidung. Eine Zahnzusatzversicherung ist kein
  Muss. Wer sagt „Ich lege lieber selbst zurück“, bekommt keine Gegenrede,
  sondern höchstens noch den Hinweis auf den Ausschluss angeratener
  Behandlungen, damit die Entscheidung informiert ist — und dann endet das
  Gespräch.
]

#abschnitt("einwand-wartezeit")[
  = Einwand „Die Wartezeit ist zu lang“ <marke-einwand-wartezeit>

  == Was dahintersteckt

  Zwei sehr verschiedene Dinge, die auseinanderzuhalten sind. Entweder ein
  grundsätzliches Unbehagen („ich zahle und bekomme nichts“), oder ein
  konkreter Anlass, weil bereits etwas ansteht. Der zweite Fall ist der
  ernstere und führt fast immer zum Thema angeratene Behandlung.

  == Rückfrage, die das klärt

  „Gibt es einen bestimmten Termin oder eine Behandlung, auf die Sie schauen?“
  Wird das bejaht, ist der Einwand kein Wartezeiteinwand mehr, sondern ein
  Ausschlussthema. Verneint der Kunde, geht es um die Erklärung der Wartezeit
  selbst.

  == Sachliche Antwort

  Die Wartezeiten im Produktmodell:

  #tabelle(
    spalten: (1fr, 26mm, 1fr),
    ausrichtung: (left, left, left),
    klein: true,
    kopf: ("Tarif", "Allgemein", "Kieferorthopädie"),
    ..alle-tarife
      .map(t => {
        let kfo = leistungen(t).KFO
        (
          t.anzeigename,
          {
            monate(t.wartezeit_monate)
            if t.at("wartezeit_entfaellt_bei_vorversicherung", default: false) [
              #linebreak()#text(size: 7.6pt, fill: dunkelgrau)[entfällt bei
                lückenlosem Vorversicherungsschutz]
            ]
          },
          if not kfo.versichert { text(fill: dunkelgrau)[nicht versichert] } else if (
            "wartezeit_monate" in kfo
          ) { monate(kfo.wartezeit_monate) } else { monate(t.wartezeit_monate) },
        )
      })
      .flatten()
  )

  Wichtig ist die Abgrenzung zur Zahnstaffel, weil beides regelmäßig
  verwechselt wird: Die Wartezeit ist ein Zeitraum, in dem noch nicht geleistet
  wird. Die Zahnstaffel ist eine Summenbegrenzung, die ab Versicherungsbeginn
  kumuliert und über #vjahre(staffel-von(brillant).dauer_jahre) (brillant,
  brillant mit Selbstbehalt) beziehungsweise
  #vjahre(staffel-von(balance).dauer_jahre) (balance, smart) ansteigt. Nach der
  Wartezeit wird geleistet — bis zur Höhe der Staffel. Beides entfällt bei
  unfallbedingten Behandlungen: Wer sich in der Wartezeit einen Zahn
  ausschlägt, fällt nicht durch das Raster der Staffel.

  Die Wartezeit ist keine Verhandlungssache. Sie steht in den Bedingungen und
  wird weder verkürzt noch „in Ausnahmefällen“ erlassen. Wer das andeutet,
  macht eine Zusage, die niemand halten kann.

  #muster(
    passt: [als sachliche Klarstellung.],
    passt-nicht: [wenn dahinter eine bereits angeratene Behandlung steht — dann
      hilft auch eine kurze Wartezeit nicht, weil der Ausschluss unabhängig
      davon greift, und das muss gesagt werden.],
  )[
    „Drei Monate, und bei smart acht. Verkürzen kann ich das nicht, das steht
    in den Bedingungen. Was ich Ihnen sagen kann: Wenn Sie bereits durchgehend
    zahnzusatzversichert sind, entfällt die Wartezeit bei brillant vollständig.
    Und bei einem Unfall greift sie ohnehin nicht.“
  ]

  == Wann der Einwand berechtigt ist

  Wenn Schutz kurzfristig gebraucht wird, ist das Produkt der falsche Weg — und
  zwar bei allen Tarifen. Bei smart ist die Wartezeit von
  #monaten(smart.wartezeit_monate) sogar ein ausdrückliches
  Ausschlusskriterium für die Empfehlung. Wer in drei Wochen einen Termin für
  eine bereits geplante Behandlung hat, bekommt hier keine Lösung, sondern eine
  klare Auskunft.
]

#abschnitt("einwand-ruecksprache-partner")[
  = Einwand „Ich muss das erst mit meinem Partner besprechen“ <marke-einwand-ruecksprache-partner>

  == Was dahintersteckt

  In der Mehrzahl der Fälle genau das, was gesagt wird: ein gemeinsam geführter
  Haushalt und eine gemeinsam getroffene Entscheidung. In einem Teil der Fälle
  ist es eine höfliche Form der Ablehnung. Beide Fälle werden gleich behandelt,
  denn die Unterscheidung ändert nichts an dem, was erlaubt ist.

  == Rückfrage, die das klärt

  Genau eine ist zulässig, und sie dient nicht dem Abschluss, sondern der
  Vorbereitung: „Gibt es eine Frage, die Sie dafür noch beantwortet haben
  sollten?“ Wenn die Antwort „nein“ lautet, ist das Thema beendet.

  == Sachliche Antwort

  Der Wunsch nach Rücksprache wird ohne Gegenrede akzeptiert. Das ist keine
  Empfehlung, sondern eine feste Grenze. Unzulässig sind sämtliche Varianten,
  den Wunsch zu unterlaufen: die Frage „Was würde Ihr Partner denn sagen?“ als
  Türöffner, das Angebot, „das Angebot bis morgen zu reservieren“, der Hinweis,
  der Beitrag steige mit dem Eintrittsalter, oder der Versuch, den Partner
  spontan ans Telefon zu holen.

  Ein zweiter Punkt ist harte Pflicht: Angaben zum Gesundheitszustand werden
  ausschließlich mit der versicherten Person selbst besprochen. Wenn beim
  Rückruf der Partner ans Telefon geht und Auskunft geben will, ist das
  abzulehnen — freundlich, aber ohne Spielraum. Das gilt auch dann, wenn die
  versicherte Person zugestimmt hat, solange sie nicht selbst am Apparat ist.

  Was zulässig und sinnvoll ist: der Kundin oder dem Kunden mitzugeben, worüber
  genau zu entscheiden ist — Tarif, Leistungsumfang, Wartezeit und die
  Ausschlüsse, insbesondere der Ausschluss angeratener Behandlungen und
  fehlender Zähne. Und ein Rückrufangebot, das die Kundin oder der Kunde
  annehmen oder ablehnen kann.

  #muster(
    passt: [immer.],
    passt-nicht: [in einer Variante, die einen Rückruftermin voraussetzt statt
      anzubieten.],
  )[
    „Selbstverständlich, das gehört besprochen. Ich schreibe Ihnen zusammen,
    worum es geht — den Tarif, die Wartezeit und die Punkte, für die nicht
    geleistet wird, damit Sie beide dieselbe Grundlage haben. Wenn Sie möchten,
    melde ich mich in der nächsten Woche noch einmal; wenn Ihnen das lieber
    ist, melden Sie sich, wenn Sie so weit sind.“
  ]

  == Wann der Einwand berechtigt ist

  Immer. Es gibt keinen Fall, in dem dieser Einwand unberechtigt wäre. Er kann
  dazu führen, dass sich niemand mehr meldet — auch das ist ein zulässiges
  Ergebnis und kein Anlass für einen zweiten Anlauf.
]

#abschnitt("einwand-krankenkasse")[
  = Einwand „Meine Krankenkasse zahlt doch schon“ <marke-einwand-krankenkasse>

  == Was dahintersteckt

  Eine ungefähr richtige Vorstellung — die gesetzliche Krankenversicherung
  leistet zur zahnärztlichen Versorgung — verbunden mit einer unklaren
  Vorstellung davon, wie weit das reicht. Häufig auch die Erinnerung an eine
  Rechnung, die tatsächlich gering ausgefallen ist, etwa bei einer einfachen
  Füllung.

  == Rückfrage, die das klärt

  „Haben Sie schon einmal einen Heil- und Kostenplan für Zahnersatz in der Hand
  gehabt?“ Wer das bejaht, kennt den Unterschied zwischen Rechnungsbetrag und
  Zuschuss aus eigener Anschauung. Wer verneint, bekommt die Erklärung.

  == Sachliche Antwort

  Die gesetzliche Krankenversicherung leistet einen Festzuschuss zur
  Regelversorgung. Wie hoch dieser im Einzelfall ausfällt, steht im Heil- und
  Kostenplan der Zahnarztpraxis, nicht in unseren Unterlagen — hier werden
  keine Zuschusshöhen genannt, auch nicht „ungefähr“. Der Zusatzschutz setzt an
  dem an, was darüber hinausgeht.

  #tabelle(
    spalten: (1fr, 1fr, 1fr, 1fr, 1fr),
    ausrichtung: (left, right, right, right, right),
    klein: true,
    bindend: false,
    kopf: ("Leistungsbereich",) + alle-tarife.map(t => t.anzeigename.replace("atra.dent.", "")),
    ..bereich-schluessel
      .map(k => (
        bereich(k).name,
        ..alle-tarife.map(t => {
          let e = leistungen(t).at(k)
          if e.versichert { prozent(e.quote) } else { text(fill: dunkelgrau)[—] }
        }),
      ))
      .flatten()
  )

  Ein Strich bedeutet, dass der Leistungsbereich in diesem Tarif nicht
  versichert ist. Auch die Deckelung unterscheidet sich, und zwar erheblich:
  Professionelle Zahnreinigung ist in allen vier Tarifen zu
  #prozent(leistungen(brillant).PZR.quote) enthalten, aber unterschiedlich weit
  begrenzt.

  #bereichstabelle("PZR")

  Hier gilt eine Grenze besonders streng: Es wird keine Zusage über die Höhe
  einer konkreten Erstattung gemacht. Quote, Staffel, Selbstbehalt, Sublimit
  und die Vorleistung der Krankenkasse wirken zusammen; verbindlich rechnet die
  Leistungsabteilung. Wer im Gespräch eine Zahl nennt, wird beim ersten
  Leistungsfall daran gemessen.

  #muster(
    passt: [um die Rollenverteilung zu klären.],
    passt-nicht: [mit einer angehängten Beispielrechnung, die als Zusage
      missverstanden werden kann.],
  )[
    „Ihre Kasse zahlt einen Festzuschuss, das ist richtig. Wie hoch der ist,
    sehen Sie erst am Heil- und Kostenplan, und der Unterschied zum
    Rechnungsbetrag ist genau der Teil, um den es hier geht. Ich kann Ihnen
    keine konkrete Erstattung zusagen — das rechnet unsere Leistungsabteilung.
    Was ich Ihnen sagen kann, sind die Quoten und wofür der Tarif überhaupt
    leistet.“
  ]

  == Wann der Einwand berechtigt ist

  Wenn jemand ausschließlich Regelversorgung in Anspruch nehmen will und damit
  zufrieden ist, ist der Zusatznutzen begrenzt und der Einwand trägt. Das ist
  eine Wertentscheidung, keine Wissenslücke — und sie wird respektiert.
]

#abschnitt("einwand-schlechte-erfahrungen")[
  = Einwand „Ich habe schlechte Erfahrungen mit Versicherungen gemacht“ <marke-einwand-schlechte-erfahrungen>

  == Was dahintersteckt

  Fast immer eine konkrete Geschichte: eine abgelehnte Leistung, eine Klausel,
  die niemand erklärt hat, ein Vertrag, der nicht das war, was verkauft wurde.
  Der Einwand richtet sich nicht gegen das Produkt, sondern gegen die
  Erwartung, wieder getäuscht zu werden.

  == Rückfrage, die das klärt

  „Was ist damals passiert?“ Und dann zuhören, ohne zu unterbrechen. Der
  Einwand ist der einzige in diesem Kapitel, bei dem die Rückfrage wichtiger
  ist als jede Antwort.

  == Sachliche Antwort

  Die Erfahrung wird nicht relativiert und der frühere Anbieter wird nicht
  schlechtgeredet. Was hilft, ist das Gegenteil dessen, was den Ärger ausgelöst
  hat: die Einschränkungen von sich aus nennen, bevor danach gefragt wird.
  Konkret sind das die Ausschlüsse, die in allen Tarifen gelten:

  #tabelle(
    spalten: (1fr, 1fr),
    ausrichtung: (left, left),
    klein: true,
    kopf: ("Ausgeschlossen sind", "Abweichung"),
    ..modell
      .ausschluesse
      .map(a => (
        a.text,
        if "ausnahme" in a {
          [#a.ausnahme.tarife.map(k => tarif(k).anzeigename.replace("atra.dent.", "")).join(" und "):
           #fliesstext(a.ausnahme.text)]
        } else { text(fill: dunkelgrau)[keine] },
      ))
      .flatten()
  )

  Dazu die Wartezeit des Tarifs, die Zahnstaffel der ersten
  #vjahre(staffel-von(brillant).dauer_jahre) beziehungsweise
  #vjahre(staffel-von(balance).dauer_jahre), ein etwaiger Selbstbehalt
  (#eur0(balance.selbstbehalt) bei balance, #eur0(brillant-sb.selbstbehalt) bei
  brillant mit Selbstbehalt) und bei brillant die Jahreshöchstgrenze von
  #eur0(brillant.jahreshoechstgrenze), die nach Ablauf der Staffel greift.

  Bei fehlenden Zähnen ist die Ausnahme eng, und sie wird genau so
  wiedergegeben — bis zu zwei Zähne, und nur bei Behandlungsbeginn nach Ablauf
  von zwölf Monaten seit Versicherungsbeginn. In allen anderen Tarifen ist das
  ausgeschlossen.

  Ebenso wichtig: „Vollschutz“ heißt nicht „unbegrenzt“. Bei brillant gilt eine
  Jahreshöchstgrenze von #eur0(brillant.jahreshoechstgrenze). Diese Erwartung
  entsteht leicht und muss aktiv korrigiert werden.

  #muster(
    passt: [fast immer.],
    passt-nicht: [wenn sie als Rhetorik gemeint ist und danach doch die
      Einschränkungen kleingeredet werden — dann richtet sie mehr Schaden an
      als jede Ausweichantwort.],
  )[
    „Das kann ich nachvollziehen, und ich will gar nicht dagegenreden. Ich mache
    es deshalb anders herum und sage Ihnen zuerst, wofür nicht geleistet wird:
    für alles, was Ihr Zahnarzt bereits angeraten hat, für Zähne, die heute
    schon fehlen, und für rein kosmetische Sachen. Dazu kommen drei Monate
    Wartezeit und in den ersten Jahren eine Höchstsumme. Wenn Ihnen dann noch
    etwas unklar ist, gehen wir das durch.“
  ]

  == Wann der Einwand berechtigt ist

  Wenn das Misstrauen so grundsätzlich ist, dass keine Auskunft es auflöst, hat
  weiteres Reden keinen Sinn. Dann wird angeboten, die Bedingungen zur
  Verfügung zu stellen, und das Gespräch endet. Auch das ist ein ordentliches
  Ergebnis.
]

#abschnitt("einwand-unterlagen")[
  = Einwand „Schicken Sie mir erst mal Unterlagen“ <marke-einwand-unterlagen>

  == Was dahintersteckt

  Drei Möglichkeiten: ein echter Wunsch, in Ruhe nachzulesen; ein Zeitproblem
  im Moment des Gesprächs; oder eine höfliche Ablehnung. Für die Behandlung des
  Einwands ist der Unterschied unerheblich — die zulässige Reaktion ist in
  allen drei Fällen dieselbe.

  == Sachliche Antwort

  Unterlagen werden geschickt. Punkt. Der Wunsch wird nicht als Vorwand
  behandelt und nicht kommentiert. Ausdrücklich unzulässig ist die verbreitete
  Erwiderung „Was müsste denn drinstehen, damit Sie sich entscheiden?“ in ihren
  Varianten — sie ist eine Technik, den Wunsch zu umgehen, und sie fällt unter
  das Verbot des Drängens. Ebenso unzulässig: das Angebot davon abhängig zu
  machen, dass „wir dann gleich einen Termin ausmachen“, oder anzudeuten, die
  Unterlagen allein reichten zur Entscheidung nicht aus.

  Zulässig ist genau eine sachliche Rückfrage, und sie dient dazu, die
  richtigen Unterlagen zu schicken: „Damit ich Ihnen das Passende schicke —
  soll ich alle vier Tarife beilegen oder den, über den wir gesprochen haben?“
  Zulässig ist außerdem, kurz zu benennen, worauf zu achten ist: die Wartezeit,
  die Zahnstaffel, ein etwaiger Selbstbehalt und die Ausschlüsse. Das ist keine
  Verkaufstechnik, sondern eine Lesehilfe.

  Ein Rückruf darf angeboten, aber nicht gesetzt werden. „Ich melde mich
  Donnerstag“ ist keine Vereinbarung, sondern eine Ankündigung — und damit
  Druck. Richtig ist: „Wenn es Ihnen recht ist, melde ich mich in etwa zwei
  Wochen. Sagen Sie mir gern, wenn Ihnen das zu früh oder zu spät ist, oder
  wenn Sie sich lieber selbst melden.“

  #muster(
    passt: [immer.],
    passt-nicht: [mit dem Zusatz „und ich rufe Sie dann an“, wenn die Kundin
      oder der Kunde dem nicht zugestimmt hat.],
  )[
    „Mache ich gern. Ich lege Ihnen die Bedingungen bei und markiere zwei
    Stellen, die erfahrungsgemäß die wichtigsten sind: die Wartezeit und die
    Aufzählung, wofür nicht geleistet wird. Wenn Sie dazu Fragen haben,
    erreichen Sie mich unter der Nummer, die auf dem Anschreiben steht.“
  ]

  == Wann der Einwand berechtigt ist

  Wer etwas Schriftliches lesen will, bevor er einen Vertrag über Jahre
  abschließt, hat recht. Der Einwand ist immer berechtigt. Dass ein Teil dieser
  Gespräche nie fortgesetzt wird, ist eingepreist und ändert nichts an der
  Regel.
]

#abschnitt("einwand-zu-alt")[
  = Einwand „Ich bin zu alt dafür“ <marke-einwand-zu-alt>

  == Was dahintersteckt

  Entweder die Sorge, gar nicht mehr aufgenommen zu werden, oder die Rechnung
  „so lange zahle ich, bis es sich lohnt“, oder die Annahme, ein bereits
  reparierter Zahn mache den Vertrag sinnlos. Alle drei Annahmen lassen sich
  sachlich prüfen.

  == Rückfrage, die das klärt

  „Darf ich fragen, wie alt Sie sind?“ Danach ist die Auskunft eindeutig, denn
  die Altersgrenzen sind harte Kriterien und keine Ermessensfrage. Zusätzlich:
  „Fehlt Ihnen aktuell ein Zahn, oder ist etwas in Behandlung?“ — das
  entscheidet oft mehr als das Alter.

  == Sachliche Antwort

  Die Eintrittsaltersgrenzen im Produktmodell stehen in #verweis-auf(<marke-eintrittsalter>). Ab
  #str(brillant.eintrittsalter.bis + 1) Jahren ist brillant in beiden Varianten
  nicht mehr abschließbar; dann ist balance der umfassendste noch mögliche
  Tarif — mit allen Leistungsbereichen, #eur0(balance.selbstbehalt)
  Selbstbehalt im Jahr und #monaten(balance.wartezeit_monate) Wartezeit. Ab
  #str(balance.eintrittsalter.bis + 1) Jahren ist kein Tarif mehr
  abschließbar. Das wird klar gesagt und nicht umschrieben.

  Zur Rechnung „bis es sich lohnt“: Die Zahnstaffel läuft bei balance über
  #vjahre(staffel-von(balance).dauer_jahre), bei brillant über
  #vjahre(staffel-von(brillant).dauer_jahre). Danach greift sie nicht mehr. Bei
  unfallbedingten Behandlungen entfällt sie von Anfang an. Ob sich das für die
  eigene Lebensplanung rechnet, entscheidet die Kundin oder der Kunde — der
  Vertrieb stellt die Zahlen bereit und rechnet keine Lebenserwartung vor.

  Zur Annahme, es sei ohnehin zu spät: Bereits fehlende Zähne sind
  ausgeschlossen, mit der engen Ausnahme in brillant und brillant mit
  Selbstbehalt für bis zu zwei bei Vertragsschluss fehlende Zähne, deren Ersatz
  nach Ablauf von zwölf Monaten begonnen wird. Alles, was künftig entsteht, ist
  normal versichert. Ein reparierter Zahn macht den Vertrag also nicht sinnlos
  — ein fehlender schränkt ihn ein.

  #muster(
    passt: [solange die Altersgrenze eingehalten ist.],
    passt-nicht: [oberhalb der Grenze — dort gibt es nichts zu formulieren,
      sondern nur mitzuteilen.],
  )[
    „Bei uns geht smart und balance bis einschließlich 70 Jahre, brillant bis
    65. Mit Ihren Angaben wäre balance der Tarif, über den wir sprechen würden.
    Was ich Ihnen offen sagen muss: Zähne, die heute schon fehlen, sind dort
    nicht mitversichert.“
  ]

  == Wann der Einwand berechtigt ist

  Ab #str(balance.eintrittsalter.bis + 1) Jahren ist der Einwand schlicht
  zutreffend: Es gibt kein Angebot. Das wird ohne Umweg gesagt. Auch unterhalb
  der Grenze kann er zutreffen, etwa wenn mehrere Zähne fehlen und der Bedarf
  genau dort liegt — dann bleibt vom Nutzen wenig übrig, und das gehört gesagt
  statt verschwiegen.
]

#abschnitt("einwand-vorvertrag")[
  = Einwand „Ich habe schon eine Zahnzusatzversicherung“ <marke-einwand-vorvertrag>

  == Was dahintersteckt

  Entweder ein bestehender, passender Vertrag — dann ist das Gespräch fachlich
  schnell zu Ende. Oder ein bestehender Vertrag, dessen Umfang die Kundin oder
  der Kunde nicht kennt. Oder ein Vertrag, mit dem es bereits Ärger gab.

  == Rückfrage, die das klärt

  „Wissen Sie, was dort versichert ist — sind zum Beispiel Implantate und
  Parodontose dabei?“ Und: „Besteht der Schutz durchgehend, ohne
  Unterbrechung?“ Die zweite Frage ist im Produktmodell folgenreich.

  == Sachliche Antwort

  Bei brillant und brillant mit Selbstbehalt entfällt die Wartezeit von
  #monaten(brillant.wartezeit_monate) vollständig, wenn ein lückenloser
  Vorversicherungsschutz besteht. Das ist der einzige Vorteil, der aus einer
  Vorversicherung folgt — und er ist präzise zu benennen, weil daraus
  regelmäßig mehr abgeleitet wird, als er hergibt. Was ausdrücklich nicht
  entfällt: Die Zahnstaffel beginnt mit dem neuen Vertrag neu. Der Ausschluss
  angeratener oder begonnener Behandlungen gilt unverändert. Bei
  Vertragsschluss fehlende Zähne bleiben ausgeschlossen, abgesehen von der
  Ausnahme für bis zu zwei Zähne bei brillant und brillant mit Selbstbehalt
  nach zwölf Monaten. Und es findet eine erneute Gesundheitsprüfung statt.

  Genau darin liegt auch der Grund, warum ein Wechsel oder ein späteres
  Aufstocken kein harmloser Schritt ist: Wer klein einsteigt und später mehr
  will, trifft auf eine erneute Gesundheitsprüfung, eine erneute Wartezeit und
  eine erneut beginnende Zahnstaffel. Diese Information gehört in jedes
  Wechselgespräch, auch wenn sie gegen den Wechsel spricht.

  Ein Vergleich mit dem bestehenden Vertrag wird nur anhand von
  Leistungsmerkmalen geführt, nicht über den Anbieter. Sinnvolle Prüfpunkte
  sind: Sind Implantate versichert und mit welcher Fallzahl (balance
  #begrenzungen(leistungen(balance).IMP, kurz: true).join(", "), brillant
  #begrenzungen(leistungen(brillant).IMP, kurz: true).join(", "))? Ist
  Parodontose versichert (in smart nicht)? Gibt es einen Selbstbehalt? Gibt es
  eine Jahreshöchstgrenze (bei brillant
  #eur0(brillant.jahreshoechstgrenze))?

  #muster(
    passt: [wenn der bestehende Vertrag erkennbar Lücken hat.],
    passt-nicht: [wenn der bestehende Schutz mindestens gleichwertig ist — dann
      wird kein Vergleich gesucht, sondern das Ergebnis genannt.],
  )[
    „Dann ist die Frage nicht, ob Sie etwas brauchen, sondern ob das, was Sie
    haben, zu dem passt, was Sie erwarten. Wenn Sie mir sagen, was dort
    versichert ist, sage ich Ihnen offen, wo wir besser sind und wo nicht. Und
    einen Punkt möchte ich vorwegschicken: Ein neuer Vertrag heißt auch neue
    Gesundheitsprüfung und eine neue Zahnstaffel — die Wartezeit entfiele bei
    brillant, die Staffel liefe aber von vorn.“
  ]

  == Wann der Einwand berechtigt ist

  Wenn der bestehende Vertrag den Bedarf gleich gut oder besser deckt, ist der
  Einwand berechtigt und wird bestätigt: „Dann würde ich an Ihrer Stelle nichts
  ändern.“ Ein Wechsel, der eine neue Staffel und eine neue Gesundheitsprüfung
  auslöst, ohne einen erkennbaren Vorteil zu bringen, ist eine
  Verschlechterung — auch wenn er ein Abschluss wäre.
]

#abschnitt("einwand-selbstbehalt")[
  = Einwand „Warum ist der Selbstbehalt überhaupt drin?“ <marke-einwand-selbstbehalt>

  == Was dahintersteckt

  Meistens der Verdacht, es handle sich um eine versteckte Einschränkung.
  Manchmal auch ein echtes Missverständnis über die Wirkungsweise — die Annahme
  nämlich, der Selbstbehalt falle je Leistungsbereich oder gar je Rechnung an.

  == Rückfrage, die das klärt

  „Wie haben Sie den Selbstbehalt bisher verstanden — pro Rechnung oder pro
  Jahr?“ Diese Frage deckt das häufigste Missverständnis auf, bevor es zum
  Leistungsfall wird.

  == Sachliche Antwort

  Im Produktmodell gibt es zwei Tarife mit Selbstbehalt.

  #tabelle(
    spalten: (1fr, 30mm),
    ausrichtung: (left, right),
    klein: true,
    kopf: ("Tarif", "Selbstbehalt je Versicherungsjahr"),
    ..alle-tarife
      .map(t => (
        t.anzeigename,
        if t.selbstbehalt == 0 { text(fill: dunkelgrau)[keiner] } else {
          text(weight: "medium", eur0(t.selbstbehalt))
        },
      ))
      .flatten()
  )

  Bei balance gilt er einmal im Jahr über alle Leistungsbereiche zusammen,
  nicht je Bereich und nicht je Rechnung. Das ist erfahrungsgemäß der am
  häufigsten missverstandene Punkt im gesamten Produkt und deshalb aktiv zu
  erklären. Bei brillant mit Selbstbehalt ist der Leistungsumfang im Übrigen
  mit brillant identisch: dieselben Quoten, dieselbe Zahnstaffel über
  #vjahre(staffel-von(brillant).dauer_jahre), dieselbe Jahreshöchstgrenze von
  #eur0(brillant.jahreshoechstgrenze). Diese Variante ist kein kleinerer
  Schutz, sondern derselbe Schutz mit Eigenbeteiligung.

  Der Selbstbehalt ist kein Trick, sondern eine Tauschentscheidung: niedrigerer
  Beitrag gegen Eigenbeteiligung im Leistungsfall. Ob sich das lohnt, hängt am
  Nutzungsmuster. Wer selten, dann aber große Rechnungen erwartet, fährt mit
  Selbstbehalt gut. Wer regelmäßig kleinere Behandlungen hat, zahlt den
  Selbstbehalt jedes Jahr aufs Neue — dann ist ein Tarif ohne Selbstbehalt in
  aller Regel die bessere Wahl, und das ist zu sagen, auch wenn die Variante
  mit Selbstbehalt günstiger aussieht.

  #muster(
    passt: [wenn der Selbstbehalt zur Debatte steht.],
    passt-nicht: [wenn er bagatellisiert wird („das merken Sie gar nicht“) —
      das ist genau die Aussage, an der später die Enttäuschung hängt.],
  )[
    „Der Selbstbehalt ist die Stellschraube für den Beitrag, sonst nichts. Bei
    balance sind es 100 EUR im Jahr — einmal im Jahr, über alle Behandlungen
    zusammen, nicht pro Rechnung und nicht pro Bereich. Wenn Sie ohnehin jedes
    Jahr beim Zahnarzt etwas machen lassen, greift das jedes Jahr; dann würde
    ich Ihnen eher einen Tarif ohne Selbstbehalt anschauen.“
  ]

  == Wann der Einwand berechtigt ist

  Wenn ein Selbstbehalt grundsätzlich abgelehnt wird und Vollschutz bezahlbar
  ist, ist brillant der richtige Tarif und balance scheidet aus. Wenn
  regelmäßige kleinere Behandlungen absehbar sind, ist der Einwand fachlich
  richtig, und die Empfehlung wird geändert — nicht verteidigt.
]

#abschnitt("einwand-kuendigung")[
  = Einwand „Was ist, wenn ich kündige — war das dann umsonst?“ <marke-einwand-kuendigung>

  == Was dahintersteckt

  Die Vorstellung, es werde etwas angespart, das bei Kündigung verfällt.
  Dahinter steht oft die Erfahrung mit Lebens- oder Rentenversicherungen und
  die Übertragung dieser Logik auf eine Krankenzusatzversicherung.

  == Rückfrage, die das klärt

  „Denken Sie an eine bestimmte Situation — etwa einen Jobwechsel oder einen
  Umzug ins Ausland?“ Häufig steckt eine konkrete Unsicherheit dahinter, die
  sich sachlich beantworten lässt, ohne über Kündigung im Allgemeinen zu reden.

  == Sachliche Antwort

  Eine Zahnzusatzversicherung ist eine Risikoversicherung, kein Sparvertrag. Es
  wird kein Guthaben aufgebaut, das ausgezahlt oder verrechnet werden könnte.
  Bezahlt wird der Schutz für den jeweiligen Zeitraum — er war vorhanden, ob er
  in Anspruch genommen wurde oder nicht. Das ist unbequem, aber es ist die
  ehrliche Antwort, und sie ist besser als jede Umschreibung.

  Zu Laufzeit, Kündigungsfristen und Kündigungsterminen enthält dieses Handbuch
  bewusst keine Angaben. Maßgeblich sind die Bedingungen und der
  Versicherungsschein; danach wird dort nachgesehen und daraus wird zitiert,
  statt aus dem Gedächtnis zu antworten.

  Was aber gesagt werden kann und im Produktmodell steht: Ein späterer
  Neuabschluss beginnt bei null. Es findet eine erneute Gesundheitsprüfung
  statt, es läuft eine erneute Wartezeit, und die Zahnstaffel beginnt neu — die
  Stufen stehen in #verweis-auf(<marke-einwand-kein-bedarf>). Wer also mit dem Gedanken spielt, den
  Vertrag „erst mal zu probieren“ und bei Bedarf später wieder abzuschließen,
  sollte wissen, dass die durchlaufenen Staffeljahre dabei verloren gehen. Ein
  Restnutzen bleibt allerdings: Bei brillant und brillant mit Selbstbehalt
  entfällt die Wartezeit, wenn lückenloser Vorversicherungsschutz besteht — ein
  durchgehend gehaltener Vorvertrag hat also für einen späteren Wechsel einen
  konkreten Wert.

  #muster(
    passt: [als Antwort auf die Frage nach dem Sinn.],
    passt-nicht: [mit einer aus dem Kopf genannten Kündigungsfrist — dazu wird
      nachgesehen.],
  )[
    „Nein, angespart wird nichts — Sie zahlen für den Schutz in der Zeit, in
    der er besteht. Wenn Sie kündigen, ist er zu Ende, und ein neuer Vertrag
    später fängt bei der Wartezeit und der Staffel wieder von vorn an. Zu den
    Fristen schaue ich Ihnen gern in den Bedingungen die genaue Stelle heraus,
    damit Sie das schriftlich haben.“
  ]

  == Wann der Einwand berechtigt ist

  Wenn erkennbar ist, dass die Absicherung nur kurzfristig gedacht ist, etwa
  für eine einzelne geplante Behandlung, trägt der Einwand vollständig. Für
  eine kurzfristige Nutzung ist das Produkt wegen Wartezeit, Zahnstaffel und
  Ausschluss angeratener Behandlungen ungeeignet, und das ist zu sagen.
]

#abschnitt("einwand-nach-diagnose")[
  = Einwand nach der Diagnose: „Mein Zahnarzt hat mir gerade eine Krone empfohlen“ <marke-einwand-nach-diagnose>

  Dies ist der heikelste Einwand in diesem Kapitel, und er ist der einzige, bei
  dem die Antwort feststeht, bevor das Gespräch beginnt.

  == Was dahintersteckt

  Ein konkreter, oft erschreckender Kostenvoranschlag und die Hoffnung, ihn
  noch abfangen zu können. Der Anruf kommt häufig unmittelbar nach dem
  Zahnarzttermin. Die Erwartungshaltung ist hoch, und die Enttäuschung wird es
  auch sein.

  == Die Antwort

  Vor Vertragsschluss angeratene oder begonnene Behandlungen sind in allen vier
  Tarifen ausgeschlossen. Für die empfohlene Krone wird nicht geleistet — nicht
  bei smart, nicht bei balance, nicht bei brillant, nicht bei brillant mit
  Selbstbehalt. Das gilt unabhängig davon, ob bereits ein Heil- und Kostenplan
  vorliegt, wann der Behandlungstermin ist und wie lange die Wartezeit des
  Tarifs wäre. Es gibt hier keine Auslegungsfrage, keinen Ermessensspielraum
  und keine Kulanzhoffnung, die man in Aussicht stellen dürfte.

  Das wird klar, früh und unmissverständlich gesagt. Wer hier ausweicht („das
  prüft dann die Leistungsabteilung“, „das kommt darauf an“), erzeugt eine
  Erwartung, die spätestens mit dem ersten Ablehnungsschreiben zusammenbricht —
  mit einer Beschwerde, einer Stornierung und einem berechtigten Vorwurf als
  Folge.

  #hinweis(titel: "Kein Ausweg wird angedeutet")[
    Ausdrücklich unzulässig ist jede Andeutung in Richtung eines Auswegs: den
    Vertrag vor dem nächsten Zahnarzttermin abzuschließen, den Heil- und
    Kostenplan zurückzuhalten, die Antragsfragen zum Gesundheitszustand
    unvollständig zu beantworten oder den Behandlungsbeginn zu verschieben, um
    die Reihenfolge zu verändern. Antragsfragen werden vollständig und
    wahrheitsgemäß beantwortet; alles andere gefährdet den gesamten Schutz und
    ist keine Beratung, sondern eine Anstiftung.
  ]

  == Rückfrage, die klärt, was noch bleibt

  „Ist außer der Krone noch etwas geplant oder besprochen worden?“ Und, weil
  das im Produktmodell Folgen hat: „Fehlt Ihnen aktuell ein Zahn?“ Bei
  Vertragsschluss fehlende Zähne sind ebenfalls ausgeschlossen; nur brillant
  und brillant mit Selbstbehalt sehen eine Ausnahme für bis zu zwei fehlende
  Zähne vor, wenn die Behandlung nach Ablauf von zwölf Monaten seit
  Versicherungsbeginn beginnt.

  == Was trotzdem sinnvoll bleibt

  Der Ausschluss betrifft die angeratene Behandlung, nicht die Zukunft. Ein
  Gebiss, an dem eine Krone nötig wird, ist selten das letzte Ereignis. Alles,
  was künftig neu entsteht und noch nicht angeraten ist, wäre normal versichert
  — mit den Wartezeiten und der Zahnstaffel. Ein Abschluss kann deshalb
  weiterhin sinnvoll sein; er löst nur das akute Problem nicht. Diese
  Unterscheidung ist der Kern des Gesprächs, und sie muss so deutlich sein,
  dass sie in keiner Richtung missverstanden werden kann.

  #muster(
    passt: [sobald eine Behandlung angeraten wurde.],
    passt-nicht: [in einer abgeschwächten Variante; jede Relativierung an
      dieser Stelle ist ein Fehler.],
  )[
    „Da muss ich Ihnen leider eine klare Absage geben: Was Ihr Zahnarzt bereits
    angeraten hat, ist in allen unseren Tarifen ausgeschlossen — für diese
    Krone würden wir nicht leisten, egal welchen Tarif Sie wählen und egal wann
    die Behandlung stattfindet. Ich sage Ihnen das lieber jetzt als in einem
    halben Jahr im Ablehnungsschreiben. Was ein Vertrag ab heute leisten würde,
    ist alles, was künftig neu dazukommt und noch nicht besprochen ist. Ob
    Ihnen das den Beitrag wert ist, ist Ihre Entscheidung — für die Krone hilft
    es nicht.“
  ]

  == Wann der Einwand berechtigt ist und das Gespräch endet

  Wenn die Kundin oder der Kunde ausschließlich wegen dieser Behandlung anruft,
  ist der Einwand berechtigt und das Gespräch endet ohne Abschluss. Es wird
  dann kein Ersatzangebot nachgeschoben und kein zweiter Anlauf unternommen.
  Was bleibt, ist ein Angebot ohne Bedingung: sich zu melden, wenn die
  Behandlung abgeschlossen ist und die Frage nach künftigem Schutz wieder offen
  ist. Sinnvollerweise mit dem Hinweis, dass der günstigste Zeitpunkt für einen
  Abschluss immer der ist, an dem noch nichts angeraten wurde — genau deshalb
  steht dieser Satz auch am Anfang jedes anderen Gesprächs.
]

#abschnitt("einwand-traegt-gespraech-beenden")[
  = Wenn der Einwand trägt: das Gespräch ohne Abschluss beenden <marke-einwand-traegt-gespraech-beenden>

  Ein Gespräch, das ohne Abschluss endet, ist ein reguläres Ergebnis und wird
  auch so behandelt. Es gibt vier Situationen, in denen das sachlich richtig
  ist:

  #buchstaben(
    [Es besteht kein Bedarf.],
    [Der Bedarf lässt sich mit keinem der vier Tarife decken, etwa weil die
     Behandlung bereits angeraten ist oder das Eintrittsalter über
     #str(balance.eintrittsalter.bis) liegt.],
    [Der Beitrag übersteigt auch beim passenden Tarif das Budget.],
    [Die Kundin oder der Kunde will nicht — aus welchem Grund auch immer, und
     ohne dass dieser Grund erfragt werden müsste.],
  )

  In allen vier Fällen gilt dasselbe Vorgehen: das Ergebnis benennen, den Grund
  kurz nennen, nichts nachschieben. Kein weiteres Angebot, keine Rückfrage nach
  dem „eigentlichen“ Grund, keine Ankündigung eines Rückrufs ohne Zustimmung.
  Wer nach einem klaren Nein noch einmal ansetzt, verstößt gegen die Grenze des
  Nichtdrängens — unabhängig davon, wie freundlich der zweite Anlauf formuliert
  ist.

  Was zulässig bleibt: die Bedingungen zur Verfügung zu stellen, wenn das
  gewünscht ist, und anzubieten, dass die Kundin oder der Kunde sich melden
  kann. Das Angebot geht in diese Richtung, nicht umgekehrt.

  Eine Notiz zum Gesprächsergebnis gehört in die Dokumentation, damit ein
  späteres Gespräch nicht bei null beginnt — und damit niemand in vier Wochen
  dieselbe Empfehlung noch einmal vorträgt. Wie dokumentiert und wie ein
  Folgekontakt aufgebaut wird, steht in #verweis-auf(<marke-nachbereitung-und-dokumentation>).
]
