// Verbindet die Gliederung aus reihenfolge.typ mit den Textbausteinen.
//
// Dadurch kann die Paragraphenfolge nicht mehr zwischen Dokument und
// Querverweis auseinanderlaufen: Beide lesen dieselbe Liste.

#import "reihenfolge.typ": paragraphen
#import "vorlage.typ": abschnitt
#import "bausteine.typ": *
#import "enterprise.typ": *

#let funktionen = (
  gegenstand: p-gegenstand,
  begriffe: p-begriffe,
  versicherungsfaehigkeit: p-versicherungsfaehigkeit,
  wartezeiten: p-wartezeiten,
  umfang: p-umfang,
  zahnstaffel: p-zahnstaffel,
  selbstbehalt: p-selbstbehalt,
  ausschluesse: p-ausschluesse,
  ausland: p-ausland,
  beitrag: p-beitrag,
  beitragsanpassung: p-beitragsanpassung,
  zahlungsverzug: p-zahlungsverzug,
  obliegenheiten: p-obliegenheiten,
  anzeigepflicht: p-anzeigepflicht,
  sachverstaendige: p-sachverstaendige,
  vertrag: p-vertrag,
  widerruf: p-widerruf,
  tarifwechsel: p-tarifwechsel,
  mitteilungen: p-mitteilungen,
  verjaehrung: p-verjaehrung,
  beschwerden: p-beschwerden,
  datenverarbeitung: p-datenverarbeitung,
  schluss: p-schluss,
)

// Setzt alle Paragraphen in der Reihenfolge der Gliederung.
//
// `bilder` ordnet einem Paragraphenschluessel ein Bild zu, das vor diesem
// Paragraphen steht: (schluessel: (datei, motiv, verhaeltnis)).
#let bedingungswerk(t, variante: none, bilder: (:)) = {
  for s in paragraphen {
    assert(s in funktionen, message: "Kein Baustein für Paragraph: " + s)
    if s in bilder {
      let b = bilder.at(s)
      v(0.6em)
      bildplatz(b.datei, b.motiv, verhaeltnis: b.at("verhaeltnis", default: 3.1))
      v(0.4em)
    }
    // Der Paragraph ist die Struktureinheit des Bedingungswerks und damit die
    // Chunk-Grenze des Suchindex. Sein Schluessel aus reihenfolge.typ ist
    // zugleich die `id` des `section` im HTML — dieselbe Liste bestimmt also
    // Reihenfolge, Nummer und Sprungmarke. Das Bild bleibt bewusst davor:
    // es gehoert zum Auftakt, nicht zum zitierten Regelungstext.
    let f = funktionen.at(s)
    abschnitt(s, if s == "selbstbehalt" { f(t, variante: variante) } else { f(t) })
  }
}
