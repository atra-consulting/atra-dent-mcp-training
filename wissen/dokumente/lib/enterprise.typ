// Erweiternde Paragraphen der Versicherungsbedingungen.
//
// Diese Datei ergaenzt lib/bausteine.typ um die Paragraphen, die ein
// vollstaendiges deutsches Bedingungswerk neben den Leistungsregeln
// braucht: Auslandsschutz, Beitragsanpassung, Zahlungsverzug,
// vorvertragliche Anzeigepflicht, Sachverstaendigenverfahren, Widerruf,
// Tarifwechsel, Mitteilungen, Verjaehrung und Gerichtsstand, Beschwerdewege
// und Datenverarbeitung. Dazu die Anlage B mit den Begriffsbestimmungen.
//
// Aufbau wie in bausteine.typ: je Paragraph eine Funktion, die den
// Tarifdatensatz entgegennimmt. Zahlen zum Leistungsversprechen stammen
// ausschliesslich aus wissen/daten/tarife.yaml; rein vertragsrechtliche Fristen
// stehen als benannte Konstanten am Kopf dieser Datei.

#import "daten.typ": *
#import "vorlage.typ": *
#import "bausteine.typ": kernbegriffe

// =======================================================================
// Vertragsrechtliche Konstanten
// =======================================================================
// Diese Werte gehoeren NICHT zum Tarifmodell. Sie beschreiben das
// Schuldverhaeltnis — Fristen, Formerfordernisse, Schwellen —, nicht das
// Leistungsversprechen. Deshalb stehen sie hier und bewusst nicht in
// wissen/daten/tarife.yaml, genau wie `vertragsrecht` in bausteine.typ.
//
// Ein Wert verdient einen Hinweis:
//   mitteilungsfrist-anpassung-monate  ersetzt die gleichlautende Frist aus
//     `vertragsrecht.anpassungsfrist-monate` in bausteine.typ, sobald die
//     Absaetze 3 und 4 des bestehenden § Beitrag durch § 11 abgeloest sind.
#let vertragsrecht-erweitert = (
  widerrufsfrist-tage: 14,
  widerruf-erstattungsfrist-tage: 30,
  mitteilungsfrist-anpassung-monate: 2,
  mahnfrist-tage: 14,
  wiederinkraftsetzung-monate: 3,
  anzeige-ausschlussfrist-jahre: 5,
  anzeige-arglistfrist-jahre: 10,
  ausuebungsfrist-monate: 1,
  benennungsfrist-wochen: 2,
  gutachtenfrist-wochen: 6,
  beschwerdefrist-wochen: 4,
  zugangsfiktion-tage: 3,
  verjaehrungsfrist-jahre: 3,
  loeschfrist-jahre: 10,
)

// Zahlwortformen fuer Tage und Wochen. daten.typ fuehrt Monate und Jahre;
// die kurzen Fristen dieses Werks brauchen dieselbe Behandlung, damit auch
// hier keine Zahl von Hand gesetzt wird.
#let tage(n) = if n == 1 { "einen Tag" } else { zahlwort(n) + " Tage" }
#let tagen(n) = if n == 1 { "einem Tag" } else { zahlwort(n) + " Tagen" }
#let wochen(n) = if n == 1 { "eine Woche" } else { zahlwort(n) + " Wochen" }
#let wochen-d(n) = if n == 1 { "einer Woche" } else { zahlwort(n) + " Wochen" }

// Nach "innerhalb" steht der Genitiv: "innerhalb eines Monats", aber
// "innerhalb von zwei Monaten". Weil sich mit der Zahl auch die Praeposition
// aendert, gehoert sie mit in die Funktion.
#let innerhalb-monate(n) = if n == 1 { "eines Monats" } else {
  "von " + zahlwort(n) + " Monaten"
}

// Die Absatzzaehlung des § Umfang haengt vom Tarif ab: den Absatz mit der
// Aufzaehlung der nicht versicherten Leistungsbereiche setzt p-umfang nur,
// wenn der Tarif ueberhaupt Luecken hat. In einem Vollschutztarif rutschen
// die folgenden Absaetze deshalb um eins nach vorn. Damit die Querverweise
// dieses Werks in jedem Tarif treffen, wird die Nummer berechnet statt
// abgetippt.
#let umfang-abs(t) = {
  let l = leistungen(t)
  let luecke = if bereich-schluessel.any(k => not l.at(k).versichert) { 1 } else { 0 }
  (
    quoten: 1,
    tabelle: 2,
    vorleistung: 3,
    nicht-versichert: 4,
    gebuehren: 4 + luecke,
    zuordnung: 5 + luecke,
    rundung: 6 + luecke,
  )
}

// =======================================================================
// § 9 Leistungen im Ausland
// =======================================================================
#let p-ausland(t) = {
  paragraf[Räumlicher Geltungsbereich]

  abs[
    Der Versicherungsschutz besteht für Heilbehandlung in der Bundesrepublik
    Deutschland.
  ]

  abs[
    Für Heilbehandlung außerhalb der Bundesrepublik Deutschland besteht kein
    Leistungsanspruch. Das gilt unabhängig davon, aus welchem Anlass sich die
    versicherte Person im Ausland aufhält, ob die Behandlung dort
    unaufschiebbar war und ob der Behandler den Anforderungen des
    #verweis("begriffe") an einen Behandler im Übrigen entspräche.
  ]

  abs[
    Eine im Ausland begonnene Behandlung wird auch dann nicht erstattet, wenn
    sie in der Bundesrepublik Deutschland fortgesetzt oder abgeschlossen
    wird. Erstattungsfähig sind in diesem Fall allein die Aufwendungen für
    die im Inland erbrachten Behandlungsmaßnahmen.
  ]

  abs[
    Verlegt die versicherte Person ihren gewöhnlichen Aufenthalt ins Ausland,
    endet der Vertrag nach #verweis("versicherungsfaehigkeit", absatz: 3).
  ]
}

