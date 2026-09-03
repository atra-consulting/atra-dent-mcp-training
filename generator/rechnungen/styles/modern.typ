// Stil modern — weissraum Zahnaesthetik.
//
// Die aufgeraeumte Praxissoftware: Farbakzent, viel Weissraum, Positionen
// nach Behandlungstag gruppiert statt flach aufgelistet, Summen im Kasten,
// Zahlungsdaten in der Fusszeile statt im Fliesstext. Zeigt keine
// Datumsspalte — das Datum steht in der Gruppenueberschrift.

#import "/wissen/dokumente/lib/daten.typ": eur
#import "/generator/rechnungen/lib/fall.typ": datum
#import "/generator/rechnungen/lib/bausteine.typ": (
  empfaenger-feld, faltmarken, positionstabelle, seitenrahmen,
)

#let setze(f) = {
  let p = f.praxis
  let akzent = rgb(p.farben.akzent)
  let flaeche = rgb(p.farben.flaeche)

  show: seitenrahmen.with(
    groesse: 9.5pt,
    rand: (links: 22mm, rechts: 18mm, oben: 18mm, unten: 28mm),
    fusszeile: {
      line(length: 100%, stroke: 0.5pt + akzent.lighten(50%))
      v(1.5mm)
      grid(
        columns: (1fr, 1fr, 1fr),
        text(size: 7pt, fill: luma(90))[
          #p.name #linebreak() #p.anschrift.strasse #linebreak()
          #p.anschrift.plz #p.anschrift.ort
        ],
        text(size: 7pt, fill: luma(90))[
          Tel. #p.kontakt.telefon #linebreak() #p.kontakt.email #linebreak()
          St.-Nr. #p.steuernummer
        ],
        text(size: 7pt, fill: luma(90))[
          #p.bank.institut #linebreak() #p.bank.iban #linebreak() #p.bank.bic
        ],
      )
    },
  )

  faltmarken()

  // Wortmarke klein geschrieben, Zusatz darunter — Markenauftritt statt
  // Behandlername.
  grid(
    columns: (1fr, auto),
    {
      text(size: 20pt, weight: "light", fill: akzent, tracking: 1.5pt, p.name)
      linebreak()
      text(size: 8.5pt, fill: luma(90), p.zusatz)
    },
    align(right + bottom, text(size: 8.5pt, fill: luma(90), p.behandler)),
  )

  v(14mm)

  grid(
    columns: (1fr, auto),
    empfaenger-feld(
      f.patient,
      rueckadresse: p.name + " · " + p.anschrift.strasse + " · "
        + p.anschrift.plz + " " + p.anschrift.ort,
    ),
    align(right + top, block(
      fill: flaeche,
      inset: 9pt,
      radius: 2pt,
      grid(
        columns: (auto, auto),
        column-gutter: 9pt,
        row-gutter: 3.5pt,
        text(fill: luma(90), size: 8.5pt)[Rechnung],
        text(weight: "medium", f.nummer),
        text(fill: luma(90), size: 8.5pt)[vom], f.datum,
        text(fill: luma(90), size: 8.5pt)[Patient geb.],
        datum(f.patient.at("geburtsdatum", default: none)),
      ),
    )),
  )

  v(16mm)
  text(size: 14pt, weight: "light", fill: akzent)[Rechnung]
  v(8mm)

  // Nach Behandlungstag gruppiert. Die Reihenfolge der Tage folgt ihrem
  // ersten Auftreten in der Falldatei, damit die Rechnung die Behandlung
  // chronologisch erzaehlt.
  let tage = f.positionen.map(x => x.at("datum", default: none)).dedup()
  for tag in tage {
    let gruppe = f.positionen.filter(x => x.at("datum", default: none) == tag)
    block(breakable: false, {
      text(weight: "medium", size: 9pt, fill: akzent, "Behandlung am " + datum(tag))
      v(2mm)
      positionstabelle(
        gruppe,
        spalten: ("nummer", "zahn", "leistung", "anzahl", "faktor", "betrag"),
        beschriftung: (
          nummer: "Ziffer",
          zahn: "Zahn",
          leistung: "Leistung",
          anzahl: "Menge",
          faktor: "Satz",
          betrag: "Betrag",
        ),
        linien: false,
        zebra: flaeche,
      )
    })
    v(5mm)
  }

  v(3mm)
  align(right, block(
    width: 80mm,
    fill: flaeche,
    inset: 10pt,
    radius: 2pt,
    grid(
      columns: (1fr, auto),
      column-gutter: 10pt,
      row-gutter: 5pt,
      [Gesamtbetrag], eur(f.summen.gesamt),
      [bereits gezahlt], eur(f.summen.gezahlt),
      grid.cell(colspan: 2, line(length: 100%, stroke: 0.5pt + akzent.lighten(40%))),
      text(weight: "medium", fill: akzent)[Offener Betrag],
      text(weight: "medium", fill: akzent, eur(f.summen.zahlbetrag)),
    ),
  ))

  v(10mm)
  [Zahlbar innerhalb von #str(f.zahlung.frist_tage) Tagen ohne Abzug.
    Bitte geben Sie bei der Überweisung die Rechnungsnummer #f.nummer an.
    Unsere Bankverbindung finden Sie in der Fußzeile.]

  v(6mm)
  text(size: 8pt, fill: luma(110))[
    Berechnet nach GOZ 2012. Umsatzsteuerfrei nach § 4 Nr. 14 UStG.
  ]
}
