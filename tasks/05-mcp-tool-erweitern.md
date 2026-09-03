# 05 — Das MCP des Kernsystems erweitern

**Dauer:** 25 min
**Werkzeug:** Claude Code
**Voraussetzung:** Übungszweig des Labs ausgecheckt
**Ziel:** Das Kernsystem bietet `schadensfall_einreichen` als Werkzeug an — mit einer Beschreibung, die ein Modell zuverlässig trifft.
**Ergebnis:** Der Inspector zeigt das neue Werkzeug, und ein Aufruf legt einen Fall an. **Mindestens:** Werkzeug mit Schema und Beschreibung in der Liste.

Die Fachlogik ist fertig: Der Service kann einen Schadensfall anlegen, die
REST-Schnittstelle nutzt ihn bereits. Was fehlt, ist die Seite zum Modell hin.
Der Code dafür ist kurz — die Arbeit steckt im Beschreibungstext.

## Schritte

1. Legt in der Werkzeugklasse für Schadensfälle eine Methode an und annotiert
   sie als MCP-Werkzeug.
2. Holt die Kundennummer aus dem Mandantenkontext, nicht aus einem Parameter.
   Im Lab kommt sie aus einem Header, den der Client setzt — ein Labormuster;
   im Betrieb stammt sie aus einem geprüften Token, nie aus dem Aufrufer.
3. Beschreibt jeden Parameter — und schreibt in die Werkzeugbeschreibung
   ausdrücklich, dass der Aufruf schreibt und sich nicht zurücknehmen lässt.
4. Startet das Kernsystem neu und prüft im Inspector, dass das Werkzeug
   auftaucht.
5. Ruft es einmal auf und seht im Backoffice nach, ob der Fall angekommen ist.

## Prompt

Zum Einstieg, wenn ihr den Assistenten arbeiten lasst:

```
In kernsystem/src/main/java/de/atra/kernsystem/mcp/ liegen die vorhandenen
MCP-Werkzeuge. Lege nach demselben Muster ein Werkzeug schadensfall_einreichen
an, das den bestehenden Service nutzt. Die Kundennummer kommt aus dem
Mandantenkontext, nicht als Parameter.
```

## Troubleshooting

- **Das Werkzeug taucht nicht auf.** Die Registrierung läuft über die
  Annotation, aber nur beim Start — der Dienst muss neu hoch.
- **Der Aufruf scheitert an der Kundennummer.** Der Inspector muss den
  Mandantenheader mitsenden; ohne ihn wirft das Kernsystem absichtlich einen
  Fehler mit erklärendem Text.
- **Geldbeträge werden zu Fließkommazahlen.** Ein `double` im Schema wird zu
  `number`, und dann rechnet eine Versicherung mit Rundungsfehlern. Im Java-Code
  gehört der Betrag auf `BigDecimal` mit fester Währung und definierter Rundung;
  über die Schnittstelle geht er als String, damit ihn unterwegs niemand durch
  ein `double` schickt. Der String ist der Transport, nicht der Typ.

## Diskussion

Vergleicht eure Beschreibungen paarweise. Die Beschreibung ist der Teil des
Werkzeugs, den ihr frei formuliert — Name, Parameterschema und Annotationen
sprechen mit, und darüber liegt noch, was der Host in seinen Systemprompt
schreibt. Sie ist zugleich der Text, über den ein Angreifer das Modell steuern
würde, wenn er ihn ändern könnte.

Und die Frage dahinter: Im Lab kommt die Kundennummer über einen Header
`x-kunden-id`, den der Client setzt. Warum ist das für eine Übung in Ordnung und
für den Betrieb nicht — und woher käme sie dort?
