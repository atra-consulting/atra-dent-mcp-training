// Satzbausteine, aus denen die Befundstile ihre Dokumente zusammensetzen.
//
// Jeder Baustein ist ein Angebot, keine Vorschrift: Ein Stil darf jeden
// einzelnen durch einen eigenen ersetzen. Was hier steht, ist das, was
// mehrere Stile gleich brauchen — Seitenspiegel, Zahnschema, Befundtabelle
// und die drei Formen der Unvollstaendigkeit.
//
// Kein Baustein liest die Falldatei. Alles kommt aus lib/nachweis.typ.

#import "nachweis.typ": datum, quadrant-zaehne

// --- Seite -------------------------------------------------------------

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

// --- Patient -----------------------------------------------------------

#let patientenblock(patient, spalten: 2) = {
  let zeilen = (
    ("Name", patient.name),
    ("geb.", datum(patient.at("geburtsdatum", default: none))),
  )
  let a = patient.at("anschrift", default: none)
  if a != none {
    zeilen.push(("Anschrift", a.strasse + ", " + a.plz + " " + a.ort))
  }
  grid(
    columns: (auto, 1fr),
    column-gutter: 4mm,
    row-gutter: 1.2mm,
    ..zeilen.map(z => (text(fill: luma(90), z.at(0)), z.at(1))).flatten(),
  )
}

// --- Zahnschema --------------------------------------------------------

// Die Kurzzeichen, mit denen ein Zahnschema beschriftet wird. Sie sind die
// Sprache, in der ein Befund seine Tatsachen mitteilt — und zugleich die
// Stelle, an der ein Sprachmodell aussteigen kann, wenn es sie nicht kennt.
// Deshalb steht die Legende auf jedem Dokument, das ein Schema zeigt.
#let kurzzeichen = (
  gesund: "—",
  fuellung: "f",
  inlay: "inl",
  teilkrone: "tk",
  krone: "K",
  bruecke: "B",
  implantat: "IMP",
  wurzelgefuellt: "wf",
  ersatzbeduerftig: "ex",
  fehlend: "×",
)

#let klartext = (
  gesund: "ohne Befund",
  fuellung: "Fuellung",
  inlay: "Inlay",
  teilkrone: "Teilkrone",
  krone: "Krone",
  bruecke: "Brueckenglied oder Anker",
  implantat: "Implantat",
  wurzelgefuellt: "wurzelgefuellt",
  ersatzbeduerftig: "ersatzbeduerftig",
  fehlend: "fehlt",
)

// Das Schema als Kreuz aus vier Quadranten, wie es auf einem Befundbogen
// steht. Zaehne ohne Eintrag gelten als ohne Befund — das ist die
// Lesekonvention echter Bogen und nicht etwa eine Aussage ueber sie.
#let zahnschema(zahnstatus, akzent: black) = {
  let nach-nummer = (:)
  for e in zahnstatus { nach-nummer.insert(e.zahn, e.zustand) }

  let zelle(zahn) = {
    let zustand = nach-nummer.at(zahn, default: "gesund")
    let zeichen = kurzzeichen.at(zustand)
    box(width: 100%, inset: (y: 1mm), {
      align(center, text(size: 6.5pt, fill: luma(120), zahn))
      align(center, text(
        size: 8.5pt,
        weight: if zustand == "gesund" { "regular" } else { "bold" },
        fill: if zustand == "gesund" { luma(160) } else { akzent },
        zeichen,
      ))
    })
  }

  let reihe(q) = grid(
    columns: (1fr,) * 8,
    ..quadrant-zaehne(q).map(zelle),
  )

  block(
    width: 100%,
    stroke: 0.5pt + luma(160),
    inset: 2mm,
    {
      grid(
        columns: (1fr, 1fr),
        column-gutter: 3mm,
        reihe(1), reihe(2),
      )
      line(length: 100%, stroke: 0.4pt + luma(180))
      grid(
        columns: (1fr, 1fr),
        column-gutter: 3mm,
        reihe(4), reihe(3),
      )
    },
  )
  v(1.5mm)
  text(size: 7pt, fill: luma(110), {
    "Legende: "
    kurzzeichen.pairs().map(p => p.at(1) + " " + klartext.at(p.at(0))).join(" · ")
  })
}

