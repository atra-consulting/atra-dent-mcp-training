// Einstiegspunkt fuer den zahnaerztlichen Befundbericht.
//
// Aufruf:
//   typst compile --root . --input fall=/submissions/nachweise/cases/<name>.yaml \
//     generator/nachweise/befund.typ <ziel>.pdf
//
// Der Stil folgt aus der Praxis des Falls. Diese Datei kennt die Stile nur
// als Namen und entscheidet nichts ueber ihr Aussehen — dieselbe Aufteilung
// wie in generator/rechnungen/rechnung.typ.

#import "/generator/nachweise/lib/nachweis.typ": lade-nachweis
#import "/generator/nachweise/styles/klassisch.typ"
#import "/generator/nachweise/styles/modern.typ"
#import "/generator/nachweise/styles/mvz.typ"
#import "/generator/nachweise/styles/landpraxis.typ"
#import "/generator/nachweise/styles/klinik.typ"

#let n = lade-nachweis()

#if n.stil == "klassisch" {
  klassisch.setze(n, dokument: "befund")
} else if n.stil == "modern" {
  modern.setze(n, dokument: "befund")
} else if n.stil == "mvz" {
  mvz.setze(n, dokument: "befund")
} else if n.stil == "landpraxis" {
  landpraxis.setze(n, dokument: "befund")
} else if n.stil == "klinik" {
  klinik.setze(n, dokument: "befund")
} else {
  panic("Kein Renderer fuer Befundstil: " + n.stil)
}