// =======================================================================
// § 11 Beitragsanpassung und Treuhänderverfahren
// =======================================================================
#let p-beitragsanpassung(t) = {
  paragraf[Beitragsanpassung und Treuhänderverfahren]

  abs[
    Der Beitrag nach #verweis("beitrag") ist auf der Grundlage der
    #begriff[Rechnungsgrundlagen] kalkuliert. Rechnungsgrundlagen sind
    #buchstaben(
      [der Schadenbedarf, also Häufigkeit und Höhe der Erstattungen je
       versicherter Person und Versicherungsjahr,],
      [die Entwicklung der Vergütungen für zahnärztliche Leistungen nach den
       in #verweis("umfang", absatz: umfang-abs(t).gebuehren) genannten Gebührenmaßstäben,],
      [die Entwicklung der Vorleistungen der gesetzlichen
       Krankenversicherung, die nach #verweis("umfang", absatz: 3) angerechnet
       werden,],
      [die Altersstruktur des versicherten Bestands sowie die Entwicklung von
       Zu- und Abgängen.],
    )
  ]

  abs[
    Der Versicherer vergleicht jährlich für jeden Tarif die erforderlichen
    mit den kalkulierten Rechnungsgrundlagen. Ergibt die Überprüfung eine
    nicht nur vorübergehende Abweichung, werden die Beiträge des betroffenen
    Tarifs überprüft und, soweit erforderlich, angepasst. Eine nur
    vorübergehende Abweichung berechtigt nicht zur Anpassung. Die Schwelle,
    ab der eine Abweichung als erheblich gilt, ergibt sich aus den
    technischen Berechnungsgrundlagen des Versicherers.
  ]

  abs[
    Eine Anpassung nach Absatz 2 wird erst wirksam, wenn ein
    #begriff[unabhängiger Treuhänder] ihr zugestimmt hat. Der Treuhänder muss
    fachlich geeignet und vom Versicherer unabhängig sein; er darf
    insbesondere nicht in einem Dienst- oder Arbeitsverhältnis zum
    Versicherer stehen und keine Bezüge von ihm erhalten, die seine
    Unabhängigkeit gefährden. Der Treuhänder prüft, ob die
    Rechnungsgrundlagen zutreffend ermittelt und die Berechnungen
    ordnungsgemäß durchgeführt wurden.
  ]

  abs[
    Die Anpassung erfasst alle Versicherungsnehmer des betroffenen Tarifs
    nach einheitlichen Grundsätzen. Eine Anpassung wegen des Alters oder des
    Gesundheitszustands einer einzelnen versicherten Person findet nicht
    statt. Der Versicherer ist ebenso verpflichtet, den Beitrag zu senken,
    wenn die Überprüfung nach Absatz 2 dies ergibt.
  ]

  abs[
    Der Versicherer teilt dem Versicherungsnehmer die Anpassung, ihren
    Umfang und die hierfür maßgeblichen Gründe spätestens
    #monate(vertragsrecht-erweitert.mitteilungsfrist-anpassung-monate) vor
    ihrem Wirksamwerden in Textform mit. Die Anpassung wird zum Beginn des
    auf die Mitteilungsfrist folgenden Versicherungsjahres wirksam.
  ]

  abs[
    Erhöht sich der Beitrag, kann der Versicherungsnehmer den Vertrag
    innerhalb
    #innerhalb-monate(vertragsrecht-erweitert.ausuebungsfrist-monate) nach Zugang der
    Mitteilung zum Zeitpunkt des Wirksamwerdens der Erhöhung kündigen. Die
    Mindestlaufzeit nach #verweis("vertrag", absatz: 1) steht diesem
    Kündigungsrecht nicht entgegen. Auf das Kündigungsrecht wird in der
    Mitteilung nach Absatz 5 hingewiesen.
  ]

  abs[
    Eine Anpassung der Erstattungsquoten, der Sublimits, der Zahnstaffel oder
    des Selbstbehalts ist mit dem Verfahren nach diesem Paragraphen nicht
    verbunden. Der Leistungsumfang nach #verweis("umfang") bis #verweis("selbstbehalt") bleibt
    von einer Beitragsanpassung unberührt.
  ]
}

