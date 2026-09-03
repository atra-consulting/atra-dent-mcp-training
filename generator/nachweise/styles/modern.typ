// Stil modern — Praxis weissraum, Hamburg.
//
// Viel Luft, linksbuendige Wortmarke statt Briefkopf, Akzentfarbe der Praxis.
// Fuehrt den Zahnstatus bewusst NICHT als Raster, sondern als Liste der
// Auffaelligkeiten: Dieselben Tatsachen, andere Darreichung. Ein Modell, das
// nur Raster lesen kann, faellt genau hier auf.

#import "/generator/nachweise/lib/nachweis.typ": datum
#import "/generator/nachweise/lib/bausteine.typ": (
  befundliste, diagnoseliste, historientabelle, patientenblock, seitenabbruch,
  seitenrahmen, unleserlich, veraltet-vermerk,
)

#let setze(n, dokument: "befund") = {
  let a = n.absender
  let akzent = rgb(a.farben.akzent)

  show: seitenrahmen.with(
    groesse: 9.5pt,
    rand: (links: 30mm, rechts: 30mm, oben: 22mm, unten: 20mm),
    fusszeile: text(size: 7.5pt, fill: luma(130))[
      #a.name · #a.anschrift.strasse · #a.anschrift.plz #a.anschrift.ort ·
      #a.kontakt.email
    ],
  )

  text(size: 17pt, weight: "light", tracking: 2pt, fill: akzent, lower(a.name))
  if a.zusatz != none {
    linebreak()
    text(size: 8.5pt, fill: luma(110), a.zusatz)
  }
  v(14mm)

  if dokument == "befund" {
    text(size: 12pt, weight: "medium", "Befundbericht")
    v(1mm)
    text(size: 8.5pt, fill: luma(110))[vom #n.befund.datum]
    v(8mm)

    if n.maengel.veraltet { veraltet-vermerk(n.befund.datum); v(6mm) }

    patientenblock(n.patient)
    v(9mm)

    block(fill: rgb(a.farben.flaeche), inset: 4mm, width: 100%, {
      text(weight: "medium", size: 9pt, fill: akzent, "Zahnstatus")
      v(2mm)
      befundliste(n.befund.zahnstatus)
    })
    v(7mm)

    if n.maengel.seite_fehlt == 2 {
      seitenabbruch(2, n.maengel.seiten_gesamt)
      return
    }

    text(weight: "medium", size: 9pt, fill: akzent, "Beurteilung")
    v(2mm)
    diagnoseliste(n.befund.diagnosen, betroffen: n.maengel.unleserlich)
    v(6mm)

    if n.befund.parodontalbefund != none {
      text(weight: "medium", size: 9pt, fill: akzent, "Parodontium")
      v(2mm)
      unleserlich(
        n.befund.parodontalbefund,
        betroffen: n.maengel.unleserlich,
        schluessel: "parodontalbefund",
      )
      v(6mm)
    }

    if n.befund.empfehlung != none {
      text(weight: "medium", size: 9pt, fill: akzent, "Weiteres Vorgehen")
      v(2mm)
      unleserlich(
        n.befund.empfehlung,
        betroffen: n.maengel.unleserlich,
        schluessel: "empfehlung",
      )
      if n.befund.hkp_beiliegend {
        v(2mm)
        text(size: 8.5pt, fill: akzent, "Heil- und Kostenplan anbei.")
      }
      v(6mm)
    }

    v(8mm)
    text(size: 8.5pt, a.behandler)
    linebreak()
    text(size: 8pt, fill: luma(120), "Zahnaerztin")
  } else {
    text(size: 12pt, weight: "medium", "Behandlungshistorie")
    v(8mm)
    patientenblock(n.patient)
    v(9mm)
    historientabelle(n.historie.eintraege, betroffen: n.maengel.unleserlich)
    v(6mm)
    text(size: 8pt, fill: luma(120),
      "Erfasst sind ausschliesslich Behandlungen in dieser Praxis.")
  }
}
