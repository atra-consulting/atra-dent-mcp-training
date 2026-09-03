# Architecture

This document says which building blocks the lab consists of and how they play
together. It records the state of the whiteboard the setup was designed on.
What holds in the domain is written down in the Bedingungswerke under
`wissen/dokumente/` and in the product model `wissen/daten/tarife.yaml`; here it
is only ever about the cut between the systems.

![Architecture of the lab](assets/architecture.svg)

The colours carry meaning. **Outlined in blue** are systems and user
interfaces — things that hold state or display it. **Filled orange** are
agents — things that decide which call comes next. The small marks on the edges
are interfaces, and where a mark sits says where the boundary runs: a mark on
the top edge of a box means *this is how you get at this box*. The dashed frame
groups what runs together. Everything inside it is one service; only the
Arztservice lies outside.

And it is orange, not blue, which is not cosmetics: precisely because it is an
agent and not a tool, you address it over A2A and not over MCP. Its Agent Card
says so itself — „Dieser Dienst ist selbst ein Agent." A blue-outlined box at
the end of an A2A arrow would contradict the legend of this picture.

That frame was a teaching device for a long time — drawn so you could see where
a service boundary would run. Since 21 August 2026 it is a real one: the
Arztservice has its own repository, its own GCP project and runs on someone
else's infrastructure. The cut did not have to change for that; it merely came
true. That is why the Arztservice now sits inside a second dashed frame — the
same sign, but for a different operator.

## The building blocks

The three domain services (the chat UI, the Sachbearbeiter-UI and the agents
aside) sort themselves along a second axis, crossing the first one, "knows a
Kunde or does not": **what is the case, what follows from it, what does it
mean.**

| Service (port) | Axis | Content | State |
|---|---|---|---|
| Kernsystem (8080) | What is the case? | Kunden, Vertraege, Schadensfaelle, Rechnung extraction | holds state, knows the Kunde |
| Rechenkern (8086) | What follows from it? | Tarif master data, Beitragsberechnung, Erstattungsberechnung | stateless, knows no Kunde |
| Wissensdienst (8082) | What does it mean? | Bedingungswerke, semantic search, GOZ-Pruefung, Tarifempfehlung, Beratungsleitfaden | stateless, knows no Kunde |

What someone *has* is the Kernsystem's answer. What *follows* from it — a
Beitrag, a monthly price — the Rechenkern computes, without knowing who is
asking. What that *means* in substance is in the Wissensdienst. The middle
question had no place of its own for a long time: the Beitrag was computed in
the Kernsystem although it needs no Kunde — the first axis mixed up with the
second, which the Rechenkern untangles.

