// Einstiegspunkt des Rechnungsgenerators.
//
// Aufruf:
//   typst compile --root . --input fall=/submissions/rechnungen/cases/<name>.yaml \
//     generator/rechnungen/rechnung.typ <ziel>.pdf
//
// Der Stil folgt aus der Praxis des Falls. Diese Datei kennt die Stile nur
// als Namen und entscheidet nichts ueber ihr Aussehen.

#import "/generator/rechnungen/lib/fall.typ": lade-fall
#import "/generator/rechnungen/styles/klassisch.typ"
#import "/generator/rechnungen/styles/modern.typ"
#import "/generator/rechnungen/styles/verrechnungsstelle.typ"
#import "/generator/rechnungen/styles/landpraxis.typ"
#import "/generator/rechnungen/styles/klinik.typ"

#let f = lade-fall()

#if f.stil == "klassisch" {
  klassisch.setze(f)
} else if f.stil == "modern" {
  modern.setze(f)
} else if f.stil == "verrechnungsstelle" {
  verrechnungsstelle.setze(f)
} else if f.stil == "landpraxis" {
  landpraxis.setze(f)
} else if f.stil == "klinik" {
  klinik.setze(f)
} else {
  panic("Kein Renderer fuer Stil: " + f.stil)
}
