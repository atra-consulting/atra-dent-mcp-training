// Gemeinsame Textbausteine der Versicherungsbedingungen.
//
// Jeder Paragraph ist eine Funktion, die den Tarifdatensatz entgegennimmt.
// Was in allen Tarifen gleich lautet, steht hier genau einmal; was sich
// unterscheidet, wird aus wissen/daten/tarife.yaml eingesetzt. Die
// Reihenfolge der Paragraphen steht in reihenfolge.typ.
//
// Grundsatz: Das Bedingungswerk beschreibt, WAS gilt. Wie ein Betrag
// rechnerisch ermittelt wird, gehoert in die Beratungsunterlage, nicht in
// die Bedingungen.

#import "daten.typ": *
#import "vorlage.typ": *

// Vertragsrechtliche Konstanten. Sie gehoeren nicht zum Tarifmodell und
// stehen deshalb bewusst hier und nicht in wissen/daten/tarife.yaml: Das
// Modell beschreibt Leistungen, nicht das Schuldverhaeltnis.
#let vertragsrecht = (
  kuendigungsfrist-monate: 3,
  mindestlaufzeit-jahre: 2,
  einreichungsfrist-monate: 24,
  zusagefrist-wochen: 2,
  vorversicherung-mindestmonate: 12,
)

// =======================================================================
// Inhaltsübersicht und Tarifmerkmale
// =======================================================================
#let inhaltsuebersicht(t) = abschnitt("uebersicht", {
  heading(level: 1, numbering: none, outlined: false)[Inhaltsübersicht]
  outline(title: none, depth: 1, indent: 0pt)

  v(1.2em)
  heading(level: 2, outlined: false)[Übersicht über die wesentlichen Tarifmerkmale]

  par(justify: true)[
    Diese Übersicht ist nicht Bestandteil der Bedingungen. Verbindlich sind
    allein die nachfolgenden Paragraphen.
  ]

  tabelle(
    spalten: (1fr, 64mm),
    ausrichtung: (left, left),
    kopf: ("Merkmal", "Regelung"),
    ..eckwerte(t).map(((bezeichnung, wert)) => (bezeichnung, wert)).flatten()
  )
})

// =======================================================================
// § Gegenstand der Versicherung
// =======================================================================
#let p-gegenstand(t) = {
  paragraf[Gegenstand der Versicherung]

  abs[
    Der Versicherer bietet Versicherungsschutz für zahnmedizinisch notwendige
    Heilbehandlung im Tarif #text(weight: "medium")[#t.anzeigename]
    nach Maßgabe dieser Bedingungen und des Versicherungsscheins.
  ]

  abs[
    Die Versicherung ist eine Zusatzversicherung. Sie ergänzt die Leistungen
    der gesetzlichen Krankenversicherung und setzt voraus, dass die
    versicherte Person während der Dauer des Vertrags bei einem Träger der
    gesetzlichen Krankenversicherung versichert ist.
  ]

  abs[
    Der Umfang des Versicherungsschutzes ergibt sich aus dem
    Versicherungsschein, aus diesen Bedingungen und aus dem
    Leistungsverzeichnis der Anlage A.
  ]
}