// =======================================================================
// § 12 Folgen verspäteter Beitragszahlung
// =======================================================================
#let p-zahlungsverzug(t) = {
  paragraf[Folgen verspäteter Beitragszahlung]

  heading(level: 2, outlined: false)[Erster Beitrag]

  abs[
    Wird der erste Beitrag nicht zu dem in #verweis("beitrag", absatz: 2) genannten
    Zeitpunkt gezahlt, beginnt der Versicherungsschutz abweichend von
    #verweis("wartezeiten", absatz: 1) erst mit dem Tag der Zahlung. Auf diese Folge
    weist der Versicherer bei Übersendung des Versicherungsscheins in
    Textform hin.
  ]

  abs[
    Solange der erste Beitrag nicht gezahlt ist, kann der Versicherer vom
    Vertrag zurücktreten. Der Rücktritt ist ausgeschlossen, wenn der
    Versicherungsnehmer die Nichtzahlung nicht zu vertreten hat.
  ]

  heading(level: 2, outlined: false)[Folgebeitrag]

  abs[
    Wird ein Folgebeitrag nicht rechtzeitig gezahlt, kann der Versicherer den
    Versicherungsnehmer in Textform mahnen (#begriff[qualifizierte Mahnung]).
    Die Mahnung bezeichnet die rückständigen Beträge des Beitrags einschließlich
    der Zinsen und Kosten im Einzelnen, setzt eine Zahlungsfrist von
    #tagen(vertragsrecht-erweitert.mahnfrist-tage) ab Zugang und belehrt über
    die Rechtsfolgen der Absätze 4 und 5.
  ]

  abs[
    Tritt nach Ablauf der Frist ein Versicherungsfall ein und ist der
    Versicherungsnehmer zu diesem Zeitpunkt mit der Zahlung im Verzug, ruht
    der Versicherungsschutz (#begriff[Ruhen des Versicherungsschutzes]). Für
    Versicherungsfälle, die während des Ruhens eintreten, besteht kein
    Leistungsanspruch. Versicherungsfälle, die vor Fristablauf eingetreten
    sind, bleiben unberührt.
  ]

  abs[
    Der Versicherer kann den Vertrag nach Ablauf der Frist ohne Einhaltung
    einer Kündigungsfrist kündigen, solange der Versicherungsnehmer im Verzug
    ist. Die Kündigung kann bereits mit der Mahnung nach Absatz 3 für den
    Fall verbunden werden, dass der Versicherungsnehmer nicht innerhalb der
    Frist zahlt; sie wird dann mit Fristablauf wirksam. Der Verzicht des
    Versicherers auf das ordentliche Kündigungsrecht nach
    #verweis("vertrag", absatz: 3) steht dem nicht entgegen.
  ]

  heading(level: 2, outlined: false)[Wiederinkraftsetzung]

  abs[
    Zahlt der Versicherungsnehmer die rückständigen Beträge einschließlich
    Zinsen und Kosten innerhalb
    #innerhalb-monate(vertragsrecht-erweitert.wiederinkraftsetzung-monate) nach
    Fristablauf vollständig nach, lebt der Versicherungsschutz mit Beginn des
    auf die Zahlung folgenden Tages wieder auf
    (#begriff[Wiederinkraftsetzung]). Ist der Vertrag nach Absatz 5 gekündigt
    worden, wird die Kündigung mit der vollständigen Nachzahlung innerhalb
    dieser Frist unwirksam.
  ]

  abs[
    Für Versicherungsfälle, die zwischen dem Fristablauf nach Absatz 3 und
    der Wiederinkraftsetzung eingetreten sind, besteht auch nach der
    Nachzahlung kein Leistungsanspruch.
  ]

  abs[
    Bereits abgelaufene Wartezeiten nach #verweis("wartezeiten") leben durch das Ruhen
    und die Wiederinkraftsetzung nicht neu auf. Die Zahnstaffel nach
    #verweis("zahnstaffel") läuft während des Ruhens weiter; ihre Fristen verlängern sich
    nicht.
  ]

  abs[
    Der Versicherer kann Erstattungen, die er trotz Ruhens des
    Versicherungsschutzes geleistet hat, zurückfordern und mit künftigen
    Erstattungen verrechnen.
  ]
}

// =======================================================================
// § 14 Vorvertragliche Anzeigepflicht
// =======================================================================
#let p-anzeigepflicht(t) = {
  paragraf[Vorvertragliche Anzeigepflicht]

  abs[
    Der Versicherungsnehmer hat bis zur Abgabe seiner Vertragserklärung alle
    ihm bekannten #begriff[gefahrerheblichen Umstände] anzuzeigen, nach denen
    der Versicherer in Textform gefragt hat. Gefahrerheblich sind die
    Umstände, die für den Entschluss des Versicherers erheblich sind, den
    Vertrag mit dem vereinbarten Inhalt zu schließen. Ist die versicherte
    Person nicht zugleich Versicherungsnehmer, hat auch sie die Fragen
    wahrheitsgemäß und vollständig zu beantworten; ihre Kenntnis steht der
    Kenntnis des Versicherungsnehmers gleich.
  ]

  abs[
    Zu den gefahrerheblichen Umständen gehören insbesondere der Zustand des
    Gebisses, bei Antragstellung fehlende und nicht ersetzte Zähne, laufende,
    angeratene oder begonnene Behandlungen sowie Behandlungen der
    zurückliegenden Jahre, nach denen in Textform gefragt wird. Der
    Leistungsausschluss für bei Antragstellung fehlende Zähne und für
    angeratene oder begonnene Behandlungen nach #verweis("ausschluesse", absatz: 1)
    besteht unabhängig davon, ob eine Anzeigepflicht verletzt wurde; die
    Rechte aus diesem Paragraphen treten neben ihn und verdrängen ihn nicht.
  ]

  abs[
    Verletzt der Versicherungsnehmer seine Anzeigepflicht, kann der
    Versicherer vom Vertrag zurücktreten. Der Rücktritt ist ausgeschlossen,
    wenn der Versicherungsnehmer die Anzeigepflicht weder vorsätzlich noch
    grob fahrlässig verletzt hat. Beruht die Verletzung auf grober
    Fahrlässigkeit, ist der Rücktritt ferner ausgeschlossen, wenn der
    Versicherer den Vertrag auch bei Kenntnis der nicht angezeigten Umstände,
    wenn auch zu anderen Bedingungen, geschlossen hätte.
  ]

  abs[
    In den Fällen, in denen der Rücktritt nach Absatz 3 Satz 3 ausgeschlossen
    ist, kann der Versicherer den Vertrag mit einer Frist von
    #monaten(vertragsrecht-erweitert.ausuebungsfrist-monate) kündigen. Das
    Kündigungsrecht besteht nicht, wenn der Versicherer den Vertrag auch bei
    Kenntnis der nicht angezeigten Umstände zu denselben Bedingungen
    geschlossen hätte.
  ]

  abs[
    Statt zurückzutreten oder zu kündigen, kann der Versicherer verlangen,
    dass die anderen Bedingungen rückwirkend Vertragsbestandteil werden
    (#begriff[Vertragsanpassung]). Hat der Versicherungsnehmer die
    Anzeigepflicht nicht zu vertreten, werden die anderen Bedingungen erst ab
    der laufenden Versicherungsperiode Vertragsbestandteil. Erhöht sich durch
    die Vertragsanpassung der Beitrag erheblich oder schließt der Versicherer
    die Leistungspflicht für den nicht angezeigten Umstand aus, kann der
    Versicherungsnehmer den Vertrag
    innerhalb #innerhalb-monate(vertragsrecht-erweitert.ausuebungsfrist-monate)
    nach Zugang der Mitteilung fristlos kündigen. Auf dieses Recht wird in
    der Mitteilung hingewiesen.
  ]

  abs[
    Tritt der Versicherer nach Absatz 3 zurück, nachdem der Versicherungsfall
    eingetreten ist, bleibt er zur Leistung verpflichtet, wenn sich die
    Verletzung der Anzeigepflicht auf einen Umstand bezieht, der weder für
    den Eintritt des Versicherungsfalls noch für den Umfang der
    Leistungspflicht ursächlich war. Bei arglistiger Verletzung der
    Anzeigepflicht entfällt die Leistungspflicht auch in diesem Fall.
  ]

  abs[
    Die Rechte nach den Absätzen 3 bis 5 stehen dem Versicherer nur zu, wenn
    er den Versicherungsnehmer durch gesonderte Mitteilung in Textform auf
    die Folgen einer Anzeigepflichtverletzung hingewiesen hat. Sie sind
    innerhalb #innerhalb-monate(vertragsrecht-erweitert.ausuebungsfrist-monate)
    auszuüben, nachdem der Versicherer von der Verletzung Kenntnis erlangt
    hat; der Versicherer hat dabei die Umstände anzugeben, auf die er sein
    Recht stützt.
  ]

  abs[
    Die Rechte nach den Absätzen 3 bis 5 erlöschen mit Ablauf von
    #jahren(vertragsrecht-erweitert.anzeige-ausschlussfrist-jahre) seit
    Vertragsschluss. Ist der Versicherungsfall vor Ablauf dieser Frist
    eingetreten, erlöschen sie insoweit nicht. Bei vorsätzlicher oder
    arglistiger Verletzung der Anzeigepflicht beträgt die Frist
    #jahre(vertragsrecht-erweitert.anzeige-arglistfrist-jahre).
  ]

  abs[
    Das Recht des Versicherers, den Vertrag wegen arglistiger Täuschung
    anzufechten, bleibt unberührt. Die Anfechtung ist innerhalb
    #innerhalb-monate(vertragsrecht-erweitert.ausuebungsfrist-monate) nach
    Kenntnis von der Täuschung zu erklären.
  ]
}

// =======================================================================
// § 15 Sachverständigenverfahren
// =======================================================================
#let p-sachverstaendige(t) = {
  paragraf[Sachverständigenverfahren]

  abs[
    Sind der Versicherungsnehmer und der Versicherer über die
    zahnmedizinische Notwendigkeit einer Heilbehandlung, über die
    Angemessenheit der Aufwendungen nach #verweis("umfang", absatz: umfang-abs(t).gebuehren) oder über die
    Zuordnung einer Rechnungsposition zu einem Leistungsbereich nach
    #verweis("umfang", absatz: umfang-abs(t).zuordnung) verschiedener Auffassung, kann jede Seite ein
    #begriff[Sachverständigenverfahren] verlangen. Das Verlangen ist in
    Textform zu erklären und hat die streitigen Punkte zu bezeichnen.
  ]

  abs[
    Jede Seite benennt innerhalb von
    #wochen-d(vertragsrecht-erweitert.benennungsfrist-wochen) nach Zugang des
    Verlangens einen Sachverständigen in Textform. Sachverständiger kann nur
    sein, wer als Zahnärztin oder Zahnarzt approbiert ist und über
    einschlägige Erfahrung auf dem streitigen Gebiet verfügt. Benennt eine
    Seite ihren Sachverständigen nicht innerhalb der Frist, kann ihn die
    andere Seite durch die für den Wohnsitz der versicherten Person
    zuständige Zahnärztekammer benennen lassen.
  ]

  abs[
    Die beiden Sachverständigen benennen vor Beginn ihrer Tätigkeit
    einvernehmlich eine dritte sachverständige Person als Obmann. Kommt eine
    Einigung nicht zustande, wird der Obmann auf Antrag einer Seite durch die
    zuständige Zahnärztekammer benannt. Weder Sachverständige noch Obmann
    dürfen die versicherte Person behandelt haben oder in einem Dienst- oder
    Auftragsverhältnis zu einer der beiden Seiten stehen.
  ]

  abs[
    Die Sachverständigen erstatten ihre Feststellungen in Textform innerhalb
    von #wochen-d(vertragsrecht-erweitert.gutachtenfrist-wochen) nach ihrer
    Beauftragung. Weichen die Feststellungen voneinander ab, entscheidet der
    Obmann innerhalb der Grenzen der beiden Feststellungen. Die versicherte
    Person hat die Untersuchung zu ermöglichen, die zur Feststellung
    erforderlichen Unterlagen zur Verfügung zu stellen und ihre Behandler
    insoweit von der Schweigepflicht zu entbinden; #verweis("obliegenheiten", absatz: 4)
    gilt entsprechend.
  ]

  abs[
    Die Feststellungen sind für beide Seiten verbindlich, soweit sie nicht
    offenbar von der wirklichen Sachlage erheblich abweichen. Der Versicherer
    hat die Erstattung nach #verweis("umfang") auf der Grundlage der verbindlichen
    Feststellungen unverzüglich zu ermitteln und auszuzahlen.
  ]

  abs[
    Jede Seite trägt die Kosten des von ihr benannten Sachverständigen; die
    Kosten des Obmanns tragen beide Seiten je zur Hälfte. Abweichend hiervon
    trägt der Versicherer die gesamten Kosten des Verfahrens, wenn dessen
    Ergebnis eine Leistungspflicht ergibt, die er zuvor ganz oder überwiegend
    abgelehnt hatte.
  ]

  abs[
    Die Durchführung des Verfahrens ist weder Voraussetzung für die
    Beschreitung des Rechtswegs noch für die Anrufung einer Stelle nach
    #verweis("beschwerden"). Die Fristen des #verweis("verjaehrung") sind für die Dauer des
    Verfahrens gehemmt.
  ]
}

// =======================================================================
// § 17 Widerrufsrecht
// =======================================================================
#let p-widerruf(t) = {
  paragraf[Widerrufsrecht]

  abs[
    Der Versicherungsnehmer kann seine Vertragserklärung innerhalb von
    #tagen(vertragsrecht-erweitert.widerrufsfrist-tage) ohne Angabe von
    Gründen in Textform widerrufen. Der Widerruf ist an die im
    Versicherungsschein genannte Stelle des Versicherers zu richten.
  ]

  abs[
    Die Frist beginnt an dem Tag, an dem dem Versicherungsnehmer der
    Versicherungsschein, die Vertragsbestimmungen einschließlich dieser
    Bedingungen und ihrer Anlagen, die weiteren Vertragsinformationen sowie
    eine Belehrung über das Widerrufsrecht und über die Rechtsfolgen des
    Widerrufs sämtlich in Textform zugegangen sind. Solange eine dieser
    Unterlagen fehlt, beginnt die Frist nicht.
  ]

  abs[
    Zur Wahrung der Frist genügt die rechtzeitige Absendung des Widerrufs.
    Einer Begründung bedarf der Widerruf nicht.
  ]

  abs[
    Im Fall eines wirksamen Widerrufs entfällt der Versicherungsschutz von
    Anfang an. Der Versicherer erstattet die bereits gezahlten Beiträge
    unverzüglich, spätestens innerhalb von
    #tagen(vertragsrecht-erweitert.widerruf-erstattungsfrist-tage) nach
    Zugang des Widerrufs.
  ]

  abs[
    Hat der Versicherungsschutz auf ausdrücklichen Wunsch des
    Versicherungsnehmers vor dem Ende der Widerrufsfrist begonnen, so gebührt
    dem Versicherer der Teil des Beitrags, der auf die Zeit bis zum Zugang
    des Widerrufs entfällt. Erstattungen, die der Versicherer für diese Zeit
    erbracht hat, sind zurückzugewähren.
  ]

  abs[
    Das Widerrufsrecht erlischt, wenn der Vertrag von beiden Seiten auf
    ausdrücklichen Wunsch des Versicherungsnehmers vollständig erfüllt ist,
    bevor der Versicherungsnehmer sein Widerrufsrecht ausgeübt hat.
  ]

  abs[
    Das Recht des Versicherungsnehmers, den Vertrag nach #verweis("vertrag") zu
    kündigen, sowie die Kündigungsrechte nach #verweis("beitragsanpassung", absatz: 6) und
    #verweis("anzeigepflicht", absatz: 5) bleiben unberührt.
  ]
}

