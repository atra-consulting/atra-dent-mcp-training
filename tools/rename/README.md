# Semantic rename harness

Renames Java identifiers through the Eclipse JDT language server, so that
overloads, shadowing and prefix collisions are resolved by a compiler rather
than by a regular expression. Renaming `verlange` here leaves
`verlangeOhneZusage` alone; a textual pass would not.

    brew install jdtls
    python3 tools/rename/apply.py . map.json --dry-run
    python3 tools/rename/apply.py . map.json

`map.json` is reviewed by a human before it is applied. The audit proposes
candidates; it does not decide.

## Why not OpenRewrite

Its newest parser handles Java 21. This project is Java 25, so recipes parse
nothing, match nothing, and exit 0 having changed nothing.

## Why not textDocument/rename

jdtls 1.60 returns only the declaration edit for it. `textDocument/references`
returns the full, correct set, so the harness anchors its own edits at the
reference start positions.

## Workspaces are per module

Importing the root aggregator `pom.xml` fails: jdtls reports
`Failed to import projects` and then answers reference queries from a partial
index — 4 edits on one run, 0 on the next. Import one module at a time.

Because a failed import under-reports **silently**, `apply.py` aborts when a
map resolves to zero edits, and after applying it greps the whole tree for
every old identifier. A rename counts as done only when nothing is left.
Cross-module symbols therefore need a pass per owning and dependent module,
and the verification says which ones are still outstanding.

## Records need a second look

A record component implicitly implements the accessor of an interface it
declares. There is no reference from the component to that accessor, so
renaming `State.betrag()` leaves `record Total(BigDecimal betrag)` behind and
the record silently stops implementing the interface. The compiler says so at
once, but the harness cannot see it coming. Build after every batch.

## What a language server cannot see

References that live in strings. `@MethodSource("faelle")` binds a JUnit
factory by name, and renaming the method leaves the annotation pointing at
nothing — the suite then runs *fewer* tests rather than failing to compile, so
the count is the only signal. Check `@MethodSource`, `@ValueSource`,
`@Qualifier`, bean names and any reflective lookup by hand, and compare the
test count against the baseline after every batch.

## Renaming into a collision

The harness renames what a reference points at; it does not check that the new
name is free in that scope. Renaming both a method `kennung` and a local
`kennung` to `id` in a method that already had a `long id` produced two
declarations of one name, and a second, subtler break: a catch block that used
to log the string now referred to a `long` that the throwing line had left
unassigned. Both were compile errors, so nothing shipped — but pick names that
are already free.

## Data keys are not identifiers

`wissen/daten/**` and `kernsystem/data/*.json` are read by Jackson with
`SNAKE_CASE` and `FAIL_ON_UNKNOWN_PROPERTIES`, so a record component *is* the
key. Renaming `gespraechsanlaesse` to `conversationReasons` made the reader look
for `conversation_reasons`, and the whole Wissensdienst context failed to start
— 100 test errors from one component. The type may be renamed; the component
binding to a data key may not.

The same sweep also rewrote the key inside `leitfaden.yaml` and inside a YAML
fixture in a Java text block, where a rename script cannot tell data from code.
After any rename touching these modules, check that every data key still
resolves:

    python3 - <<'EOF'
    # every key in the maintained data must match a Java component or literal
    EOF

Never include `wissen/daten/` or `kernsystem/data/` in a rename's file list.

## Ein Rename trifft auch Prosa

Nicht nur Referenzen in Strings, sondern den Text darin. In 46afd5f wurde die
catch-Variable `weiter` zu `rethrown` — und dieselbe Ersetzung machte aus dem
Label `"Anliegen weiterreichen"` ein `"Anliegen rethrownreichen"`, sichtbar in
der Debug-Spur des Chats. Das kompiliert, alle Tests bleiben gruen, und
auffallen kann es nur jemandem, der hinsieht.

Wonach also suchen: ein englisches Fragment, das mitten in einem deutschen Wort
klebt. Die frueheren Sweeps fanden das nicht, weil sie ein deutsches Wort
*neben* einem englischen suchten -- hier gibt es keine Luecke dazwischen.
