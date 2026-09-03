// Stil klinik — Klinik am Kiefernwald.
//
// Der mehrseitige Beleg einer grossen Einrichtung: Deckblatt mit
// Zusammenfassung, danach die Einzelaufstellung mit wiederholtem
// Tabellenkopf und Zwischensummen, zuletzt die Laborleistungen als Anlage.
//
// Das Deckblatt nennt bereits den Rechnungsbetrag. Ein Agent, der nur die
// erste Seite liest, kommt damit zum richtigen Gesamtbetrag, aber zu keiner
// einzigen Position — auch das ist eine Pruefung wert.

#import "/wissen/dokumente/lib/daten.typ": eur
#import "/generator/rechnungen/lib/fall.typ": datum
#import "/generator/rechnungen/lib/bausteine.typ": (
  empfaenger-feld, faltmarken, positionstabelle, seitenrahmen, summenblock,
)

#let setze(f) = {
  let p = f.praxis
  let akzent = rgb(p.farben.akzent)
  let flaeche = rgb(p.farben.flaeche)

  show: seitenrahmen.with(
    groesse: 9pt,
    rand: (links: 22mm, rechts: 18mm, oben: 26mm, unten: 22mm),
    // Die Kopfzeile beginnt erst auf Seite 2: auf dem Deckblatt stuende sie
    // ueber dem Briefkopf und waere dort doppelt.
    kopfzeile: context {
      if counter(page).get().first() > 1 {
        grid(
          columns: (1fr, auto),
          text(size: 8pt, fill: akzent, weight: "medium", p.name),
          text(size: 8pt, fill: luma(110))[Rechnung #f.nummer vom #f.datum],
        )
        v(1mm)
        line(length: 100%, stroke: 0.5pt + akzent.lighten(50%))
      }
    },
    fusszeile: context align(center, text(size: 7.5pt, fill: luma(110))[
      Seite #counter(page).display() von #counter(page).final().first()
    ]),
  )

  faltmarken()

  // --- Deckblatt -------------------------------------------------------

  block(width: 100%, fill: akzent, inset: (x: 12pt, y: 13pt), {
    text(size: 16pt, weight: "medium", fill: white, p.name)
    linebreak()
    text(size: 9pt, fill: white.darken(12%), p.zusatz)
  })
  v(10mm)

  grid(
    columns: (1fr, auto),
    empfaenger-feld(
      f.patient,
      breite: 80mm,
      rueckadresse: p.name + " · " + p.anschrift.plz + " " + p.anschrift.ort,
    ),
    align(right + top, grid(
      columns: (auto, auto),
      column-gutter: 9pt,
      row-gutter: 3.5pt,
      text(size: 8.5pt, fill: luma(100))[Rechnungsnummer],
      text(weight: "medium", f.nummer),
      text(size: 8.5pt, fill: luma(100))[Rechnungsdatum], f.datum,
      text(size: 8.5pt, fill: luma(100))[Geburtsdatum],
      datum(f.patient.at("geburtsdatum", default: none)),
      text(size: 8.5pt, fill: luma(100))[Ärztliche Leitung], p.behandler,
    )),
  )

  v(16mm)
  text(size: 15pt, weight: "medium", fill: akzent)[Rechnung]
  v(3mm)
  [Für die in unserem Hause erbrachten Leistungen berechnen wir Ihnen den
    folgenden Betrag. Die Einzelaufstellung finden Sie auf den folgenden
    Seiten, die zahntechnischen Leistungen in der Anlage.]

  v(10mm)
  block(width: 100%, fill: flaeche, inset: 12pt, grid(
    columns: (1fr, auto),
    column-gutter: 10pt,
    row-gutter: 6pt,
    [Zahnärztliche Leistungen (GOZ)], eur(f.summen.je-art.goz),
    [Leistungen nach GOÄ], eur(f.summen.je-art.goae),
    [Verlangensleistungen], eur(f.summen.je-art.verlangen),
    [Materialkosten], eur(f.summen.je-art.material),
    [Zahntechnische Leistungen (Anlage)], eur(f.summen.je-art.labor),
    grid.cell(colspan: 2, line(length: 100%, stroke: 0.6pt + akzent)),
    text(size: 11pt, weight: "medium", fill: akzent)[Rechnungsbetrag],
    text(size: 11pt, weight: "medium", fill: akzent, eur(f.summen.gesamt)),
  ))

  v(10mm)
  [Zahlbar innerhalb von #str(f.zahlung.frist_tage) Tagen ohne Abzug auf das
    Konto #p.bank.iban bei der #p.bank.institut (#p.bank.bic), unter Angabe
    der Rechnungsnummer #f.nummer.]

  v(8mm)
  text(size: 8pt, fill: luma(110))[
    #p.name · #p.anschrift.strasse · #p.anschrift.plz #p.anschrift.ort ·
    Tel. #p.kontakt.telefon · #p.kontakt.email · St.-Nr. #p.steuernummer ·
    Umsatzsteuerfrei nach § 4 Nr. 14 UStG.
  ]

  // --- Einzelaufstellung -----------------------------------------------

  pagebreak()

  text(size: 12pt, weight: "medium", fill: akzent)[Einzelaufstellung]
  v(2mm)
  text(size: 8.5pt, fill: luma(100))[
    Zu Rechnung #f.nummer vom #f.datum, Patient #f.patient.name
  ]
  v(6mm)

  // Abschnitte in fester Reihenfolge. Ein Abschnitt ohne Positionen wird
  // ausgelassen, damit keine leere Ueberschrift stehenbleibt.
  let abschnitte = (
    (titel: "Zahnärztliche Leistungen nach GOZ", art: "goz"),
    (titel: "Leistungen nach GOÄ (§ 6 Absatz 2 GOZ)", art: "goae"),
    (titel: "Verlangensleistungen (§ 2 Absatz 3 GOZ)", art: "verlangen"),
    (titel: "Materialkosten (§ 9 GOZ)", art: "material"),
  )

  for a in abschnitte {
    let gruppe = f.positionen.filter(x => x.art == a.art)
    if gruppe.len() == 0 { continue }

    // sticky haelt die Ueberschrift bei ihrer Tabelle. Ohne das steht sie
    // verwaist am Seitenfuss, wenn der Abschnitt gerade nicht mehr passt.
    block(sticky: true, {
      text(weight: "medium", size: 9.5pt, a.titel)
      v(2mm)
    })
    positionstabelle(
      gruppe,
      spalten: ("datum", "nummer", "zahn", "leistung", "anzahl", "faktor", "betrag"),
      beschriftung: (
        datum: "Datum",
        nummer: "GOZ/GOÄ",
        zahn: "Zahn",
        leistung: "Leistung",
        anzahl: "Anz",
        faktor: "Faktor",
        betrag: "Betrag",
      ),
      linien: true,
      kopf-fill: flaeche,
    )
    v(2mm)
    align(right, text(weight: "medium", size: 9pt)[
      Zwischensumme: #eur(f.summen.je-art.at(a.art))
    ])
    v(7mm)
  }

  // --- Anlage Labor ----------------------------------------------------

  let labor = f.positionen.filter(x => x.art == "labor")
  if labor.len() > 0 {
    pagebreak()
    text(size: 12pt, weight: "medium", fill: akzent)[
      Anlage: Zahntechnische Leistungen
    ]
    v(2mm)
    text(size: 8.5pt, fill: luma(100))[
      Zu Rechnung #f.nummer vom #f.datum. Die Leistungen wurden im
      hauseigenen Labor erbracht.
    ]
    v(6mm)
    positionstabelle(
      labor,
      spalten: ("datum", "zahn", "leistung", "anzahl", "betrag"),
      beschriftung: (
        datum: "Datum",
        zahn: "Zahn",
        leistung: "Zahntechnische Leistung",
        anzahl: "Anz",
        betrag: "Betrag",
      ),
      linien: true,
      kopf-fill: flaeche,
    )
    v(4mm)
    summenblock(
      (("Summe zahntechnische Leistungen", f.summen.je-art.labor, true),),
      breite: 78mm,
    )
  }
}
