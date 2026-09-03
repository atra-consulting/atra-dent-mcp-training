// Anlage C — Allgemeine Geschäftsbedingungen.
//
// Die AGB stehen neben dem Bedingungswerk: Sie regeln nicht das
// Leistungsversprechen, sondern die Abwicklung des Geschaeftsverkehrs
// zwischen Versicherer und Versicherungsnehmer. Deshalb zaehlen sie in
// Ziffern und nicht in Paragraphen, und deshalb verweisen sie auf die
// Paragraphen des Werks, statt deren Regelungen zu wiederholen.
//
// Aufbau: `ziffer(...)` setzt die Ueberschriften einer eigenen, bei 1
// beginnenden Zaehlung. Die Absatzzaehlung `abs(...)` aus vorlage.typ wird
// dabei je Ziffer zurueckgesetzt, damit "(1) (2) (3)" wie im Werk lesbar
// bleibt.

#import "daten.typ": *
#import "vorlage.typ": *

// --- Geschaeftsrechtliche Konstanten -----------------------------------
// Wie in enterprise.typ: Fristen des Schuldverhaeltnisses, keine
// Tarifgroessen. Sie gehoeren nicht in wissen/daten/tarife.yaml.
#let geschaeftsbedingungen = (
  annahmefrist-wochen: 2,
  lastschrift-vorabinfo-tage: 5,
  aenderungsankuendigung-wochen: 6,
  aenderungswiderspruch-wochen: 6,
  vollmachtsnachweis-wochen: 2,
)

#let wochen-d-agb(n) = if n == 1 { "einer Woche" } else { zahlwort(n) + " Wochen" }
#let tagen-agb(n) = if n == 1 { "einem Tag" } else { zahlwort(n) + " Tagen" }

// --- Zaehlung der Ziffern ----------------------------------------------

#let agb-ziffer-nr = counter("agb-ziffer")

// Eine Ziffer der Geschaeftsbedingungen. Bewusst Ebene 2 und nicht Ebene 1:
// Ebene 1 traegt im Werk das Paragraphenzeichen und die volle Linie, und
// genau davon soll sich eine Ziffer der Geschaeftsbedingungen unterscheiden.
// Die Ebene-1-Regel der Vorlage setzt ausserdem die Absatzzaehlung zurueck —
// das muss hier von Hand geschehen.
#let ziffer(titel) = {
  agb-ziffer-nr.step()
  absatz-nr.update(0)
  heading(level: 2, numbering: none, outlined: false, context [
    #agb-ziffer-nr.display(). #h(0.35em) #titel
  ])
}

