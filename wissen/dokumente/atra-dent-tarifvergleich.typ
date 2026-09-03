// Tarifvergleich atra.dent
//
// Beratungsunterlage, kein Bedingungswerk. Hier steht, WIE gerechnet wird —
// die Bedingungswerke sagen nur, WAS gilt. Gerechnet wird ausschließlich mit
// erstattung() aus lib/daten.typ, damit die Fachlogik genau einmal existiert.

#import "lib/vorlage.typ": *

#let tarife = ("ATRA_DENT_S", "ATRA_DENT_B", "ATRA_DENT_X").map(tarif)
#let variante = tarif("ATRA_DENT_X_SB")
#let alle = tarife + (variante,)

#let kurzname(t) = t.anzeigename.replace("atra.dent.", "")

#show: avb-dokument.with(
  titel: "Tarifvergleich",
  untertitel: "Zahnzusatzversicherung für Erwachsene",
  tarifname: "smart · balance · brillant",
  bedingungsnummer: "VGL 01/2026",
  stand: stand-text,
  titelbild: "/wissen/dokumente/bilder/vergleich-titel.png",
  titelbild-motiv: "Bild 3 — Titelmotiv Tarifvergleich",
  paragraphen: false,
)

#abschnitt("einordnung")[
  = Was dieses Dokument ist

  #abs[
    Dieses Dokument stellt die Tarife der Zahnzusatzversicherung atra.dent
    gegenüber. Es dient der Beratung und der Vorbereitung einer Entscheidung.
  ]

  #abs[
    Verbindlich sind allein die Allgemeinen Versicherungsbedingungen des jeweils
    gewählten Tarifs. Weicht dieses Dokument von ihnen ab, gehen die Bedingungen
    vor.
  ]

  #abs[
    Alle Beträge sind Euro-Beträge. Alle Jahresangaben beziehen sich auf das
    Versicherungsjahr, also auf zwölf Monate ab Versicherungsbeginn, nicht auf
    das Kalenderjahr.
  ]
]

#abschnitt("tarifueberblick")[
  = Die Tarife im Überblick

  #abs[
    #tabelle(
      spalten: (30mm, 1fr),
      ausrichtung: (left, left),
      kopf: ("Tarif", "Positionierung"),
      ..alle
        .map(t => (text(weight: "bold", t.anzeigename), t.positionierung))
        .flatten()
    )
  ]
]

#bildplatz(
  "/wissen/dokumente/bilder/vergleich-leistung.png",
  "Bild 10 — Auftakt Leistungsvergleich",
  verhaeltnis: 2.4,
)

#abschnitt("leistungsvergleich")[
  = Leistungsvergleich

  #abs[
    Die Prozentsätze verstehen sich einschließlich der Vorleistung der
    gesetzlichen Krankenversicherung. Ein Strich bedeutet, dass der
    Leistungsbereich in diesem Tarif nicht versichert ist. Die Variante mit
    Selbstbehalt hat denselben Leistungsumfang wie brillant.
  ]

  #abs[
    #tabelle(
      spalten: (12mm, 1fr, 15mm, 15mm, 15mm),
      ausrichtung: (left, left, right, right, right),
      kopf: ("Kürzel", "Leistungsbereich") + tarife.map(kurzname),
      ..bereich-schluessel
        .map(k => (
          kuerzel(k),
          bereich(k).name,
          ..tarife.map(t => {
            let e = leistungen(t).at(k)
            if not e.versichert { text(fill: dunkelgrau)[—] } else {
              text(weight: "medium", prozent(e.quote))
            }
          }),
        ))
        .flatten()
    )
  ]

  #abs[
    Die Quote allein entscheidet nicht über die Erstattung. Wo eine Begrenzung
    besteht, wirkt sie unabhängig von der Quote:

    #tabelle(
      spalten: (12mm, 1fr, 1fr, 1fr),
      ausrichtung: (left, left, left, left),
      klein: true,
      kopf: ("Kürzel",) + tarife.map(kurzname),
      ..bereich-schluessel
        .filter(k => tarife.any(t => begrenzungen(leistungen(t).at(k)).len() > 0))
        .map(k => (
          kuerzel(k),
          ..tarife.map(t => {
            let g = begrenzungen(leistungen(t).at(k), kurz: true)
            if g.len() == 0 { text(fill: dunkelgrau)[keine] } else { g.join("; ") }
          }),
        ))
        .flatten()
    )
  ]
]

