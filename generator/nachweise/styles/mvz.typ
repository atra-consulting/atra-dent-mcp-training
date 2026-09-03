// Stil mvz — MVZ Zahnzentrum Wurzelwerk, uebermittelt durch die Deutsche
// Dentalabrechnung Sued.
//
// Absender und Verfasser fallen auseinander: Auf dem Papier steht oben die
// Stelle, die es verschickt, und darunter die, die den Befund erhoben hat.
// Genau dieselbe Trennung wie beim Rechnungsstil verrechnungsstelle — nur
// dass eine Abrechnungsstelle keinen Befund erhebt und ihn deshalb hier
// ausdruecklich weiterreicht.
//
// Formularhaft: Kopfband, Aktenzeichen, feste Feldbeschriftungen. Das
// unpersoenlichste der fuenf Dokumente.

#import "/generator/nachweise/lib/nachweis.typ": datum
#import "/generator/nachweise/lib/bausteine.typ": (
  diagnoseliste, historientabelle, seitenabbruch, seitenrahmen, unleserlich,
  veraltet-vermerk, zahnschema,
)

#let feld(bezeichnung, inhalt) = grid(
  columns: (32mm, 1fr),
  row-gutter: 1.4mm,
  text(size: 7.5pt, fill: luma(100), upper(bezeichnung)),
  inhalt,
)

#let setze(n, dokument: "befund") = {
  let a = n.absender
  let akzent = rgb(a.farben.akzent)

  show: seitenrahmen.with(
    groesse: 8.8pt,
    rand: (links: 22mm, rechts: 18mm, oben: 16mm, unten: 18mm),
    fusszeile: grid(
      columns: (1fr, auto),
      text(size: 7pt, fill: luma(110))[
        Uebermittelt durch #n.praxis.name · #n.praxis.anschrift.plz #n.praxis.anschrift.ort
      ],
      context text(size: 7pt, fill: luma(110))[
        Blatt #counter(page).display() / #counter(page).final().first()
      ],
    ),
  )

  block(fill: akzent, width: 100%, inset: (x: 4mm, y: 2.5mm), {
    text(fill: white, size: 10pt, weight: "medium", tracking: 0.5pt, upper(a.name))
    linebreak()
    text(fill: white.transparentize(20%), size: 7.5pt, a.zusatz)
  })
  v(6mm)

  if dokument == "befund" {
    feld("Dokument", "Zahnaerztlicher Befundbericht")
    feld("Aktenzeichen", if n.befund.aktenzeichen != none { n.befund.aktenzeichen } else { "—" })
    feld("Befunddatum", n.befund.datum)
    feld("Patient", n.patient.name)
    feld("Geburtsdatum", datum(n.patient.at("geburtsdatum", default: none)))
    feld("Behandler", a.behandler)
    v(5mm)
    line(length: 100%, stroke: 0.4pt + luma(170))
    v(5mm)

    if n.maengel.veraltet { veraltet-vermerk(n.befund.datum); v(4mm) }

    text(size: 7.5pt, fill: luma(100), upper("Zahnstatus"))
    v(2mm)
    zahnschema(n.befund.zahnstatus, akzent: akzent)
    v(6mm)

    if n.maengel.seite_fehlt == 2 {
      seitenabbruch(2, n.maengel.seiten_gesamt)
      return
    }

    text(size: 7.5pt, fill: luma(100), upper("Befund und Diagnose"))
    v(2mm)
    diagnoseliste(n.befund.diagnosen, betroffen: n.maengel.unleserlich)
    v(5mm)

    if n.befund.parodontalbefund != none {
      text(size: 7.5pt, fill: luma(100), upper("Parodontalstatus"))
      v(2mm)
      unleserlich(
        n.befund.parodontalbefund,
        betroffen: n.maengel.unleserlich,
        schluessel: "parodontalbefund",
      )
      v(5mm)
    }

    if n.befund.empfehlung != none {
      text(size: 7.5pt, fill: luma(100), upper("Geplante Massnahmen"))
      v(2mm)
      unleserlich(
        n.befund.empfehlung,
        betroffen: n.maengel.unleserlich,
        schluessel: "empfehlung",
      )
      v(3mm)
      feld("HKP beiliegend", if n.befund.hkp_beiliegend { "ja" } else { "nein" })
      v(5mm)
    }

    v(8mm)
    grid(
      columns: (1fr, 1fr),
      column-gutter: 8mm,
      {
        line(length: 100%, stroke: 0.4pt + luma(120))
        text(size: 7.5pt)[#a.behandler]
      },
      {
        line(length: 100%, stroke: 0.4pt + luma(120))
        text(size: 7.5pt, fill: luma(120))[Datum, Stempel]
      },
    )
  } else {
    feld("Dokument", "Behandlungsuebersicht")
    feld("Patient", n.patient.name)
    feld("Geburtsdatum", datum(n.patient.at("geburtsdatum", default: none)))
    v(5mm)
    line(length: 100%, stroke: 0.4pt + luma(170))
    v(5mm)
    historientabelle(n.historie.eintraege, betroffen: n.maengel.unleserlich)
    v(5mm)
    text(size: 7.5pt, fill: luma(110), style: "italic",
      "Maschinell erstellt aus dem Abrechnungsbestand. Nicht abgerechnete"
        + " Leistungen erscheinen nicht.")
  }
}