// =======================================================================
// Anlage C — Allgemeine Geschäftsbedingungen
// =======================================================================
#let anlage-agb(t) = {
  pagebreak()

  // Wie die uebrigen Anlagen eine eigene Struktureinheit. Die Ziffern
  // darin bleiben Ueberschriften der zweiten Ebene und teilen den
  // Abschnitt nicht weiter auf — die Geschaeftsbedingungen wirken als
  // Ganzes, und genau so werden sie zitiert.
  abschnitt("anlage-c", {
    heading(level: 1, numbering: none)[Anlage C — Allgemeine Geschäftsbedingungen]

    agb-ziffer-nr.update(0)

    par(justify: true)[
      Diese Geschäftsbedingungen regeln die Abwicklung des Geschäftsverkehrs
      zwischen dem Versicherer und dem Versicherungsnehmer. Sie sind Bestandteil
      des Vertrags und treten neben die Allgemeinen Versicherungsbedingungen.
      Widersprechen sich eine Ziffer dieser Geschäftsbedingungen und ein
      Paragraph der Versicherungsbedingungen, geht der Paragraph vor.
    ]

    // ---------------------------------------------------------------
    ziffer[Geltungsbereich]

    abs[
      Diese Geschäftsbedingungen gelten für alle Verträge über die
      Zahnzusatzversicherung des Versicherers sowie für die Anbahnung solcher
      Verträge. Sie gelten gegenüber dem Versicherungsnehmer; gegenüber der
      versicherten Person gelten sie, soweit sie deren Mitwirkung betreffen.
    ]

    abs[
      Die Allgemeinen Versicherungsbedingungen einschließlich des
      Leistungsverzeichnisses der Anlage A und der Begriffsbestimmungen der
      Anlage B gehen diesen Geschäftsbedingungen vor. Diese
      Geschäftsbedingungen regeln nicht den Umfang des Versicherungsschutzes;
      dafür sind allein #verweis("umfang") bis #verweis("ausland") maßgeblich.
    ]

    abs[
      Abweichende Bedingungen des Versicherungsnehmers werden nicht
      Vertragsbestandteil, auch wenn der Versicherer ihnen nicht ausdrücklich
      widerspricht.
    ]

    // ---------------------------------------------------------------
    ziffer[Zustandekommen des Vertrags]

    abs[
      Die Darstellung der Tarife in Werbe- und Beratungsunterlagen ist kein
      Angebot, sondern eine Aufforderung zur Abgabe eines Antrags. Der
      Versicherungsnehmer gibt mit dem Antrag seine Vertragserklärung ab und
      ist daran für die Dauer von
      #wochen-d-agb(geschaeftsbedingungen.annahmefrist-wochen) gebunden.
    ]

    abs[
      Der Vertrag kommt zustande, wenn der Versicherer den Antrag annimmt. Die
      Annahme erfolgt durch Übersendung des Versicherungsscheins oder durch
      eine gesonderte Erklärung in Textform. Weicht der Versicherungsschein vom
      Antrag ab, gilt die Abweichung als genehmigt, wenn der
      Versicherungsnehmer ihr nicht innerhalb von
      #wochen-d-agb(geschaeftsbedingungen.annahmefrist-wochen) nach Zugang in
      Textform widerspricht; auf diese Wirkung wird bei Übersendung
      hingewiesen.
    ]

    abs[
      Der Versicherer ist nicht verpflichtet, einen Antrag anzunehmen. Er kann
      die Annahme insbesondere von der Beantwortung der Fragen nach
      #verweis("anzeigepflicht", absatz: 1), von einem Risikozuschlag oder von einem
      Leistungsausschluss abhängig machen; nimmt er den Antrag nicht an,
      schuldet er keine Begründung.
    ]

    abs[
      Der Beginn des Versicherungsschutzes richtet sich nach #verweis("wartezeiten"), das
      Widerrufsrecht des Versicherungsnehmers nach #verweis("widerruf"). Der Vertrag
      wird vom Versicherer gespeichert; der Versicherungsnehmer kann jederzeit
      eine Abschrift in Textform verlangen.
    ]

    // ---------------------------------------------------------------
    ziffer[Vertragssprache]

    abs[
      Die Vertragssprache ist Deutsch. In deutscher Sprache werden der Antrag,
      der Versicherungsschein, die Versicherungsbedingungen mit ihren Anlagen
      und die gesamte Kommunikation während der Vertragslaufzeit abgefasst.
    ]

    abs[
      Stellt der Versicherer Unterlagen zusätzlich in einer anderen Sprache zur
      Verfügung, geschieht dies allein zur Erleichterung des Verständnisses.
      Bei Abweichungen ist die deutsche Fassung maßgeblich.
    ]

    abs[
      Rechnungen und Nachweise sind in deutscher Sprache einzureichen. Der
      Versicherer kann für fremdsprachige Unterlagen eine Übersetzung auf
      Kosten des Versicherungsnehmers verlangen.
    ]

    // ---------------------------------------------------------------
    ziffer[Kommunikationswege und elektronische Kommunikation]

    abs[
      Erklärungen gegenüber dem Versicherer bedürfen der Textform
      (#verweis("schluss", absatz: 1)); für Mitteilungen und Anschriftenänderungen
      gilt #verweis("mitteilungen"). Als Zugangswege stehen der Postweg, die vom
      Versicherer benannte elektronische Adresse und, soweit eingerichtet, ein
      persönliches elektronisches Postfach zur Verfügung.
    ]

    abs[
      Versicherer und Versicherungsnehmer können vereinbaren, dass Unterlagen
      ausschließlich elektronisch bereitgestellt werden. Der
      Versicherungsnehmer kann diese Vereinbarung jederzeit für die Zukunft in
      Textform widerrufen und die Zusendung in Papierform verlangen; hierfür
      wird kein Entgelt erhoben.
    ]

    abs[
      Wird ein elektronisches Postfach genutzt, gilt eine dort eingestellte
      Erklärung als zugegangen, sobald der Versicherungsnehmer über die
      Einstellung an der von ihm benannten elektronischen Adresse benachrichtigt
      worden ist und er das Postfach unter gewöhnlichen Umständen abrufen kann.
      Der Versicherungsnehmer hat das Postfach in angemessenen Abständen
      abzurufen.
    ]

    abs[
      Für die Kündigung des Vertrags, für die Mahnung nach
      #verweis("zahlungsverzug", absatz: 3) und für den Widerruf nach #verweis("widerruf") genügt die
      elektronische Übermittlung nur, wenn sie den Anforderungen der Textform
      genügt und die erklärende Person erkennen lässt.
    ]

    abs[
      Der Versicherer weist darauf hin, dass unverschlüsselte elektronische
      Nachrichten auf dem Übertragungsweg von Dritten zur Kenntnis genommen
      werden können. Für die Übermittlung von Gesundheitsdaten stellt der
      Versicherer einen gesicherten Weg zur Verfügung; #verweis("datenverarbeitung") bleibt
      unberührt.
    ]

    // ---------------------------------------------------------------
    ziffer[Zahlungsverkehr und Lastschrift]

    abs[
      Der Beitrag ist nach #verweis("beitrag", absatz: 2) zu Monatsbeginn im
      Voraus fällig. Erfüllungsort für die
      Beitragszahlung ist der Sitz des Versicherers. Die Zahlung erfolgt
      bargeldlos in Euro.
    ]

    abs[
      Erteilt der Versicherungsnehmer ein SEPA-Lastschriftmandat, gilt die
      Beitragszahlung als rechtzeitig, wenn der Beitrag zum Fälligkeitstag
      eingezogen werden kann und der Versicherungsnehmer einer berechtigten
      Einziehung nicht widerspricht. Der Versicherer kündigt den Einzug
      spätestens #tagen-agb(geschaeftsbedingungen.lastschrift-vorabinfo-tage)
      vor dem Fälligkeitstag an; die Ankündigung kann mit dem
      Versicherungsschein oder der Beitragsrechnung verbunden werden.
    ]

    abs[
      Konnte der Beitrag aus Gründen, die der Versicherungsnehmer zu vertreten
      hat, nicht eingezogen werden, oder widerspricht er einer berechtigten
      Einziehung, kann der Versicherer die von der kontoführenden Stelle
      berechneten Rücklastschriftkosten in tatsächlicher Höhe verlangen. Ein
      pauschaliertes Entgelt wird nicht erhoben. Der Nachweis eines geringeren
      Schadens bleibt dem Versicherungsnehmer unbenommen.
    ]

    abs[
      Scheitert der Einzug wiederholt, kann der Versicherer den
      Versicherungsnehmer auffordern, den Beitrag künftig durch Überweisung zu
      zahlen. Die Folgen verspäteter Zahlung richten sich nach #verweis("zahlungsverzug").
    ]

    abs[
      Erstattungen zahlt der Versicherer auf das nach #verweis("obliegenheiten", absatz: 5)
      benannte Konto. Der Versicherungsnehmer hat eine Änderung der
      Bankverbindung nach #verweis("mitteilungen", absatz: 2) anzuzeigen; Zahlungen auf ein
      nicht widerrufenes Konto wirken befreiend.
    ]

    // ---------------------------------------------------------------
    ziffer[Aufrechnung und Zurückbehaltung]

    abs[
      Der Versicherungsnehmer kann gegen Forderungen des Versicherers nur mit
      unbestrittenen oder rechtskräftig festgestellten Gegenforderungen
      aufrechnen. Dasselbe gilt für die Ausübung eines Zurückbehaltungsrechts;
      es steht ihm nur zu, soweit sein Gegenanspruch auf demselben
      Vertragsverhältnis beruht.
    ]

    abs[
      Der Versicherer kann mit fälligen Beitragsforderungen gegen
      Erstattungsansprüche aufrechnen. Er kann ferner Erstattungen, die er ohne
      Rechtsgrund geleistet hat, mit künftigen Erstattungen verrechnen;
      #verweis("zahlungsverzug", absatz: 9) bleibt unberührt.
    ]

    abs[
      Die Aufrechnung des Versicherers ist ausgeschlossen, soweit der
      Erstattungsanspruch der Zahlung einer bereits beglichenen
      Behandlungsrechnung dient und die Aufrechnung die versicherte Person
      unbillig belasten würde.
    ]

    // ---------------------------------------------------------------
    ziffer[Abtretungsverbot]

    abs[
      Ansprüche aus dem Vertrag können nach #verweis("obliegenheiten", absatz: 5) ohne
      Zustimmung des Versicherers weder abgetreten noch verpfändet werden.
      Diese Ziffer erläutert das Verbot; sie erweitert es nicht.
    ]

    abs[
      Der Versicherer erteilt die Zustimmung insbesondere dann, wenn der
      Erstattungsanspruch an den behandelnden Zahnarzt oder an ein von ihm
      beauftragtes Abrechnungsunternehmen abgetreten werden soll und die
      Abtretung der Höhe nach bestimmt ist. Die Zustimmung ist in Textform zu
      erteilen.
    ]

    abs[
      Das Verbot gilt nicht für den Übergang von Ansprüchen kraft Gesetzes und
      nicht für den Erbfall.
    ]

    // ---------------------------------------------------------------
    ziffer[Vollmacht von Vermittlern]

    abs[
      Ein Versicherungsvermittler ist bevollmächtigt, den Antrag des
      Versicherungsnehmers, dessen Erklärungen zur Vertragsanbahnung und die
      Antworten auf die Fragen nach #verweis("anzeigepflicht", absatz: 1) entgegenzunehmen
      und an den Versicherer weiterzuleiten. Was der Versicherungsnehmer dem
      Vermittler mündlich mitteilt, gilt insoweit als dem Versicherer
      mitgeteilt.
    ]

    abs[
      Darüber hinaus ist der Vermittler nicht bevollmächtigt. Er kann
      insbesondere weder Erklärungen zum Umfang des Versicherungsschutzes
      abgeben, die von #verweis("umfang") abweichen, noch Beiträge stunden, noch
      Kündigungen oder Widerrufe mit Wirkung für den Versicherer
      entgegennehmen; #verweis("mitteilungen", absatz: 7) bleibt unberührt.
    ]

    abs[
      Eine über Absatz 1 hinausgehende Vollmacht wirkt gegenüber dem
      Versicherungsnehmer nur, wenn sie ihm in Textform nachgewiesen wird. Der
      Versicherungsnehmer kann einen solchen Nachweis verlangen; der
      Versicherer erteilt ihn innerhalb von
      #wochen-d-agb(geschaeftsbedingungen.vollmachtsnachweis-wochen).
    ]

    abs[
      Zur Entgegennahme von Beiträgen ist der Vermittler nur berechtigt, wenn
      er eine vom Versicherer ausgestellte Inkassovollmacht in Textform
      vorlegt.
    ]

    // ---------------------------------------------------------------
    ziffer[Vergütung von Vermittlern]

    abs[
      Die Vergütung des Versicherungsvermittlers zahlt der Versicherer. Sie ist
      im Beitrag nach #verweis("beitrag") enthalten. Der Versicherungsnehmer schuldet
      dem Vermittler für die Vermittlung dieses Vertrags kein gesondertes
      Entgelt.
    ]

    abs[
      Eine gesonderte Vergütungsvereinbarung zwischen dem Versicherungsnehmer
      und einem Versicherungsberater oder Makler ist nicht Bestandteil dieses
      Vertrags. Der Versicherer ist an sie nicht gebunden; ihre Wirksamkeit
      berührt den Versicherungsvertrag nicht.
    ]

    abs[
      Endet der Vertrag vorzeitig, entsteht daraus kein Anspruch des
      Versicherungsnehmers auf Erstattung von Vergütungsanteilen. Der Anspruch
      auf anteilige Beitragsrückerstattung nach #verweis("vertrag", absatz: 5) bleibt
      unberührt.
    ]

    abs[
      Der Versicherer legt auf Verlangen des Versicherungsnehmers in Textform
      dar, in welcher Form der vermittelnde Vermittler vergütet wird.
    ]

    // ---------------------------------------------------------------
    ziffer[Schriftform- und Texterfordernisse]

    abs[
      Soweit diese Geschäftsbedingungen oder die Versicherungsbedingungen die
      Textform vorsehen, genügt jede lesbare, auf einem dauerhaften
      Datenträger abgegebene Erklärung, die die erklärende Person erkennen
      lässt. Einer eigenhändigen Unterschrift bedarf es nicht.
    ]

    abs[
      Mündliche Nebenabreden bestehen nicht. Änderungen und Ergänzungen des
      Vertrags bedürfen der Textform; dies gilt auch für die Aufhebung dieses
      Erfordernisses.
    ]

    abs[
      Das Erfordernis der Textform gilt nicht für Erklärungen, für die das
      Gesetz eine strengere Form vorschreibt; insoweit tritt die gesetzliche
      Form an ihre Stelle.
    ]

    abs[
      Der Versicherungsnehmer kann jederzeit verlangen, dass ihm der
      Versicherungsschein, die Versicherungsbedingungen und diese
      Geschäftsbedingungen erneut in Textform übermittelt werden.
    ]

    // ---------------------------------------------------------------
    ziffer[Salvatorische Klausel]

    abs[
      Ist eine Bestimmung dieser Geschäftsbedingungen unwirksam oder
      undurchführbar, bleibt die Wirksamkeit der übrigen Bestimmungen unberührt;
      für die Versicherungsbedingungen gilt dasselbe nach
      #verweis("schluss", absatz: 3).
    ]

    abs[
      An die Stelle der unwirksamen Bestimmung tritt die gesetzliche Regelung.
      Eine ergänzende Auslegung zum Nachteil des Versicherungsnehmers findet
      nicht statt.
    ]

    abs[
      Die Unwirksamkeit einer Bestimmung dieser Geschäftsbedingungen lässt den
      Bestand des Versicherungsvertrags unberührt.
    ]

    // ---------------------------------------------------------------
    ziffer[Änderungen dieser Geschäftsbedingungen]

    abs[
      Der Versicherer kann diese Geschäftsbedingungen ändern, soweit die
      Änderung erforderlich ist, um sie an eine geänderte Rechtslage, an eine
      höchstrichterliche Rechtsprechung oder an geänderte technische
      Verfahren des Geschäftsverkehrs anzupassen, und soweit die Änderung den
      Versicherungsnehmer nicht unangemessen benachteiligt.
    ]

    abs[
      Der Umfang des Versicherungsschutzes, die Erstattungsquoten, die
      Sublimits, der Selbstbehalt, die Zahnstaffel und der Beitrag können auf
      diesem Weg nicht geändert werden. Für den Beitrag gilt ausschließlich
      #verweis("beitragsanpassung").
    ]

    abs[
      Der Versicherer teilt die beabsichtigte Änderung, ihren Inhalt und den
      Zeitpunkt ihres Wirksamwerdens mindestens
      #wochen-d-agb(geschaeftsbedingungen.aenderungsankuendigung-wochen) vorher
      in Textform mit. Die Mitteilung stellt die alte und die neue Fassung
      gegenüber und weist auf das Widerspruchsrecht nach Absatz 4 hin.
    ]

    abs[
      Die Änderung gilt als genehmigt, wenn der Versicherungsnehmer ihr nicht
      innerhalb von
      #wochen-d-agb(geschaeftsbedingungen.aenderungswiderspruch-wochen) nach
      Zugang der Mitteilung in Textform widerspricht. Widerspricht er, bleiben
      die bisherigen Geschäftsbedingungen für ihn bestehen; der Versicherer
      kann den Vertrag daraufhin nicht kündigen.
    ]

    abs[
      Das Kündigungsrecht des Versicherungsnehmers nach #verweis("vertrag", absatz: 2)
      bleibt unberührt.
    ]

    // ---------------------------------------------------------------
    v(1em)
    hinweis(titel: "Hinweis")[
      Diese Geschäftsbedingungen sind Teil eines frei erfundenen Musters für
      Schulungszwecke. Sie begründen keine Rechte und Pflichten und stellen
      kein Angebot dar. Die genannten Stellen, Wege und Verfahren sind
      beschrieben, nicht benannt; Anschriften und Kontaktdaten enthält dieses
      Muster bewusst nicht.
    ]
  })
}
