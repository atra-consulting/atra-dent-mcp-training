// Stil verrechnungsstelle — Deutsche Dentalabrechnung Sued AG.
//
// Formularhaft und kuehl. Der Absender ist die Abrechnungsstelle, behandelt
// hat jemand anderes; beide stehen auf dem Beleg, und das ist die eigentliche
// Schwierigkeit fuer einen auslesenden Agenten. Keine Faktorspalte — der
// Steigerungssatz steht im Leistungstext. Material steht in einem eigenen
// Abschnitt unter den zahnaerztlichen Leistungen.

#import "/wissen/dokumente/lib/daten.typ": eur
#import "/generator/rechnungen/lib/fall.typ": datum, faktor-text
#import "/generator/rechnungen/lib/bausteine.typ": (
  empfaenger-feld, faltmarken, positionstabelle, seitenrahmen, summenblock,
)

#let setze(f) = {
  let p = f.praxis
  let akzent = rgb(p.farben.akzent)
  let flaeche = rgb(p.farben.flaeche)
  // Optional: nur Abrechnungsstellen fuehren einen abweichenden Behandler.
  // Fehlt der Block, entfaellt der Praxiskasten — der Stil muss auch mit
  // einer Praxis funktionieren, die selbst abrechnet (Pruefmodus).
  let auftrag = p.at("im_auftrag_von", default: none)
  let vorgang = f.vorgang

  show: seitenrahmen.with(
    groesse: 8.5pt,
    fusszeile: grid(
      columns: (1fr, auto),
      text(size: 7pt, fill: luma(110))[
        #p.name · #p.anschrift.strasse · #p.anschrift.plz #p.anschrift.ort ·
        St.-Nr. #p.steuernummer
      ],
      context text(size: 7pt, fill: luma(110))[
        Seite #counter(page).display() von #counter(page).final().first()
      ],
    ),
  )

  faltmarken()

  grid(
    columns: (1fr, auto),
    {
      text(size: 12pt, weight: "bold", fill: akzent, upper(p.name))
      linebreak()
      text(size: 8pt, fill: luma(90), p.zusatz)
    },
    align(right + top, text(size: 8pt, fill: luma(90))[
      #p.anschrift.strasse #linebreak()
      #p.anschrift.plz #p.anschrift.ort #linebreak()
      Tel. #p.kontakt.telefon
    ]),
  )
  v(2mm)
  line(length: 100%, stroke: 1.2pt + akzent)
  v(8mm)

  grid(
    columns: (1fr, 62mm),
    column-gutter: 8mm,
    empfaenger-feld(
      f.patient,
      breite: 80mm,
      rueckadresse: p.name + " · " + p.anschrift.plz + " " + p.anschrift.ort,
    ),
    // Nummernblock: die Kennungen, an denen die Abrechnungsstelle den
    // Vorgang fuehrt. Ein Agent muss hier die Rechnungsnummer von der
    // Kunden- und Vorgangsnummer unterscheiden.
    block(fill: flaeche, inset: 8pt, width: 100%, grid(
      columns: (auto, 1fr),
      column-gutter: 6pt,
      row-gutter: 3.5pt,
      text(size: 7.5pt)[Rechnungs-Nr.], text(size: 8pt, weight: "bold", f.nummer),
      text(size: 7.5pt)[Rechnungsdatum], text(size: 8pt, f.datum),
      text(size: 7.5pt)[Kunden-Nr.],
      text(size: 8pt, vorgang.at("kundennummer", default: "—")),
      text(size: 7.5pt)[Vorgang],
      text(size: 8pt, vorgang.at("vorgangsnummer", default: "—")),
      text(size: 7.5pt)[Geburtsdatum],
      text(size: 8pt, datum(f.patient.at("geburtsdatum", default: none))),
    )),
  )

  v(10mm)
  text(size: 10.5pt, weight: "bold")[Rechnung über zahnärztliche Leistungen]
  v(3mm)
  if auftrag != none {
    block(width: 100%, fill: flaeche, inset: 8pt, {
      text(weight: "medium")[Behandelnde Praxis: ]
      [#auftrag.name, #auftrag.behandler, #auftrag.anschrift.strasse,
        #auftrag.anschrift.plz #auftrag.anschrift.ort]
      v(2mm)
      text(size: 7.5pt, fill: luma(90))[
        Die Forderung wurde an die #p.name abgetreten. Zahlungen mit
        schuldbefreiender Wirkung können ausschließlich an die
        Abrechnungsstelle geleistet werden.
      ]
    })
  }

  v(8mm)

  // Der Steigerungssatz wandert in den Leistungstext. Dadurch fehlt die
  // Faktorspalte, obwohl die Information vorhanden ist.
  let mit-faktor = f.positionen.map(x => {
    let fa = x.at("faktor", default: none)
    if fa == none { x } else {
      (..x, leistung: x.leistung + " (" + faktor-text(fa) + ")")
    }
  })

  let zahnaerztlich = mit-faktor.filter(x => x.art in ("goz", "goae", "verlangen"))
  let sachkosten = mit-faktor.filter(x => x.art in ("material", "labor"))

  positionstabelle(
    zahnaerztlich,
    spalten: ("datum", "nummer", "zahn", "leistung", "anzahl", "betrag"),
    beschriftung: (
      datum: "Datum",
      nummer: "Ziffer",
      zahn: "Zahn",
      leistung: "Bezeichnung der Leistung",
      anzahl: "Anz",
      betrag: "Betrag EUR",
    ),
    linien: true,
    kopf-fill: flaeche,
  )

  if sachkosten.len() > 0 {
    v(6mm)
    text(weight: "medium", size: 9pt)[Material- und Laborkosten nach § 9 GOZ]
    v(2mm)
    positionstabelle(
      sachkosten,
      spalten: ("datum", "leistung", "anzahl", "betrag"),
      beschriftung: (
        datum: "Datum",
        leistung: "Bezeichnung",
        anzahl: "Anz",
        betrag: "Betrag EUR",
      ),
      linien: true,
      kopf-fill: flaeche,
    )
  }

  v(6mm)
  summenblock(
    (
      (
        "Zahnärztliche Leistungen",
        f.summen.je-art.goz + f.summen.je-art.goae + f.summen.je-art.verlangen,
        false,
      ),
      (
        "Material und Labor",
        f.summen.je-art.material + f.summen.je-art.labor,
        false,
      ),
      ("Rechnungsbetrag", f.summen.gesamt, true),
    ),
    breite: 78mm,
  )

  v(10mm)
  text(size: 8pt)[
    Zahlbar innerhalb von #str(f.zahlung.frist_tage) Tagen ohne Abzug auf das
    Konto #p.bank.iban (#p.bank.institut, #p.bank.bic) unter Angabe der
    Rechnungsnummer #f.nummer. Umsatzsteuerfrei nach § 4 Nr. 14 UStG.
  ]
}
