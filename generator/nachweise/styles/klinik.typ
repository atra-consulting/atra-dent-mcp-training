// Stil klinik — Klinik am Kiefernwald.
//
// Das ausfuehrlichste der fuenf: Kopfzeile auf jeder Seite, Fallnummer,
// Abteilungsangabe, und der Zahnstatus doppelt — einmal als Raster, einmal
// als Aufzaehlung. Eine Klinik dokumentiert redundant, weil mehrere Stellen
// dasselbe Blatt lesen.
//
// Fuer den Pruefer ist das der bequemste Fall. Er steht bewusst neben
// landpraxis: Dieselbe Fachlichkeit, einmal gruendlich dokumentiert und
// einmal in drei Zeilen abgehandelt.

#import "/generator/nachweise/lib/nachweis.typ": datum
#import "/generator/nachweise/lib/bausteine.typ": (
  befundliste, diagnoseliste, historientabelle, seitenabbruch, seitenrahmen,
  unleserlich, veraltet-vermerk, zahnschema,
)

#let setze(n, dokument: "befund") = {
  let a = n.absender
  let akzent = rgb(a.farben.akzent)

  show: seitenrahmen.with(
    groesse: 9pt,
    rand: (links: 25mm, rechts: 20mm, oben: 26mm, unten: 20mm),
    kopfzeile: {
      grid(
        columns: (1fr, auto),
        text(size: 8pt, weight: "medium", fill: akzent, a.name),
        text(size: 7.5pt, fill: luma(110))[
          #n.patient.name · geb. #datum(n.patient.at("geburtsdatum", default: none))
        ],
      )
      v(1mm)
      line(length: 100%, stroke: 0.6pt + akzent)
    },
    fusszeile: grid(
      columns: (1fr, auto),
      text(size: 7pt, fill: luma(110))[
        #a.anschrift.strasse · #a.anschrift.plz #a.anschrift.ort
      ],
      context text(size: 7pt, fill: luma(110))[
        Seite #counter(page).display() von #counter(page).final().first()
      ],
    ),
  )

  if dokument == "befund" {
    block(fill: rgb(a.farben.flaeche), width: 100%, inset: 4mm, {
      text(size: 11pt, weight: "medium", fill: akzent, "Zahnaerztlicher Befundbericht")
      linebreak()
      text(size: 8pt, fill: luma(90))[
        Abteilung fuer Zahnerhaltung und Parodontologie ·
        Fallnummer #if n.befund.aktenzeichen != none { n.befund.aktenzeichen } else { "—" } ·
        Erhebungsdatum #n.befund.datum
      ]
    })
    v(7mm)

    if n.maengel.veraltet { veraltet-vermerk(n.befund.datum); v(5mm) }

    text(weight: "medium", fill: akzent, "1 Zahnstatus")
    v(2.5mm)
    zahnschema(n.befund.zahnstatus, akzent: akzent)
    v(4mm)
    text(size: 8.5pt, fill: luma(90), "Auffaellige Einzelbefunde im Klartext:")
    v(1.5mm)
    befundliste(n.befund.zahnstatus)
    v(6mm)

    if n.maengel.seite_fehlt == 2 {
      seitenabbruch(2, n.maengel.seiten_gesamt)
      return
    }

    text(weight: "medium", fill: akzent, "2 Diagnosen")
    v(2.5mm)
    diagnoseliste(n.befund.diagnosen, betroffen: n.maengel.unleserlich)
    v(6mm)

    if n.befund.parodontalbefund != none {
      text(weight: "medium", fill: akzent, "3 Parodontaler Befund")
      v(2.5mm)
      unleserlich(
        n.befund.parodontalbefund,
        betroffen: n.maengel.unleserlich,
        schluessel: "parodontalbefund",
      )
      v(6mm)
    }

    if n.befund.empfehlung != none {
      text(weight: "medium", fill: akzent, "4 Therapieplanung")
      v(2.5mm)
      unleserlich(
        n.befund.empfehlung,
        betroffen: n.maengel.unleserlich,
        schluessel: "empfehlung",
      )
      if n.befund.hkp_beiliegend {
        v(2mm)
        block(
          fill: rgb(a.farben.flaeche),
          inset: 2.5mm,
          width: 100%,
          text(size: 8.5pt)[Ein Heil- und Kostenplan ist diesem Bericht als Anlage beigefuegt.],
        )
      }
      v(6mm)
    }

    v(6mm)
    text(size: 8.5pt)[#a.anschrift.ort, den #n.befund.datum]
    v(11mm)
    line(length: 60mm, stroke: 0.5pt + black)
    text(size: 8pt)[#a.behandler]
    linebreak()
    text(size: 7.5pt, fill: luma(120))[Aerztlicher Direktor]
  } else {
    block(fill: rgb(a.farben.flaeche), width: 100%, inset: 4mm, {
      text(size: 11pt, weight: "medium", fill: akzent, "Behandlungsdokumentation")
      linebreak()
      text(size: 8pt, fill: luma(90), "Auszug aus dem Klinikinformationssystem")
    })
    v(7mm)
    historientabelle(n.historie.eintraege, betroffen: n.maengel.unleserlich)
    v(5mm)
    text(size: 8pt, fill: luma(110), style: "italic",
      "Vorbehandlungen ausserhalb dieser Klinik sind nur erfasst, soweit sie"
        + " dem Haus mitgeteilt wurden.")
  }
}
