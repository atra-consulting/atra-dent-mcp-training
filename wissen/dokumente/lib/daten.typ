// Datenzugriff und Formatierung.
//
// Saemtliche Zahlen der Versicherungsbedingungen stammen aus
// wissen/daten/tarife.yaml. Beitraege gehoeren nicht dazu: sie fuehrt das
// Kernsystem (oas/openapi.yaml). In den Dokumenten selbst steht keine
// abgetippte Quote, kein abgetippter Betrag und keine abgetippte Frist.
// Aendert sich das Tarifmodell, aendern sich die Bedingungen beim
// naechsten Build mit.

// Absolut ab der Repo-Wurzel (typst wird mit --root . aufgerufen): die
// Daten liegen ausserhalb von wissen/dokumente/, damit der Wissensdienst
// sie unabhaengig von den Dokumentquellen liest.
#let modell = yaml("/wissen/daten/tarife.yaml")
#let cd = yaml("/assets/corporate-design.yaml")

// --- Bilder ------------------------------------------------------------
// Auf true setzen, sobald die generierten Bilder unter
// wissen/dokumente/bilder/ liegen.
// Solange false, zeigen die Dokumente an den Bildstellen einen Platzhalter
// mit dem jeweiligen Motivhinweis.
#let bilder-verfuegbar = true

// Auf true setzen, sobald assets/atra-dent-logo.svg vorliegt.
#let logo-verfuegbar = true

// --- Farben aus dem Corporate Design -----------------------------------

#let cd-farbe(schluessel) = {
  let f = cd.farben.find(x => x.schluessel == schluessel)
  assert(f != none, message: "Unbekannte Farbe: " + schluessel)
  rgb(f.hex)
}

#let dunkelblau = cd-farbe("dunkelblau")
#let hellblau = cd-farbe("hellblau")
#let dunkelgrau = cd-farbe("dunkelgrau")
#let hellgrau = cd-farbe("hellgrau")
#let dunkelorange = cd-farbe("dunkelorange")
#let hellorange = cd-farbe("hellorange")

#let schriftfamilie = (cd.schrift.familie, "Helvetica", "Arial")

// Flaechentoene. Ausschliesslich als Aufhellung der Markenfarben abgeleitet,
// damit keine Farbe ausserhalb der Palette im Dokument landet.
#let blau-zebra = dunkelblau.lighten(96%)
#let blau-flaeche = dunkelblau.lighten(94%)
#let blau-kasten = hellblau.lighten(78%)
#let orange-kasten = dunkelorange.lighten(93%)

// --- Zugriff auf das Tarifmodell ---------------------------------------

#let tarif(schluessel) = {
  let t = modell.tarife.find(x => x.schluessel == schluessel)
  assert(t != none, message: "Unbekannter Tarif: " + schluessel)
  t
}

// Loest die Verweiskette leistungen_wie auf, damit Tarifvarianten die
// Leistungen ihres Grundtarifs erben, ohne sie zu wiederholen.
#let leistungen(t) = {
  if "leistungen" in t { t.leistungen } else { tarif(t.leistungen_wie).leistungen }
}

#let bereich(schluessel) = {
  let b = modell.leistungsbereiche.find(x => x.schluessel == schluessel)
  assert(b != none, message: "Unbekannter Leistungsbereich: " + schluessel)
  b
}

#let bereich-schluessel = modell.leistungsbereiche.map(b => b.schluessel)

#let staffel(schluessel) = {
  let s = modell.staffeln.find(x => x.schluessel == schluessel)
  assert(s != none, message: "Unbekannte Staffel: " + schluessel)
  s
}

#let staffel-von(t) = staffel(t.staffel)

// Beschreibungstexte aus der YAML enthalten harte Zeilenumbrueche aus dem
// Faltblock-Stil. Fuer den Satz werden sie zu Fliesstext normalisiert.
#let fliesstext(s) = s.split(regex("\s+")).filter(w => w != "").join(" ")

