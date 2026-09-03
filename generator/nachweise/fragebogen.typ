// Einstiegspunkt fuer den Gesundheitsfragebogen.
//
// Aufruf:
//   typst compile --root . --input fall=/submissions/nachweise/cases/<name>.yaml \
//     generator/nachweise/fragebogen.typ <ziel>.pdf
//
// Das einzige der drei Dokumente, das dem Corporate Design folgt — und zwar
// aus genau dem Grund, aus dem Befund und Historie es NICHT tun: Diesen Bogen
// verschickt atra selbst. Die Praxen sind fremde Absender, atra ist es nicht.
// Umgekehrt gilt die Begruendung, die generator/rechnungen/data/praxen.yaml
// in ihrem Kopf gibt.
//
// Es gibt hier keine Stile. Ein Formular sieht immer gleich aus; die Varianz
// steckt in dem, was die Kundin hineinschreibt — und in dem, was sie
// weglaesst.

#import "/generator/nachweise/lib/nachweis.typ": datum, lade-nachweis
#import "/generator/nachweise/lib/bausteine.typ": seitenrahmen

#let n = lade-nachweis()

#let dunkelblau = rgb("#264892")
#let hellblau = rgb("#a7c6eb")
#let hellgrau = rgb("#dedede")

// Die Anmutung einer Handschrift, ohne eine Handschrift vorauszusetzen: Ist
// keine der Schreibschriften vorhanden, faellt Typst auf die Grundschrift
// zurueck, und die Tinte allein traegt den Unterschied. Das genuegt — es geht
// nicht um Echtheit, sondern darum, Eingetragenes von Gedrucktem zu trennen.
#let tinte = rgb("#1c3f7a")
// Nur eine Schreibschrift in der Liste, obwohl mehrere denkbar waeren: Typst
// warnt bei jeder Familie, die es nicht findet, und eine Warnung, die bei
// jedem Build erscheint, liest nach der dritten Woche niemand mehr.
#let handschrift(inhalt) = text(
  font: ("Bradley Hand", "Helvetica Neue"),
  fill: tinte,
  size: 10pt,
  inhalt,
)

#let kaestchen(gesetzt) = box(
  width: 3.2mm,
  height: 3.2mm,
  stroke: 0.5pt + luma(110),
  inset: 0pt,
  align(center + horizon, if gesetzt {
    text(fill: tinte, size: 8pt, weight: "bold", "X")
  } else { none }),
)

#let ankreuzzeile(antwort) = {
  let a = antwort.at("ankreuz", default: none)
  grid(
    columns: (auto, auto, auto, auto),
    column-gutter: (1.5mm, 6mm, 1.5mm),
    kaestchen(a == "ja"), text(size: 8.5pt, "ja"),
    kaestchen(a == "nein"), text(size: 8.5pt, "nein"),
  )
}

#show: seitenrahmen.with(
  groesse: 9.5pt,
  rand: (links: 22mm, rechts: 20mm, oben: 18mm, unten: 18mm),
  fusszeile: grid(
    columns: (1fr, auto),
    text(size: 7pt, fill: luma(120),
      "atra.dent ist eine erfundene Zahnzusatzversicherung. Dieser Bogen dient"
        + " ausschliesslich Schulungszwecken."),
    context text(size: 7pt, fill: luma(120))[
      Seite #counter(page).display() von #counter(page).final().first()
    ],
  ),
)

#block(fill: dunkelblau, width: 100%, inset: (x: 5mm, y: 3.5mm), {
  text(fill: white, size: 14pt, weight: "medium", tracking: 0.5pt, "atra.dent")
  linebreak()
  text(fill: hellblau, size: 9pt, "Gesundheitsfragebogen zur Risikopruefung")
})

#v(6mm)

#text(size: 8.5pt, fill: luma(90))[
  Bitte fuellen Sie den Bogen vollstaendig aus. Unvollstaendige Angaben
  koennen die Pruefung verzoegern.
]

#v(5mm)

#grid(
  columns: (auto, 1fr, auto, auto),
  column-gutter: 3mm,
  row-gutter: 2.5mm,
  text(size: 8.5pt, fill: luma(100), "Name"),
  handschrift(n.patient.name),
  text(size: 8.5pt, fill: luma(100), "geboren am"),
  handschrift(datum(n.patient.at("geburtsdatum", default: none))),
)

#v(2mm)
#line(length: 100%, stroke: 0.5pt + hellgrau)
#v(5mm)

#if n.fragebogen == none [
  #text(style: "italic", fill: luma(110))[
    Zu diesem Vorgang liegt kein ausgefuellter Fragebogen vor.
  ]
] else {
  for (i, antwort) in n.fragebogen.antworten.enumerate() {
    block(breakable: false, {
      grid(
        columns: (6mm, 1fr, auto),
        column-gutter: 2mm,
        text(size: 9pt, fill: luma(120), str(i + 1) + "."),
        text(size: 9pt, antwort.frage),
        // Nur Ja/Nein-Fragen tragen Kaestchen. Der Unterschied ist wichtig:
        // Eine offene Frage ohne Kaestchen ist vollstaendig, eine
        // Ja/Nein-Frage mit zwei leeren Kaestchen ist unbeantwortet — und
        // genau das soll ein Pruefer sehen koennen.
        if antwort.at("typ", default: "ja_nein") == "ja_nein" {
          ankreuzzeile(antwort)
        },
      )
      let frei = antwort.at("freitext", default: none)
      v(1.5mm)
      grid(
        columns: (6mm, 1fr),
        column-gutter: 2mm,
        none,
        {
          if frei != none { handschrift(frei) }
          v(0.8mm)
          line(length: 100%, stroke: 0.4pt + hellgrau)
        },
      )
    })
    v(3.5mm)
  }

  v(6mm)
  grid(
    columns: (1fr, 1fr),
    column-gutter: 10mm,
    {
      handschrift(datum(n.fragebogen.at("ausgefuellt_am", default: none)))
      v(0.8mm)
      line(length: 100%, stroke: 0.4pt + luma(140))
      text(size: 7.5pt, fill: luma(120), "Datum")
    },
    {
      handschrift(n.patient.name)
      v(0.8mm)
      line(length: 100%, stroke: 0.4pt + luma(140))
      text(size: 7.5pt, fill: luma(120), "Unterschrift")
    },
  )
}
