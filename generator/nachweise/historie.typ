// Einstiegspunkt fuer die Behandlungshistorie.
//
// Aufruf:
//   typst compile --root . --input fall=/submissions/nachweise/cases/<name>.yaml \
//     generator/nachweise/historie.typ <ziel>.pdf
//
// Dasselbe Briefpapier wie der Befund, weil dieselbe Praxis sie ausstellt.
// Deshalb liegt die Fallunterscheidung hier und nicht in einem eigenen
// Stilsatz: Ein zweiter Satz Vorlagen fuer dieselben fuenf Absender liefe
// irgendwann auseinander.

#import "/generator/nachweise/lib/nachweis.typ": lade-nachweis
#import "/generator/nachweise/styles/klassisch.typ"
#import "/generator/nachweise/styles/modern.typ"
#import "/generator/nachweise/styles/mvz.typ"
#import "/generator/nachweise/styles/landpraxis.typ"
#import "/generator/nachweise/styles/klinik.typ"

#let n = lade-nachweis()

#if n.stil == "klassisch" {
  klassisch.setze(n, dokument: "historie")
} else if n.stil == "modern" {
  modern.setze(n, dokument: "historie")
} else if n.stil == "mvz" {
  mvz.setze(n, dokument: "historie")
} else if n.stil == "landpraxis" {
  landpraxis.setze(n, dokument: "historie")
} else if n.stil == "klinik" {
  klinik.setze(n, dokument: "historie")
} else {
  panic("Kein Renderer fuer Befundstil: " + n.stil)
}
