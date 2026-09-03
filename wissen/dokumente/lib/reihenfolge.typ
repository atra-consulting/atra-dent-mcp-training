// Die Gliederung des Bedingungswerks — die einzige Stelle, an der die
// Reihenfolge der Paragraphen steht.
//
// Diese Datei importiert nichts. Dadurch koennen sowohl die Textbausteine
// (fuer Querverweise) als auch die Dokumente (fuer den Aufbau) sie lesen,
// ohne dass ein Zirkelimport entsteht.
//
// Wer einen Paragraphen einschiebt, umstellt oder streicht, aendert diese
// Liste — und nur sie. Alle Querverweise ziehen automatisch nach.

#let paragraphen = (
  "gegenstand",
  "begriffe",
  "versicherungsfaehigkeit",
  "wartezeiten",
  "umfang",
  "zahnstaffel",
  "selbstbehalt",
  "ausschluesse",
  "ausland",
  "beitrag",
  "beitragsanpassung",
  "zahlungsverzug",
  "obliegenheiten",
  "anzeigepflicht",
  "sachverstaendige",
  "vertrag",
  "widerruf",
  "tarifwechsel",
  "mitteilungen",
  "verjaehrung",
  "beschwerden",
  "datenverarbeitung",
  "schluss",
)

// Paragraphennummer eines Schluessels. Bricht den Build, wenn der Schluessel
// nicht in der Gliederung steht — genau das soll ein toter Verweis tun.
#let nr(schluessel) = {
  let i = paragraphen.position(s => s == schluessel)
  assert(i != none, message: "Unbekannter Paragraph im Querverweis: " + schluessel)
  i + 1
}