// =======================================================================
// § Begriffsbestimmungen
// =======================================================================
// Steht bewusst vor allen Paragraphen, die die Begriffe verwenden.
//
// Die Liste ist exportiert: Anlage B fuehrt nur Begriffe, die hier NICHT
// stehen, und verweist fuer die uebrigen hierher. So kann eine Erlaeuterung
// im Register nicht von der Legaldefinition abweichen.
#let kernbegriffe = (
  (
    "Versicherungsfall",
    [die zahnmedizinisch notwendige Heilbehandlung einer versicherten Person
     wegen Krankheit oder Unfallfolgen; er beginnt mit der Heilbehandlung und
     endet, wenn nach medizinischem Befund keine Behandlungsbedürftigkeit
     mehr besteht],
  ),
  (
    "Versicherungsjahr",
    [der Zeitraum von zwölf Monaten ab dem im Versicherungsschein genannten
     Versicherungsbeginn; jeder folgende Zeitraum von zwölf Monaten bildet
     ein weiteres Versicherungsjahr],
  ),
  (
    "Behandler",
    [die die Heilbehandlung durchführende, in der Bundesrepublik Deutschland
     zur Ausübung der Zahnheilkunde approbierte oder zugelassene Person],
  ),
  (
    "Vorleistung",
    [die für dieselbe Behandlung von der gesetzlichen Krankenversicherung
     erbrachte Leistung],
  ),
  ("Erstattung", [der Betrag, den der Versicherer nach diesen Bedingungen auszahlt]),
  (
    "Erstattungsquote",
    [der Prozentsatz, mit dem erstattungsfähige Aufwendungen eines
     Leistungsbereichs erstattet werden],
  ),
  ("Sublimit", [ein Höchstbetrag, der die Erstattung eines Leistungsbereichs begrenzt]),
  (
    "Selbstbehalt",
    [ein Betrag, der von den Erstattungen eines Versicherungsjahres abgezogen wird],
  ),
  (
    "Zahnstaffel",
    [die Begrenzung der Summe aller Erstattungen in den ersten
     Versicherungsjahren],
  ),
  (
    "Jahreshöchstgrenze",
    [die Begrenzung der Erstattung eines einzelnen Versicherungsjahres],
  ),
  (
    "Wartezeit",
    [der Zeitraum ab Versicherungsbeginn, für dessen Dauer kein
     Leistungsanspruch besteht],
  ),
  (
    "Unfall",
    [ein plötzlich von außen auf den Körper wirkendes Ereignis, durch das die
     versicherte Person unfreiwillig eine Gesundheitsschädigung erleidet],
  ),
  (
    "Angeratene Behandlung",
    [eine Behandlung, die ein Behandler vor Vertragsschluss empfohlen hat,
     insbesondere durch Eintrag in der Behandlungsdokumentation],
  ),
  (
    "Begonnene Behandlung",
    [eine Behandlung, bei der mit der ersten Behandlungsmaßnahme begonnen
     wurde],
  ),
)

#let p-begriffe(t) = {
  paragraf[Begriffsbestimmungen]

  abs[
    Die nachstehenden Begriffe haben in diesen Bedingungen die folgende
    Bedeutung:

    #tabelle(
      spalten: (46mm, 1fr),
      ausrichtung: (left, left),
      bindend: false,
      kopf: ("Begriff", "Bedeutung"),
      ..kernbegriffe.map(((begriff, bedeutung)) => (begriff, bedeutung)).flatten()
    )
  ]

  abs[
    Alle in diesen Bedingungen genannten Jahresgrenzen beziehen sich auf das
    Versicherungsjahr, nicht auf das Kalenderjahr.
  ]

  abs[
    Personenbezogene Bezeichnungen in diesen Bedingungen gelten für alle
    Geschlechter.
  ]
}

// =======================================================================
// § Versicherungsfähigkeit
// =======================================================================
#let p-versicherungsfaehigkeit(t) = {
  paragraf[Versicherungsfähigkeit]

  abs[
    Versicherungsfähig sind natürliche Personen mit gewöhnlichem Aufenthalt
    in der Bundesrepublik Deutschland, die bei einem Träger der gesetzlichen
    Krankenversicherung versichert sind.
  ]

  abs[
    Der Vertrag kann geschlossen werden, wenn die zu versichernde Person bei
    Versicherungsbeginn das #(t.eintrittsalter.von). Lebensjahr vollendet und
    das #(t.eintrittsalter.bis + 1). Lebensjahr noch nicht vollendet hat
    (#begriff[Eintrittsalter]). Maßgeblich ist das Alter am Tag des
    Versicherungsbeginns.
  ]

  abs[
    Entfällt eine Voraussetzung des Absatzes 1 während der Vertragsdauer,
    endet die Versicherung zum Ende des Monats, in dem der Versicherer davon
    Kenntnis erlangt. Der Versicherungsnehmer hat den Wegfall unverzüglich
    anzuzeigen.
  ]
}