// =======================================================================
// § 18 Tarifwechsel
// =======================================================================
#let p-tarifwechsel(t) = {
  paragraf[Tarifwechsel]

  abs[
    Ein Wechsel des Vertrags in einen anderen Tarif des Versicherers ist
    ausgeschlossen. Ein Anspruch darauf besteht weder für den
    Versicherungsnehmer noch für die versicherte Person.
  ]

  abs[
    Absatz 1 gilt auch für den Wechsel zwischen einem Tarif und seiner
    Variante mit Selbstbehalt nach #verweis("selbstbehalt").
  ]

  abs[
    Soll die versicherte Person in einem anderen Tarif versichert werden, ist
    der bestehende Vertrag nach #verweis("vertrag") zu beenden und ein neuer
    Vertrag zu schließen. Für den neuen Vertrag gelten die
    Versicherungsfähigkeit nach #verweis("versicherungsfaehigkeit"), die
    Wartezeiten nach #verweis("wartezeiten") und die Zahnstaffel nach
    #verweis("zahnstaffel") von Neuem. Erworbene Rechte aus dem beendeten
    Vertrag werden nicht angerechnet.
  ]

  abs[
    Der Versicherer bietet die folgenden Tarife an; der Vertrag ist im Tarif
    #text(weight: "medium")[#t.anzeigename] geführt:

    #tabelle(
      spalten: (1fr, 40mm),
      ausrichtung: (left, right),
      kopf: ("Tarif", "Selbstbehalt je Versicherungsjahr"),
      ..modell.tarife
        .map(x => (
          x.anzeigename,
          if x.selbstbehalt == 0 { "keiner" } else { eur0(x.selbstbehalt) },
        ))
        .flatten()
    )
    Der für einen Tarif maßgebliche Beitrag ergibt sich aus dem
    Versicherungsschein; er ist nicht Bestandteil dieser Bedingungen.
  ]
}

