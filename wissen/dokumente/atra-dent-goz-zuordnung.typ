// GOZ-Zuordnung atra.dent
//
// Nachschlagewerk, kein Bedingungswerk und keine Beratungsunterlage. Es
// beantwortet genau eine Frage: In welchen Leistungsbereich faellt eine
// Gebuehrennummer? Saemtliche Angaben stammen aus
// wissen/daten/goz-zuordnung.yaml, im Dokument selbst steht keine
// abgetippte Nummer.

#import "lib/vorlage.typ": *

#let goz = yaml("/wissen/daten/goz-zuordnung.yaml")
#let goae = yaml("/wissen/daten/goae-auszug.yaml")

#show: avb-dokument.with(
  titel: "GOZ-Zuordnung",
  untertitel: "Gebührennummern und Leistungsbereiche",
  tarifname: "alle Tarife",
  bedingungsnummer: "GOZ 01/2026",
  stand: stand-text,
  paragraphen: false,
)

// --- Hilfsfunktionen ---------------------------------------------------

// ISO-Datum aus der YAML in deutscher Schreibweise. lib/daten.typ macht das
// nur fuer das Tarifmodell; hier wird dieselbe Konvention gebraucht.
#let datum(d) = {
  let iso = if type(d) == str { d } else { d.display("[year]-[month]-[day]") }
  let teile = iso.split("-")
  if teile.len() == 3 { teile.at(2) + "." + teile.at(1) + "." + teile.at(0) } else { iso }
}

#let positionen-von(schluessel) = goz.positionen.filter(p => (
  p.at("leistungsbereich", default: none) == schluessel
))

// Eine Tabelle Nummer / Bezeichnung / Abschnitt / Anmerkung. Lang, also
// umbrechend — sonst erzwingt sie eine leere Vorseite.
#let positionstabelle(liste) = tabelle(
  spalten: (17mm, 1fr, 12mm),
  ausrichtung: (left, left, center),
  klein: true,
  bindend: false,
  kopf: ("Nummer", "Kurzbezeichnung der Leistung", "Abs."),
  ..liste
    .map(p => (
      text(weight: "medium", p.nummer),
      {
        p.bezeichnung
        if "hinweis" in p {
          linebreak()
          text(size: 7.6pt, fill: dunkelgrau, fliesstext(p.hinweis))
        }
      },
      text(fill: dunkelgrau, p.abschnitt),
    ))
    .flatten()
)

#abschnitt("zweck")[
  = Wozu dieses Dokument dient

  #abs[
    Die Leistungsbereiche der Tarife atra.dent sind in den Versicherungsbedingungen
    mit Worten beschrieben. Wer eine Zahnarztrechnung prüft, muss daraus ableiten,
    in welchen Bereich eine Rechnungsposition gehört. Dieses Dokument ersetzt die
    Ableitung durch ein Nachschlagen: Es ordnet den Gebührennummern der
    Gebührenordnung für Zahnärzte die #zahlwort(bereich-schluessel.len())
    Leistungsbereiche des Modells zu.
  ]

  #abs[
    Grundlage ist #goz.stand. Maßgeblich sind Nummer und Kurzbezeichnung der
    Leistung; Punktzahlen, Steigerungssätze und Beträge kommen hier nicht vor. Für
    die Erstattung gelten allein die Allgemeinen Versicherungsbedingungen des
    gewählten Tarifs.
  ]

  #hinweis(titel: "Auszug, kein vollständiges Verzeichnis")[
    #fliesstext(goz.hinweis)
  ]

  #abs[
    Die Zuordnung folgt drei Abgrenzungen, die das Produktmodell bewusst so setzt
    und die sich nicht aus der Gebührenordnung selbst ergeben:

    #buchstaben(
      [Die Krone auf einem Implantat gehört zu #kuerzel("ZE"). Der Bereich
       #kuerzel("IMP") umfasst die Implantatinsertion samt Aufbau und den dafür
       nötigen Knochenaufbau, nicht die Versorgung darauf.],
      [Im Labor gefertigte Einlagefüllungen gehören zu #kuerzel("INL"), direkt im
       Mund gelegte Füllungen zu #kuerzel("ZERH"). Die Grenze verläuft in der
       Gebührenordnung genau zwischen den Nummern 2130 und 2150.],
      [Die örtliche Betäubung ist Teil der jeweiligen Behandlung und kein
       #kuerzel("NAR"). Dieser Bereich meint ausschließlich Vollnarkose und
       Sedierung.],
    )
  ]
]

