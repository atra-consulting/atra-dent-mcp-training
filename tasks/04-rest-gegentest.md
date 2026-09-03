# 04 — Derselbe Auftrag, aber über REST

**Dauer:** 15 min
**Werkzeug:** Claude Code
**Voraussetzung:** Aufgabe 03 abgeschlossen
**Ziel:** Ihr habt dieselbe Fachfrage ohne MCP gelöst und könnt beide Wege gegeneinander halten.
**Ergebnis:** Eine Antwort auf demselben Stand — und ein Urteil, was der Umweg über MCP gekauft hat.

Der Wissensdienst hat neben MCP eine gewöhnliche REST-Schnittstelle mit
OpenAPI-Beschreibung. Ein Assistent mit Terminal kommt also auch ohne MCP an
dieselben Daten. Die Frage ist nicht, ob es geht — sondern was es unterscheidet.

## Schritte

1. Startet eine neue Sitzung **ohne** den MCP-Server.
2. Gebt dem Assistenten die OpenAPI-Beschreibung des Wissensdienstes.
3. Stellt dieselbe Fachfrage wie in Aufgabe 03.
4. Beobachtet, was der Assistent tut, bis die Antwort steht — und wie oft er
   nachfragt oder danebengreift.
5. Notiert drei Unterschiede zum MCP-Weg.

## Prompt

```
Im Ordner oas/ liegt wissen.yaml, die OpenAPI-Beschreibung eines laufenden
Dienstes auf Port 8082. Beantworte mit curl gegen diesen Dienst: Ab wann zahlt
atra.dent bei Zahnersatz, und welche Wartezeit gilt im Tarif Komfort?
```

## Diskussion

Beide Wege führen zum Ziel — das ist das ehrliche Ergebnis dieser Übung und
kein Argument gegen MCP. Die Unterschiede liegen woanders: Wer beschreibt die
Schnittstelle, wer hält die Zugangsdaten, was passiert beim zehnten Dienst, und
was steht dauerhaft im Kontext. Sammelt die Beobachtungen für das Plenum.
