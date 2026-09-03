// Stil landpraxis — Zahnarztpraxis Waldemar Plombeck, Klein-Kariesbach.
//
// Eng gesetzt, kleiner Satzspiegel, alles auf eine Seite gedrueckt. Keine
// Ueberschriftenhierarchie, sondern durchlaufender Text mit fetten
// Stichworten — so schreibt jemand, der seit dreissig Jahren dieselbe
// Vorlage in derselben Praxissoftware benutzt.
//
// Der schwierigste der fuenf fuer ein Modell: Die Tatsachen stehen nicht in
// Feldern, sondern im Fliesstext.

#import "/generator/nachweise/lib/nachweis.typ": datum
#import "/generator/nachweise/lib/bausteine.typ": (
  befundliste, historientabelle, seitenabbruch, seitenrahmen, unleserlich,
  veraltet-vermerk,
)

#let setze(n, dokument: "befund") = {
  let a = n.absender

  show: seitenrahmen.with(
    schrift: ("Times New Roman", "Times", "Georgia"),
    groesse: 10pt,
    rand: (links: 20mm, rechts: 18mm, oben: 15mm, unten: 15mm),
    fusszeile: text(size: 7pt, fill: luma(120))[
      #a.name, #a.anschrift.strasse, #a.anschrift.plz #a.anschrift.ort,
      Tel. #a.kontakt.telefon
    ],
  )

  text(size: 11pt, weight: "bold", a.name)
  linebreak()
  text(size: 8.5pt)[
    #a.anschrift.strasse · #a.anschrift.plz #a.anschrift.ort · Tel. #a.kontakt.telefon
  ]
  v(1.5mm)
  line(length: 100%, stroke: 1.2pt + black)
  v(5mm)

  if dokument == "befund" {
    if n.maengel.veraltet { veraltet-vermerk(n.befund.datum); v(3mm) }

    text(weight: "bold", "Befund ")
    [#n.patient.name, geb. #datum(n.patient.at("geburtsdatum", default: none)),
      erhoben am #n.befund.datum.]
    v(4mm)

    text(weight: "bold", "Zahnstatus: ")
    v(1.5mm)
    befundliste(n.befund.zahnstatus)
    v(4mm)

    if n.maengel.seite_fehlt == 2 {
      seitenabbruch(2, n.maengel.seiten_gesamt)
      return
    }

    for (i, d) in n.befund.diagnosen.enumerate() {
      if i == 0 { text(weight: "bold", "Beurteilung: ") }
      unleserlich(d, betroffen: n.maengel.unleserlich, schluessel: "diagnose" + str(i))
      [ ]
    }
    v(4mm)

    if n.befund.parodontalbefund != none {
      text(weight: "bold", "PA-Befund: ")
      unleserlich(
        n.befund.parodontalbefund,
        betroffen: n.maengel.unleserlich,
        schluessel: "parodontalbefund",
      )
      v(4mm)
    }

    if n.befund.empfehlung != none {
      text(weight: "bold", "Empfehlung: ")
      unleserlich(
        n.befund.empfehlung,
        betroffen: n.maengel.unleserlich,
        schluessel: "empfehlung",
      )
      if n.befund.hkp_beiliegend { [ HKP liegt bei.] }
      v(4mm)
    }

    v(8mm)
    [#a.anschrift.ort, #n.befund.datum]
    v(9mm)
    text(size: 9pt, a.behandler)
  } else {
    text(weight: "bold", "Behandlungen ")
    [#n.patient.name, geb. #datum(n.patient.at("geburtsdatum", default: none))]
    v(4mm)
    historientabelle(n.historie.eintraege, betroffen: n.maengel.unleserlich)
    v(4mm)
    text(size: 8pt, style: "italic", "Auszug aus der Karteikarte.")
  }
}