// =======================================================================
// § Beginn des Versicherungsschutzes und Wartezeiten
// =======================================================================
#let p-wartezeiten(t) = {
  paragraf[Beginn des Versicherungsschutzes und Wartezeiten]

  let kfo = leistungen(t).KFO
  let vorversicherung = t.at("wartezeit_entfaellt_bei_vorversicherung", default: false)

  abs[
    Der Versicherungsschutz beginnt mit dem im Versicherungsschein genannten
    Zeitpunkt, jedoch nicht vor Ablauf der Wartezeiten und nicht vor Zugang
    der Annahmeerklärung.
  ]

  abs[
    Es gelten die folgenden Wartezeiten ab Versicherungsbeginn:

    #tabelle(
      spalten: (1fr, 34mm, 44mm),
      ausrichtung: (left, right, left),
      kopf: ("Geltung", "Wartezeit", "Entfällt"),

      [alle Leistungsbereiche],
      monate(t.wartezeit_monate),
      if vorversicherung [bei Vorversicherung nach Absatz 3] else [—],

      [Kieferorthopädie],
      if kfo.versichert and "wartezeit_monate" in kfo {
        monate(kfo.wartezeit_monate)
      } else [entfällt],
      [—],

      [unfallbedingte Behandlung],
      [keine],
      [—],
    )
  ]

  if vorversicherung {
    abs[
      Die allgemeine Wartezeit entfällt, wenn die versicherte Person
      unmittelbar vor Versicherungsbeginn ununterbrochen mindestens
      #monaten(vertragsrecht.vorversicherung-mindestmonate) in einer
      Zahnzusatzversicherung versichert war und dies dem Versicherer
      innerhalb von #monaten(vertragsrecht.kuendigungsfrist-monate) nach
      Versicherungsbeginn in Textform nachweist. Für die Wartezeit der
      Kieferorthopädie gilt dies nicht.
    ]
  }

  abs[
    Für Aufwendungen aufgrund von Behandlungsmaßnahmen, die vor Ablauf der
    einschlägigen Wartezeit durchgeführt werden, besteht kein
    Leistungsanspruch.
  ]

  abs[
    Die Wartezeiten entfallen für Behandlungen, die infolge eines nach
    Versicherungsbeginn eingetretenen Unfalls notwendig werden.
  ]
}

