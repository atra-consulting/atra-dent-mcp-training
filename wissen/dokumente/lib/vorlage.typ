// Satzvorlage fuer die Versicherungsbedingungen.
//
// Setzt Seitenspiegel, Typografie und Auszeichnungen im atra Corporate Design
// und stellt die Bausteine bereit, aus denen die Dokumente bestehen:
// Paragraphen mit fortlaufender Zaehlung, nummerierte Absaetze, Verweise,
// Tabellen und Hinweiskaesten.

#import "daten.typ": *
#import "reihenfolge.typ": nr

// --- Zaehler -----------------------------------------------------------

#let absatz-nr = counter("absatz")

// --- Struktur fuer den Suchindex ---------------------------------------
//
// Im PDF traegt sich die Gliederung ueber Typografie: Groesse, Farbe, Linie.
// Ein Suchindex sieht davon nichts. Er braucht Chunk-Grenzen als Markup.
// Deshalb bekommt jede Struktureinheit in der HTML-Fassung ein eigenes
// `section` mit sprechender `id`. Diese `id` ist dieselbe, die die API als
// `abschnittId` einer Fundstelle ausliefert und die als Sprungmarke hinter
// dem Doppelkreuz in der HTML-URL steht — sie muss also stabil bleiben und
// darf keine laufende Nummer sein.
//
// Im PDF bleibt der Inhalt unveraendert und ohne jede Huelle: die Fassung
// fuer den Menschen darf sich nicht daran aendern, dass eine Maschine die
// zweite Fassung liest.
#let abschnitt(id, inhalt) = context if target() == "html" {
  html.elem("section", attrs: (id: id), inhalt)
} else {
  inhalt
}

// --- Bausteine fuer den Text -------------------------------------------

#let paragraf(titel) = heading(level: 1, titel)

