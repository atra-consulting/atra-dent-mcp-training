// Laedt eine Falldatei, prueft sie und stellt die Werte bereit.
//
// Die einzige Stelle im Generator, die Falldaten liest. Kein Stil liest die
// YAML-Datei selbst und keiner rechnet — dieselbe Trennung wie in
// generator/rechnungen/lib/fall.typ. Die Stile duerfen im Aussehen beliebig
// auseinanderlaufen, in den Fakten nicht.
//
// Alle Pruefungen sind assert und brechen den Build ab. Ein fehlerhafter Fall
// erzeugt kein PDF, sondern eine Meldung: Ein stillschweigend halb gerendertes
// Dokument waere hier besonders teuer, weil die Unvollstaendigkeit eines
// Nachweises absichtlich vorkommt und ein echter Fehler dann wie ein
// gewollter Mangel aussaehe.

// --- Stammdaten --------------------------------------------------------

// Die Praxen kommen aus dem Rechnungsgenerator und nicht aus einer eigenen
// Datei. Dieselbe Praxis, die einem Kunden eine Rechnung geschrieben hat,
// schreibt auch seinen Befund — damit passen die eingereichten Unterlagen zur
// Schadenhistorie im Kernsystem, und ein Pruefer kann beides nebeneinander
// legen. Eine zweite Praxenliste liefe irgendwann auseinander.
#let praxen-datei = yaml("/generator/rechnungen/data/praxen.yaml")

// Zulaessige Befundstile. Nicht dieselben wie die Rechnungsstile: Ein Befund
// ist kein Geschaeftsbrief, und "verrechnungsstelle" ergaebe hier gar keinen
// Sinn — eine Abrechnungsstelle erhebt keinen Befund.
#let stile = ("klassisch", "modern", "mvz", "landpraxis", "klinik")

// Welcher Befundstil zu welcher Praxis gehoert. Der Stil ist Eigenschaft der
// Praxis und nicht des Falls: Eine Praxis schreibt immer gleich.
#let befundstil-je-praxis = (
  beisser: "klassisch",
  weissraum: "modern",
  dds: "mvz",
  plombeck: "landpraxis",
  kiefernwald: "klinik",
)

// Zustaende, die ein Zahn im Befund tragen kann. Bewusst knapp gehalten: Der
// Facharzt soll aus dem Befund lesen, nicht einen Katalog nachschlagen.
#let zahnzustaende = (
  "gesund",
  "fuellung",
  "inlay",
  "teilkrone",
  "krone",
  "bruecke",
  "implantat",
  "wurzelgefuellt",
  "ersatzbeduerftig",
  "fehlend",
)

// Die vier Merkmale der Risikoeinschaetzung mit ihren Gewichten. Sie stehen
// hier, weil erwartung.merkmale gegen sie geprueft wird — nicht, damit der
// Generator etwas einschaetzte. Die Einschaetzung selbst trifft spaeter der
// Arztservice; was hier steht, ist die Ground Truth aus Menschenhand.
#let merkmale = (
  ANGERATENE_BEHANDLUNG: 40,
  ERSATZBEDARF: 25,
  SANIERUNGSGRAD: 20,
  PARODONTALBEFUND: 15,
)

#let nicht-beurteilbar = "NICHT_BEURTEILBAR"

// --- Formatierung ------------------------------------------------------

// YAML liefert Datumsangaben je nach Bibliothek als Zeichenkette oder als
// datetime. Beide Zweige stehen defensiv, wie im Rechnungsgenerator.
#let datum(d) = {
  if d == none { return "" }
  let iso = if type(d) == str { d } else { d.display("[year]-[month]-[day]") }
  let teile = iso.split("-")
  if teile.len() == 3 { teile.at(2) + "." + teile.at(1) + "." + teile.at(0) } else { iso }
}

#let iso(d) = if type(d) == str { d } else { d.display("[year]-[month]-[day]") }

// --- Zahnschema --------------------------------------------------------

// FDI: erste Ziffer der Quadrant (1-4), zweite die Position von der Mitte
// nach hinten (1-8). Milchzaehne (Quadranten 5-8) kommen nicht vor — das Lab
// versichert Erwachsene.
#let fdi-gueltig(zahn) = {
  if type(zahn) != str or zahn.len() != 2 { return false }
  let q = zahn.at(0)
  let p = zahn.at(1)
  ("1", "2", "3", "4").contains(q) and ("1", "2", "3", "4", "5", "6", "7", "8").contains(p)
}

// Die vier Quadranten in der Reihenfolge, in der ein Zahnschema gelesen wird:
// oben rechts, oben links, unten links, unten rechts.
#let quadrant-zaehne(q) = {
  let folge = if q == 1 or q == 4 { (8, 7, 6, 5, 4, 3, 2, 1) } else { (1, 2, 3, 4, 5, 6, 7, 8) }
  folge.map(p => str(q) + str(p))
}