// =======================================================================
// § Umfang des Versicherungsschutzes
// =======================================================================
#let p-umfang(t) = {
  paragraf[Umfang des Versicherungsschutzes]

  let l = leistungen(t)
  let versichert = bereich-schluessel.filter(k => l.at(k).versichert)
  let nicht-versichert = bereich-schluessel.filter(k => not l.at(k).versichert)

  abs[
    Der Versicherer erstattet erstattungsfähige Aufwendungen für
    zahnmedizinisch notwendige Heilbehandlung in Höhe der für den jeweiligen
    Leistungsbereich genannten Erstattungsquote, höchstens jedoch bis zum
    dort genannten Sublimit und nur unter den dort genannten weiteren
    Voraussetzungen.
  ]

  abs[
    Es gelten die folgenden Erstattungsquoten und Begrenzungen:

    #tabelle(
      spalten: (13mm, 1fr, 17mm, 52mm),
      ausrichtung: (left, left, right, left),
      kopf: ("Kürzel", "Leistungsbereich", "Quote", "Begrenzung"),
      ..versichert
        .map(k => {
          let e = l.at(k)
          let g = begrenzungen(e)
          (
            kuerzel(k),
            bereich(k).name,
            prozent(e.quote),
            if g.len() == 0 { text(fill: dunkelgrau)[keine] } else { g.join("; ") },
          )
        })
        .flatten()
    )
  ]

  abs[
    Die Erstattungsquoten verstehen sich einschließlich der Vorleistung. Die
    Vorleistung wird auf die Erstattung angerechnet.
  ]

  if nicht-versichert.len() > 0 {
    abs[
      In diesem Tarif sind Aufwendungen für die folgenden Leistungsbereiche
      nicht versichert:

      #buchstaben(
        ..nicht-versichert.map(k => [#bereich(k).name (#kuerzel(k))]),
      )
      Für diese Aufwendungen besteht kein Leistungsanspruch; sie bleiben bei
      der Ermittlung der erstattungsfähigen Aufwendungen unberücksichtigt.
    ]
  }

  abs[
    Erstattungsfähig sind Aufwendungen, die nach der Gebührenordnung für
    Zahnärzte oder dem einheitlichen Bewertungsmaßstab für zahnärztliche
    Leistungen berechnet werden dürfen und die zahnmedizinisch notwendig
    sind. Aufwendungen, die das nach diesen Maßstäben übliche Maß
    übersteigen, kann der Versicherer auf den angemessenen Betrag herabsetzen.
  ]

  abs[
    Zu den erstattungsfähigen Aufwendungen gehören auch die Kosten für
    Material und zahntechnische Laborleistungen, die nach der
    Gebührenordnung für Zahnärzte gesondert berechnet werden dürfen. Sie
    werden dem Leistungsbereich der Behandlung zugerechnet, für die sie
    angefallen sind, und teilen deren Erstattungsquote und Begrenzung. Lässt
    sich die zugehörige Behandlung nicht feststellen, besteht für diese
    Aufwendungen kein Leistungsanspruch.
  ]

  abs[
    Die Zuordnung einer Rechnungsposition zu einem Leistungsbereich richtet
    sich nach dem Leistungsverzeichnis der Anlage A. Grenzt eine
    Rechnungsposition an zwei Leistungsbereiche, ist die dort genannte
    Abgrenzung maßgeblich.
  ]

  abs[
    Erstattungsbeträge werden kaufmännisch auf zwei Nachkommastellen
    gerundet. Ergibt sich rechnerisch ein Betrag kleiner als null, gilt der
    Betrag als null.
  ]
}

// =======================================================================
// § Zahnstaffel
// =======================================================================
#let p-zahnstaffel(t) = {
  paragraf[Begrenzung der Erstattung in den ersten Versicherungsjahren]

  let s = staffel-von(t)
  let begrenzte = s.stufen.filter(x => x.betrag != none)

  abs[
    In den ersten #vjahren(s.dauer_jahre) ab Versicherungsbeginn ist die
    Summe aller Erstattungen begrenzt (Zahnstaffel). Die Begrenzung wirkt
    kumuliert: Maßgeblich ist die Summe aller seit Versicherungsbeginn
    ausgezahlten Beträge, nicht der Betrag eines einzelnen
    Versicherungsjahres.
  ]

  abs[
    Es gelten die folgenden Höchstbeträge:

    #tabelle(
      spalten: (1fr, 52mm),
      ausrichtung: (left, right),
      kopf: ("Zeitraum seit Versicherungsbeginn", "Erstattung insgesamt höchstens"),
      ..begrenzte
        .map(x => (
          if x.bis_jahr == 1 { [im ersten Versicherungsjahr] } else {
            [in den ersten #vjahren(x.bis_jahr)]
          },
          eur0(x.betrag),
        ))
        .flatten(),
      [ab dem #(s.dauer_jahre + 1). Versicherungsjahr],
      [unbegrenzt],
    )
  ]

  abs[
    Auf die Zahnstaffel wird der tatsächlich ausgezahlte Betrag angerechnet.
    Beträge, die wegen des Selbstbehalts oder eines Sublimits nicht
    ausgezahlt wurden, mindern die Zahnstaffel nicht.
  ]

  abs[
    Die Zahnstaffel entfällt für Behandlungen, die infolge eines nach
    Versicherungsbeginn eingetretenen Unfalls notwendig werden.
  ]

  if t.at("jahreshoechstgrenze", default: none) != none {
    abs[
      Nach Ablauf der Zahnstaffel ist die Erstattung auf
      #eur0(t.jahreshoechstgrenze) je Versicherungsjahr begrenzt
      (Jahreshöchstgrenze). Für unfallbedingte Behandlungen gilt die
      Jahreshöchstgrenze unverändert.
    ]
  } else {
    abs[
      Nach Ablauf der Zahnstaffel besteht keine betragsmäßige Begrenzung der
      Erstattung je Versicherungsjahr.
    ]
  }
}

