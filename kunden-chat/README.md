# Kunden-Chat

The customer-facing surface: a full-screen chat with example questions,
streaming answers and a demo login that establishes which Vertrag is being
talked about.

It is the counterpart to the [Sachbearbeiter-UI](../sachbearbeiter-ui/README.md).
The same domain twice, once as a form and once as a dialogue — and the
difference is not the styling, it is who decides which call comes next.

## It knows no domain logic

That is the one property that defines this UI. It does not know what a Sublimit
is, what a Tarif reimburses, or when a Wartezeit is running. Its only
conversational partner is the Orchestrator, which it reaches through its own
server route `/api/chat` and an `AgentClient` adapter.

All it brings along is context: which Kunde is signed in, and what has been said
so far. What comes of that is decided on the other side.

## Layout

SvelteKit with Svelte 5 and Tailwind 4; the chat is wired up through the Vercel
AI SDK (`ai`, `@ai-sdk/svelte`).

| Route                                | Content                                                                     |
| ------------------------------------ | --------------------------------------------------------------------------- |
| `/login`                             | Demo login: pick one of three Kunden, cookie `demo-kunde`                   |
| `/`                                  | The chat                                                                    |
| `/api/chat`                          | Server route to the agent, the answer as a UI message stream                |
| `/api/beleg`                         | Takes a Rechnung as a PDF and streams it on for extraction                  |
| `/wissen/dokumente/[dokumentId]/pdf` | Passes a Bedingungswerk through — the browser never talks to the Wissensdienst |

The demo login is not authentication and is not meant to be. What it establishes
is the Vertrag being discussed — without it the agent would not know whose Tarif
it is talking about. Three Kunden with different Tarife and claim histories are
on offer, so that the same question visibly gets different answers.

## Two adapters behind one interface

`AgentClient` (`src/lib/types/chat.ts`) is the interface towards the back. There
are two implementations, and `AGENT_CLIENT` picks one:

| Value             | Adapter         | What is behind it                                                          |
| ----------------- | --------------- | --------------------------------------------------------------------------- |
| `a2a` (default)   | `agent/a2a.ts`  | the **Orchestrator** on 8084, A2A 1.0 over JSON-RPC, the answer as SSE      |
| `mock`            | `agent/mock.ts` | nothing — answers out of `wissen/daten/tarife.yaml`, no model, no network   |

**The surface addresses exactly one agent.** The Orchestrator classifies and
forwards; the Beratungsagent on 8085 cannot be reached from here, and is not
meant to be. A second way in would be a short cut past the classification — and
would put the decision about who is responsible into a user interface.

**The mock is not a preview.** It recognises Tarife and Leistungsbereiche from
the text, promises no reimbursement amount (no more than the real
Beratungsagent does) and has no memory. It exists so that the surface runs
without the Java services: anyone working on the CSS should not have to bring up
a Gemini key, the Kernsystem and the Wissensdienst first.

Any value other than `a2a` or `mock` raises an error at startup instead of
quietly falling back to the mock. A typo should be noticed at once, not read
past in plausible-looking answers — plausible answers are precisely what is
dangerous here.

### What rides on the transport

Two identifiers travel to the Orchestrator, and neither of them is in the
message:

- **`x-kunden-id`** — the Kundennummer from the `demo-kunde` cookie. The
  Orchestrator passes the same header on to the Beratungsagent, which passes it
  on over its MCP connection to the Kernsystem. A value written into the text of
  the message is not evaluated on the other side.
- **`contextId`** — the thread of the conversation, from the `gespraech` session
  cookie. Assigned server-side, not chosen by the browser: anyone who may pick
  their own conversation id can drop into someone else's thread. Signing in as a
  different Kunde clears the cookie and starts a new conversation.

The `taskId` of an open follow-up question is held in memory by the adapter, not
in a cookie — it is none of the browser's business.

This route no longer loads any **master data** for the agent. The Beratungsagent
reads the customer record itself over MCP; the surface knows only the number.

### The Beleg upload

A dental Rechnung goes to `/api/beleg` as a PDF. The route hands the body on
unchanged to `extractRechnung` in the Kernsystem — no intermediate buffer,
the browser's stream *is* the stream to the Kernsystem. Back comes what the
Rechnung says: header, Patient, Positionen, amounts.

Why not straight from the browser? The Kernsystem checks `x-api-key` on every
path except `/actuator/` and has no CORS configuration. A direct call would put
the key in the browser and a special case in the access model.

Two limits, both applied before the first byte: `application/pdf` as the content
type, otherwise 415, and at most 10 MB, otherwise 413. The built server
(adapter-node) additionally cuts off at `BODY_SIZE_LIMIT` — `start.sh` sets the
same value; in development mode it does not apply.

