// Stil landpraxis — Zahnarztpraxis Waldemar Plombeck.
//
// Der schlichteste der fuenf. Keine Tabellenlinien, keine Flaechen, die
// Betraege sind mit Punktfuehrung angebunden wie in einem
// Inhaltsverzeichnis. Zahn und Anzahl stehen im Leistungstext, der
// Steigerungssatz wird gar nicht ausgewiesen.
//
// Dass der Faktor fehlt, ist Absicht: dann steht eine Information in der
// Ground Truth, die im PDF nicht existiert. Genau daran zeigt sich, ob ein
// auslesender Agent "nicht angegeben" zurueckmeldet statt einen Wert zu
// erfinden.

#import "/wissen/dokumente/lib/daten.typ": eur
#import "/generator/rechnungen/lib/bausteine.typ": empfaenger-feld, faltmarken, seitenrahmen

#let setze(f) = {
  let p = f.praxis

  show: seitenrahmen.with(
    groesse: 10pt,
    rand: (links: 28mm, rechts: 24mm, oben: 24mm, unten: 24mm),
    fusszeile: none,
  )

  faltmarken()

  // Briefkopf linksbuendig, gross, ohne Zierrat.
  text(size: 14pt, weight: "medium", p.behandler)
  linebreak()
  text(size: 9pt)[
    #p.anschrift.strasse · #p.anschrift.plz #p.anschrift.ort ·
    Telefon #p.kontakt.telefon
  ]
  v(3mm)
  line(length: 100%, stroke: 0.6pt + luma(60))
  v(12mm)

  empfaenger-feld(
    f.patient,
    rueckadresse: p.behandler + ", " + p.anschrift.strasse + ", "
      + p.anschrift.plz + " " + p.anschrift.ort,
  )

  v(12mm)
  align(right)[#p.anschrift.ort, den #f.datum]
  v(6mm)

  text(size: 11pt, weight: "medium")[Rechnung Nr. #f.nummer]
  v(2mm)
  [Für die zahnärztliche Behandlung erlaube ich mir zu berechnen:]
  v(6mm)

  // Positionen als Fliessliste mit Punktfuehrung. Zahn und Anzahl wandern in
  // den Text, der Faktor entfaellt vollstaendig.
  for pos in f.positionen {
    let zusatz = ()
    let z = pos.at("zahn", default: none)
    if z != none { zusatz.push("Zahn " + z) }
    let a = pos.at("anzahl", default: none)
    if a != none and a > 1 { zusatz.push(str(a) + "×") }

    let beschreibung = pos.leistung
    if zusatz.len() > 0 { beschreibung = beschreibung + " (" + zusatz.join(", ") + ")" }

    let nummer = pos.at("nummer", default: none)
    let vorne = if nummer == none { "" } else { nummer }

    block(below: 5pt, grid(
      columns: (14mm, 1fr, auto),
      column-gutter: 4pt,
      align: (left, left, right),
      text(vorne),
      // box mit 1fr traegt die Punktfuehrung: der Kasten fuellt die Restbreite
      // der Zeile, repeat setzt die Punkte hinein.
      [#beschreibung #box(width: 1fr, repeat(gap: 3pt, text(fill: luma(160))[.]))],
      eur(pos.betrag),
    ))
  }

  v(4mm)
  line(length: 100%, stroke: 0.6pt + luma(60))
  v(2mm)
  grid(
    columns: (1fr, auto),
    text(weight: "medium")[Rechnungsbetrag],
    text(weight: "medium", eur(f.summen.gesamt)),
  )

  v(10mm)
  [Ich bitte um Überweisung innerhalb von #str(f.zahlung.frist_tage) Tagen auf
    mein Konto bei der #p.bank.institut, #p.bank.iban.]

  v(10mm)
  [Mit freundlichen Grüßen]
  v(12mm)
  [#p.behandler]

  v(8mm)
  text(size: 8pt, fill: luma(90))[
    Berechnet nach der Gebührenordnung für Zahnärzte. St.-Nr. #p.steuernummer.
    Umsatzsteuerfrei nach § 4 Nr. 14 UStG.
  ]
}