// =======================================================================
// § 19 Mitteilungen und Anschriftenänderung
// =======================================================================
#let p-mitteilungen(t) = {
  paragraf[Mitteilungen und Anschriftenänderung]

  abs[
    Willenserklärungen und Anzeigen gegenüber dem Versicherer bedürfen nach
    #verweis("schluss", absatz: 1) der Textform. Sie sind an die im
    Versicherungsschein genannte Stelle zu richten. Erklärungen des
    Versicherers gegenüber dem Versicherungsnehmer bedürfen ebenfalls der
    Textform.
  ]

  abs[
    Der Versicherungsnehmer hat dem Versicherer eine Änderung seines Namens,
    seiner Anschrift, seiner elektronischen Adresse und seiner
    Bankverbindung unverzüglich in Textform anzuzeigen. Dieselbe Pflicht
    trifft ihn für die Anschrift der versicherten Person, soweit sie nicht
    zugleich Versicherungsnehmer ist.
  ]

  abs[
    Hat der Versicherungsnehmer eine Änderung seiner Anschrift nicht nach
    Absatz 2 angezeigt, genügt für eine Willenserklärung des Versicherers,
    die dem Versicherungsnehmer gegenüber abzugeben ist, die Absendung eines
    eingeschriebenen Briefes an die letzte dem Versicherer bekannte
    Anschrift. Die Erklärung gilt
    #tage(vertragsrecht-erweitert.zugangsfiktion-tage) nach der Absendung als
    zugegangen (#begriff[Zugangsfiktion]). Satz 1 und 2 gelten entsprechend,
    wenn der Versicherungsnehmer seinen Namen geändert und dies nicht
    angezeigt hat.
  ]

  abs[
    Die Zugangsfiktion nach Absatz 3 gilt nicht für die Kündigung des
    Vertrags durch den Versicherer und nicht für die Mahnung nach
    #verweis("zahlungsverzug", absatz: 3).
  ]

  abs[
    Der Versicherungsnehmer ist alleiniger Vertragspartner des Versicherers.
    Erklärungen des Versicherers gegenüber dem Versicherungsnehmer wirken
    auch für und gegen die versicherte Person. Die versicherte Person kann
    Auskünfte über ihren eigenen Versicherungsschutz und über die zu ihrer
    Person verarbeiteten Daten unmittelbar beim Versicherer verlangen;
    #verweis("datenverarbeitung", absatz: 7) bleibt unberührt.
  ]

  abs[
    Der Versicherer kann mit dem Versicherungsnehmer vereinbaren, dass
    Mitteilungen über ein elektronisches Postfach oder an eine benannte
    elektronische Adresse übermittelt werden. Das Nähere regelt Ziffer 4 der
    Allgemeinen Geschäftsbedingungen in Anlage C.
  ]

  abs[
    Eine Mitteilung an einen Versicherungsvermittler gilt nur dann als dem
    Versicherer zugegangen, wenn der Vermittler zu ihrer Entgegennahme
    bevollmächtigt ist. Der Umfang der Vollmacht ergibt sich aus Ziffer 8 der
    Allgemeinen Geschäftsbedingungen in Anlage C.
  ]
}

// =======================================================================
// § 20 Verjährung, Gerichtsstand und anwendbares Recht
// =======================================================================
#let p-verjaehrung(t) = {
  paragraf[Verjährung, Gerichtsstand und anwendbares Recht]

  abs[
    Ansprüche aus dem Versicherungsvertrag verjähren in
    #jahren(vertragsrecht-erweitert.verjaehrungsfrist-jahre). Die Verjährung
    beginnt mit dem Schluss des Jahres, in dem der Anspruch entstanden ist
    und der Gläubiger von den anspruchsbegründenden Umständen und der Person
    des Schuldners Kenntnis erlangt hat oder ohne grobe Fahrlässigkeit
    erlangen müsste.
  ]

  abs[
    Ist ein Anspruch beim Versicherer angemeldet worden, ist die Verjährung
    bis zu dem Zeitpunkt gehemmt, zu dem die Entscheidung des Versicherers
    dem Anspruchsteller in Textform zugeht. Die Einreichungsfrist für
    Rechnungen nach #verweis("obliegenheiten", absatz: 2) bleibt daneben bestehen; sie ist
    keine Verjährungsfrist.
  ]

  abs[
    Für Klagen des Versicherungsnehmers oder der versicherten Person gegen
    den Versicherer ist neben dem Gericht am Sitz des Versicherers auch das
    Gericht zuständig, in dessen Bezirk der Versicherungsnehmer zur Zeit der
    Klageerhebung seinen Wohnsitz oder, in Ermangelung eines solchen, seinen
    gewöhnlichen Aufenthalt hat.
  ]

  abs[
    Für Klagen des Versicherers gegen den Versicherungsnehmer ist
    ausschließlich das Gericht zuständig, in dessen Bezirk der
    Versicherungsnehmer seinen Wohnsitz oder, in Ermangelung eines solchen,
    seinen gewöhnlichen Aufenthalt hat. Hat der Versicherungsnehmer seinen
    Wohnsitz nach Vertragsschluss verlegt und ist sein neuer Wohnsitz nicht
    bekannt, ist das Gericht am Sitz des Versicherers zuständig.
  ]

  abs[
    Für den Vertrag gilt das Recht der Bundesrepublik Deutschland
    (#verweis("schluss", absatz: 2)). Zwingende Vorschriften des Staates, in dem der
    Versicherungsnehmer seinen gewöhnlichen Aufenthalt hat, bleiben
    unberührt. Vertragssprache ist Deutsch; Ziffer 3 der Allgemeinen
    Geschäftsbedingungen in Anlage C gilt ergänzend.
  ]

  abs[
    Die Anrufung einer Stelle nach #verweis("beschwerden") hemmt die Verjährung für die
    Dauer des dortigen Verfahrens. Sie ist keine Voraussetzung für die
    Beschreitung des Rechtswegs.
  ]
}