// =======================================================================
// § Selbstbehalt
// =======================================================================
// `variante` nimmt einen Tarifdatensatz auf, der sich vom Grundtarif nur im
// Selbstbehalt und im Beitrag unterscheidet.
#let p-selbstbehalt(t, variante: none) = {
  paragraf[Selbstbehalt]

  if t.selbstbehalt == 0 {
    abs[
      In diesem Tarif wird kein Selbstbehalt erhoben. Die Erstattung wird
      ungekürzt ausgezahlt, soweit die Zahnstaffel und die
      Jahreshöchstgrenze nach #verweis("zahnstaffel") nicht entgegenstehen.
    ]
  } else {
    abs[
      Von den Erstattungen eines Versicherungsjahres wird ein Selbstbehalt
      von #eur0(t.selbstbehalt) abgezogen.
    ]

    abs[
      Der Selbstbehalt wird einmal je Versicherungsjahr erhoben und gilt für
      alle Leistungsbereiche gemeinsam. Er wird von der ersten Erstattung des
      Versicherungsjahres und, soweit diese ihn nicht ausschöpft, von den
      folgenden Erstattungen abgezogen, bis er verbraucht ist.
    ]

    abs[
      Ein in einem Versicherungsjahr nicht verbrauchter Selbstbehalt wird
      nicht auf das folgende Versicherungsjahr übertragen.
    ]

    abs[
      Der Selbstbehalt wird abgezogen, bevor die Begrenzung durch die
      Zahnstaffel nach #verweis("zahnstaffel") angewendet wird.
    ]
  }

  if variante != none {
    abs[
      Ist im Versicherungsschein die Variante
      #text(weight: "medium")[#variante.anzeigename] vereinbart, wird
      abweichend von Absatz 1 von den Erstattungen eines Versicherungsjahres
      ein Selbstbehalt von #eur0(variante.selbstbehalt) abgezogen. Der
      Beitrag ergibt sich aus dem Versicherungsschein.
    ]

    abs[
      Für den Selbstbehalt der Variante gilt: Er wird einmal je
      Versicherungsjahr für alle Leistungsbereiche gemeinsam erhoben, von den
      Erstattungen des Versicherungsjahres abgezogen, bis er verbraucht ist,
      und nicht auf das folgende Versicherungsjahr übertragen. Der Abzug
      erfolgt, bevor die Begrenzung durch die Zahnstaffel nach
      #verweis("zahnstaffel") angewendet wird.
    ]

    abs[
      Im Übrigen gelten für die Variante diese Bedingungen unverändert,
      insbesondere der Leistungsumfang nach #verweis("umfang") und die
      Zahnstaffel nach #verweis("zahnstaffel").
    ]
  }
}