// --- Pruefungen --------------------------------------------------------

#let praxis-von(schluessel) = {
  let treffer = praxen-datei.praxen.find(p => p.schluessel == schluessel)
  assert(treffer != none, message: "Unbekannte Praxis: " + schluessel)
  treffer
}

// Wer den Befund unterschreibt. Bei einer Verrechnungsstelle ist das nicht
// sie selbst: Sie rechnet nur ab. Der Befund kommt vom Leistungserbringer,
// den praxen.yaml unter im_auftrag_von fuehrt — Absender und Behandler fallen
// auseinander, und genau das soll auf dem Papier sichtbar bleiben.
#let absender-von(pr) = {
  let auftraggeber = pr.at("im_auftrag_von", default: none)
  if auftraggeber == none {
    (
      name: pr.name,
      zusatz: pr.at("zusatz", default: none),
      behandler: pr.behandler,
      anschrift: pr.anschrift,
      kontakt: pr.kontakt,
      farben: pr.farben,
    )
  } else {
    // Kontakt und Farben bleiben die der Abrechnungsstelle: Sie stellt das
    // Briefpapier, auf dem das MVZ schreibt.
    (
      name: auftraggeber.name,
      zusatz: "Befundbericht, uebermittelt durch " + pr.name,
      behandler: auftraggeber.behandler,
      anschrift: auftraggeber.anschrift,
      kontakt: pr.kontakt,
      farben: pr.farben,
    )
  }
}

#let pruefe-zahnstatus(eintraege, pfad) = {
  for (i, e) in eintraege.enumerate() {
    let wo = pfad + ": zahnstatus[" + str(i) + "]"
    assert("zahn" in e, message: wo + " ohne zahn")
    assert(fdi-gueltig(e.zahn), message: wo + ": " + e.zahn + " ist keine FDI-Nummer (11-48)")
    assert("zustand" in e, message: wo + " ohne zustand")
    assert(
      zahnzustaende.contains(e.zustand),
      message: wo + ": unbekannter Zustand '" + e.zustand + "'",
    )
  }
  // Ein Zahn kann nicht zweimal im Schema stehen. Ohne diese Pruefung faende
  // ein Widerspruch im Befund nie auf, und Widersprueche sollen hier
  // ausschliesslich zwischen Fragebogen und Befund vorkommen, nie im Befund
  // selbst.
  let nummern = eintraege.map(e => e.zahn)
  assert(
    nummern.dedup().len() == nummern.len(),
    message: pfad + ": ein Zahn steht mehrfach im Zahnstatus",
  )
}

// Prueft die Ground Truth und rechnet den Score, sofern er gueltig ist.
//
// Die Regel, um die es hier geht: Ein einziges NICHT_BEURTEILBAR macht den
// Score ungueltig. Ein nicht beurteilbares Merkmal wird weder als 0 noch als
// 100 gewertet — beides waere eine Aussage, die die Unterlagen nicht
// hergeben. Wer Unwissen in eine Zahl rechnet, bekommt eine Zahl, die
// hinterher aussieht wie Wissen.
#let pruefe-erwartung(erwartung, pfad) = {
  assert("merkmale" in erwartung, message: pfad + ": erwartung.merkmale fehlt")
  assert("erwartete_zone" in erwartung, message: pfad + ": erwartung.erwartete_zone fehlt")

  let offen = ()
  let punkte = 0.0

  for (name, gewicht) in merkmale {
    assert(
      name in erwartung.merkmale,
      message: pfad + ": Merkmal " + name + " fehlt in erwartung.merkmale",
    )
    let m = erwartung.merkmale.at(name)
    assert("wert" in m, message: pfad + ": " + name + " ohne wert")
    assert(
      "begruendung" in m and m.begruendung != none,
      message: pfad + ": " + name + " ohne begruendung — die Ground Truth muss sagen, warum",
    )
    let w = m.wert
    if w == nicht-beurteilbar {
      offen.push(name)
    } else {
      assert(
        type(w) == int and w >= 0 and w <= 100,
        message: pfad + ": " + name + " hat Wert " + repr(w) + ", erlaubt sind 0-100 oder "
          + nicht-beurteilbar,
      )
      punkte += w * gewicht / 100.0
    }
  }

  let score = if offen.len() > 0 { none } else { calc.round(punkte, digits: 1) }
  let zone = if score == none {
    "kein_score"
  } else if score < 30 { "gruen" } else if score < 70 { "gelb" } else { "rot" }

  assert(
    erwartung.erwartete_zone == zone,
    message: pfad + ": erwartete_zone ist '" + erwartung.erwartete_zone + "', aus den Merkmalen"
      + " folgt aber '" + zone + "'"
      + if offen.len() > 0 { " (nicht beurteilbar: " + offen.join(", ") + ")" } else {
        " (Score " + repr(score) + ")"
      },
  )

  (score: score, zone: zone, offen: offen)
}

