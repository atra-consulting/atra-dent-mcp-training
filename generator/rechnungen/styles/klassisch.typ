// Stil klassisch — Praxis Dr. Beisser.
//
// Das nuechterne Kammer-Formular: schwarzweiss, dichte Tabelle mit Linien,
// Briefkopf als Text ohne Logo. Zeigt alle Spalten, auch Faktor und Anzahl.
// Die Vorlage, an der sich die Varianz der anderen vier misst.

#import "/wissen/dokumente/lib/daten.typ": eur
#import "/generator/rechnungen/lib/fall.typ": datum
#import "/generator/rechnungen/lib/bausteine.typ": (
  empfaenger-feld, faltmarken, positionstabelle, seitenrahmen, summenblock,
  zahlungshinweis,
)

#let setze(f) = {
  let p = f.praxis

  show: seitenrahmen.with(
    groesse: 9pt,
    // Grid statt align(center) mit h(1fr): in einem zentrierten Block hat
    // h(1fr) keine Wirkung, und die Zeile bricht um.
    fusszeile: grid(
      columns: (1fr, auto),
      text(size: 7.5pt, fill: luma(90))[
        #p.anschrift.strasse · #p.anschrift.plz #p.anschrift.ort ·
        Tel. #p.kontakt.telefon · St.-Nr. #p.steuernummer
      ],
      context text(size: 7.5pt, fill: luma(90))[
        Seite #counter(page).display() von #counter(page).final().first()
      ],
    ),
  )

  faltmarken()

  // Briefkopf: reiner Text, zentriert, mit Linie darunter. Kein Logo.
  align(center, {
    text(size: 13pt, weight: "medium", tracking: 0.8pt, upper(p.behandler))
    linebreak()
    text(size: 8.5pt)[
      Zahnarzt · #p.anschrift.strasse · #p.anschrift.plz #p.anschrift.ort
    ]
  })
  v(2mm)
  line(length: 100%, stroke: 0.8pt + black)
  v(10mm)

  grid(
    columns: (1fr, auto),
    empfaenger-feld(
      f.patient,
      // Kurzform mit dem Behandlernamen: die volle Praxisbezeichnung sprengt
      // die einzeilige Rueckadresse des Anschriftfelds.
      rueckadresse: p.behandler + " · " + p.anschrift.strasse + " · "
        + p.anschrift.plz + " " + p.anschrift.ort,
    ),
    align(right + top, grid(
      columns: (auto, auto),
      column-gutter: 8pt,
      row-gutter: 2.5pt,
      [Rechnungsnummer], f.nummer,
      [Rechnungsdatum], f.datum,
      ..if f.patient.at("geburtsdatum", default: none) != none {
        ([Geburtsdatum], datum(f.patient.geburtsdatum))
      } else { () },
    )),
  )

  v(14mm)
  text(size: 11pt, weight: "medium")[Rechnung über zahnärztliche Leistungen]
  v(6mm)

  positionstabelle(
    f.positionen,
    spalten: ("datum", "zahn", "nummer", "leistung", "anzahl", "faktor", "betrag"),
    beschriftung: (
      datum: "Datum",
      zahn: "Zahn",
      nummer: "GOZ-Nr.",
      leistung: "Leistung",
      anzahl: "Anz",
      faktor: "Faktor",
      betrag: "Betrag",
    ),
    linien: true,
  )

  v(5mm)
  summenblock(
    (
      ("Zahnärztliche Leistungen nach GOZ", f.summen.je-art.goz, false),
      ("Leistungen nach GOÄ", f.summen.je-art.goae, false),
      (
        "Material- und Laborkosten",
        f.summen.je-art.material + f.summen.je-art.labor,
        false,
      ),
      ("Rechnungsbetrag", f.summen.gesamt, true),
    ),
    breite: 92mm,
  )

  v(10mm)
  zahlungshinweis(f)

  v(8mm)
  text(size: 8pt, fill: luma(70))[
    Die Berechnung erfolgt nach der Gebührenordnung für Zahnärzte (GOZ 2012).
    Umsatzsteuerfrei nach § 4 Nr. 14 UStG.
  ]
}