// Dieselben Tatsachen als Liste. Manche Praxen fuehren kein Schema, sondern
// zaehlen auf — und ein Fliesstext ist fuer ein Sprachmodell etwas anderes
// als ein Raster, obwohl beides dasselbe sagt.
#let befundliste(zahnstatus) = {
  let auffaellig = zahnstatus.filter(e => e.zustand != "gesund")
  if auffaellig.len() == 0 {
    return text(style: "italic", "Kein pathologischer Befund an den Einzelzaehnen.")
  }
  table(
    columns: (auto, 1fr),
    stroke: none,
    // Ohne Spaltenabstand stiesse die Zahnnummer an den Klartext ("16Fuellung"),
    // weil inset.x auf 0 steht und die Tabelle sonst keinen Zwischenraum kennt.
    column-gutter: 3mm,
    inset: (x: 0pt, y: 1.1mm),
    align: (right + top, left),
    ..auffaellig
      .map(e => (text(weight: "medium", e.zahn), klartext.at(e.zustand)))
      .flatten(),
  )
}

// --- Unvollstaendigkeit ------------------------------------------------

// Drei Formen, und alle drei sind Eigenschaften des Falls und keine Fehler
// des Generators: eine unleserliche Stelle, eine fehlende Folgeseite, ein
// veralteter Stand. Sie sind der eigentliche Gegenstand dieses Materials —
// an ihnen entscheidet sich, ob ein Pruefer NICHT_BEURTEILBAR sagt oder
// stattdessen raet.

#let unleserlich(inhalt, betroffen: (), schluessel: none) = {
  if schluessel != none and betroffen.contains(schluessel) {
    box(
      fill: luma(225),
      inset: (x: 1.5mm, y: 0.6mm),
      text(fill: luma(90), style: "italic", size: 0.85em, "[unleserlich]"),
    )
  } else { inhalt }
}

#let seitenabbruch(seite, gesamt) = {
  v(6mm)
  align(center, text(size: 8pt, fill: luma(120), style: "italic",
    "— Seite " + str(seite - 1) + " von " + str(gesamt) + " —"))
  v(2mm)
  align(center, text(size: 7.5pt, fill: luma(150),
    "(Fortsetzung auf der folgenden Seite)"))
}

#let veraltet-vermerk(befunddatum) = block(
  width: 100%,
  inset: 2mm,
  stroke: (dash: "dashed", paint: luma(150), thickness: 0.5pt),
  text(size: 8pt, fill: luma(90), style: "italic",
    "Kopie aus der Patientenakte, Stand " + befunddatum + "."),
)

// --- Historie ----------------------------------------------------------

// Die Behandlungshistorie als Tabelle. Sie ist bei Bestandskunden aus der
// Schadenhistorie des Kernsystems abgeleitet und deckt sich deshalb mit dem,
// was ein Pruefer dort ohnehin sieht. Beim Neukunden gibt es diese
// Gegenprobe nicht — dort ist die Historie die einzige Quelle.
#let historientabelle(eintraege, betroffen: ()) = {
  if eintraege.len() == 0 {
    return text(style: "italic", fill: luma(90),
      "Fuer den erfragten Zeitraum sind in dieser Praxis keine Behandlungen dokumentiert.")
  }
  table(
    columns: (auto, auto, 1fr),
    stroke: (x, y) => if y == 0 { (bottom: 0.6pt + luma(120)) } else {
      (bottom: 0.3pt + luma(210))
    },
    // Wie bei der Befundliste: ohne Spaltenabstand liefe das Datum in die
    // Zahnnummer ("10.03.202646") und waere als Datum nicht mehr zu erkennen.
    column-gutter: 4mm,
    inset: (x: 0pt, y: 1.4mm),
    align: (left + top, left + top, left + top),
    table.header(
      text(weight: "medium", size: 0.9em, "Datum"),
      text(weight: "medium", size: 0.9em, "Zahn"),
      text(weight: "medium", size: 0.9em, "Behandlung"),
    ),
    ..eintraege
      .enumerate()
      .map(((i, e)) => {
        let z = e.at("zahn", default: none)
        (
          datum(e.datum),
          if z == none { "—" } else { z },
          unleserlich(
            e.behandlung,
            betroffen: betroffen,
            schluessel: "historie" + str(i),
          ),
        )
      })
      .flatten(),
  )
}

// --- Diagnosen und Empfehlung ------------------------------------------

#let diagnoseliste(diagnosen, betroffen: ()) = {
  if diagnosen.len() == 0 { return none }
  for (i, d) in diagnosen.enumerate() {
    grid(
      columns: (6mm, 1fr),
      row-gutter: 0mm,
      text(fill: luma(120), str(i + 1) + "."),
      unleserlich(d, betroffen: betroffen, schluessel: "diagnose" + str(i)),
    )
    v(0.8mm)
  }
}
