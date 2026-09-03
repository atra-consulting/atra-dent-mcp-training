# AGENTS.md

Project knowledge for every AI assistant. This file is the way in: it names what
is needed right away when working on this repository, and links the detailed
documents for everything beyond that. Configuration specific to Claude Code
lives in [CLAUDE.md](CLAUDE.md), which imports this file.

## Project

A lab project around the invented dental supplementary insurance **atra.dent**.
The same domain logic stands three times side by side: as a REST interface for
screens, as an MCP tool for models, and as a standalone agent behind A2A. The
domain is deliberately dull — every rule can be looked up in the Bedingungswerke.

Java 25 and Spring Boot on the service side, SvelteKit (Svelte 5) for the two
user interfaces, Gemini as the language model. Data lives in JSON and YAML
files; there is no database.

Orientation and flows: [README.md](README.md); the cut of the systems and where
it stands: [architecture.md](architecture.md).

## Building blocks

| Directory | Job | Port | More |
|---|---|---|---|
| `kernsystem/` | Kunden, Vertraege, Schadensfaelle, Rechnung extraction. REST **and** MCP, hard Mandantengrenze through `x-kunden-id` | 8080 | [README](kernsystem/README.md) |
| `rechenkern/` | Tariff master data, Beitragsberechnung and Erstattungsberechnung. REST **and** MCP, stateless, no Mandant | 8086 | |
| `wissen/` | Wissensdienst: semantic search in the Bedingungswerke, GOZ-Pruefung. REST **and** MCP | 8082 | [README](wissen/README.md) |
| `produktmodell/` | The one Java parser of `wissen/daten/tarife.yaml`. Shared module of Wissensdienst and Rechenkern | — | |
| `agenten/` | Orchestrator (8084), Beratungsagent (8085), Schadensfallagent (8087). Six Maven modules: `a2a`, `model`, `mcp`, three services, plus `cards/` | see left | [README](agenten/README.md) |
| `sachbearbeiter-ui/` | Backoffice, forms onto fixed endpoints, no agent in between — the reference point in the lab | 5173 | [README](sachbearbeiter-ui/README.md) |
| `kunden-chat/` | Chat surface, talks only to the Orchestrator | 5174 | [README](kunden-chat/README.md) |
| `oas/` | The binding contracts: `openapi.yaml`, `rechenkern.yaml`, `wissen.yaml` | — | |
| `bruno/` | Bruno collection against the running services, with assertions | — | |

The Schadensfallagent is the only building block that starts on its own: a
poller picks up cases in status `eingereicht` every ten seconds.

## Material and the machinery for it

Two directories are not building blocks. **Neither is started by `./start.sh`**
and nothing is built in either. There is one crossing, and it is deliberate: the
Kernsystem's extraction tests read the checked-in PDFs and case files under
`submissions/rechnungen/` as their ground truth, so the Kernsystem's
`mvn test` fails without that directory. Nothing reaches into `generator/`.

| Directory | Job | More |
|---|---|---|
| `submissions/` | What arrives at atra: dental Rechnungen and health declarations as PDF/A, each with the case file that is its ground truth. Checked in, needs no Typst — this is what a participant uploads into the Kunden-Chat | [README](submissions/README.md) |
| `generator/` | The Typst machinery that produces them: five invented practices in five layouts, `reconcile.sh` and the Rechnung form. Trainer-side only, needs Typst | [README](generator/README.md) |

The split is the point: material and machinery were one directory each until
August 2026, and a newcomer could not tell from the layout which half he needed.
`generator/` writes into `submissions/`, and nothing runs the other way.
`generator/` is not free-standing — its templates import from
`wissen/dokumente/lib/daten.typ` and `wissen/daten/goz-zuordnung.yaml`, so every
build runs `typst compile --root .` from the repository root.