#abschnitt("beitrag-und-zugang")[
  = Beitrag, Selbstbehalt und Zugang

  #abs[
    #tabelle(
      spalten: (1.6fr, 1fr, 1fr, 1fr, 1fr),
      ausrichtung: (left, right, right, right, right),
      klein: true,
      kopf: ("Merkmal",) + alle.map(kurzname),
      ..range(eckwerte(tarife.first()).len())
        .map(i => (
          eckwerte(tarife.first()).at(i).at(0),
          ..alle.map(t => eckwerte(t).at(i).at(1)),
        ))
        .flatten()
    )
  ]
]

#abschnitt("zahnstaffel")[
  = Zahnstaffel im Verlauf

  #abs[
    Die Zahnstaffel begrenzt die Summe aller Erstattungen seit
    Versicherungsbeginn. Sie wirkt kumuliert und entfällt bei unfallbedingten
    Behandlungen.
  ]

  #let max-staffeljahre = calc.max(..alle.map(t => staffel-von(t).dauer_jahre))

  #abs[
    #tabelle(
      spalten: (1fr, 1fr, 1fr, 1fr),
      ausrichtung: (left, right, right, right),
      klein: true,
      kopf: ("Zeitraum seit Versicherungsbeginn",) + tarife.map(kurzname),
      ..range(1, max-staffeljahre + 2)
        .map(jahr => (
          if jahr == 1 { [im ersten Versicherungsjahr] } else if (
            jahr == max-staffeljahre + 1
          ) { [ab dem #(max-staffeljahre + 1). Versicherungsjahr] } else {
            [in den ersten #vjahren(jahr)]
          },
          ..tarife.map(t => {
            let stufe = staffel-von(t).stufen.find(s => s.bis_jahr == jahr)
            if stufe != none { eur0(stufe.betrag) } else {
              let jhg = t.at("jahreshoechstgrenze", default: none)
              if jhg == none { text(fill: dunkelgrau)[unbegrenzt] } else {
                text(fill: dunkelgrau)[#eur0(jhg) p. a.]
              }
            }
          }),
        ))
        .flatten()
    )
  ]

  #let staffeldauern = alle.map(t => [#kurzname(t) #vjahre(staffel-von(t).dauer_jahre)]).join(", ", last: " und ")

  #abs[
    Die Staffel läuft unterschiedlich lang: #staffeldauern. Danach greift die
    Jahreshöchstgrenze des Tarifs, soweit eine besteht.
  ]
]

#abschnitt("ausschluesse")[
  = Ausschlüsse

  #abs[
    Die folgenden Ausschlüsse gelten in allen Tarifen. Nur einer unterscheidet
    zwischen ihnen.

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
            [#a.ausnahme.tarife.map(k => kurzname(tarif(k))).dedup().join(" und "):
             #a.ausnahme.text]
          } else { text(fill: dunkelgrau)[keine] },
        ))
        .flatten()
    )
  ]
]

#abschnitt("beispiele")[
  = Beispiele

  #abs[
    Die folgenden Fälle zeigen dieselbe Rechnung in allen Tarifen. Angegeben
    sind der Rechnungsbetrag, die Vorleistung der gesetzlichen
    Krankenversicherung und das Versicherungsjahr; daraus ergeben sich
    Erstattung und Eigenanteil. Maßgeblich sind allein die Allgemeinen
    Versicherungsbedingungen des jeweiligen Tarifs.
  ]

  // Ein durchgerechneter Fall, in allen Tarifen zugleich.
  #let fallvergleich(titel, beschreibung, rechnung, gkv, bereichsschluessel, jahr) = {
    heading(level: 2, titel)
    par(justify: true, beschreibung)

    let ergebnisse = alle.map(t => (
      t: t,
      r: erstattung(
        t,
        leistungsbereich: bereichsschluessel,
        rechnung: rechnung,
        gkv: gkv,
        jahr: jahr,
      ),
    ))

    tabelle(
      spalten: (1fr, auto, auto, auto),
      ausrichtung: (left, right, right, left),
      kopf: ("Tarif", "Erstattung", "Eigenanteil", "Anmerkung"),
      ..ergebnisse
        .map(e => (
          e.t.anzeigename,
          text(weight: "bold", eur(e.r.betrag)),
          eur(rechnung - gkv - e.r.betrag),
          text(size: 8.5pt, fill: dunkelgrau, if not e.r.versichert {
            "nicht versichert"
          } else if e.r.staffel-greift { "Staffel greift" } else if e.r.limit-greift {
            "Sublimit greift"
          } else { "" }),
        ))
        .flatten()
    )
  }

  #fallvergleich(
    "Fall 1 — Krone im ersten Versicherungsjahr",
    [Zahnersatz (#kuerzel("ZE")), Rechnungsbetrag #eur0(2000), Vorleistung der
     gesetzlichen Krankenversicherung #eur0(600), erstes Versicherungsjahr,
     bisher keine Leistungen in Anspruch genommen.],
    2000,
    600,
    "ZE",
    1,
  )

  #fallvergleich(
    "Fall 2 — Wurzelbehandlung im dritten Versicherungsjahr",
    [Zahnerhalt (#kuerzel("ZERH")), Rechnungsbetrag #eur0(900), keine
     Vorleistung der gesetzlichen Krankenversicherung, drittes
     Versicherungsjahr, bisher keine Leistungen in Anspruch genommen.],
    900,
    0,
    "ZERH",
    3,
  )

  #fallvergleich(
    "Fall 3 — Professionelle Zahnreinigung",
    [Prophylaxe (#kuerzel("PZR")), Rechnungsbetrag #eur0(120), keine Vorleistung,
     zweites Versicherungsjahr. Hier zeigt sich, dass ein Sublimit die Quote
     schlägt.],
    120,
    0,
    "PZR",
    2,
  )

  #hinweis(titel: "Was die Beispiele unterstellen")[
    Alle Fälle unterstellen, dass im laufenden Versicherungsjahr weder
    Selbstbehalt noch Zahnstaffel bereits verbraucht sind und dass die Wartezeit
    abgelaufen ist. Im gelebten Vertrag verschiebt jede vorangegangene
    Erstattung das Ergebnis.
  ]
]