8083 (the Kernsystem's fallback port, see below), 8084, 8085 and 8087
(Orchestrator, Beratungsagent and Schadensfallagent) and 5173/5174 (the two user
interfaces) lie outside this axis. The Arztservice is no longer on the list: it
runs on someone else's infrastructure behind a URL, and the URL is all the lab
knows of it. The full port list is in the [README.md](README.md).

### Sachbearbeiter-UI

The internal backoffice. A list of Kunden, the file of a single Kunde with
master data, Vertrag and claim history, plus a Tarifvergleich with a Beitrag
calculator. It sees and maintains everything, but it does so through forms:
fixed screens against fixed endpoints, with no agent in between. That makes it
the control group of the lab — the same domain, once as a form and once as a
conversation.

It talks REST per `oas/openapi.yaml` and `oas/rechenkern.yaml`, wrapped behind a
`BackofficeApi` interface. Tarifvergleich, the Beitrag calculator and the
Erstattungsberechnung in the Schadensfall dialog call the Rechenkern; everything
else calls the Kernsystem.

Exactly one path leads out of it, and it is described under the claim flow
below: "Erneut prüfen lassen" calls the A2A skill `fall_pruefen`, with
`x-kunden-id` taken from the loaded case. Otherwise this UI speaks to no agent
at all.

### Chat UI

The surface for customers. A full-screen chat with example questions, streamed
answers and a demo login that establishes the link to a Vertrag. It knows no
domain logic. Its only conversation partner is the Orchestrator, which it
reaches over A2A through a server route of its own and an `AgentClient` adapter.

**Exactly one.** The Beratungsagent cannot be reached from the UI, and that is
not laziness: a second entrance would be a shortcut past the routing decision,
and it would put the question of who is responsible into a user interface.

The Kundennummer leaves as the header `x-kunden-id` and never as part of a
message; the conversation thread is held by a server-issued cookie that becomes
the `contextId` in the protocol. The route forwards no master data — the
Beratungsagent reads the file itself over MCP. Beside all this there is still a
mock adapter (`AGENT_CLIENT=mock`) so the UI runs without the Java services.

Below the answer bubble it shows what the domain agent sent along in the data
part: at most two **views**, as a table, as bars or as a card. Here too it
interprets nothing — it checks `kind`, formats, and displays. The mock delivers
none: it calls no tools and would have nothing to build one from without making
it up.

#### The debug trace

A checkbox above the transcript reveals what happens between question and
answer: every A2A hop, every MCP tool call with its arguments and its result,
every model call with its decision — and above every answer, who wrote it. That
is what this lab is actually about, and without this view you simply cannot see
it from the front.

It is carried by the `TracePoint` in the module `agenten/a2a`. It is the payload
of the `StatusChannel` — which used to carry only a string, and with it sender
and protocol were already lost on the first hop. A point comes into being **at
the place that makes the call**, and travels forward as a data part beside the
status text in the interim update. The `SdkAgentClient` passes it through
without overwriting the sender: what the Beratungsagent did stands in the trace
under its name. Same rule as for the status texts in `ObservedTools` — nothing
is derived or reconstructed in the UI, because a guessed trace would look just
like a real one.

Because the point is a data part and not a new event, a caller that reads only
text parts gets exactly the same status line as before.

**It also shows what is deliberately *not* going out.** The fields of an MCP
point hold the raw result, and that includes the hits from
`beratungsleitfaden_suchen` — internal guidance that the confidentiality guard
keeps out of the answer. For a lab that is right: without this spot there would
be no way to show what the guard is protecting against. For a real customer
channel it is a leak, and anyone adopting this setup leaves the raw results
where they arise.

### Orchestrator

Takes the Anliegen and decides who handles it. It answers nothing in the domain
itself. Its job is placement: if it is about choosing or changing a Tarif, the
Beratungsagent takes over; if it is about a Rechnung, the Schadensfallagent
does.

### Beratungsagent

Conducts the Beratung. It works out what the caller needs, explains cover along
the Bedingungswerke and recommends a Tarif. For that it needs three systems: the
Kernsystem says what someone has today, the Rechenkern computes what a change
would cost, the Wissensdienst says what that means in substance and where it is
written down. How the conversation itself is run comes from the
Beratungsleitfaden — internal material that is never quoted to a customer.

It conducts **two conversations that are only the same from the outside**:
Bestandsberatung for a customer with a running Vertrag, and Neuberatung for an
Interessent with no file. For the one, "which Tarif suits me" is a question
about switching that starts from today's Tarif; for the other it is a question
of choice that cannot be answered without an Eintrittsalter. Both are skills of
the *same* agent, because the header `x-kunden-id` settles which of the two it
is — two agents would have turned that into a decision a model makes.

It **writes one thing**: the Kontaktdaten of the logged-in customer — Anschrift,
E-Mail-Adresse, Telefonnummer. Everything else in the Beratung stays a
recommendation. The line runs where the consequence does: an Anschrift changes
no obligation on either side and is correctable, whereas Name, Geburtsdatum,
Vorversicherung and fehlende Zaehne are Antragsangaben that Beitrag, Wartezeit
and Ausschluesse hang on, and `meinen_tarif_wechseln` would execute a
Nachversicherung without mentioning the health check, the Wartezeit or the
Zahnstaffel. So the one write costs a turn on purpose: the agent reads the
complete new entry back and asks — `input-required`, the same state as any other
question — and only the confirmation writes. The confirmation is a rule of the
prompt; that only this one tool is reachable at all is a rule of the allowlist,
and those two are not the same kind of guarantee.

A turn ends in one of **four outcomes**, and A2A has a state for each: answered
(`completed`), a question back (`input-required`, not final — the case waits), a
refusal (`rejected`) and a fault (`failed`). Keeping the last two apart is not
hair-splitting: a refusal is a statement about the Anliegen, a fault is a
statement about the service. Question and refusal arise in the agent's model and
leave it through signal tools, so nobody has to guess them out of prose —
[docs/agent-outcomes.md](docs/agent-outcomes.md) explains it.

Beside the prose it puts into the data part of its artifact what the UI may show
below the answer: **at most two views**, each with a `kind` field — a Tarif
comparison, a Beitrag comparison, the ranking of a recommendation, the Vertrag,
the claim history, the Kontaktdaten as they stand after a change. They are assembled in the `ViewCollector`, and specifically
**from the raw results of the tools that were called, never from the answer
text**. Same rule as for the evidence, and it weighs more here: a wrong
Fundstelle catches an attentive reader's eye, a wrong number in a neatly typeset
table catches nobody's. All the model decides is which tool to call.

Two limits therefore live in code and not in a prompt:

- **From a Tarifempfehlung only `rang`, key and display name travel.** The
  reasoning, the short formula, the grounds of need and the duty to point things
  out all come from the internal Beratungsleitfaden. The `ConfidentialityGuard`
  inspects the answer text — a data part would sail past it untouched. The
  `reihenfolgeplatz` stays out too: `rang` is the outcome of the needs analysis,
  the reihenfolgeplatz is the Leitfaden's `EmpfehlungOrder`.
- **Only the Kernsystem knows which column belongs to the customer.** That comes
  from `mein_vertrag_lesen`; if no Vertrag was read in this turn, the table
  carries no marking. Nothing is inferred — a wrongly marked column would be
  worse than an unmarked one.

What was assembled the collector reports as an internal trace point of its own,
`views`, carrying the data part exactly as it goes out — not a description of
it. Without it the debug trace would end at the tool's raw result, and what
became of it would be recorded nowhere.

### Schadensfallagent (8087)

Handles submitted Belege. It does not read the Rechnung itself — date, sender
and the individual Positionen with amount, description, Zahn, Behandlungsdatum
and, where the Rechnung states one, Gebuehrennummer, all arrive ready-made from
the Kernsystem. It checks the numbers against the Tarif, assigns them to
Leistungsbereiche, recomputes the Erstattung in the Rechenkern and obtains an
expert opinion from the externally hosted Arztservice — the one call in the lab
that crosses the service boundary out of a Schadensfall. At the end stands a
**recommendation**: `geprueft_freigabe` or `geprueft_eskalation`.

That the extraction does not sit with it is the more interesting part of the
cut: extraction is deterministic and therefore belongs where the same file
always yields the same thing. What a model makes of it begins with the
assignment — and that is exactly where it becomes contestable.

**It is the only service in the lab that starts on its own.** A poller looks for
cases in status `eingereicht` on a fixed beat and claims each of them by
compare-and-set over REST (`in_pruefung`; a second caller gets a `409` and moves
on). The A2A skill `fall_pruefen` is the same path on demand — only with a door
in front of it: it requires `x-kunden-id` and refuses without that header, in
the same words it uses for someone else's case. The poller does not go through
that door; it invokes the check directly. Fetching, claiming and rolling back
deliberately run over REST and not over MCP: those are infrastructure steps, and
a tool for them would be a tool a model could call.

**Between the model's verdict and the Kernsystem stands a guard.** The model
submits its proposal through a signal tool; `schadensfall_bewerten` is not
offered to it but reserved for the code. Only then does the deterministic
`ApprovalGuard` check the proposal against the raw results of the tools; the
`BewertungWriter` writes it afterwards. The guard only ever tightens: a
Eskalation proposed by the model never becomes a Freigabe. Approving, rejecting
and paying out it cannot do in any case — the tool does not even know those
states.

**It checks the question too, not just the answer.** A tool answers reliably
whatever you ask it: the Rechenkern computes every Tarif, every date and every
Position you hand it, and the Wissensdienst checks the Gebuehrennummern against
whichever Tarif it is told. The model picks both, and the Position texts come
out of a submitted Rechnung. A guard that looks only at results will therefore
bless a cleanly computed amount for somebody else's Tarif. **Six rules** hold
the **arguments** of the calls against the Vertrag and the file — Tarif,
Versicherungsbeginn, day of treatment, Positionen and their Leistungsbereich. A
seventh holds an argument against nothing at all: `unfallbedingt` makes the
Zahnstaffel fall away in the Rechenkern and thereby raises the amount, but it
appears neither in the case nor in the OAS nor in the check prompt — a flag
that has been set is the finding. On top of that comes a catch-all finding that
holds the Erstattung below the invoice total, because `verbrauch` cannot be
checked independently without rebuilding the Rechenkern's domain logic.

**A rule that silently does not run is worse than no rule.** Two of them did
exactly that: the general Tarif Wartezeit dropped out as soon as the model had
skipped `tarife_vergleichen`, and the duplicate check ran over a list of cases
that is empty without a call too. Both now report "not checked in this turn" as
a Eskalationsgrund — the same stance as for an unread Vertrag. The order in the
prompt says "no step is optional"; but it is the rule that enforces it, not the
prompt.

Its interesting case is not the clear yes but the Eskalation: the GOZ-Pruefung
knows four states, and two of them — `NICHT_BESTIMMBAR` and `UNBEKANNT` — are
neither a yes nor a no. Passing them on as "not covered" is a wrong answer.

**And counting them stubbornly as a defect means never releasing a Rechnung
again.** GOZ 0010 („Eingehende Untersuchung") appears on practically every
dental invoice and deliberately has no Leistungsbereich of its own — it shares
the fate of the Behandlung it belongs to. The calibration run of August 2026
proved the point: fourteen of fourteen demo cases came back with `GOZ_UNCLEAR`
without anything being broken. Since then it is the **model** that answers the
question which Behandlung such a Position belongs to, and the guard checks the
answer: the named Leistungsbereich must be that of another Position on the
**same Zahn** which the Wissensdienst itself reported as `ENTHALTEN`. If the
Position carries no Zahn — Untersuchung, Mundhygienestatus, PZR and
fluoridation are whole-jaw services — the entire Rechnung serves as the anchor
space. If something uncovered sits on the same Zahn at the same time, a human
decides. Exactly the same mechanism has carried the Positionen **with no number
at all** (material, Labor, patient-requested extras) since 2026-08-19.
`UNBEKANNT` and "no finding" remain a defect unconditionally, and a Rechnung
without a single covered main service remains a Eskalation. Details in
`wissen/README.md`, the construction in [agenten/README.md](agenten/README.md).

Its full trace lands under `--tracelog` in `tracelog/schadensfall.jsonl` — every
model call, every tool call and the A2A hop to the Arztservice. That hop is the
only entry there that leaves the machine; since the move it carries a
`run.app` address beside it and no `localhost` port number. An abbreviated
version of the same trace lands on the case itself, in the
`bearbeitungsprotokoll`.

### Kernsystem

Runs the contract world: master data of the Kunden with their embedded Vertrag,
and the claim history with all cases and their Positionen. It knows the Kunde
and says what he has. A Schadensfall passes through seven states, and which
transition is allowed the Kernsystem enforces itself — not the caller.

On top of that comes **Rechnung extraction**: structured data is won from a
dental invoice marked as PDF/A — deterministically, without OCR and without a
model, using a generic rule set applied identically to every document. It is the
dependable counterpart to the agentic reading of the same Belege: an agent may
interpret, the extraction may not. Documents without a PDF/A marking are
rejected with a generic error message.

Tarif master data and free-standing Beitragsberechnung the Kernsystem no longer
runs itself since the move to the Rechenkern — they know no Kunde and belong
there. A single tool stays here, `mein_beitrag_berechnen`: it reads the date of
birth from the calling customer's file and delegates the arithmetic to the
Rechenkern, see below. The `erstattungsbetrag` of a case does not arise here
either: it comes in from outside as a PATCH, from the Sachbearbeitung.

The binding contract is in `oas/openapi.yaml`. The same domain logic sits
additionally as nine MCP tools on `/mcp` — same process, same port, a different
cut. Details in `kernsystem/README.md`.

### Rechenkern

Computes what **follows** from a customer's master data, without knowing a
customer itself: the Tarif master data, the monthly Beitrag for a date of birth,
a Tarif and a desired start, and the Erstattung for a Schadensfall — Quote,
Sublimits, GKV-Vorleistung, Selbstbehalt and Zahnstaffel in the order the
contract prescribes, against a Vorverbrauch supplied with the call. Stateless
and with no outgoing call — same input, same answer, always; nothing is stored.

It is the answer to a question the old layout had no place for: the Beitrag was
computed in the Kernsystem although it needs no Kunde, and the Tarif master data
sat there a second time next to the product model. Both move here, together with
the Altersbaender from `beitraege.json`. For the Erstattungsberechnung the
Rechenkern additionally reads the Tarif rules (Quoten, Sublimits, Staffeln)
straight from the product model `wissen/daten/tarife.yaml` — through the same
parser as the Wissensdienst (`produktmodell/`), not through a copy of its own.

Contract in `oas/rechenkern.yaml`, exposed under `/api/v1` as REST and as four
MCP tools on `/mcp`: `tarife_auflisten`, `tarif_lesen`, `beitrag_berechnen`,
`erstattung_berechnen`. None of them takes a Kundennummer, and there is no
`x-kunden-id` header — the service has no Mandant because it holds no records.
For the same reason it demands **no** API key: it holds no personal data that
would need protecting.

Two callers: the Kernsystem, for `mein_beitrag_berechnen`, and the
Beratungsagent, as a third MCP connection beside Kernsystem and Wissensdienst.
If the Rechenkern is down, the Kernsystem answers its one tool with an
intelligible refusal instead of computing on its own — a second place that
computes would be exactly the duplication this cut removes.

### Wissensdienst

Runs the product knowledge: the Bedingungswerke of the four Tarife, the
Tarifvergleich and the GOZ directory. Two capabilities build on that — a
semantic search over the conditions that cites every Fundstelle down to the
paragraph, and the checking of Gebuehrennummern against a Tarif. That check
knows four states, and the fact that `NICHT_BESTIMMBAR` is not a rejection among
them is one of the lessons of the lab. The Wissensdienst also serves the
documents themselves, as PDF for display and as HTML for further processing.

It knows not one single customer. It does not say what someone gets, but what
the product means.

Beside that it runs the internal advisory material: the Beratungshandbuch for
running a conversation and a rule section from which a Tarifempfehlung can be
derived. Both live in a **separate** search space — separated by construction,
not by a filter. There are two vector indexes, and neither can serve a passage
of the other because it does not contain it. The reason is that "Wartezeit",
"Selbstbehalt" and "fehlende Zähne" occur in both bodies of material: on the
same question the Handbuch has an answer for the adviser and the Bedingungswerk
one for the Vertrag. A filter that somebody forgets once will then serve a sales
instruction as a contractual condition — and nobody notices, because the answer
looks plausible.

For the same reason the two advisory tools `beratungsleitfaden_suchen` and
`tarifempfehlung` have no REST counterpart. Over MCP they are reached by an
agent that is on the adviser's side anyway; an open HTTP endpoint would be
exactly the route by which internal guidance ends up with the customer.

Contract in `oas/wissen.yaml`, construction and reasoning in
`wissen/README.md`.

## Why every system offers both REST and MCP

All three systems offer the same domain logic over two protocols. That is not
redundancy, it is the subject of the lab.

REST serves people and user interfaces. The resources are fixed, so are the
calls, and whoever built the screen decided beforehand which endpoints come up
in which order. MCP serves agents. The same operations become tools with a
description, and the decision which tool is called with which arguments falls
only at runtime, in the model.

That shifts what makes an interface good. With REST the schema counts; with MCP
the description counts on top of it, because it is the only thing a foreign
model gets to see before it calls. A description must therefore also say what a
tool does *not* answer — otherwise the model calls it in the wrong place. See
the two tools in `wissen/dienst/src/main/java/consulting/atra/wissen/mcp/`.

Both entrances call the same domain logic and return the same objects. They
cannot drift apart: what changes in the domain changes for both.

### Where the Kundennummer sits

At the Kernsystem the difference shows up in a second place, and that one is the
more uncomfortable. The Wissensdienst knows not one customer; the Kernsystem
knows them all. Over REST the Kundennummer sits in the path —
`GET /kunden/10001` — and whoever holds the API key gets at every file. The
boundary is drawn by the calling application, and that works, because there it
is settled before build time which call comes when.

Over MCP nothing is settled. A model decides at runtime which tool it calls with
which arguments — a boundary that depended on that decision would be no
boundary. So the Kundennummer moves out of the arguments and into the header
`x-kunden-id`: no tool accepts it, not even optionally, and a Schadensfall
belonging to somebody else is answered exactly like a number that does not
exist.

That shifts the cut of the operations, too. Searching across all Kunden and
creating a Kunde do not exist as tools — they do not fit an identity that is
fixed from outside. The single `PATCH /kunden/{id}` becomes two tools with
different intent that together can do less than the PATCH: risk-relevant
application data cannot be changed over MCP at all, and setting the status to
`inaktiv` would be a termination. A tool set is not a translation of the
contract but a decision of its own about what the other side should be able to
do.

## A2A: protocol and SDK stack

Orchestrator, Beratungsagent and Arztservice all three speak A2A in the same SDK
generation — the first two in this repository, the third in a foreign one since
the move:
`org.springaicommunity:spring-ai-a2a-server-autoconfigure:0.3.0`
over `io.github.a2asdk:a2a-java-sdk-{spec,server-common,client,client-transport-jsonrpc}:0.3.3.Final`
(packages `io.a2a.*`), protocol version 0.3. Pinned to this line on purpose: the
1.x generation of the SDK (group `org.a2aproject.sdk`) renames the packages and
speaks a different protocol on the wire — source- and protocol-incompatible with
what the autoconfiguration binds here. Details and the verified references in
[docs/superpowers/specs/2026-08-12-a2a-sdk-spike-befunde.md](docs/superpowers/specs/2026-08-12-a2a-sdk-spike-befunde.md).
That the pin has to stay identical across the repository boundary is not
tidiness: it is the condition for the call happening at all.

For a long time the Arztservice supplied only two beans for this — `AgentCard`
and `AgentExecutor` — and the autoconfiguration took care of the rest
(controller, task store, event queue). In the cloud there are more: a filter
that checks `x-api-key` in front of `POST /` and `/tasks/**`, and two Firestore
beans for the task store and the record of open questions. The task store
displaces the autoconfiguration's default without switching it off — that
default carries `@ConditionalOnMissingBean`. Beratungsagent and Orchestrator
supply four: the same two, plus two controllers of their own
(`MandantenMessageController`, `StreamController`) that replace the community
version of the autoconfiguration — the one to lift the header `x-kunden-id` into
the `ServerCallContext`, the other to offer `message/stream` at all (the
autoconfiguration brings no endpoint for it; the Arztservice needs neither: no
Mandant, no streaming). The Agent Card itself stays a YAML file per service
(`agenten/cards/*.yaml`; at the Arztservice it sits on the classpath in its own
repository instead of in the file system, because the deployment unit there is
an image and a path next to it would be a source of error the lab setup never
had); an `AgentCardLoader` loads it into the SDK type and only fills in the
absolute `url` on delivery, from that service's base-URL property.

| Path | Purpose |
|---|---|
| `GET /.well-known/agent-card.json` | the Agent Card — at the Arztservice the only domain path **without** a key, see below |
| `POST /` | JSON-RPC 2.0, method `message/send` — the synchronous normal case. At the Arztservice only with `x-api-key` |
| `POST /` with `Accept: text/event-stream` | method `message/stream`, SSE interim updates — **only** Beratungsagent and Orchestrator, through an SSE controller of their own: `spring-ai-a2a-server` 0.3.0 brings no endpoint for it |
| `GET /tasks/{id}`, `POST /tasks/{id}/cancel` | fetching and cancelling tasks — **REST form**, not a JSON-RPC method. The method `tasks/get` known from A2A does not exist in this SDK generation (spike finding 4); an SDK client calling it would come up empty. At the Arztservice likewise behind the key, and `cancel` takes effect only on the local instance there — see below |
| `GET /actuator/health` | operations, not a domain entrance |

The Arztservice does not offer `message/stream` (`capabilities.streaming: false`
in its card) — a request there is synchronous and blocks until its answer is
ready, but for at most thirty seconds. Its card promises no push notifications
either; if you neither stream nor push, you keep the promise small and therefore
keep it.

**Routing in the Orchestrator runs through a tool, not through configuration.**
The Orchestrator holds no domain tool itself, but exactly one for routing:
`nachricht_an_agenten` (`AgentTool`), which a language model calls by tool
calling when it decides which subagent is up. The available subagents the
Orchestrator discovers for itself at startup — it fetches the Agent Card under
every configured base URL (`agenten.subagents.catalog`) via
`/.well-known/agent-card.json` and writes name, description and skills into the
system prompt (`SubagentCatalog`, log line `Agent entdeckt: '<Name>' unter
<URL>`). Two things the tool deliberately does not carry as parameters, so they
cannot come from the model: the Kundennummer travels in the `TaskContext` (a
`ThreadLocal` for the duration of one conversational step), and the open thread
to a subagent — which `taskId`/`contextId` continues a pending question — lives
in the `ThreadRegistry`, indexed by (conversation, agent).

**The Mandantengrenze stays where it is, in the transport.** `x-kunden-id`
travels as a header on every hop — chat UI → Orchestrator → Beratungsagent →
MCP connection to the Kernsystem — never as part of a message and never as a
tool argument; details under
[What an agent brings together](#what-an-agent-brings-together--and-what-gets-lost)
further down and in [agenten/README.md](agenten/README.md).

## Two flows

### Tarifwechsel

1. The customer asks in the chat whether switching is worth it.
2. The Orchestrator recognises a Beratung question and hands over.
3. The Beratungsagent reads today's Vertrag from the Kernsystem and has the
   Beitraege of the Tarife in question computed.
4. From the Wissensdienst it fetches what differs in substance — with the
   Fundstelle, so the statement is evidenced.
5. It answers with a recommendation and the evidence. If a cheaper Tarif fits
   just as well, it says so; the Beratungsleitfaden demands that explicitly.

The catch is in step 3: a Nachversicherung brings a fresh health check, a fresh
Wartezeit and a Zahnstaffel that starts over. An agent that only compares
Beitraege misses exactly that.

### Claim

1. The customer uploads a dental invoice into the chat. The chat UI streams the
   PDF to the Kernsystem, which reads it out deterministically — text layer and
   fixed parser rules per layout, no OCR and no model. Back come the invoice
   header, the Patient and the Positionen, and nothing beyond what is on the
   Rechnung.
2. The result travels along as a data part. The Orchestrator passes it
   **unevaluated** to the Beratungsagent: it recognises without a model call
   that a Beleg is attached (`belegAttached`), and the data part itself travels
   past the model in the TaskContext — the model sees only a note. So the one
   who reads the Beleg is the agent that needs it, not the one that passes it
   on.
3. The Beratungsagent creates the case — status `eingereicht`. Nothing is
   checked in doing so; acceptance promises no Erstattung. **It submits the
   entire Rechnung:** every line, including material, Labor (§ 9 GOZ) and
   patient-requested extras that carry no Gebuehrennummer. That is why the case
   amount *is* the invoice total. A Position without a number does not bring its
   Leistungsbereich with it; it gets one only during the check, from the main
   Behandlung on the same Zahn.
4. The Schadensfallagent fetches the case itself and consumes these Positionen.
   It extracts nothing further: reading a Rechnung twice, once
   deterministically and once through a model, would mean keeping two truths.
5. It checks the Gebuehrennummern against the Tarif and gets one of the four
   states back per number, recomputes the Erstattung in the Rechenkern and asks
   the Arztservice for an expert opinion. This one step goes onto the network
   and to a foreign operator — the four before it stay inside the service. If
   the opinion does not arrive, that is a Eskalation (`ARZT_UNAVAILABLE`) and
   not a silent Freigabe.
6. The approval guard checks the model's proposal against these raw results —
   and against the arguments they were obtained with; then the
   `BewertungWriter` writes the Bewertung into the case: Freigabe recommended,
   or Eskalation with reasons. `UNBEKANNT` is always a Eskalation and never a
   rejection. `NICHT_BESTIMMBAR`, by contrast, is a **question**, and the model
   answers it: it names the Leistungsbereich of the Position's main Behandlung,
   and the guard verifies that this main Behandlung is on the same Rechnung and
   is `ENTHALTEN`. Otherwise it stays at `GOZ_UNCLEAR`.
7. Deciding and paying out happen in the Sachbearbeiter-UI. That is also where
   the way back leads from: "Erneut prüfen lassen" sets the case to
   `eingereicht` and then calls the A2A skill `fall_pruefen` — the same check
   path the poller takes, just on request.

Belege to practise on are ready in `submissions/rechnungen/pdf/` — the same case
data in five different practice styles, with the case file beside each PDF as
ground truth. They are checked in, so nothing has to be built to submit one.
What produced them sits in `generator/rechnungen/`, outside the running lab; its
form (`generator/rechnungen/frontend.py`) calls the Kernsystem hard-wired on
8080, and that is why 8083 stays free as the Kernsystem's fallback port.

## The Arztservice

The Arztservice is the only building block outside the service boundary. It is
hosted elsewhere and is a genuine black box: there is no contract about its
insides, only about what it can do. Four things it can do — answer a Fachfrage,
judge the Positionen of a dental invoice, assess a health declaration, and
agree an appointment in a practice calendar. The fourth arrived with card
version `3.1.0` and is the only one of them that runs over several turns.

**Since 21 August 2026, "hosted elsewhere" is no longer "imagine it that way".**
The service lives in its own repository
([atra-arzt-service](https://github.com/atra-consulting/atra-arzt-service)), in
its own GCP project (`atra-arzt-service-prod`), and runs on Cloud Run in
`europe-west1` at `https://atra-arzt-service-lqryukukoa-ew.a.run.app`. The lab
knows of it is one entry under `agenten.subagents.catalog` — its address and
its key together. The callers are the Orchestrator, which routes to it
like any other agent it has discovered, and the Schadensfallagent, whose tool
`arzt_befragen` binds to whichever discovered agent offers the skill
`rechnung-beurteilen`.

**Neither agent knows the Arztservice by name.** Both take a list of agent
addresses, fetch whatever card is there, and decide from the card what the agent
can do — the Orchestrator by putting its skills into its system prompt, the
Schadensfallagent by looking for one skill in particular. Delete the address and
the capability is gone: the Orchestrator stops offering it, and the
Schadensfallagent stops being given the tool, so every Schadensfall escalates
with `ARZT_UNAVAILABLE` instead of quietly passing without a Fachauskunft. That
is what makes wiring a foreign agent an exercise rather than a code change.

**There is no copy left to fall back on.** `arztservice/` has disappeared from
this repository; whoever does not set URL and key has no Arztservice, only
`ARZT_UNAVAILABLE`. That is the point of the exercise: a service boundary
with another instance of your own still standing behind it is no boundary.

**Driven through, not asserted:** on 21 August 2026 Schadensfall 50073 came in —
two implant insertions under GOZ 9010 on the same Zahn 36, four weeks apart. The
Schadensfallagent called Cloud Run (a `200` from a `Java-http-client` in the
access log), the Arztservice found the duplication, and the case ended on
`geprueft_eskalation` with the reason `ARZT_FLAGGED`. At that point the local
copy was still lying beside it and was not running.

The access is A2A and not MCP — but the reason is not who hosts it.

**MCP assumes the other side is a tool: one call, one result, no intent of its
own.** The Arztservice is itself an agent. It may ask back when the Positionen
are missing, and it decides for itself how it arrives at its answer. A call
becomes a conversation between two parties that know each other only by their
promised capabilities. That is the axis on which the choice between MCP and A2A
is made.

With `termin-vereinbaren` that stopped being an argument and became a
requirement. Booking an appointment takes several turns — the service offers
three slots, reads the chosen one back, and only then books — and every one of
them has to carry the same `taskId`. A tool call cannot do that. It is also the
first place where `completed` alone says nothing: the same state carries
`terminbestaetigung` when a slot was booked and `terminauskunft` when none was
free, which is why `SubagentResponse` now carries the artifact name and the
Orchestrator's result marker names it.

Foreign hosting is a **second, independent** reason, and it applies to the
Arztservice on top: inside the service you can adjust interfaces whenever
something does not fit — one commit, one deployment, done. Across the boundary
you cannot.

Until the move that sentence was a thought experiment; there was nowhere to test
it, because the Arztservice ran as an eighth process on the same machine. Now it
can be read off the price: card version `2.1.0` became `3.0.0`, because access
now demands a key and a third skill was added. Inside the service that would be
two lines and a restart. Across the boundary it is a breaking change you have to
announce — and the card version is the announcement.

That the two reasons are different can be seen at the Beratungsagent. It lies
*inside* the service boundary and speaks A2A all the same — because it has the
same property: if the Eintrittsalter is missing it asks back instead of
guessing. A tool guesses or fails; an agent asks. Conflate the two axes and you
conclude that everything of your own must be MCP, and then you rebuild an agent
as a tool.

### The door is shut, the contract stays open

`POST /` and `/tasks/**` demand an API key in the header `x-api-key`; the Agent
Card declares it as an `apiKey` security scheme, so that a foreign agent reads
it out of the contract and not out of an email. The card itself, `GET /` and
`/actuator/**` still answer without a key.

**That is not a forgotten corner but the condition for the rest holding.** If
the card lay behind the key, a foreign agent would never learn there is one — it
would get a 401 to a question it never asked. Access protection that also
encloses its own explanation is not protection: it is a closed door with no
nameplate.

On the lab side that costs one line: `AGENTEN_ARZTSERVICE_API_KEY`. The key
travels like the Kundennummer, as a header and never as part of a message — same
route, same reasoning: what a model must not be able to set belongs in the
transport.

### State across an instance change

The A2A task store and the record of open questions — the note of which subagent
asked one — have lived in Firestore since the move and no longer in process
memory. Cloud Run scales without session affinity; without this step the answer
to a question regularly landed on an instance that knew nothing of the question,
and the service replied in the wrong role. A conversation now survives an
instance change.

**Two things this explicitly does not lift**, and neither of them therefore
appears in the Agent Card:

- **`tasks/cancel` acts only on the instance currently running the task.** What
  is persisted is the state, not the running agent — that lives in the process.
- **Push notifications are off** (`pushNotifications: false`).

What is not promised need not be kept; what is promised must be. Across a
service boundary that is the whole difference.

### What is inside the black box

From outside, the Arztservice is an edge: an Agent Card, a JSON-RPC path, a key
in the header, three promised capabilities. Behind it lies the same construction
as in our own service — an orchestrator that places things, and three subagents
that do the work. That a calling agent sees none of this is the point: it knows
the promise, not the build.

**What follows is therefore a quotation and not an interface.** The source for
it is in
[atra-arzt-service](https://github.com/atra-consulting/atra-arzt-service) and no
longer in this repository — you can read it, you cannot rely on it. It stands
here anyway, because otherwise the lab would lose one of its three lessons: you
have to have looked inside once to understand why you must not depend on looking
inside.

| | |
|---|---|
| **Orchestrator** | Places every message and distributes it. Three stages, cheapest to dearest: open question → structured fields → model call |
| **Facharzt** | Answers questions of fact. A language model in an expert role — it invents |
| **Rechnungsbeurteiler** | Judges the Positionen of a dental invoice: plausible or conspicuous, usual or questionable. Also a language model, but in an assessor's role |
| **Risikoeinschaetzer** | Assesses a health declaration: four attributes, each a value or `NICHT_BEURTEILBAR`. It assesses and does not decide — it is never even shown the target Tarif or the thresholds, or it would compute towards the outcome instead of towards the finding |

**The difference between the three subagents is intended, and it does not lie in
the technology.** All three are language models; the difference is in the
prompt. The Facharzt answers with assurance and without asking back, even when
information is missing or the question is outside its field — no questions, no
caveats, but concrete numbers. The Rechnungsbeurteiler justifies every
assessment, judges conservatively in case of doubt, and asks back when the
Positionen are missing. The Risikoeinschaetzer goes furthest in the other
direction: for it, `NICHT_BEURTEILBAR` is the most valuable finding and not the
weak answer — exactly what the Facharzt's role forbids. A prompt sets a stance,
not a truth; and that the same model appears three different ways in the same
service is the shortest proof of it.

**That is the second lesson of the lab, and it mirrors the first.** The
Wissensdienst teaches from within that `NICHT_BESTIMMBAR` is not a rejection —
pass the state on as "not covered" and you answer wrongly. The Arztservice
teaches from outside that a fluent answer is not evidence. Both times the
mistake is the same: an agent passes on something it has not checked. The only
sign of it sits in the `data` part of the answer; the prose does not carry it,
because that would break the role.

Because every incoming message goes through the orchestrator, and that is a
model call, the service needs a Gemini key and does not start without it. Since
the move that key is no business of the lab's: it lives in the Secret Manager of
its own project and is mounted into the container by Cloud Run. The lab holds
only what it reaches the service with: one entry under the
`agenten.subagents.catalog` of each agent that should know it, its `url` and its
`api-key` in the same three lines, both fed from `.env` in the repository root
(`AGENTEN_ARZTSERVICE_URL`, `AGENTEN_ARZTSERVICE_API_KEY`). A key set on an entry
is sent to that entry's agent and to no other, even when another agent's card
asks for one. The same file is
read by `./start.sh` and, for the Wissensdienst as the remaining building block
with a Dockerfile, by `docker run --env-file .env`.

There is a price for that, and it does not belong in a footnote: **this path
through the lab now needs a network.** Without a connection or without a key the
Schadensfallagent gets no Arztauskunft — and because an outage is a Eskalation for
it and not a silent Freigabe, the case then ends on `ARZT_UNAVAILABLE`.
That is the right reaction and still a limitation nobody had before.

## Where things stand

How far a building block has got is stated in the status matrix in
[docs/user-journeys.md](docs/user-journeys.md) — and only there. It is cut along
the journeys, and that is where the seams are named too: which step still runs
by hand and which promise is merely asserted. The same status here would be a
second copy, and two sources of status drift apart the moment something changes
— and then the wrong one gets read without anyone noticing. This document
describes the cut, not the progress.

`./start.sh` starts all building blocks; individual ones by name, e.g.
`./start.sh wissen kernsystem`. It no longer has to start the Arztservice — that
runs elsewhere, and all the lab needs for it are the two lines in `.env`. Where
to begin and which addresses to use is in the [README.md](README.md); the
construction of the three agent services is in
[agenten/README.md](agenten/README.md).

## What an agent brings together — and what gets lost

Each of the two systems draws a boundary of its own. The Wissensdienst runs two
separate vector indexes so that no sales instruction is served as a contractual
condition. The Kernsystem takes the Kundennummer out of the arguments and puts
it into the header so that no model can set it. Both boundaries are clean — and
**both end at their own interface**.

After that, a single model context holds everything side by side: passages from
the Bedingungswerk, passages from the Beratungshandbuch, a customer's file. What
two systems kept apart before now lies in one prompt. That is the third lesson
of the lab, and the only one that shows up on the client side: **a boundary that
exists only in the calling system does not exist after the call.** Whoever wants
to keep it has to draw it again there.

The Beratungsagent redraws it in two places, and the difference between them is
the point:

- **The Mandantengrenze** lies in the transport, across all three hops. The
  header `x-kunden-id` goes from the chat UI to the Orchestrator, from there to
  the Beratungsagent and from there onto the MCP connection to the Kernsystem.
  It is never part of a message and never a tool argument. If it is missing, the
  Kernsystem answers with a refusal — the failure case is a denial, never
  somebody else's file.
- **The separation of the search spaces** cannot be secured that way: both
  bodies of material are text and lie in the same context. The system prompt
  forbids quoting from the Leitfaden — but a prompt is a request, negotiated at
  runtime inside the model. So a deterministic guard sees every answer before it
  goes out and compares it against the Leitfaden passages of the same turn. It
  finds verbatim reuse, not paraphrase; it is the second line and not a
  replacement for the first. On that hangs a condition you cannot see by
  looking: it has to actually get sight of those passages. For a while it did
  not — the tool results carry a transport envelope, and if you do not peel it
  off, you check against nothing. What depended on that and what caused it is in
  [agenten/README.md](agenten/README.md).

On that also hangs the reason the answer is **buffered** instead of streamed word
by word: streamed text cannot be taken back. The status line flows live, the
answer only after the check.