// =======================================================================
// § Einschränkung der Leistungspflicht
// =======================================================================
#let p-ausschluesse(t) = {
  paragraf[Einschränkung der Leistungspflicht]

  abs[
    Keine Leistungspflicht besteht für:

    #tabelle(
      spalten: (9mm, 1fr, 46mm),
      ausrichtung: (left, left, left),
      kopf: ("", "Ausgeschlossen sind", "Abweichung in diesem Tarif"),
      ..modell
        .ausschluesse
        .enumerate()
        .map(((i, a)) => {
          let hat-ausnahme = ("ausnahme" in a) and (t.schluessel in a.ausnahme.tarife)
          (
            text(weight: "medium", numbering("a)", i + 1)),
            a.text,
            if hat-ausnahme {
              [Der Versicherer erstattet #a.ausnahme.text.]
            } else { text(fill: dunkelgrau)[keine] },
          )
        })
        .flatten()
    )
  ]

  abs[
    Der Versicherer kann die Leistung verweigern, soweit der
    Versicherungsnehmer eine Obliegenheit nach
    #verweis("obliegenheiten", absatz: 2) oder
    #verweis("obliegenheiten", absatz: 4) vorsätzlich verletzt hat. Bei grob
    fahrlässiger Verletzung ist der Versicherer berechtigt, die Leistung in
    einem der Schwere des Verschuldens entsprechenden Verhältnis zu kürzen.
  ]
}

// =======================================================================
// § Beitrag
// =======================================================================
#let p-beitrag(t) = {
  paragraf[Beitrag]

  abs[
    Der Beitrag ergibt sich aus dem Versicherungsschein. Seine Höhe richtet
    sich nach dem vereinbarten Tarif und dem Eintrittsalter der versicherten
    Person; sie ist nicht Bestandteil dieser Bedingungen.
  ]

  abs[
    Der Beitrag ist ein Monatsbeitrag und jeweils zu Monatsbeginn im Voraus
    fällig. Der erste Beitrag ist unverzüglich nach Zugang des
    Versicherungsscheins zu zahlen.
  ]

}

// =======================================================================
// § Obliegenheiten und Auszahlung der Erstattung
// =======================================================================
#let p-obliegenheiten(t) = {
  paragraf[Obliegenheiten und Auszahlung der Erstattung]

  abs[
    Die Erstattung wird fällig, wenn dem Versicherer die Rechnung des
    Behandlers und, soweit eine Vorleistung erbracht wurde, die Abrechnung
    der gesetzlichen Krankenversicherung vorliegen.
  ]

  abs[
    Rechnungen sind innerhalb von
    #monaten(vertragsrecht.einreichungsfrist-monate) nach Abschluss der
    Behandlung einzureichen. Die Rechnung muss die einzelnen Leistungen, die
    behandelten Zähne und die berechneten Gebührenpositionen ausweisen. Nach
    Ablauf der Frist eingereichte Rechnungen begründen keinen
    Leistungsanspruch.
  ]

  let l = leistungen(t)
  let planpflichtig = ("ZE", "IMP", "KFO").filter(k => l.at(k).versichert)

  abs[
    Vor Beginn einer Behandlung in den Leistungsbereichen
    #planpflichtig.map(k => bereich(k).name).join(", ", last: " oder ")
    kann ein Heil- und Kostenplan eingereicht werden. Der Versicherer teilt
    innerhalb von #zahlwort(vertragsrecht.zusagefrist-wochen) Wochen nach
    Zugang in Textform mit, in welcher Höhe er auf der Grundlage des Plans
    leistet. An diese Mitteilung ist er gebunden, soweit die Behandlung dem
    Plan entspricht.
  ]

  abs[
    Der Versicherungsnehmer hat auf Verlangen die zur Prüfung der
    Leistungspflicht erforderlichen Auskünfte zu erteilen. Die versicherte
    Person hat den Behandler insoweit von der Schweigepflicht zu entbinden.
  ]

  abs[
    Die Auszahlung erfolgt in Euro auf ein vom Versicherungsnehmer benanntes
    Konto. Ansprüche aus dem Vertrag können ohne Zustimmung des Versicherers
    weder abgetreten noch verpfändet werden.
  ]
}

