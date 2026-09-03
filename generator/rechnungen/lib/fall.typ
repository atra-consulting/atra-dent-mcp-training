// Laedt eine Falldatei, prueft sie und stellt die gerechneten Werte bereit.
//
// Die einzige Stelle im Generator, die Falldaten liest und verrechnet. Kein
// Stil rechnet selbst. Deshalb duerfen die fuenf Stile im Aussehen beliebig
// auseinanderlaufen, in den Fakten aber nicht.
//
// Alle Pruefungen sind assert und brechen den Build ab. Ein fehlerhafter Fall
// erzeugt kein PDF, sondern eine Meldung — das ist beabsichtigt und zugleich
// die Formularvalidierung des Frontends, damit es keine zweite, abweichende
// Regelmenge gibt.

#import "/wissen/dokumente/lib/daten.typ": zahl, eur

// --- Stammdaten --------------------------------------------------------

#let praxen-datei = yaml("/generator/rechnungen/data/praxen.yaml")
#let goz-katalog = yaml("/wissen/daten/goz-zuordnung.yaml")

#let stile = ("klassisch", "modern", "verrechnungsstelle", "landpraxis", "klinik")

// Nur art: goz ist ueber die Gebuehrennummer einem Leistungsbereich
// zuzuordnen. Die uebrigen vier Arten liegen ausserhalb der Anlage 1 zur GOZ
// und sind deshalb bewusst nicht aufloesbar — genau die Luecke, die
// wissen/daten/goz-zuordnung.yaml in ihrem Kopf selbst benennt.
#let arten = ("goz", "goae", "material", "labor", "verlangen")

// --- Formatierung ------------------------------------------------------

// YAML liefert Datumsangaben als Zeichenkette in ISO-Form. Der Zweig fuer
// datetime steht defensiv, weil das an der YAML-Bibliothek haengt und nicht
// am Modell.
#let datum(d) = {
  if d == none { return "" }
  let iso = if type(d) == str { d } else { d.display("[year]-[month]-[day]") }
  let teile = iso.split("-")
  if teile.len() == 3 { teile.at(2) + "." + teile.at(1) + "." + teile.at(0) } else { iso }
}

// Steigerungssatz in der Schreibweise der Rechnung: 2,3fach
#let faktor-text(f) = if f == none { "" } else { zahl(f, nachkomma: 1) + "fach" }

// --- Nachschlagen ------------------------------------------------------

#let goz-eintrag(nummer) = goz-katalog.positionen.find(p => p.nummer == nummer)

#let praxis-von(schluessel) = {
  let p = praxen-datei.praxen.find(x => x.schluessel == schluessel)
  assert(
    p != none,
    message: "Unbekannte Praxis: " + schluessel + ". Bekannt sind: "
      + praxen-datei.praxen.map(x => x.schluessel).join(", "),
  )
  assert(
    p.stil in stile,
    message: "Praxis " + schluessel + " nennt unbekannten Stil: " + p.stil,
  )
  p
}

// --- Pruefungen --------------------------------------------------------

#let pruefe-position(p, i) = {
  let wo = "Position " + str(i + 1) + ": "

  assert("art" in p, message: wo + "Feld art fehlt")
  assert(
    p.art in arten,
    message: wo + "unbekannte Art " + p.art + ". Erlaubt: " + arten.join(", "),
  )
  assert("leistung" in p, message: wo + "Feld leistung fehlt")
  assert("betrag" in p, message: wo + "Feld betrag fehlt")
  assert(type(p.betrag) in (int, float), message: wo + "betrag ist keine Zahl")

  if p.art == "goz" {
    assert("nummer" in p, message: wo + "Art goz ohne Feld nummer")
    assert(
      type(p.nummer) == str,
      message: wo + "nummer muss in Anfuehrungszeichen stehen, "
        + "sonst gehen fuehrende Nullen verloren",
    )
    assert(
      goz-eintrag(p.nummer) != none,
      message: wo + "GOZ-Nummer " + p.nummer
        + " steht nicht in wissen/daten/goz-zuordnung.yaml",
    )
  }

  let f = p.at("faktor", default: none)
  if f != none {
    assert(type(f) in (int, float), message: wo + "faktor ist keine Zahl")
    assert(
      f >= 1.0 and f <= 3.5,
      message: wo + "faktor " + str(f) + " liegt ausserhalb 1,0 bis 3,5",
    )
  }
}

// --- Laden -------------------------------------------------------------

#let lade-fall() = {
  let pfad = sys.inputs.at("fall", default: none)
  assert(
    pfad != none,
    message: "Kein Fall angegeben. Aufruf: typst compile --root . "
      + "--input fall=/submissions/rechnungen/cases/<name>.yaml generator/rechnungen/rechnung.typ <ziel>.pdf",
  )

  let f = yaml(pfad)

  for feld in ("rechnungsnummer", "rechnungsdatum", "praxis", "patient", "positionen") {
    assert(feld in f, message: "Fall " + pfad + ": Feld " + feld + " fehlt")
  }
  assert("name" in f.patient, message: "Fall " + pfad + ": patient.name fehlt")
  assert(f.positionen.len() > 0, message: "Fall " + pfad + ": keine Positionen")

  for (i, p) in f.positionen.enumerate() { pruefe-position(p, i) }

  let pr = praxis-von(f.praxis)

  // Der Stil kommt von der Praxis. Die Ueberschreibung ueber --input stil=
  // existiert allein fuer den Pruefmodus von build.sh; keine Falldatei und
  // kein Frontend-Aufruf verwendet sie.
  let stil = sys.inputs.at("stil", default: pr.stil)
  assert(stil in stile, message: "Unbekannter Stil: " + stil)

  let zahlung = f.at("zahlung", default: (:))
  let gezahlt = zahlung.at("bereits_gezahlt", default: 0)

  // Kaufmaennisch auf zwei Nachkommastellen, einmal am Ende — wie im
  // Produktmodell unter wissen/.
  let summe = ps => calc.round(ps.fold(0.0, (s, p) => s + p.betrag), digits: 2)

  let je-art = (:)
  for a in arten {
    je-art.insert(a, summe(f.positionen.filter(p => p.art == a)))
  }
  let gesamt = summe(f.positionen)

  (
    pfad: pfad,
    nummer: f.rechnungsnummer,
    datum: datum(f.rechnungsdatum),
    praxis: pr,
    stil: stil,
    patient: f.patient,
    // Optionaler Block fuer Kennungen, die nur manche Absender fuehren
    // (Kunden- und Vorgangsnummer einer Verrechnungsstelle). Steht er nicht
    // in der Falldatei, bekommt der Stil ein leeres Dictionary und
    // entscheidet selbst, was er anzeigt.
    vorgang: f.at("vorgang", default: (:)),
    // Jede GOZ-Position traegt ihren Leistungsbereich aus derselben Quelle,
    // die auch die Erstattung steuert. Damit kann eine erzeugte Rechnung
    // nicht in Widerspruch zum Produktmodell geraten.
    positionen: f.positionen.map(p => (
      ..p,
      leistungsbereich: if p.art == "goz" {
        goz-eintrag(p.nummer).leistungsbereich
      } else { none },
    )),
    summen: (
      je-art: je-art,
      gesamt: gesamt,
      gezahlt: gezahlt,
      zahlbetrag: calc.round(gesamt - gezahlt, digits: 2),
    ),
    zahlung: (
      frist_tage: zahlung.at("frist_tage", default: 30),
      bereits_gezahlt: gezahlt,
    ),
  )
}
