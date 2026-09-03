// Allgemeine Versicherungsbedingungen — atra.dent.balance
//
// Das Dokument legt nur die Rahmendaten fest. Die Paragraphenfolge steht in
// lib/reihenfolge.typ, der Text in lib/bausteine.typ, die Zahlen in
// wissen/daten/tarife.yaml.

#import "lib/vorlage.typ": *
#import "lib/bausteine.typ": inhaltsuebersicht, anlage-leistungsverzeichnis
#import "lib/enterprise.typ": anlage-begriffe
#import "lib/agb.typ": anlage-agb
#import "lib/gliederung.typ": bedingungswerk

#let t = tarif("ATRA_DENT_B")

#show: avb-dokument.with(
  titel: "Allgemeine Versicherungs­bedingungen",
  untertitel: "Zahnzusatzversicherung für Erwachsene",
  tarifname: t.anzeigename,
  tarifschluessel: t.schluessel,
  bedingungsnummer: "AVB BALANCE 01/2026",
  stand: stand-text,
  titelbild: "/wissen/dokumente/bilder/avb-titel.png",
  titelbild-motiv: "Bild 1 — Titelmotiv der Bedingungswerke",
)

#inhaltsuebersicht(t)

#bildplatz(
  "/wissen/dokumente/bilder/avb-balance-auftakt.png",
  "Bild 6 — Auftakt balance",
  verhaeltnis: 2.4,
)
#bedingungswerk(t, bilder: (
    vertrag: (
      datei: "/wissen/dokumente/bilder/avb-balance-vertrag.png",
      motiv: "Bild 7 — Vertragsteil balance",
      verhaeltnis: 3.1,
    ),
  ), variante: none)
#anlage-leistungsverzeichnis(t)
#anlage-begriffe(t)
#anlage-agb(t)