**The Arztservice does not live in this repository.** It is the only building
block outside the service boundary, and since 21 August 2026 it is genuinely
operated elsewhere: its own repo
([atra-arzt-service](https://github.com/atra-consulting/atra-arzt-service)), its
own GCP project, Cloud Run in `europe-west1` at
`https://atra-arzt-service-lqryukukoa-ew.a.run.app`. All the lab knows of it is
its address — one entry under `agenten.subagents.catalog` of whichever agent
should know it, with its key beside it — and four promised skills: `fachfrage-beantworten`,
`rechnung-beurteilen`, `risikoeinschaetzung` and `termin-vereinbaren`. It is not
started, not built and not covered by the tests. Its callers are the
Orchestrator and the Schadensfallagent (Tool `arzt_befragen`, bound to whichever
discovered agent offers `rechnung-beurteilen`).

## Language rule (important)

**Identifiers are English. German is reserved for the terms in the domain
glossary and for the interface descriptions a language model reads.**

- **With a human the assistant speaks English** — in chat, in explanations, in
  follow-up questions, even when asked in German. Everything else here governs
  what lands in the repository: code, documentation, commits. The conversation
  about it is not what is meant.
- Terms from [docs/domain-glossary.md](docs/domain-glossary.md) stay German
  (Tarif, Beitrag, Schadensfall, Mandant, …). If a word is not in the glossary,
  it is English — however domain-like it sounds. That is the whole test. A
  German word that is genuinely needed and missing goes into the glossary first.
- German also stays in the interface descriptions, because models are meant to
  understand and answer in German: MCP tool names, the `@McpTool` and
  `@McpToolParam` descriptions **and the parameter names of those tools**, agent
  cards, system prompts, and the `instructions` of the MCP servers.
- Everything else is English: classes, methods, fields, variables, constants,
  test names and `@DisplayName`, package and file names, configuration keys,
  shell functions.
- Established technical vocabulary is never translated (Tool, Client, Stream,
  Task, Event, Trace, Header, Session, Request/Response/Result, Exception,
  Repository, Configuration, …), and neither are the structural words of
  software (Key, Value, Data, Field, Node, Parser, Store, …).
- Compounds: the glossary term stays German, everything around it is English
  (`TarifTools`, `BeitragsberechnungRequest`, `taskId`). The type carries the
  noun and the method carries an English verb — `Beitragsberechnung.calculate()`,
  not `beitragBerechnen()`.
- Names on the wire stay as they are: the fields of `oas/*.yaml` and of the MCP
  tool schemas, the keys of the YAML data under `wissen/daten/`, and the JSON
  data of the Kernsystem are **not** renamed. **`operationId` is not covered by
  that exemption** — it is a code-generation name and follows the verb-plus-noun
  rule.
- **There are no comments in the code, and no Javadoc.** Where a comment is
  unavoidable it is English; an MCP input type documents itself through
  `@JsonPropertyDescription`.
- **Documentation is English** — READMEs, `architecture.md`, `docs/*`,
  `AGENTS.md`, `CLAUDE.md`. German survives in exactly three places: the
  interface descriptions above, the labels of the two user interfaces, and the
  Bruno collection. `docs/superpowers/` is exempt as a dated record.
- Details and counter-examples: [docs/code-conventions.md](docs/code-conventions.md).

## Build & Run

```bash
cp .env.beispiel .env        # enter GEMINI_API_KEY
./setup.sh                   # check every tool, pre-install every dependency
./start.sh                   # all building blocks
./start.sh wissen kernsystem # only the ones named
./start.sh --tracelog        # also write the traffic to tracelog/
./start.sh --debug           # also open a debug port per building block
./start.sh --hilfe
```

`./stop.sh` clears the lab out when Ctrl+C did not get the chance — by port, and
by working directory for what has no port left. `./stop.sh --hilfe` explains the
three steps. Both scripts read the building blocks and their ports from
`components.sh`, which also loads `.env`; a port belongs in that one file.

**Every one of these scripts has a PowerShell twin beside it** — `setup.ps1`,
`start.ps1`, `stop.ps1`, `components.ps1`, `wissen/*.ps1`, the `generator/`
scripts — with the same options, the same output and the same ports out of
`components.ps1`. Windows PowerShell 5.1 is enough; nothing there needs
`pwsh` 7. What it does need is one command per machine —
`Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` — because Windows starts
out `Restricted` and none of these scripts is signed; see
[README.md](README.md#quickstart) for the two cases where that is not enough. A change to one of the shell scripts belongs in its twin in the same
commit, and two of them have to stay bit-identical in what they compute rather
than merely equivalent: `wissen/stand.ps1` has to arrive at the same
fingerprint as `wissen/stand.sh`, because `wissen/generated/stand.txt` is
versioned and both `start.sh` and `start.ps1` compare against it. The `.ps1`
files carry a UTF-8 BOM on purpose: without it PowerShell 5.1 reads them as the
machine's ANSI codepage and every umlaut in their output turns to mojibake.

`--debug` gives the six JVMs a JDWP agent and runs the two user interfaces
under the Node inspector, each on its own port plus 1000 (Kernsystem 9080,
Sachbearbeiter-UI 6173, …), bound to `127.0.0.1`. The script only opens the
ports; attaching is the IDE's job, and the run configurations for both
IntelliJ and VS Code are checked in — see
[README.md](README.md#attaching-a-debugger).

Prerequisites: Java 25 (six Spring Boot applications), Maven 3.9+, Node.js
20.19+ (or 22.12+). Typst and Python 3 only to rebuild Bedingungswerke,
Rechnungen and health declarations — the finished documents sit under
`wissen/generated/` and `submissions/`.

`./start.sh` checks only what it also starts: without Node the Java services run
anyway. Ctrl+C stops everything the script started.

Building-block names for `./start.sh`: `rechenkern`, `kernsystem`, `wissen`,
`agenten-beratung`, `agenten-schadensfall`, `agenten-orchestrator`,
`sachbearbeiter-ui`, `kunden-chat`.

**Three services need `GEMINI_API_KEY`** and will not start without it:
Orchestrator, Beratungsagent, Schadensfallagent. The key lives in `.env` in the
root — the same file is read by `./start.sh` and, for the Wissensdienst as the
only building block left with a Dockerfile, by `docker run --env-file .env`. It
is not versioned; `.env.beispiel` next to it shows what belongs in it.

**The lab no longer runs offline.** The Arztservice is hosted elsewhere; anyone
who wants to drive the Schadensfall (Journey 3) all the way through needs a
network and a valid `AGENTEN_ARZTSERVICE_API_KEY`. Without both, the
Schadensfallagent gets no Arztauskunft and every case ends on
`ARZT_UNAVAILABLE` — correct, but not something to demonstrate. Beratung
and Tarifwechsel (Journeys 1 and 2) are unaffected.

Every port can be overridden through the environment (`KERNSYSTEM_PORT`,
`WISSEN_PORT`, … `KUNDEN_CHAT_PORT`). 8083 stays free as the fallback port of
the Kernsystem, because the form of the Rechnung generator is nailed to 8080.

Building all Java building blocks at once:

```bash
mvn install -DskipTests      # the root reactor: produktmodell, rechenkern,
                             # kernsystem, wissen/dienst, agenten
```

The `pom.xml` in the root is a pure aggregator and **not** a parent: every
building block keeps its own `spring-boot-starter-parent`. It bundles them into
one reactor so that an IDE sees the whole project and the order
(`produktmodell` first) comes out right by itself. In IntelliJ this `pom.xml` is
the one to open as a Maven project, not the one of a single building block.

Building individual building blocks — unchanged, and exactly what `start.sh` and
`setup.sh` do:

```bash
mvn -f produktmodell/pom.xml install -DskipTests   # shared module, has to go first
mvn -f kernsystem/pom.xml package
mvn -f agenten/pom.xml install -DskipTests         # reactor of all six agent modules
```

## Tests

```bash
mvn -f kernsystem/pom.xml test        # likewise rechenkern, produktmodell,
                                      # wissen/dienst
mvn -f agenten/pom.xml test           # all six agent modules
cd sachbearbeiter-ui && npm test      # vitest; the same in kunden-chat
cd sachbearbeiter-ui && npm run check # svelte-check; the same in kunden-chat
bru run --env Lokal                   # in bruno/, against the running services
```

The Bruno collection carries assertions and works as a smoke test over the whole
chain — it needs the services running, which the Maven and npm tests do not.

## Conventions

- **Where German stays, it stays without umlauts** (`ae`, `oe`, `ue`) — in code,
  contracts, field names, enum values and file names, and in the glossary terms
  that English prose embeds.
- **Money in MCP input types is a `String`**, not a `BigDecimal` — otherwise the
  tool schema produces `"type": "number"` and contradicts the `Geldbetrag`
  contract. Conversion happens in a `toInternal()` method of its own, with
  validation.
- **Every `@McpTool` declares its annotations.** Left out, the defaults claim
  `destructiveHint` and `openWorldHint` for a tool that only reads. Reading:
  `readOnlyHint = true, destructiveHint = false`. Writing: `readOnlyHint =
  false`, and `destructiveHint` then separates overwriting from adding.
- **Enums whose wire value differs** from the Java constant are taken as
  `String` in MCP inputs and resolved through `fromValue()`.
- **An agent card is the contract for the caller**, not the inside view of the
  agent: its text travels verbatim into the system prompt of the Orchestrator
  and decides routing there. Never name a condition the caller cannot evaluate.
- Directories and build artifacts carry English technical names (`generated/`,
  `build`); modules with a domain cut carry German ones (`kernsystem/`,
  `wissen/`).
- **JVM arguments belong in `<properties>`, never in a plugin
  `<configuration>`.** Maven gives an explicit `<configuration><jvmArguments>`
  precedence over the `spring-boot.run.jvmArguments` property, so `--debug`
  would lose its JDWP agent and the service would start normally with only the
  debug port missing — a failure with nothing to show for it.
  `wissen/dienst/pom.xml` is the only module that needs a JVM argument at all
  (`--enable-native-access` for the tokenizer's native library) and sets it as
  the property's default, which the command line may override.
- **`.idea/` and `.vscode/` are ignored except for the run configurations.**
  `.idea/runConfigurations/`, `.vscode/launch.json` and `.vscode/tasks.json` are
  carved out of `.gitignore` and versioned; everything else in those directories
  is state of one machine. A run configuration is a fact about the lab — which
  script, which debug port — and a workshop should not spend its first half hour
  typing it into a dialog. The carve-out is a whitelist: a new file elsewhere
  under `.idea/` stays ignored, and `git add` refuses it without `-f`.

Reasoning and counter-examples: [docs/code-conventions.md](docs/code-conventions.md).

## Commits

Conventional Commits with an English subject, lower case. Glossary terms stay
German inside them:

```
fix(schadensfall): an ambiguous Rechnung goes to a human
test(kernsystem): the Bestand must not contradict the Wissensbasis
docs: pull the 30-second limit and the calibration numbers through everywhere
```

The subject says what now holds — not which file was touched.

## Brand

Colours, type, logos and imagery live in
[`atra-brand`](https://github.com/atra-consulting/atra-brand) — look them up
there, do not copy them from another project.

## Further reading

| Document | Content |
|---|---|
| [README.md](README.md) | Orientation, quickstart, addresses, tracelog |
| [architecture.md](architecture.md) | The cut of the systems, the two flows, the why behind the decisions |
| [docs/user-journeys.md](docs/user-journeys.md) | The three journeys as Mermaid diagrams, and where they stand — verified against the code |
| [docs/agent-outcomes.md](docs/agent-outcomes.md) | The four `Outcome`s of an agent step and their `TaskState`s |
| [docs/domain-glossary.md](docs/domain-glossary.md) | The domain terms that stay German |
| [docs/code-conventions.md](docs/code-conventions.md) | Language rule, MCP input types, agent cards |