// Ein nummerierter Absatz in der Form "(1) Text".
//
// Im PDF haengt die Nummer in einer eigenen Spalte, damit der Text
// buendig steht. Ein Raster ueberlebt den HTML-Export aber nicht — Typst
// verwirft `grid` samt Inhalt, und der Absatztext waere dort schlicht weg.
// Die HTML-Fassung setzt die Nummer deshalb in den Fliesstext. Sie gehoert
// dorthin: der Absatz ist die feinste zitierfaehige Einheit ("§ 5 Absatz 3"),
// und seine Nummer muss im gefundenen Text mitstehen.
#let abs(body) = {
  absatz-nr.step()
  context if target() == "html" {
    html.elem("div", attrs: (class: "absatz"), {
      text(weight: "medium")[(#absatz-nr.display())]
      [ ]
      body
    })
  } else {
    block(
      below: 0.9em,
      grid(
        columns: (2.1em, 1fr),
        column-gutter: 0pt,
        text(weight: "medium")[(#absatz-nr.display())],
        body,
      ),
    )
  }
}

// Aufzaehlung innerhalb eines Absatzes, buchstabiert wie im
// Versicherungssatz ueblich: a) b) c). Bleibt zusammen.
//
// Aus demselben Grund wie bei `abs` traegt die HTML-Fassung den Buchstaben
// im Text und nicht in einer Rasterspalte: auf ihn wird verwiesen
// ("Buchstabe b"), also muss er im ausgelieferten Text stehen.
#let buchstaben(..punkte) = {
  let items = punkte.pos()
  context if target() == "html" {
    html.elem("div", attrs: (class: "buchstaben"), for (i, p) in items.enumerate() {
      html.elem("div", attrs: (class: "buchstabe"), {
        text(weight: "medium", numbering("a)", i + 1))
        [ ]
        p
      })
    })
  } else {
    block(
      inset: (left: 2.1em),
      below: 0.9em,
      breakable: false,
      for (i, p) in items.enumerate() {
        grid(
          columns: (1.7em, 1fr),
          row-gutter: 0.5em,
          text(weight: "medium", numbering("a)", i + 1)),
          p,
        )
      },
    )
  }
}

// Querverweis auf einen anderen Paragraphen. Das Argument ist der Schluessel
// aus reihenfolge.typ, nicht die Nummer — die ergibt sich aus der Gliederung.
#let verweis(schluessel, absatz: none, buchstabe: none) = {
  let s = [§~#nr(schluessel)]
  if absatz != none { s = [#s Absatz~#absatz] }
  if buchstabe != none { s = [#s Buchstabe~#buchstabe] }
  s
}

// Legaldefinition: der definierte Begriff wird bei seiner Einfuehrung
// ausgezeichnet. Medium statt Semibold, weil Helvetica Neue kein Semibold
// mitbringt und der Schnitt sonst auf Bold zurueckfaellt.
#let begriff(body) = text(weight: "medium", fill: dunkelblau, body)

// Technische Kuerzel im Fliesstext. Bewusst kein raw() — Typst setzt raw in
// einer Monospace, die nicht zum Corporate Design gehoert und im
// Bedingungstext wie ein Debug-Ausdruck wirkt.
#let kuerzel(k) = text(weight: "medium", tracking: 0.6pt, size: 0.92em, k)

// Hinweiskasten. Kein Bestandteil der Bedingungen, sondern Lesehilfe.
#let hinweis(titel: none, body) = block(
  width: 100%,
  fill: blau-kasten,
  stroke: (left: 2.5pt + hellblau),
  inset: (x: 12pt, y: 10pt),
  above: 1.2em,
  below: 1.2em,
  radius: (right: 2pt),
  breakable: false,
  {
    if titel != none {
      text(size: 8pt, weight: "bold", fill: dunkelblau, tracking: 0.5pt, upper(titel))
      v(-0.3em)
    }
    set text(size: 9pt)
    body
  },
)

// --- Tabellen ----------------------------------------------------------
//
// Kopfzeile als Vollflaeche in Dunkelblau mit weisser Schrift. Die Farbe
// wird an der Zelle gesetzt, nicht ueber eine globale show-Regel — sonst
// traefe sie auch Tabellen ohne dunklen Kopf und schriebe dort Weiss auf
// Hellblau.
#let tabelle(
  spalten: auto,
  kopf: (),
  ausrichtung: auto,
  klein: false,
  // Kurze Tabellen bleiben zusammen. Lange muessen umbrechen duerfen, sonst
  // erzwingen sie eine fast leere Vorseite; ihre Kopfzeile wiederholt sich.
  bindend: true,
  ..zeilen,
) = block(
  above: 1.2em,
  below: 1.2em,
  breakable: not bindend,
  {
    // Blocksatz und Trennung gehoeren nicht in Tabellenzellen.
    set par(justify: false)
    set text(hyphenate: false, size: if klein { 8.5pt } else { 9.3pt })
    table(
      columns: spalten,
      align: ausrichtung,
      stroke: none,
      inset: (x: 8pt, y: 6.5pt),
      fill: (_, y) => if y == 0 { dunkelblau } else if calc.odd(y) { blau-zebra } else {
        white
      },
      table.header(
        ..kopf.map(z => text(fill: white, weight: "bold", size: 8.3pt, z)),
      ),
      ..zeilen.pos(),
    )
  },
)

// Schlichte Tabelle ohne Farbflaeche, fuer Kaesten und Nebenrechnungen.
#let tabelle-schlicht(spalten: auto, kopf: (), ausrichtung: auto, ..zeilen) = block(
  above: 0.8em,
  below: 0.4em,
  breakable: false,
  {
    set par(justify: false)
    set text(hyphenate: false)
    table(
      columns: spalten,
      align: ausrichtung,
      stroke: (bottom: 0.5pt + hellgrau),
      inset: (x, y) => (
        left: if x == 0 { 0pt } else { 9pt },
        right: 0pt,
        top: 4pt,
        bottom: 4pt,
      ),
      table.header(
        ..kopf.map(z => text(
          size: 8pt,
          weight: "bold",
          fill: dunkelblau,
          tracking: 0.4pt,
          upper(z),
        )),
      ),
      ..zeilen.pos(),
    )
  },
)

// --- Bildplatz ---------------------------------------------------------

// Die Hoehe folgt dem Seitenverhaeltnis des gelieferten Bildes, damit nichts
// beschnitten wird — auch dann nicht, wenn sich der Seitenspiegel aendert.
#let bildplatz(datei, motiv, verhaeltnis: 2.4) = layout(flaeche => {
  let hoehe = flaeche.width / verhaeltnis
  if bilder-verfuegbar {
    block(width: 100%, clip: true, radius: 2pt, image(
      datei,
      width: 100%,
      height: hoehe,
      fit: "cover",
    ))
  } else {
    block(
      width: 100%,
      height: hoehe,
      fill: blau-flaeche,
      stroke: 0.6pt + hellblau,
      radius: 2pt,
      inset: 14pt,
      align(center + horizon, {
        text(size: 8pt, weight: "bold", fill: dunkelblau, tracking: 0.6pt, upper("Bildplatz"))
        linebreak()
        v(0.3em)
        text(size: 8.5pt, fill: dunkelgrau, style: "italic", motiv)
        linebreak()
        v(0.3em)
        text(size: 7.5pt, fill: dunkelgrau, tracking: 0.4pt, datei)
      }),
    )
  }
})

// --- Dokumentvorlage ---------------------------------------------------

#let avb-dokument(
  titel: "Allgemeine Versicherungsbedingungen",
  untertitel: none,
  tarifname: "",
  tarifschluessel: "",
  bedingungsnummer: "",
  stand: "",
  titelbild: none,
  titelbild-motiv: "",
  // Paragraphenzeichen nur im Bedingungswerk. Eine Beratungsunterlage ist
  // kein Regelwerk und zaehlt schlicht durch.
  paragraphen: true,
  // Vertraulichkeitskennzeichnung. Bleibt bei den Bedingungswerken und der
  // Beratungsunterlage `none`: Das sind veroeffentlichte Verbraucher-
  // dokumente, sie sind zitierfaehig und tragen keine Einstufung.
  //
  // Internes Material setzt beides. Der Vermerk laeuft klein in der Fusszeile
  // jeder Textseite mit, der Hinweis erklaert auf der Titelseite, was die
  // Einstufung bedeutet. Beides gehoert ins Dokument selbst und nicht nur in
  // die Metadaten der Schnittstelle: Ein PDF wandert weiter als das System,
  // das es ausgeliefert hat, und muss seine Einstufung mitfuehren.
  vertraulichkeitsvermerk: none,
  vertraulichkeitshinweis: none,
  doc,
) = {
  set document(title: titel + " " + tarifname, author: "atra consulting")

  set page(
    paper: "a4",
    margin: (top: 26mm, bottom: 24mm, left: 30mm, right: 34mm),
    header: context {
      if counter(page).get().first() > 1 {
        set text(size: 7.5pt, fill: dunkelgrau)
        grid(
          columns: (1fr, auto),
          align(left)[#tarifname],
          align(right)[#titel],
        )
        v(-0.55em)
        line(length: 100%, stroke: 0.5pt + hellgrau)
      }
    },
    footer: context {
      if counter(page).get().first() > 1 {
        set text(size: 7pt, fill: dunkelgrau)
        // Die Einstufung steht ueber der Trennlinie und damit naeher am Text
        // als an der Seitenzahl. Wer eine Seite einzeln ausdruckt oder
        // kopiert, hat sie dann trotzdem vor sich.
        if vertraulichkeitsvermerk != none {
          align(center, text(
            size: 6.5pt,
            weight: "medium",
            fill: dunkelorange,
            tracking: 1pt,
            upper(vertraulichkeitsvermerk),
          ))
          v(-0.35em)
        }
        line(length: 100%, stroke: 0.5pt + hellgrau)
        v(-0.3em)
        grid(
          columns: (auto, 1fr, auto),
          column-gutter: 10pt,
          align(left)[#bedingungsnummer],
          align(center)[Fassung #stand],
          align(right)[Seite #counter(page).display() von #counter(page).final().first()],
        )
      }
    },
  )

  set text(font: schriftfamilie, size: 9.8pt, lang: "de", region: "DE", hyphenate: true)
  set par(justify: true, leading: 0.70em, spacing: 0.9em, first-line-indent: 0pt)

  set heading(numbering: (..n) => if n.pos().len() != 1 { none } else if paragraphen {
    [§ #n.pos().first()]
  } else {
    [#n.pos().first().]
  })

  // Die Ueberschriften sind der zweite Teil der Chunk-Grenze: das `section`
  // sagt, wo ein Abschnitt anfaengt, die Ueberschrift sagt, wie er heisst.
  // Sie wird als Fundstelle zitiert ("§ 5 Umfang des Versicherungsschutzes")
  // und muss deshalb im HTML die Paragraphenbezeichnung mitfuehren, die im
  // PDF aus dem gesetzten Zaehler links neben dem Titel steht.
  show heading.where(level: 1): it => {
    absatz-nr.update(0)
    context if target() == "html" {
      html.elem("h1", if it.numbering == none { it.body } else {
        counter(heading).display(it.numbering) + [ ] + it.body
      })
    } else {
      block(above: 2.2em, below: 1.0em, {
        set text(size: 12.5pt, weight: "bold", fill: dunkelblau)
        if it.numbering == none {
          it.body
        } else {
          grid(
            columns: (auto, 1fr),
            column-gutter: 0.7em,
            context counter(heading).display(it.numbering),
            it.body,
          )
        }
        v(0.35em)
        line(length: 100%, stroke: 0.6pt + hellblau)
      })
    }
  }

  show heading.where(level: 2): it => context if target() == "html" {
    html.elem("h2", it.body)
  } else {
    block(
      above: 1.6em,
      below: 0.7em,
      text(size: 10.5pt, weight: "bold", fill: dunkelblau, it.body),
    )
  }

  show link: set text(fill: dunkelblau)

  // --- Titelseite ---
  {
    set page(header: none, footer: none)
    v(2mm)
    if logo-verfuegbar {
      image("/assets/atra-dent-logo.svg", width: 52mm)
    } else {
      text(size: 20pt, weight: "bold", fill: dunkelblau)[atra#text(fill: dunkelorange)[.]dent]
    }

    v(16mm)
    line(length: 28mm, stroke: 2.5pt + dunkelorange)
    v(6mm)

    text(size: 21pt, weight: "bold", fill: dunkelblau, titel)
    if untertitel != none {
      linebreak()
      v(1mm)
      text(size: 12.5pt, fill: dunkelgrau, untertitel)
    }

    v(6mm)
    text(size: 15pt, weight: "medium")[#tarifname]

    v(3mm)
    set text(size: 8.5pt, fill: dunkelgrau, tracking: 0.7pt)
    if tarifschluessel != "" [Tarif #upper(tarifschluessel.replace("_", ".")) \ ]
    if bedingungsnummer != "" [#bedingungsnummer \ ]
    [Fassung #stand]

    v(10mm)
    if titelbild != none {
      bildplatz(titelbild, titelbild-motiv, verhaeltnis: 2.4)
    }

    // Der Vertraulichkeitshinweis steht auf der Titelseite und nicht erst im
    // ersten Abschnitt: Wer das Dokument aufschlaegt, um eine Stelle daraus
    // vorzulesen, soll die Einstufung vor dem Inhalt sehen. Zurueckhaltend
    // gesetzt — ein Kasten in der Hausfarbe, kein Wasserzeichen.
    if vertraulichkeitshinweis != none {
      v(10mm)
      block(
        width: 100%,
        fill: orange-kasten,
        stroke: (left: 2.5pt + dunkelorange),
        inset: (x: 14pt, y: 12pt),
        radius: (right: 2pt),
        {
          text(
            size: 8pt,
            weight: "bold",
            fill: dunkelorange,
            tracking: 0.8pt,
            upper(if vertraulichkeitsvermerk != none { vertraulichkeitsvermerk } else {
              "Vertraulich"
            }),
          )
          v(-0.25em)
          set text(size: 9pt)
          set par(justify: true, leading: 0.62em)
          vertraulichkeitshinweis
        },
      )
    }

    v(1fr)
    // Die Kennzeichnung als erfundenes Muster steht genau einmal im
    // gesamten Dokument, klein, am Fuss der Titelseite.
    text(size: 7pt, fill: dunkelgrau)[
      Fiktives Muster für Schulungs- und Demonstrationszwecke. Begründet keinen
      Versicherungsschutz, stellt kein Angebot dar, ohne Bezug zu realen
      Produkten oder Anbietern. · atra consulting
    ]
    pagebreak()
  }

  doc
}
