# 07 — Orchestrator erweitern und den Arztservice anbinden

**Dauer:** 25 min
**Werkzeug:** Claude Code
**Voraussetzung:** Aufgabe 06 abgeschlossen
**Ziel:** Der Orchestrator wählt euren Agenten anhand der Karte, und euer Agent holt eine Auskunft über die Servicegrenze.
**Ergebnis:** Ein Fall läuft vom Kunden-Chat bis zum fremden Arztservice durch, nachvollziehbar in der Spur. **Kür** — das Lernziel steht nach Übung 06.

Jetzt schließt sich der Kreis. Der Orchestrator bekommt keinen Code, sondern
eine Adresse — er liest die Karte selbst und entscheidet danach. Zu finden ist
euer Agent also nicht von allein: Ihr tragt ihn ein. Was ohne euer Zutun
passiert, ist die Wahl.

Der Arztservice hängt an A2A, weil er ein eigenständiges Gegenüber ist: Er nimmt
einen Auftrag an, prüft ihn und darf ihn begründet ablehnen. Nicht, weil er
jemand anderem gehört — fremde Häuser betreiben auch MCP-Server, und ein
A2A-Agent kann im eigenen Rechenzentrum stehen.

## Schritte

1. Tragt die Adresse eures Agenten bei den Subagenten des Orchestrators ein.
2. Startet den Orchestrator neu und prüft im Log, dass er die Karte gezogen hat.
3. Stellt im Kunden-Chat ein Anliegen, das zu eurer Fertigkeit passt.
4. Verfolgt in der Debug-Spur, wie der Orchestrator wählt und weiterreicht.
5. Lasst den Fall prüfen, bis der Arztservice befragt wird — und schaut nach,
   welche Daten ihn erreichen.

## Prompt

Der einzige Eingriff am Orchestrator ist eine Zeile in seiner
`application.yaml` — dort steht bereits eine Liste von Subagenten-Adressen.
Kein Code, keine Registrierung, keine Fallunterscheidung.

Das Anliegen für den Chat, frei formuliert, zum Beispiel:

```
Ich war beim Zahnarzt und möchte die Rechnung einreichen.
```

## Troubleshooting

- **Der Orchestrator wählt euren Agenten nie.** Die Wahl fällt allein anhand der
  Beschreibung in der Karte. Wenn sie zu vage ist, kommt nichts an — schärft die
  Fertigkeitsbeschreibung und startet neu.
- **Der Arztservice antwortet nicht.** Er läuft in einer fremden Umgebung und
  braucht einen Schlüssel. Fehlt er, endet die Prüfung mit einer klaren Meldung
  statt mit einer stillen Freigabe — auch das ist eine Entwurfsentscheidung.
- **Ihr wollt in Ruhe arbeiten.** Der Poller des Schadensfallagenten greift
  regelmäßig zu; für Vorführungen lässt er sich abschalten.

## Diskussion

Der Arztservice bekommt Behandlungspositionen, aber keine Kundenakte. Warum ist
das die richtige Grenze? Und was wäre nötig gewesen, wenn er stattdessen ein
MCP-Werkzeug des Kernsystems geworden wäre?

Drei Dinge, die dieses Lab noch nicht kann und die in Produktion vor dem ersten
echten Fall stehen müssten — sammelt, wer sie in eurem Haus beantworten würde:

- **Zeit.** Was passiert, wenn der Arztservice nach 30 Sekunden nicht antwortet?
  Wer bricht ab, und woran merkt es der Kunde?
- **Abbruch.** Der Auftrag kennt `canceled`. Wer darf abbrechen — und was tut der
  Agent, der schon halb fertig ist?
- **Wiederholung.** Wenn derselbe Fall zweimal ankommt: Zahlt die Versicherung
  zweimal? Woran erkennt der Agent, dass er diesen Fall schon hatte?