#abschnitt("gebuehrenordnung")[
  = Was die Gebührenordnung ordnet

  #abs[
    Das Gebührenverzeichnis gliedert die zahnärztlichen Leistungen in Abschnitte.
    Diese Gliederung ist nicht deckungsgleich mit den Leistungsbereichen des
    Modells — mehrere Abschnitte verteilen sich auf mehrere Bereiche, und
    umgekehrt sammelt ein Bereich Nummern aus mehreren Abschnitten. Die folgende
    Tabelle zeigt, wie dicht jeder Abschnitt erfasst ist.
  ]

  #abs[
    #tabelle(
      spalten: (12mm, 1fr, 18mm, 22mm),
      ausrichtung: (center, left, right, left),
      klein: true,
      bindend: false,
      kopf: ("Abs.", "Bezeichnung des Abschnitts", "Nummern", "Abdeckung"),
      ..goz
        .abdeckung
        .map(a => (
          text(weight: "medium", a.abschnitt),
          {
            a.bezeichnung
            if "anmerkung" in a {
              linebreak()
              text(size: 7.6pt, fill: dunkelgrau, fliesstext(a.anmerkung))
            }
          },
          str(a.positionen),
          a.grad,
        ))
        .flatten()
    )
  ]

  #abs[
    Insgesamt sind #goz.positionen.len() Gebührennummern erfasst. Davon sind
    #goz.positionen.filter(p => p.at("leistungsbereich", default: none) != none).len()
    einem Leistungsbereich zugeordnet und
    #goz.positionen.filter(p => p.at("leistungsbereich", default: none) == none).len()
    keinem. Der Buchstabe I wird in der Gebührenordnung nicht vergeben; auf
    Abschnitt H folgt Abschnitt J.
  ]
]

#abschnitt("verteilung")[
  = Wie viele Nummern auf welchen Bereich entfallen

  #abs[
    #tabelle(
      spalten: (16mm, 1fr, 18mm, 1fr),
      ausrichtung: (left, left, right, left),
      klein: true,
      bindend: false,
      kopf: ("Kürzel", "Leistungsbereich", "Nummern", "Abschnitte"),
      ..bereich-schluessel
        .map(k => {
          let liste = positionen-von(k)
          (
            kuerzel(k),
            bereich(k).name,
            if liste.len() == 0 { text(fill: dunkelgrau)[0] } else {
              text(weight: "medium", str(liste.len()))
            },
            if liste.len() == 0 { text(fill: dunkelgrau)[keine] } else {
              liste.map(p => p.abschnitt).dedup().join(", ")
            },
          )
        })
        .flatten()
    )
  ]

  #abs[
    Zwei Bereiche bleiben leer. Das ist kein Versehen, sondern ein Ergebnis: Sie
    lassen sich an der Gebührennummer grundsätzlich nicht erkennen.

    #for b in goz.bereiche_ohne_position [
      #hinweis(titel: bereich(b.leistungsbereich).name)[
        #fliesstext(b.begruendung)
      ]
    ]
  ]
]

#abschnitt("zuordnung")[
  = Die Zuordnung im Einzelnen

  #abs[
    Die folgenden Tabellen führen je Leistungsbereich alle zugeordneten
    Gebührennummern auf. Die kleine graue Zeile unter einer Bezeichnung begründet
    die Entscheidung dort, wo sie nicht auf der Hand liegt. Die Spalte ganz rechts
    nennt den Abschnitt der Gebührenordnung.
  ]
]

// Anders als die Anlagen der Bedingungswerke wird dieser Teil weiter
// aufgeteilt: Nachgeschlagen wird immer in genau einem Leistungsbereich,
// nie im Verzeichnis als Ganzem. Der Bereich ist damit die Struktureinheit,
// und sein Kuerzel steht in der Sprungmarke.
#for k in bereich-schluessel {
  let liste = positionen-von(k)
  if liste.len() > 0 {
    abschnitt("zuordnung-" + k, {
      heading(level: 2, bereich(k).name + " · " + k)
      par(justify: true, fliesstext(bereich(k).beschreibung))
      positionstabelle(liste)
    })
  }
}

#abschnitt("nicht-zuordenbar")[
  = Nicht zuordenbare Positionen

  #abs[
    Die folgenden Nummern fallen in keinen der
    #zahlwort(bereich-schluessel.len()) Leistungsbereiche. Das hat zwei Gründe.
    Entweder hat die Leistung keinen eigenen Inhalt im Sinne des Modells —
    Untersuchung, Planung, Abformung, Zuschläge und Nachbehandlungen teilen das
    Schicksal der Behandlung, zu der sie gehören. Oder die Leistung gehört
    fachlich zu keinem der Bereiche, etwa die Zahnentfernung: Sie erhält keinen
    Zahn und ersetzt keinen.
  ]

  #abs[
    Für eine Rechnungsprüfung ist das die wichtigste Tabelle des Dokuments. Wo
    hier eine Nummer steht, darf ein Prüfer nicht raten, sondern muss die
    Hauptleistung heranziehen, zu der die Position gehört.
  ]

  #positionstabelle(goz.positionen.filter(p => (
    p.at("leistungsbereich", default: none) == none
  )))
]