// --- Laden -------------------------------------------------------------

#let lade-nachweis() = {
  let pfad = sys.inputs.at("fall")
  let f = yaml(pfad)

  assert("kundenId" in f, message: "Fall " + pfad + ": kundenId fehlt")
  assert("praxis" in f, message: "Fall " + pfad + ": praxis fehlt")
  assert("patient" in f, message: "Fall " + pfad + ": patient fehlt")
  assert("name" in f.patient, message: "Fall " + pfad + ": patient.name fehlt")
  assert("befund" in f, message: "Fall " + pfad + ": befund fehlt")

  let pr = praxis-von(f.praxis)
  let stil = sys.inputs.at("stil", default: befundstil-je-praxis.at(f.praxis))
  assert(stil in stile, message: "Unbekannter Befundstil: " + stil)

  let befund = f.befund
  assert("datum" in befund, message: "Fall " + pfad + ": befund.datum fehlt")
  let zahnstatus = befund.at("zahnstatus", default: ())
  pruefe-zahnstatus(zahnstatus, "Fall " + pfad)

  let maengel = f.at("maengel", default: (:))
  let seite-fehlt = maengel.at("seite_fehlt", default: none)
  let seiten-gesamt = maengel.at("seiten_gesamt", default: none)
  assert(
    seite-fehlt == none or seiten-gesamt != none,
    message: "Fall " + pfad + ": maengel.seite_fehlt ohne maengel.seiten_gesamt — ohne"
      + " Gesamtzahl steht auf dem Papier nicht, dass etwas fehlt",
  )

  let fragebogen = f.at("fragebogen", default: none)
  if fragebogen != none {
    for (i, antwort) in fragebogen.antworten.enumerate() {
      let wo = "Fall " + pfad + ": fragebogen.antworten[" + str(i) + "]"
      assert("frage" in antwort, message: wo + " ohne frage")
      let typ = antwort.at("typ", default: "ja_nein")
      assert(
        ("ja_nein", "offen").contains(typ),
        message: wo + ": unbekannter typ '" + typ + "', erlaubt sind ja_nein und offen",
      )
      let a = antwort.at("ankreuz", default: none)
      assert(
        a == none or ("ja", "nein").contains(a),
        message: wo + ": ankreuz ist " + repr(a) + ", erlaubt sind ja, nein oder null",
      )
      // Eine offene Frage kann nicht angekreuzt sein. Ohne diese Pruefung
      // stuende auf dem Bogen ein Kreuz, das der Fall gar nicht meint.
      assert(
        typ == "ja_nein" or a == none,
        message: wo + ": offene Frage mit ankreuz — das gibt der Bogen nicht her",
      )
    }
  }

  let erwartung = f.at("erwartung", default: none)
  assert(
    erwartung != none,
    message: "Fall " + pfad + ": erwartung fehlt. Ohne Ground Truth ist der Fall als"
      + " Pruefmaterial wertlos",
  )
  let bewertung = pruefe-erwartung(erwartung, "Fall " + pfad)

  // Zaehne, die nach dem Befund fehlen. Der Wert wird gegen die Akte im
  // Kernsystem gelesen und darf ihr nicht widersprechen; geprueft werden kann
  // das hier nicht, weil kunden.json nicht Teil dieses Generators ist.
  let fehlende = zahnstatus.filter(e => e.zustand == "fehlend").len()

  (
    pfad: pfad,
    kundenId: f.kundenId,
    praxis: pr,
    absender: absender-von(pr),
    stil: stil,
    patient: f.patient,
    befund: (
      datum: datum(befund.datum),
      datum_iso: iso(befund.datum),
      aktenzeichen: befund.at("aktenzeichen", default: none),
      zahnstatus: zahnstatus,
      fehlende_zaehne: fehlende,
      diagnosen: befund.at("diagnosen", default: ()),
      empfehlung: befund.at("empfehlung", default: none),
      hkp_beiliegend: befund.at("hkp_beiliegend", default: false),
      parodontalbefund: befund.at("parodontalbefund", default: none),
    ),
    maengel: (
      seite_fehlt: seite-fehlt,
      seiten_gesamt: seiten-gesamt,
      unleserlich: maengel.at("unleserlich", default: ()),
      veraltet: maengel.at("veraltet", default: false),
    ),
    historie: f.at("historie", default: (eintraege: ())),
    fragebogen: fragebogen,
    // Die Ground Truth reist mit, damit build.sh und Tests sie lesen koennen.
    // In KEIN Dokument gehoert sie hinein: Ein Nachweis, der seine eigene
    // Bewertung mitbringt, pruefte den Facharzt nicht mehr.
    erwartung: (
      merkmale: erwartung.merkmale,
      zone: bewertung.zone,
      score: bewertung.score,
      offen: bewertung.offen,
      hinweis: erwartung.at("hinweis", default: none),
    ),
  )
}