// =======================================================================
// § 21 Beschwerden und außergerichtliche Streitbeilegung
// =======================================================================
#let p-beschwerden(t) = {
  paragraf[Beschwerden und außergerichtliche Streitbeilegung]

  abs[
    Der Versicherungsnehmer und die versicherte Person können sich mit einer
    Beschwerde unmittelbar an den Versicherer wenden. Der Versicherer
    unterhält hierfür eine vom Leistungsbereich unabhängige interne
    Beschwerdestelle. Die Beschwerde kann in Textform erhoben werden; die für
    ihre Entgegennahme zuständige Stelle ist im Versicherungsschein und auf
    den Geschäftsunterlagen des Versicherers benannt.
  ]

  abs[
    Der Versicherer bestätigt den Eingang einer Beschwerde unverzüglich und
    beantwortet sie in Textform. Kann eine abschließende Antwort nicht
    innerhalb von #wochen-d(vertragsrecht-erweitert.beschwerdefrist-wochen)
    erteilt werden, teilt der Versicherer den Sachstand und den
    voraussichtlichen Abschluss mit.
  ]

  abs[
    Führt die Beschwerde nicht zu einer Einigung, kann der
    Versicherungsnehmer den #begriff[Versicherungsombudsmann] anrufen. Es
    handelt sich um eine unabhängige und für Verbraucher kostenfreie
    Schlichtungsstelle der deutschen Versicherungswirtschaft, die als
    anerkannte Verbraucherschlichtungsstelle tätig wird. Der Versicherer ist
    dem Schlichtungsverfahren angeschlossen; die für die Anrufung
    erforderlichen Angaben teilt der Versicherer auf Anfrage mit.
  ]

  abs[
    Unabhängig davon kann sich der Versicherungsnehmer mit einer Beschwerde
    an die für die Versicherungsaufsicht zuständige Behörde, die
    Bundesanstalt für Finanzdienstleistungsaufsicht, wenden. Die
    Aufsichtsbehörde entscheidet nicht über einzelne Ansprüche aus dem
    Vertrag; sie überwacht die Einhaltung der aufsichtsrechtlichen
    Vorschriften.
  ]

  abs[
    Wurde der Vertrag im elektronischen Geschäftsverkehr geschlossen, steht
    dem Versicherungsnehmer zusätzlich der Weg über die von der Europäischen
    Kommission betriebene Plattform zur Online-Streitbeilegung offen. Die
    Zugangsdaten teilt der Versicherer auf Anfrage mit.
  ]

  abs[
    Die Anrufung einer Stelle nach den Absätzen 3 bis 5 ist für den
    Versicherungsnehmer freiwillig. Sie schließt die Beschreitung des
    Rechtswegs nach #verweis("verjaehrung") und die Durchführung eines
    Sachverständigenverfahrens nach #verweis("sachverstaendige") nicht aus. Für die Dauer des
    Verfahrens ist die Verjährung nach #verweis("verjaehrung", absatz: 6) gehemmt.
  ]

  abs[
    Beschwerden über einen Versicherungsvermittler sind an den Versicherer zu
    richten, soweit sie die Vermittlung dieses Vertrags betreffen.
  ]
}

// =======================================================================
// § 22 Verarbeitung personenbezogener Daten
// =======================================================================
#let p-datenverarbeitung(t) = {
  paragraf[Verarbeitung personenbezogener Daten]

  abs[
    Der Versicherer verarbeitet personenbezogene Daten des
    Versicherungsnehmers und der versicherten Person, soweit dies für die
    Begründung, Durchführung und Beendigung des Vertrags erforderlich ist.
    Zu diesen Zwecken gehören die Antrags- und Risikoprüfung nach
    #verweis("anzeigepflicht"), die Verwaltung des Vertrags, die Beitragserhebung und die
    Beitragsanpassung nach #verweis("beitragsanpassung"), die Prüfung der Leistungspflicht und
    die Ermittlung der Erstattung nach #verweis("umfang") sowie die Bearbeitung von
    Beschwerden nach #verweis("beschwerden").
  ]

  abs[
    Rechtsgrundlage der Verarbeitung ist die Erfüllung des Vertrags und die
    Durchführung vorvertraglicher Maßnahmen (Artikel 6 Absatz 1 Buchstabe b
    der Datenschutz-Grundverordnung), die Erfüllung rechtlicher
    Verpflichtungen des Versicherers (Artikel 6 Absatz 1 Buchstabe c der
    Datenschutz-Grundverordnung) sowie, soweit ausdrücklich genannt, die
    Wahrung berechtigter Interessen (Artikel 6 Absatz 1 Buchstabe f der
    Datenschutz-Grundverordnung).
  ]

  abs[
    Für die Durchführung des Vertrags sind #begriff[Gesundheitsdaten]
    erforderlich, insbesondere Befunde, Behandlungspläne, Rechnungen und
    Abrechnungen. Sie werden auf der Grundlage einer ausdrücklichen
    Einwilligung der versicherten Person verarbeitet (Artikel 9 Absatz 2
    Buchstabe a der Datenschutz-Grundverordnung). Die versicherte Person
    entbindet ihre Behandler nach #verweis("obliegenheiten", absatz: 4) insoweit von der
    Schweigepflicht, als dies zur Prüfung der Leistungspflicht erforderlich
    ist (#begriff[Schweigepflichtentbindung]). Die Entbindung ist auf den
    jeweiligen Prüfungszweck begrenzt; eine allgemeine Entbindung wird nicht
    verlangt.
  ]

  abs[
    Die Einwilligung und die Schweigepflichtentbindung können jederzeit für
    die Zukunft in Textform widerrufen werden. Der Widerruf lässt die
    Rechtmäßigkeit der bis dahin erfolgten Verarbeitung unberührt. Kann der
    Versicherer die Leistungspflicht ohne die betroffenen Daten nicht prüfen,
    besteht insoweit kein Leistungsanspruch; #verweis("ausschluesse", absatz: 2) gilt
    entsprechend.
  ]

  abs[
    Daten werden an Dritte nur übermittelt, soweit dies zur Durchführung des
    Vertrags erforderlich oder gesetzlich zulässig ist. Empfänger können
    sein: Behandler und deren Abrechnungsstellen zur Klärung von Rechnungen,
    Sachverständige und der Obmann im Verfahren nach #verweis("sachverstaendige"), die
    gesetzliche Krankenversicherung zur Abstimmung der nach
    #verweis("umfang", absatz: 3) anzurechnenden Vorleistung, Rückversicherer, im
    Auftrag des Versicherers tätige Dienstleister sowie der
    Versicherungsvermittler im Rahmen seiner Vollmacht. Auftragsverarbeiter
    werden schriftlich verpflichtet und dürfen die Daten nur weisungsgebunden
    verarbeiten.
  ]

  abs[
    Der Versicherer speichert die Daten, solange dies für die genannten
    Zwecke erforderlich ist, mindestens jedoch bis zum Ablauf der
    Verjährungsfrist nach #verweis("verjaehrung", absatz: 1) und darüber hinaus, soweit
    handels- und steuerrechtliche Aufbewahrungspflichten entgegenstehen;
    diese betragen längstens
    #jahre(vertragsrecht-erweitert.loeschfrist-jahre). Danach werden die
    Daten gelöscht oder anonymisiert.
  ]

  abs[
    Der Versicherungsnehmer und die versicherte Person haben nach Maßgabe
    der Datenschutz-Grundverordnung das Recht auf
    #buchstaben(
      [Auskunft über die zu ihrer Person verarbeiteten Daten,],
      [Berichtigung unrichtiger und Vervollständigung unvollständiger Daten,],
      [Löschung und auf Einschränkung der Verarbeitung,],
      [Datenübertragbarkeit hinsichtlich der von ihnen bereitgestellten
       Daten,],
      [Widerspruch gegen eine Verarbeitung, die auf berechtigte Interessen
       gestützt ist,],
      [Beschwerde bei einer Datenschutzaufsichtsbehörde.],
    )
    Die Rechte sind gegenüber dem Versicherer in Textform geltend zu machen.
    Die versicherte Person übt die Rechte hinsichtlich der zu ihrer Person
    verarbeiteten Daten selbst aus; sie ist insoweit nicht auf den
    Versicherungsnehmer verwiesen.
  ]

  abs[
    Eine ausschließlich auf einer automatisierten Verarbeitung beruhende
    Entscheidung über die Leistungspflicht findet nicht statt. Die
    Ablehnung einer Erstattung wird stets von einer natürlichen Person
    verantwortet.
  ]

  abs[
    Die nach der Datenschutz-Grundverordnung zu erteilenden Informationen
    stellt der Versicherer gesondert in Textform zur Verfügung. Dieser
    Paragraph regelt die vertraglichen Pflichten und Rechte; er ersetzt
    diese Information nicht.
  ]
}

