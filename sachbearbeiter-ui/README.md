# Sachbearbeiter-UI

The internal backoffice: a customer list, a customer record with master data,
Vertrag and claim history, plus a Tarifvergleich with a Beitrag calculator.

It sees and maintains everything, but it does so through forms — fixed masks on
fixed endpoints, with no agent in between. **That is its purpose in the lab.**
It is the control group: the same domain twice, once as a form and once as a
dialogue in the [Kunden-Chat](../kunden-chat/README.md). Operate the two side by
side and you see what an agent changes at this point, and what it does not.

There is one exception, and it is an action rather than a path to the data:
„Erneut prüfen lassen" nudges the Schadensfallagent — see
[below](#calling-the-schadensfallagent).

## Layout

SvelteKit with Svelte 5, Tailwind 4 and shadcn components over bits-ui. Fetching
data is entirely the business of `+page.server.ts`; the components are handed
finished data and fetch nothing themselves.

| Route                             | Content                                                                                               |
| --------------------------------- | ----------------------------------------------------------------------------------------------------- |
| `/kunden`                         | Customer list                                                                                         |
| `/kunden/neu`                     | Create a Kunde                                                                                        |
| `/kunden/[id]`                    | Customer record: master data, Vertrag, claim history                                                  |
| `/kunden/[id]/bearbeiten`         | Edit master data                                                                                      |
| `/kunden/[id]/schadensfaelle/neu` | Submit a Schadensfall                                                                                 |
| `/faelle`                         | Work queue: cases across all Kunden, status filter, refreshes itself every 5 s (auto-refresh)         |
| `/faelle/[id]`                    | Case detail: Rechnung, Positionen with Zahn, date and GOZ status, Bewertung, Bearbeitungsprotokoll, actions |
| `/faelle/[id]/protokoll`          | Every agent call on the case — each Protokolleintrag with all its model, tool and A2A steps           |
| `/tarifvergleich`                 | The four Tarife side by side, with a Beitrag calculator                                               |

## Three sources, three routes

The UI draws from three directions, and the separation is deliberate.

**The Kernsystem** supplies everything customer-related — master data, Vertrag,
Schadensfaelle. Access runs through a single interface `BackofficeApi`
(`src/lib/server/backoffice/api.ts`), which mirrors the contract
`oas/openapi.yaml`; `RestBackofficeApi` (`src/lib/server/backoffice/rest.ts`) is
the only implementation.

**The Rechenkern** supplies tariff master data (`basisbeitragMonatlich`),
Beitrag and Erstattung — `listTarife`, `calculateBeitrag` and
`calculateErstattung` in that same `RestBackofficeApi` address it over a base
URL of its own, without a key: the Rechenkern demands none.

**The Produktmodell** supplies the coverage detail of the Tarifvergleich —
Leistungsbereiche, Staffeln, which Quote and which Sublimit a Tarif carries.
These do not come over an API but straight out of `wissen/daten/tarife.yaml`
(`src/lib/server/tarifwerk.ts`) — the same file the Bedingungswerke are typeset
from.

## Start

**Through the lab** — `./start.sh sachbearbeiter-ui` from the repository root.
The script checks the Node version, installs the dependencies if needed and
starts on **5173**; `SACHBEARBEITER_UI_PORT` overrides the port.

**On its own** — `npm install && npm run dev` in this directory.

| Environment variable          | Default                        | Effect                                     |
| ----------------------------- | ------------------------------ | ------------------------------------------ |
| `KERNSYSTEM_URL`              | `http://localhost:8080/api/v1` | Base URL of the Kernsystem                 |
| `KERNSYSTEM_API_KEY`          | `atra-lab-2026`                | sent along as `x-api-key`                  |
| `RECHENKERN_URL`              | `http://localhost:8086/api/v1` | Base URL of the Rechenkern, no key         |
| `TARIFE_YAML_PATH`            | `../wissen/daten/tarife.yaml`  | Path to the Produktmodell                  |
| `SCHADENSFALLAGENT_URL`       | `http://localhost:8087`        | A2A address of the Schadensfallagent       |

**Tests** — `npm test` (Vitest) covers the formatting and parsing helpers in
`src/lib/format.ts`, the mapping of a finding to its Position and the findings
without a Position in `src/lib/positionen.ts`, the A2A call in
`src/lib/server/schadensfallagent.ts` and the actions in
`src/routes/faelle/[id]/`; none of it needs the Kernsystem, the Rechenkern or
the Schadensfallagent.

## Calling the Schadensfallagent

„Erneut prüfen lassen" on `/faelle/[id]` sets the case to `eingereicht` — if it
is already there, it stays there — and then calls the **Schadensfallagent**
(8087) directly: JSON-RPC `message/send`, the same A2A envelope as in the
Kunden-Chat, only without a stream
(`src/lib/server/schadensfallagent.ts`). The response appears as a notice at the
top of the page.

The button is offered on `eingereicht`, `in_pruefung`, `geprueft_freigabe` and
`geprueft_eskalation`. **`eingereicht` is the most important of the four**: that
is exactly the state a case sits in right after a customer has submitted it. On
the three decided states the button is absent, because there it would no longer
be a repeat but the reversal of a human being's decision.

As long as the SchadensfallPoller is running the call is not necessary — the
agent picks up submitted cases by itself, and the button merely saves the wait
for its next round. With `SCHADENSFALL_POLL_ENABLED=false` (the demo setting, in
which nothing is supposed to happen on its own) it is instead the **only** route
to a Pruefung. The messages on screen say which of the two it is, rather than
promising a poller that nobody may have started.

**`message/send` answers after 30 seconds — finished or not.** That is as long
as the SDK's A2A handler waits (`a2a.blocking.agent.timeout.seconds=30`); a
Pruefung in the lab takes a measured 35. The normal case is therefore HTTP 200
with the task on **`working`**: the response is over, the run is not. For as
long as that lasts the button reads „Der Agent prüft …" and stays disabled.

**A `working` is not a refusal** — and that is the class of bug this module has
been cleaned up over three times. Every state other than `completed` looks at
first glance like "it didn't work", and three of them in fact mean a Pruefung is
under way right now: `working` in the regular case, a timeout on a hanging
connection, and a lost claim on `in_pruefung`. The page frames all three as „Die
Prüfung läuft, Sie müssen nichts tun". Anything the module does not recognise
stays `abgelehnt`; reassurance on suspicion would again be a promise nobody
keeps.

The 90-second deadline in the client only guards the hanging connection after
that; in regular operation it never fires. Only if the service does not answer
at all does the case remain on `eingereicht`.

**A second press doubles up.** It does not abort the running Pruefung, it puts a
second one beside it; whichever finishes first is written, and the other loses
its result to the conflict. The button stays available on `in_pruefung` all the
same — the Sachbearbeitung may decide at any time — but the card and the notice
advise waiting for the Bewertung.

**A refusal is not always a refusal.** The same A2A state `rejected` carries in
one case "the case is on `in_pruefung`" (someone is working, sit back) and in
another "it is now on `genehmigt`" (decided, nothing more is coming).
`schadensfallagent.ts` tells them apart by the **state named** and not by the
phrasing — otherwise „Sie müssen nichts tun" would stand above a finished case.

**The header `x-kunden-id` carries the Kundennummer of the case.** The
Schadensfallagent rejects every A2A call whose Kundennummer does not match the
record — and one without the header just the same. The number therefore comes
from the Kernsystem's response and never from the form.

**Domain logic stays outside here too.** The surface sends the case number over
and displays state, prose and recommendation the way the service words them.
Whether a case is to be cleared is the agent's decision.

## Decided, and why

**One interface, one implementation.** The UI was first developed against a
contract nobody had implemented yet — for which there was a `MockBackofficeApi`
with fixtures behind the same `BackofficeApi` interface. The Kernsystem and the
Rechenkern have since been built and the mock is gone
(`src/lib/server/backoffice/index.ts` unconditionally creates a
`RestBackofficeApi`); the interface stays all the same, because it keeps the
adapter exchangeable should that change again.

**No domain logic in the UI.** What gets reimbursed is not the form's decision.
The UI displays what it is given and sends back what was entered. Otherwise the
reimbursement rules would exist twice — once here and once in the Rechenkern —
and nobody would know which of them holds.