The extraction is shown and then attaches itself to the **next** message: it
goes out there as a data part under the key `rechnung`, and from that the
Orchestrator recognises a claim without a model call. Unlike the Kundennummer it
therefore travels inside the message — the difference being that a Beleg asserts
content, not identity.

**Nothing is interpreted here.** Which Position belongs to which
Leistungsbereich, and what of it gets reimbursed, is not this surface's
decision.

## Views below the answer

A Tarifvergleich in prose is an imposition: the customer has to dig numbers out
of sentences that a model wrote from a table. So the table travels with it. At
most **two** blocks stand below the answer bubble — three stop being a hint and
become a dashboard.

Several kinds, told apart by the `kind` field: `tarifvergleich` (table),
`beitragsvergleich` (bars), `tarifempfehlung` (ranking), `vertrag` (card),
`kontaktdaten` (card), `schadensfaelle` (table). The `vertrag` only appears when
nothing else does; `kontaktdaten` goes first when it appears, because it is the
read-back of something that has just been written.

**The numbers come from the raw tool results, never from the answer text.** The
`ViewCollector` in the Beratungsagent assembles them, the Orchestrator passes
them through unchanged, and they travel as data parts of their own — **past the
Orchestrator's model**. That is the heart of the matter: a wrong Fundstelle
catches an attentive reader's eye, a wrong number in a neatly typeset table
catches nobody's. The Orchestrator's model is told only _which kind_ of view is
going along, so that its text leads into the view instead of retelling it —
never the values.

**The Tarifvergleich links its Bedingungswerk.** It carries Quoten with no
Fundstelle in the sentence, because the Produktmodell is not a citable source.
Showing it is legitimate all the same, because every Tarif carries the
`dokumentId` of its Bedingungswerk — and that reference is the place where you
read it up. The last row of the table therefore leads there, one link per
column, and below the table stands what is binding. A reference you cannot click
would be nothing but the claim that somewhere to look it up exists.

The way there runs over the surface's own route
`/wissen/dokumente/[dokumentId]/pdf` and not over a link to port 8082: in this
application the browser never addresses a domain service directly — the same
line as with `/api/beleg`. Otherwise the address of the Wissensdienst would be
part of the delivered page, and every move of the service a change to the
surface.

Here too the surface interprets nothing. It checks `kind`, formats and displays.

Three things that are deliberately the way they are:

- **Monetary amounts arrive in two forms.** The Kernsystem carries them as a
  decimal string (`"450.00"`, so that no binary float shifts a reimbursement by
  a cent), the Produktmodell serialises them as a number. `euro()` in
  `src/lib/format.ts` displays both the same way — to the customer it is the
  same amount.
- **A missing amount does not turn into a zero.** On an open Schadensfall it
  reads „noch nicht entschieden"; a zero on display would assert that a decision
  had been made, and made against the customer.
- **Colour never carries the message on its own.** The customer's Tarif is set
  apart in colour, carries the words „Ihr Tarif" beside it and is named once
  more in the sentence below the table. If her Tarif is not in the comparison at
  all, the sentence says so too — otherwise she would go looking for a column
  that is not there.

The bar chart is built from two `div`s and a width in percent, with no charting
library: three or four values in a single series, without axes and without zoom,
are not a case for a rendering stack — and a library would bring its own colour
wheel, whereas the colours here come from the corporate palette in
`src/routes/layout.css`.

**Under `AGENT_CLIENT=mock` there are no views.** The mock calls no tools and
would have nothing to build one from without inventing it.

## The debug trace

Above the conversation sits a checkbox: **Agentenkommunikation anzeigen** (show
the agent communication). With it on, what actually happened stands between
question and answer — every hop, every tool call, every model call, each with
its protocol in front:

```
Sie → Chat-UI
▸ Was zahlt brillant bei Implantaten?

  A2A     chat-ui → orchestrator   SendStreamingMessage   frage den Orchestrator
  MODELL  orchestrator             einordnen              ordne Ihr Anliegen ein
  MODELL  orchestrator             einordnen              habe Ihr Anliegen eingeordnet
                                                          ▸ Angaben: result BERATUNG
  A2A     orchestrator → beratung  SendStreamingMessage   reiche Ihr Anliegen weiter
  MCP     beratung → kernsystem    mein_vertrag_lesen     sehe in Ihrem Vertrag nach
  MCP     beratung → wissen        bedingungen_suchen     schlage in den Bedingungen nach
                                                          ▸ Angaben: result, arguments
  A2A     beratung → orchestrator  SendStreamingMessage   bekomme die Antwort zurück

Beratungsagent · über Orchestrator · A2A
▸ Im Tarif brillant sind Implantate …
```

A few things about it matter more than the styling:

**None of it is guessed.** Every point arises where the call is made — the MCP
point in `ObservedTools` of the Beratungsagent, the classification point in the
Orchestrator, the first one in `agent/a2a.ts`. The surface derives nothing and
adds nothing. The same rule as for the status line: a model that describes its
own working steps will also describe steps it never took.

**A failed call is shown as well.** If a tool throws — because the model handed
a signal tool an unknown value, say — its return point carries **`failed`** with
the wording of the fault instead of `result`, under the same sequence number as
the outbound leg. An outbound leg without a return would look as if the call
were still hanging; a `result` in that place would look like an answer from the
tool. The attempt still counts towards the limit.

The display keeps the two apart, right down into the line: `trace.ts` puts the
wording into a **field of its own**, `failed`, on the display line, and
`TraceEntry` writes „Gescheitert · …" below it in the warning colour instead of
„Erfolgreich". Either field closes the pairing; only when neither is set is the
line still waiting for its return leg. If the error text went into `result`, the
distinction would be lost exactly where it is needed.

**The line carries only what the panel cannot.** What came back stands below,
under `result`. The line above it says that the call came back at all, and how
long it took — that duration comes out of the pairing and is in no field, so
without the line it would be lost. It does not measure the payload a second
time: where a value is too big to read at a glance, its own lid names its size.
A panel whose values fit in 200 characters opens by itself.

**A tool result is shown unpacked.** An MCP server wraps its answer in a list of
content parts; a single part carrying nothing but `text` is transport, not
content, and `unpack` in `trace.ts` takes it off — `monatsbeitrag: 20.90`
instead of the envelope around it. More than one part, or a part carrying more
than its text, stays as it came, because there the envelope is the content. The
panel follows the order it is read in: what came back, what was asked, and last,
marked with `⇥`, what rode along on the transport.

**The sender stays the sender.** What the Beratungsagent did stands under its
name, even where the Orchestrator passed it through. And who wrote the answer
stands above the bubble — taken from the `source` field of the artifact, not
inferred from the wording.

**The checkbox only switches the display.** The trace is always streamed, and it
stays attached to the message. So it is possible to uncover after the fact what
happened during an earlier answer, without asking the same question again. The
state of the checkbox lives in `localStorage`.

> **This view shows more than would otherwise go out.** The data of an MCP point
> holds the raw result of the tool — including that of
> `beratungsleitfaden_suchen` and `tarifempfehlung`. That is internal
> Beratungsmaterial, which the `ConfidentialityGuard` keeps out of the answer;
> over the trace it reaches the browser anyway. For this lab that is intended,
> because without that spot there would be no way to show what the guard is
> protecting against. For a real customer channel it is a leak.

Under `AGENT_CLIENT=mock` the mock reports a trace too — and it tells the truth
about the mock: no protocol, no agent, no MCP server.

## Start

**Through the lab** — `./start.sh kunden-chat` from the repository root. The
script checks the Node version, installs the dependencies if needed and starts
on **5174**; `KUNDEN_CHAT_PORT` overrides the port.

**On its own** — `npm install && npm run dev` in this directory.

With the default `a2a` the surface needs the **Orchestrator on 8084**, and that
in turn needs the Beratungsagent along with the Kernsystem and the
Wissensdienst:

```bash
./start.sh kernsystem wissen agenten-beratung agenten-orchestrator kunden-chat
```

If 8084 is not running, every message comes back as a refusal — the surface
invents nothing. Anyone working on the surface alone takes `AGENT_CLIENT=mock`.

| Environment variable | Default                         | Effect                                        |
| -------------------- | ------------------------------- | --------------------------------------------- |
| `AGENT_CLIENT`       | `a2a`                           | `mock` switches to the adapter without network |
| `AGENT_URL`          | `http://localhost:8084/a2a/rpc` | JSON-RPC path of the Orchestrator             |
| `TARIFE_YAML_PATH`   | `../wissen/daten/tarife.yaml`   | Path to the Produktmodell (mock only)         |
| `WISSEN_URL`         | `http://localhost:8082/api/v1`  | For passing the Bedingungswerke through       |

## Decided, and why

**The agent is reached over a server route, not from the browser.** That keeps
the Orchestrator's address out of the delivered JavaScript, and the customer
context is added server-side from the cookie instead of being sent along by the
client. A browser that asserts its own Vertrag is no basis for giving
information.

**Streaming from the start.** The answer is streamed, by the mock as well. An
agent that thinks first and then emits everything at once feels different from
one that writes — and building that in only at the end means rebuilding half the
surface.

**The protocol towards the front is decoupled from the protocol towards the
back.** The `AgentClient` delivers `AgentEvent`s — `text-delta`, `status`,
`trace`, `done`, `error` — and only `/api/chat` translates them into the AI
SDK's UI message stream.
That is why the surface could be moved onto the SDK without touching the
adapter; and conversely, connecting to the real Orchestrator touched not a
single component.