// =======================================================================
// Anlage B — Begriffsbestimmungen
// =======================================================================
//
// Das Verzeichnis fuehrt die im Werk mit `begriff[...]` ausgezeichneten
// Legaldefinitionen und die zehn Leistungsbereiche aus wissen/daten/tarife.yaml
// zusammen. Die Eintraege werden als Paare (Stichwort, Erlaeuterung)
// gesammelt und alphabetisch sortiert, damit sich die Reihenfolge beim
// Hinzufuegen eines Leistungsbereichs von selbst ergibt.
#let anlage-begriffe(t) = {
  pagebreak()

  // Der Seitenumbruch bleibt ausserhalb: er gehoert zur Seitengestaltung.
  // Das Verzeichnis selbst ist eine Struktureinheit und wird im HTML zu
  // einem eigenen Abschnitt, damit es als Ganzes zitierbar bleibt.
  abschnitt("anlage-b", {
    heading(level: 1, numbering: none)[Anlage B — Begriffsbestimmungen]

    par(justify: true)[
      Dieses Verzeichnis führt in alphabetischer Folge die Begriffe zusammen,
      die in den einzelnen Paragraphen eingeführt werden, sowie die
      Leistungsbereiche des Leistungsverzeichnisses der Anlage A. Die
      Grundbegriffe des Bedingungswerks stehen nicht hier, sondern in
      #verweis("begriffe"); sie werden nicht wiederholt, damit keine zweite,
      abweichende Fassung entstehen kann. Dieses Verzeichnis ist Bestandteil
      dieser Bedingungen und dient dem Auffinden; es begründet keine von den
      Paragraphen abweichende Bedeutung. Weichen Erläuterung und
      Paragraphentext voneinander ab, geht der Paragraphentext vor; die
      Fundstelle steht deshalb in jeder Erläuterung.
    ]

    let l = leistungen(t)

    // Die Leistungsbereiche kommen aus dem Modell, samt Quote und
    // Versicherungsstatus des jeweiligen Tarifs.
    let aus-bereichen = bereich-schluessel.map(k => {
      let b = bereich(k)
      let e = l.at(k)
      (
        b.name,
        [Leistungsbereich #kuerzel(k) des Leistungsverzeichnisses (Anlage A).
         #fliesstext(b.beschreibung)
         #if e.versichert [
           In diesem Tarif versichert mit einer Erstattungsquote von
           #prozent(e.quote).
         ] else [
           In diesem Tarif nicht versichert (#verweis("umfang", absatz: 4)).
         ]],
      )
    })

    let aus-bedingungen = (
      (
        "Allgemeine Wartezeit",
        [Zeitraum ab Versicherungsbeginn, für dessen Dauer kein
         Leistungsanspruch besteht (#verweis("wartezeiten", absatz: 2)). In diesem Tarif
         beträgt sie #monate(t.wartezeit_monate)#if t.at(
           "wartezeit_entfaellt_bei_vorversicherung",
           default: false,
         ) [ und entfällt bei lückenlosem Vorversicherungsschutz].],
      ),
      (
        "Angeratene Behandlung",
        [Behandlung, die ein Behandler vor Vertragsschluss schriftlich oder in
         der Behandlungsdokumentation empfohlen hat (#verweis("begriffe", absatz: 1)).
         Sie ist vom Versicherungsschutz ausgeschlossen und nach
         #verweis("anzeigepflicht", absatz: 2) anzuzeigen.],
      ),
      (
        "Begonnene Behandlung",
        [Behandlung, bei der mit der ersten Behandlungsmaßnahme begonnen wurde
         (#verweis("begriffe", absatz: 1)).],
      ),
      (
        "Eintrittsalter",
        [Alter der zu versichernden Person am Tag des Versicherungsbeginns
         (#verweis("versicherungsfaehigkeit", absatz: 2)). In diesem Tarif muss es zwischen
         #t.eintrittsalter.von und #t.eintrittsalter.bis Jahren liegen.],
      ),
      (
        "Erstattungsquote",
        [Prozentsatz, mit dem der erstattungsfähige Betrag eines
         Leistungsbereichs vervielfacht wird (#verweis("umfang", absatz: 1)). Die
         Quote versteht sich einschließlich der Vorleistung der gesetzlichen
         Krankenversicherung.],
      ),
      (
        "Gefahrerheblicher Umstand",
        [Umstand, der für den Entschluss des Versicherers erheblich ist, den
         Vertrag mit dem vereinbarten Inhalt zu schließen
         (#verweis("anzeigepflicht", absatz: 1)).],
      ),
      (
        "Gesundheitsdaten",
        [Daten über den Gesundheitszustand der versicherten Person,
         insbesondere Befunde, Behandlungspläne, Rechnungen und Abrechnungen
         (#verweis("datenverarbeitung", absatz: 3)).],
      ),
      (
        "Jahreshöchstgrenze",
        [Betragsmäßige Begrenzung der Erstattung je Versicherungsjahr nach
         Ablauf der Zahnstaffel (#verweis("zahnstaffel")).
         #if t.at("jahreshoechstgrenze", default: none) == none [
           In diesem Tarif besteht keine Jahreshöchstgrenze.
         ] else [
           In diesem Tarif beträgt sie #eur0(t.jahreshoechstgrenze).
         ]],
      ),
      (
        "Qualifizierte Mahnung",
        [Mahnung in Textform, die die rückständigen Beträge bezeichnet, eine
         Zahlungsfrist von #tagen(vertragsrecht-erweitert.mahnfrist-tage) setzt
         und über die Rechtsfolgen belehrt (#verweis("zahlungsverzug", absatz: 3)).],
      ),
      (
        "Rechnungsgrundlagen",
        [Die der Beitragskalkulation zugrunde liegenden Größen, insbesondere
         Schadenbedarf, Vergütungsentwicklung, Vorleistungen der gesetzlichen
         Krankenversicherung und Bestandsstruktur (#verweis("beitragsanpassung", absatz: 1)).],
      ),
      (
        "Ruhen des Versicherungsschutzes",
        [Zustand, in dem für neu eintretende Versicherungsfälle kein
         Leistungsanspruch besteht, obwohl der Vertrag fortbesteht
         (#verweis("zahlungsverzug", absatz: 4)).],
      ),
      (
        "Sachverständigenverfahren",
        [Verfahren zur Klärung von Meinungsverschiedenheiten über die
         zahnmedizinische Notwendigkeit, die Angemessenheit der Aufwendungen
         oder die Zuordnung zu einem Leistungsbereich (#verweis("sachverstaendige")).],
      ),
      (
        "Schweigepflichtentbindung",
        [Auf den jeweiligen Prüfungszweck begrenzte Erklärung der versicherten
         Person, mit der sie ihre Behandler gegenüber dem Versicherer von der
         Schweigepflicht entbindet (#verweis("obliegenheiten", absatz: 4),
         #verweis("datenverarbeitung", absatz: 3)).],
      ),
      (
        "Selbstbehalt",
        [Betrag, der von den Erstattungen eines Versicherungsjahres abgezogen
         wird (#verweis("begriffe"), #verweis("selbstbehalt")).
         #if t.selbstbehalt == 0 [
           In diesem Tarif wird kein Selbstbehalt erhoben.
         ] else [
           In diesem Tarif beträgt er #eur0(t.selbstbehalt).
         ]],
      ),
      (
        "Sublimit",
        [Begrenzung der Erstattung innerhalb eines einzelnen
         Leistungsbereichs, nach Betrag, nach Anzahl der Behandlungen oder nach
         Zeitraum (#verweis("umfang", absatz: 1) und #verweis("umfang", absatz: 2)).],
      ),
      (
        "Tarifwechsel",
        [Umstellung des Vertrags in einen anderen Tarif desselben Versicherers.
         In diesen Bedingungen ausgeschlossen (#verweis("tarifwechsel")).],
      ),
      (
        "Textform",
        [Form, in der Erklärungen abzugeben sind: lesbar, auf einem dauerhaften
         Datenträger und unter Nennung der erklärenden Person, ohne
         eigenhändige Unterschrift (#verweis("schluss", absatz: 1),
         #verweis("mitteilungen", absatz: 1)).],
      ),
      (
        "Treuhänder",
        [Vom Versicherer unabhängige, fachlich geeignete Person, deren
         Zustimmung eine Beitragsanpassung wirksam werden lässt
         (#verweis("beitragsanpassung", absatz: 3)).],
      ),
      (
        "Unfall",
        [Plötzlich von außen auf den Körper wirkendes Ereignis, durch das die
         versicherte Person unfreiwillig eine Gesundheitsschädigung erleidet
         (#verweis("wartezeiten")). Wartezeiten und Zahnstaffel entfallen für
         Unfallfolgen.],
      ),
      (
        "Versicherte Person",
        [Die Person, für deren zahnmedizinische Heilbehandlung
         Versicherungsschutz besteht. Sie ist nicht notwendig zugleich
         Versicherungsnehmer (#verweis("mitteilungen", absatz: 5)).],
      ),
      (
        "Versicherungsfall",
        [Die zahnmedizinisch notwendige Heilbehandlung einer versicherten
         Person wegen Krankheit oder Unfallfolgen (#verweis("begriffe", absatz: 1)).],
      ),
      (
        "Versicherungsjahr",
        [Die zwölf Monate ab dem Versicherungsbeginn und jeder folgende
         Zeitraum von zwölf Monaten; nicht das Kalenderjahr
         (#verweis("begriffe", absatz: 1)).],
      ),
      (
        "Versicherungsnehmer",
        [Der Vertragspartner des Versicherers. Ihn treffen die Pflichten aus
         dem Vertrag, ihm stehen die Gestaltungsrechte zu
         (#verweis("mitteilungen", absatz: 5)).],
      ),
      (
        "Vertragsanpassung",
        [Rückwirkende Einbeziehung anderer Vertragsbedingungen anstelle eines
         Rücktritts oder einer Kündigung wegen verletzter Anzeigepflicht
         (#verweis("anzeigepflicht", absatz: 5)).],
      ),
      (
        "Wiederinkraftsetzung",
        [Wiederaufleben des ruhenden Versicherungsschutzes nach vollständiger
         Nachzahlung der rückständigen Beträge (#verweis("zahlungsverzug", absatz: 6)).],
      ),
      (
        "Zahnstaffel",
        [Kumulierte Begrenzung aller Erstattungen in den ersten
         Versicherungsjahren (#verweis("zahnstaffel")). In diesem Tarif läuft sie über
         #jahre(staffel-von(t).dauer_jahre).],
      ),
      (
        "Zugangsfiktion",
        [Regel, nach der eine an die letzte bekannte Anschrift abgesandte
         Erklärung des Versicherers nach
         #tagen(vertragsrecht-erweitert.zugangsfiktion-tage) als zugegangen
         gilt, wenn eine Anschriftenänderung nicht angezeigt wurde
         (#verweis("mitteilungen", absatz: 3)).],
      ),
    )

    // Was bereits § Begriffsbestimmungen bestimmt, wird hier nicht wiederholt.
    let kern = kernbegriffe.map(((begriff, _)) => begriff)
    let eintraege = (aus-bereichen + aus-bedingungen)
      .filter(e => not kern.contains(e.at(0)))
      .sorted(key: e => e.at(0))

    // Zwei Spalten wie in der Begriffstabelle des § Begriffsbestimmungen:
    // Stichwort links, Bedeutung rechts. Bewusst nicht `tabelle` aus
    // vorlage.typ — die ist unteilbar, und ein Verzeichnis dieser Laenge muss
    // ueber Seiten laufen und seinen Kopf dabei wiederholen.
    block(above: 1.2em, {
      set par(justify: false)
      set text(hyphenate: false, size: 8.5pt)
      table(
        columns: (44mm, 1fr),
        align: (left, left),
        stroke: none,
        inset: (x: 8pt, y: 6.5pt),
        fill: (_, y) => if y == 0 { dunkelblau } else if calc.odd(y) { blau-zebra } else {
          white
        },
        table.header(
          repeat: true,
          ..("Begriff", "Bedeutung").map(z => text(
            fill: white,
            weight: "bold",
            size: 8.3pt,
            z,
          )),
        ),
        ..eintraege
          .map(((stichwort, erlaeuterung)) => (
            text(weight: "medium", stichwort),
            erlaeuterung,
          ))
          .flatten()
      )
    })
  })
}