#bildplatz(
  "/wissen/dokumente/bilder/vergleich-entscheidung.png",
  "Bild 11 — Auftakt Entscheidung",
  verhaeltnis: 3.1,
)

#abschnitt("tarifwahl")[
  = Welcher Tarif zu wem passt

  #abs[
    Die Tarife unterscheiden sich im Umfang der Erstattung, nicht in der
    Qualität der Behandlung. Wer welchen Tarif wählt, hängt davon ab, welche
    Leistungsbereiche abgesichert sein sollen und welcher Eigenanteil tragbar
    ist.
  ]

  // Abgeleitet statt behauptet: welche Bereiche fehlen, wo die Quote unter 100
  // liegt, wie viele Bereiche es insgesamt gibt.
  #let fehlende(t) = bereich-schluessel.filter(k => not leistungen(t).at(k).versichert)
  #let unter-voll(t) = bereich-schluessel.filter(k => {
    let e = leistungen(t).at(k)
    e.versichert and e.quote < 100
  })

  #abs[
    #buchstaben(
      [#text(weight: "medium")[#tarife.at(0).anzeigename] deckt Zahnersatz und
       Zahnerhalt zu #prozent(leistungen(tarife.at(0)).ZE.quote) beziehungsweise
       #prozent(leistungen(tarife.at(0)).ZERH.quote) ab. Nicht versichert sind
       #fehlende(tarife.at(0)).map(k => bereich(k).name).join(", ", last: " und ").
       Der Tarif hat keinen Selbstbehalt und die längste allgemeine Wartezeit.],
      [#text(weight: "medium")[#tarife.at(1).anzeigename] versichert alle
       #zahlwort(bereich-schluessel.len()) Leistungsbereiche. Der Selbstbehalt
       von #eur0(tarife.at(1).selbstbehalt) je Versicherungsjahr senkt den
       Beitrag; er fällt einmal jährlich an, unabhängig davon, wie viele
       Behandlungen stattfinden.],
      [#text(weight: "medium")[#tarife.at(2).anzeigename] erstattet in
       #zahlwort(bereich-schluessel.len() - unter-voll(tarife.at(2)).len())
       von #zahlwort(bereich-schluessel.len()) Leistungsbereichen
       #prozent(100), in
       #unter-voll(tarife.at(2)).map(k => bereich(k).name).join(", ", last: " und ")
       #unter-voll(tarife.at(2)).map(k => prozent(leistungen(tarife.at(2)).at(k).quote)).join(" bzw. ").
       Der Tarif hat keinen Selbstbehalt. Dafür endet die
       Versicherungsfähigkeit mit #tarife.at(2).eintrittsalter.bis Jahren, und
       nach Ablauf der Staffel gilt eine Jahreshöchstgrenze von
       #eur0(tarife.at(2).jahreshoechstgrenze).],
      [#text(weight: "medium")[#variante.anzeigename] hat denselben
       Leistungsumfang wie brillant. Gegen einen Selbstbehalt von
       #eur0(variante.selbstbehalt) je Versicherungsjahr fällt der Beitrag
       niedriger aus. Die Beitragshöhe führt der Rechenkern, nicht dieses
       Produktmodell.],
    )
  ]

  #abs[
    In allen Tarifen gilt: Behandlungen, die bei Vertragsschluss bereits
    angeraten oder begonnen waren, sind nicht versichert. Der Abschluss lohnt
    sich deshalb vor dem Befund, nicht danach.
  ]
]