// Stand des Tarifmodells in deutscher Schreibweise.
#let stand-text = {
  let s = modell.stand
  let iso = if type(s) == str { s } else { s.display("[year]-[month]-[day]") }
  let teile = iso.split("-")
  if teile.len() == 3 { teile.at(2) + "." + teile.at(1) + "." + teile.at(0) } else { iso }
}

// --- Zahlformatierung (deutsche Konvention) ----------------------------

#let zahl(n, nachkomma: 2) = {
  let negativ = n < 0
  let skaliert = str(int(calc.round(calc.abs(n) * calc.pow(10, nachkomma))))
  while skaliert.len() <= nachkomma { skaliert = "0" + skaliert }

  let ganz = if nachkomma > 0 { skaliert.slice(0, skaliert.len() - nachkomma) } else { skaliert }
  let rest = if nachkomma > 0 { skaliert.slice(skaliert.len() - nachkomma) } else { "" }

  let gruppen = ()
  while ganz.len() > 3 {
    gruppen.insert(0, ganz.slice(ganz.len() - 3))
    ganz = ganz.slice(0, ganz.len() - 3)
  }
  gruppen.insert(0, ganz)

  let ausgabe = gruppen.join(".")
  if nachkomma > 0 { ausgabe = ausgabe + "," + rest }
  if negativ { "\u{2212}" + ausgabe } else { ausgabe }
}

#let eur(n, nachkomma: 2) = zahl(n, nachkomma: nachkomma) + "\u{00A0}EUR"
#let eur0(n) = zahl(n, nachkomma: 0) + "\u{00A0}EUR"
#let prozent(n) = zahl(n, nachkomma: 0) + "\u{00A0}%"

// Zahlwoerter fuer die Fliesstextform von Fristen und Anzahlen.
#let zahlwort(n) = {
  let w = (
    "null", "ein", "zwei", "drei", "vier", "fünf", "sechs",
    "sieben", "acht", "neun", "zehn", "elf", "zwölf",
  )
  if n >= 0 and n < w.len() { w.at(n) } else { str(n) }
}

// Nominativ/Akkusativ ("beträgt acht Monate") und Dativ ("von acht Monaten")
// sind getrennt gefuehrt — im Bedingungstext kommen beide Faelle vor.
#let monate(n) = if n == 1 { "einen Monat" } else { zahlwort(n) + " Monate" }
#let monaten(n) = if n == 1 { "einem Monat" } else { zahlwort(n) + " Monaten" }
#let jahre(n) = if n == 1 { "ein Jahr" } else { zahlwort(n) + " Jahre" }
#let jahren(n) = if n == 1 { "einem Jahr" } else { zahlwort(n) + " Jahren" }
#let behandlungen(n) = if n == 1 { "eine Behandlung" } else {
  zahlwort(n) + " Behandlungen"
}

// Das Bedingungswerk definiert das Versicherungsjahr eigens, um genau die
// Unschaerfe von "Jahr" auszuschliessen. Also wird auch der definierte
// Begriff verwendet.
#let vjahre(n) = if n == 1 { "ein Versicherungsjahr" } else {
  zahlwort(n) + " Versicherungsjahre"
}
#let vjahren(n) = if n == 1 { "einem Versicherungsjahr" } else {
  zahlwort(n) + " Versicherungsjahren"
}

// --- Abgeleitete Aussagen ueber einen Tarif ----------------------------

// Die Begrenzungen eines Leistungsbereichs als Liste von Teilsaetzen.
// Steht genau einmal, damit Bedingungswerk und Beratungsunterlage nicht
// verschiedene Teilmengen der Felder zeigen.
#let begrenzungen(e, kurz: false) = {
  if not e.versichert { return ("nicht versichert",) }
  let g = ()
  if "limit_pro_jahr" in e {
    g.push(eur0(e.limit_pro_jahr) + if kurz { " p. a." } else { " je Versicherungsjahr" })
  }
  if "limit_gesamt" in e {
    g.push(eur0(e.limit_gesamt) + if kurz { " gesamt" } else { " für die gesamte Vertragsdauer" })
  }
  if "faelle_pro_jahr" in e {
    if e.faelle_pro_jahr == none {
      g.push("Anzahl unbegrenzt")
    } else {
      g.push("höchstens " + behandlungen(e.faelle_pro_jahr) + " je Versicherungsjahr")
    }
  }
  if "max_faelle" in e {
    g.push("höchstens " + behandlungen(e.max_faelle) + " in " + vjahren(e.zeitraum_jahre))
  }
  if "bedingung" in e { g.push("nur bei " + e.bedingung) }
  g
}

