// Stil klassisch — Praxis Dr. Beisser.
//
// Der nuechterne Befundbogen: schwarzweiss, Serifenlose, Briefkopf als Text
// ohne Logo, das Zahnschema als Raster. Fuehrt alles auf, was der Fall
// hergibt. Die Vorlage, an der sich die Varianz der anderen vier misst.

#import "/generator/nachweise/lib/nachweis.typ": datum
#import "/generator/nachweise/lib/bausteine.typ": (
  befundliste, diagnoseliste, historientabelle, patientenblock, seitenabbruch,
  seitenrahmen, unleserlich, veraltet-vermerk, zahnschema,
)

#let kopf(n) = {
  let a = n.absender
  align(center, {
    text(size: 13pt, weight: "medium", tracking: 0.8pt, upper(a.behandler))
    linebreak()
    text(size: 8.5pt)[
      Zahnarzt · #a.anschrift.strasse · #a.anschrift.plz #a.anschrift.ort
    ]
  })
  v(2mm)
  line(length: 100%, stroke: 0.8pt + black)
  v(8mm)
}

#let setze(n, dokument: "befund") = {
  let a = n.absender

  show: seitenrahmen.with(
    groesse: 9pt,
    fusszeile: grid(
      columns: (1fr, auto),
      text(size: 7.5pt, fill: luma(90))[
        #a.anschrift.strasse · #a.anschrift.plz #a.anschrift.ort · Tel. #a.kontakt.telefon
      ],
      context text(size: 7.5pt, fill: luma(90))[
        Seite #counter(page).display() von #counter(page).final().first()
      ],
    ),
  )

  kopf(n)

  if dokument == "befund" {
    align(center, text(size: 11pt, weight: "medium", tracking: 1.2pt, "ZAHNAERZTLICHER BEFUND"))
    v(5mm)

    if n.maengel.veraltet { veraltet-vermerk(n.befund.datum); v(4mm) }

    grid(
      columns: (1fr, auto),
      patientenblock(n.patient),
      align(right, text(size: 8.5pt)[
        Befunddatum: #n.befund.datum
        #if n.befund.aktenzeichen != none [\ Az. #n.befund.aktenzeichen]
      ]),
    )
    v(6mm)

    text(weight: "medium", "Zahnstatus")
    v(2mm)
    zahnschema(n.befund.zahnstatus)
    v(6mm)

    if n.maengel.seite_fehlt == 2 {
      seitenabbruch(2, n.maengel.seiten_gesamt)
      return
    }

    text(weight: "medium", "Diagnosen")
    v(2mm)
    diagnoseliste(n.befund.diagnosen, betroffen: n.maengel.unleserlich)
    v(5mm)

    if n.befund.parodontalbefund != none {
      text(weight: "medium", "Parodontaler Befund")
      v(2mm)
      unleserlich(
        n.befund.parodontalbefund,
        betroffen: n.maengel.unleserlich,
        schluessel: "parodontalbefund",
      )
      v(5mm)
    }

    if n.befund.empfehlung != none {
      text(weight: "medium", "Therapieempfehlung")
      v(2mm)
      unleserlich(
        n.befund.empfehlung,
        betroffen: n.maengel.unleserlich,
        schluessel: "empfehlung",
      )
      if n.befund.hkp_beiliegend {
        v(2mm)
        text(style: "italic", "Ein Heil- und Kostenplan liegt diesem Bericht bei.")
      }
      v(5mm)
    }

    v(6mm)
    text(size: 8.5pt)[#a.anschrift.ort, den #n.befund.datum]
    v(10mm)
    line(length: 55mm, stroke: 0.5pt + black)
    text(size: 8pt)[#a.behandler]
  } else {
    align(center, text(size: 11pt, weight: "medium", tracking: 1.2pt, "BEHANDLUNGSHISTORIE"))
    v(5mm)
    patientenblock(n.patient)
    v(6mm)
    historientabelle(n.historie.eintraege, betroffen: n.maengel.unleserlich)
    v(6mm)
    text(size: 8pt, fill: luma(90), style: "italic",
      "Auszug aus der Patientenakte. Behandlungen ausserhalb dieser Praxis sind nicht erfasst.")
  }
}
