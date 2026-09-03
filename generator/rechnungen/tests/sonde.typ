// Sonde fuer lib/fall.typ. Setzt keine Rechnung, sondern zeigt nur die
// geladenen und gerechneten Werte. Damit laesst sich die Datenschicht
// pruefen, bevor es einen einzigen Stil gibt.

#import "/generator/rechnungen/lib/fall.typ": lade-fall
#import "/wissen/dokumente/lib/daten.typ": eur

#let f = lade-fall()

Nummer: #f.nummer \
Datum: #f.datum \
Praxis: #f.praxis.name \
Stil: #f.stil \
Patient: #f.patient.name \
Positionen: #f.positionen.len() \
Gesamt: #eur(f.summen.gesamt) \
Zahlbetrag: #eur(f.summen.zahlbetrag)

#for p in f.positionen [
  #p.art #p.at("nummer", default: "—") #p.leistung — #eur(p.betrag) — Bereich: #repr(p.leistungsbereich) \
]
