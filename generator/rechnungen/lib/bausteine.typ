// Satzbausteine, aus denen die Stile ihre Rechnungen zusammensetzen.
//
// Jeder Baustein ist ein Angebot, keine Vorschrift: ein Stil darf jeden
// einzelnen durch einen eigenen ersetzen. Was hier steht, ist das, was
// mehrere Stile gleich brauchen — Seitenspiegel nach DIN 5008, Anschriftfeld,
// eine Positionstabelle mit waehlbaren Spalten.
//
// Kein Baustein rechnet. Alle Zahlen kommen aus lib/fall.typ.

#import "/wissen/dokumente/lib/daten.typ": eur, zahl
#import "fall.typ": datum, faktor-text

// --- Seite -------------------------------------------------------------

// Falz- und Lochmarken nach DIN 5008. Sie gehoeren zum Geschaeftsbrief und
// fehlen auf keiner echten Rechnung aus einer Praxissoftware.
#let faltmarken() = {
  place(top + left, dx: -12mm, dy: 87mm, line(length: 5mm, stroke: 0.3pt + luma(140)))
  place(top + left, dx: -12mm, dy: 192mm, line(length: 5mm, stroke: 0.3pt + luma(140)))
  place(top + left, dx: -12mm, dy: 148.5mm, line(length: 8mm, stroke: 0.3pt + luma(140)))
}

#let seitenrahmen(
  schrift: ("Helvetica Neue", "Helvetica", "Arial"),
  groesse: 9.5pt,
  rand: (links: 25mm, rechts: 20mm, oben: 20mm, unten: 20mm),
  fusszeile: none,
  kopfzeile: none,
  body,
) = {
  set page(
    paper: "a4",
    margin: (
      left: rand.links,
      right: rand.rechts,
      top: rand.oben,
      bottom: rand.unten,
    ),
    header: kopfzeile,
    footer: fusszeile,
  )
  set text(font: schrift, size: groesse, lang: "de", hyphenate: false)
  set par(justify: false, leading: 0.65em)
  body
}

// --- Anschrift ---------------------------------------------------------

// Anschriftfeld nach DIN 5008: Rueckadresse klein darueber, darunter die
// Anschrift des Empfaengers.
#let empfaenger-feld(patient, rueckadresse: none, breite: 85mm) = block(
  width: breite,
  {
    if rueckadresse != none {
      text(size: 0.72em, underline(rueckadresse))
      v(3mm)
    }
    patient.name
    linebreak()
    let a = patient.at("anschrift", default: none)
    if a != none {
      a.strasse
      linebreak()
      a.plz + " " + a.ort
    }
  },
)

// --- Positionen --------------------------------------------------------

// Zellinhalt einer Spalte fuer eine Position. Die Spaltenschluessel sind
// bewusst knapp: sie tauchen in jedem Stil auf.
#let zelle(p, spalte) = {
  if spalte == "datum" {
    datum(p.at("datum", default: none))
  } else if spalte == "zahn" {
    let z = p.at("zahn", default: none)
    if z == none { "" } else { z }
  } else if spalte == "nummer" {
    let n = p.at("nummer", default: none)
    if n == none { "" } else { n }
  } else if spalte == "leistung" {
    p.leistung
  } else if spalte == "anzahl" {
    let a = p.at("anzahl", default: none)
    if a == none { "" } else { str(a) }
  } else if spalte == "faktor" {
    faktor-text(p.at("faktor", default: none))
  } else if spalte == "betrag" {
    eur(p.betrag)
  } else {
    panic("Unbekannte Spalte: " + spalte)
  }
}

// Breiten je Spaltenschluessel. Die Leistungsspalte nimmt den Rest.
#let spaltenbreite = (
  datum: 18mm,
  zahn: 10mm,
  nummer: 16mm,
  leistung: 1fr,
  anzahl: 10mm,
  faktor: 15mm,
  betrag: 23mm,
)

// Generische Positionstabelle. spalten bestimmt Auswahl und Reihenfolge,
// beschriftung die Kopfzeile — beides unterscheidet die Stile voneinander.
#let positionstabelle(
  positionen,
  spalten: ("datum", "zahn", "nummer", "leistung", "anzahl", "faktor", "betrag"),
  beschriftung: (:),
  linien: true,
  kopf-fill: none,
  zebra: none,
) = {
  let rechts = ("anzahl", "faktor", "betrag")
  table(
    columns: spalten.map(s => spaltenbreite.at(s)),
    align: spalten.map(s => if s in rechts { right + top } else { left + top }),
    stroke: if linien { (x, y) => (bottom: 0.4pt + luma(190)) } else { none },
    fill: (x, y) => {
      if y == 0 { kopf-fill } else if zebra != none and calc.rem(y, 2) == 0 {
        zebra
      } else { none }
    },
    inset: (x: 3pt, y: 4.5pt),
    table.header(
      ..spalten.map(s => text(
        weight: "medium",
        size: 0.9em,
        beschriftung.at(s, default: s),
      )),
    ),
    ..positionen.map(p => spalten.map(s => zelle(p, s))).flatten(),
  )
}

// --- Summen ------------------------------------------------------------

// Rechtsbuendiger Summenblock. zeilen ist eine Liste von (Bezeichnung, Betrag,
// hervorgehoben) — welche Zeilen ein Stil zeigt, entscheidet er selbst.
#let summenblock(zeilen, breite: 80mm) = align(
  right,
  block(
    width: breite,
    grid(
      columns: (1fr, auto),
      column-gutter: 8pt,
      row-gutter: 5pt,
      ..zeilen
        .map(z => {
          let (bezeichnung, betrag, stark) = z
          (
            text(weight: if stark { "medium" } else { "regular" }, bezeichnung),
            text(weight: if stark { "medium" } else { "regular" }, eur(betrag)),
          )
        })
        .flatten(),
    ),
  ),
)

// --- Zahlung -----------------------------------------------------------

#let zahlungshinweis(f) = {
  let b = f.praxis.bank
  [Bitte überweisen Sie den Rechnungsbetrag von #eur(f.summen.zahlbetrag)
    innerhalb von #str(f.zahlung.frist_tage) Tagen unter Angabe der
    Rechnungsnummer #f.nummer auf das folgende Konto:]
  v(3mm)
  grid(
    columns: (auto, 1fr),
    column-gutter: 8pt,
    row-gutter: 2.5pt,
    [Kontoinhaber], f.praxis.name,
    [Institut], b.institut,
    [IBAN], b.iban,
    [BIC], b.bic,
  )
}