#abschnitt("ohne-nummer")[
  = Positionen ohne Gebührennummer

  #abs[
    Nicht jede Rechnungsposition trägt eine Gebührennummer. Material,
    zahntechnische Laborleistungen und Verlangensleistungen werden nach der
    Gebührenordnung gesondert berechnet und stehen ohne Ziffer auf der
    Rechnung. Bei Zahnersatz ist das regelmäßig der größere Teil des Betrags:
    Die Laborrechnung für eine Krone übersteigt das Honorar für ihre
    Eingliederung.
  ]

  #abs[
    Für sie steht hier eine Regel und keine Nummer. Nachschlagen lässt sich
    nichts — die Zuordnung entscheidet sich an der Behandlung, zu der die
    Position gehört.
  ]

  #for r in goz.ohne_gebuehrennummer [
    #hinweis(titel: r.bezeichnung)[
      #fliesstext(r.begruendung)
      #if "fundstelle" in r [
        #linebreak()
        #text(size: 7.6pt, fill: dunkelgrau)[Fundstelle: #r.fundstelle der Allgemeinen Versicherungsbedingungen]
      ]
    ]
  ]
]

#abschnitt("goae")[
  = Nummern aus der Gebührenordnung für Ärzte

  #abs[
    Der Zahnarzt darf bestimmte Leistungen über die Gebührenordnung für Ärzte
    abrechnen. Auf der Rechnung steht dann eine vierstellige Ziffer wie jede
    andere, und die Nummernkreise überschneiden sich: #kuerzel("5000") ist in
    der Gebührenordnung für Zahnärzte ein Brückenanker und in der für Ärzte
    eine Röntgenaufnahme. Auf einer Zahnarztrechnung gilt im Zweifel die
    Gebührenordnung für Zahnärzte.
  ]

  #hinweis(titel: "Auszug, kein Verzeichnis")[
    #fliesstext(goae.hinweis)
  ]

  #tabelle(
    spalten: (17mm, 1fr),
    ausrichtung: (left, left),
    klein: true,
    bindend: false,
    kopf: ("Nummer", "Kurzbezeichnung der Leistung"),
    ..goae
      .positionen
      .map(p => (
        text(weight: "medium", p.nummer),
        {
          p.bezeichnung
          if "hinweis" in p {
            linebreak()
            text(size: 7.6pt, fill: dunkelgrau, fliesstext(p.hinweis))
          }
        },
      ))
      .flatten()
  )

  #abs[
    Quelle: #goae.quelle, abgerufen am #datum(goae.abgerufen).
  ]
]

#abschnitt("grenzen")[
  = Grenzen dieser Zuordnung

  #abs[
    Die Zuordnung ordnet Nummern, keine Behandlungsfälle. Drei Einschränkungen
    sind beim Gebrauch mitzudenken:

    #buchstaben(
      [Eine Nummer kann je nach Anlass in verschiedene Bereiche fallen. Wo das der
       Fall ist, steht es in der Anmerkung. Die Anmerkung ist dann keine
       Verzierung, sondern die eigentliche Regel.],
      [Ob eine Leistung überhaupt erstattungsfähig ist, entscheidet die Zuordnung
       nicht. Wartezeiten, Zahnstaffel, Selbstbehalt, Sublimits und die
       Ausschlüsse wirken unabhängig davon.],
      [Rechnungen enthalten regelmäßig Positionen, die nicht aus dem
       Gebührenverzeichnis stammen: ärztliche Leistungen, Material- und
       Laborkosten sowie analog berechnete Leistungen. Für sie gibt es keine
       Nummer in diesem Verzeichnis. Was stattdessen gilt, steht in den beiden
       Abschnitten davor: eine Regel für Positionen ohne Gebührennummer und
       ein knapper Auszug aus der Gebührenordnung für Ärzte.],
    )
  ]

  #abs[
    Quelle der Nummern und Kurzbezeichnungen: #goz.quelle, abgerufen am
    #datum(goz.abgerufen). Die Zuordnung zu den
    Leistungsbereichen ist demgegenüber keine Angabe der Gebührenordnung, sondern
    eine Festlegung dieses Produktmodells.
  ]
]