// =======================================================================
// § Dauer und Ende des Versicherungsverhältnisses
// =======================================================================
#let p-vertrag(t) = {
  paragraf[Dauer und Ende des Versicherungsverhältnisses]

  abs[
    Der Vertrag wird auf unbestimmte Zeit geschlossen. Die Mindestlaufzeit
    beträgt #jahre(vertragsrecht.mindestlaufzeit-jahre) ab
    Versicherungsbeginn.
  ]

  abs[
    Nach Ablauf der Mindestlaufzeit kann der Versicherungsnehmer den Vertrag
    zum Ende eines jeden Versicherungsjahres mit einer Frist von
    #monaten(vertragsrecht.kuendigungsfrist-monate) kündigen. Das Recht zur
    Kündigung aus wichtigem Grund bleibt unberührt.
  ]

  abs[
    Der Versicherer verzichtet auf das ordentliche Kündigungsrecht.
  ]

  abs[
    Der Vertrag endet außerdem mit dem Tod der versicherten Person sowie in
    den Fällen des #verweis("versicherungsfaehigkeit", absatz: 3).
  ]

  abs[
    Endet der Vertrag im Lauf eines Versicherungsjahres, wird der auf die
    nicht abgelaufene Vertragszeit entfallende Teil des Beitrags erstattet.
    Bereits erbrachte Leistungen bleiben unberührt.
  ]
}

// =======================================================================
// § Schlussbestimmungen
// =======================================================================
#let p-schluss(t) = {
  paragraf[Schlussbestimmungen]

  abs[
    Willenserklärungen und Anzeigen gegenüber dem Versicherer bedürfen der
    Textform.
  ]

  abs[
    Für den Vertrag gilt deutsches Recht, soweit zwingende Vorschriften nicht
    etwas anderes bestimmen.
  ]

  abs[
    Ist eine Bestimmung dieser Bedingungen unwirksam, bleibt die Wirksamkeit
    der übrigen Bestimmungen unberührt. An die Stelle der unwirksamen
    Bestimmung tritt die gesetzliche Regelung.
  ]
}

// =======================================================================
// Anlage A — Leistungsverzeichnis
// =======================================================================
// Der Seitenumbruch und das Auftaktbild stehen bewusst vor dem `abschnitt`:
// Beides gehoert zur Seitengestaltung, nicht zum Leistungsverzeichnis. Was
// zitiert wird, faengt bei der Ueberschrift an.
#let anlage-leistungsverzeichnis(t) = {
  pagebreak()

  bildplatz(
    "/wissen/dokumente/bilder/avb-leistung.png",
    "Bild 2 — Auftakt zum Leistungsverzeichnis",
    verhaeltnis: 3.1,
  )
  v(1.0em)

  abschnitt("anlage-a", {
    heading(level: 1, numbering: none)[Anlage A — Leistungsverzeichnis]

    par(justify: true)[
      Das Leistungsverzeichnis bestimmt, welche Behandlungen einem
      Leistungsbereich zugeordnet werden. Es ist Bestandteil dieser
      Bedingungen. Für die Zuordnung gilt
      #verweis("umfang", absatz: if bereich-schluessel.any(k => (
        not leistungen(t).at(k).versichert
      )) { 6 } else { 5 }).
    ]

    let l = leistungen(t)

    tabelle(
      spalten: (13mm, 34mm, 1fr, 15mm),
      ausrichtung: (left, left, left, right),
      klein: true,
      bindend: false,
      kopf: ("Kürzel", "Leistungsbereich", "Umfasst und Abgrenzung", "Quote"),
      ..bereich-schluessel
        .map(k => {
          let b = bereich(k)
          let e = l.at(k)
          (
            kuerzel(k),
            text(weight: "medium", b.name),
            fliesstext(b.beschreibung),
            if e.versichert { text(weight: "medium", prozent(e.quote)) } else {
              text(fill: dunkelgrau, style: "italic")[nicht \ versichert]
            },
          )
        })
        .flatten()
    )
  })
}