// Die Eckwerte eines Tarifs als (Bezeichnung, Wert)-Paare. Einmal
// definiert, zweifach gesetzt: als Uebersicht im Bedingungswerk und als
// Spalte im Tarifvergleich.
#let eckwerte(t) = {
  let l = if "leistungen" in t { t.leistungen } else { modell.tarife.find(x => (
    x.schluessel == t.leistungen_wie
  )).leistungen }
  let kfo = l.KFO
  let s = modell.staffeln.find(x => x.schluessel == t.staffel)
  (
    (
      "Selbstbehalt je Versicherungsjahr",
      if t.selbstbehalt == 0 { "keiner" } else { eur0(t.selbstbehalt) },
    ),
    (
      "Eintrittsalter",
      str(t.eintrittsalter.von) + " bis " + str(t.eintrittsalter.bis) + " Jahre",
    ),
    (
      "Allgemeine Wartezeit",
      if t.at("wartezeit_entfaellt_bei_vorversicherung", default: false) {
        monate(t.wartezeit_monate) + "; entfällt bei Vorversicherung"
      } else { monate(t.wartezeit_monate) },
    ),
    (
      "Wartezeit Kieferorthopädie",
      if kfo.versichert and "wartezeit_monate" in kfo {
        monate(kfo.wartezeit_monate)
      } else { "entfällt" },
    ),
    ("Zahnstaffel", vjahre(s.dauer_jahre)),
    (
      "Jahreshöchstgrenze nach der Staffel",
      if t.at("jahreshoechstgrenze", default: none) == none { "keine" } else {
        eur0(t.jahreshoechstgrenze)
      },
    ),
  )
}

// --- Erstattungsrechnung -----------------------------------------------
//
// Die Fachlogik steht genau hier. Das Bedingungswerk beschreibt, was gilt;
// gerechnet wird nur in der Beratungsunterlage, und zwar mit dieser
// Funktion. Sie fuehrt die sechs Schritte des Modells aus und
// gibt die Zwischenstaende zurueck, damit ein Rechenweg darstellbar ist.
#let erstattung(t, leistungsbereich: "ZE", rechnung: 0, gkv: 0, jahr: 1) = {
  let l = if "leistungen" in t { t.leistungen } else { modell.tarife.find(x => (
    x.schluessel == t.leistungen_wie
  )).leistungen }
  let e = l.at(leistungsbereich)

  if not e.versichert {
    return (
      versichert: false,
      betrag: 0,
      schritte: (),
      staffel-greift: false,
      limit-greift: false,
    )
  }

  let s1 = rechnung
  let s2 = s1 * e.quote / 100
  let s3 = if "limit_pro_jahr" in e { calc.min(s2, e.limit_pro_jahr) } else { s2 }
  let s4 = calc.max(s3 - gkv, 0)
  let s5 = calc.max(s4 - t.selbstbehalt, 0)

  let staffel = modell.staffeln.find(x => x.schluessel == t.staffel)
  let stufe = staffel.stufen.find(x => x.bis_jahr == jahr)
  let deckel = if stufe == none { none } else { stufe.betrag }
  let jhg = t.at("jahreshoechstgrenze", default: none)

  let s6 = s5
  if deckel != none { s6 = calc.min(s6, deckel) }
  if deckel == none and jhg != none { s6 = calc.min(s6, jhg) }

  (
    versichert: true,
    betrag: s6,
    quote: e.quote,
    limit-greift: s3 < s2,
    staffel-greift: s6 < s5,
    deckel: deckel,
    schritte: (
      (nr: 1, betrag: s1),
      (nr: 2, betrag: s2),
      (nr: 3, betrag: s3),
      (nr: 4, betrag: s4),
      (nr: 5, betrag: s5),
      (nr: 6, betrag: s6),
    ),
  )
}
