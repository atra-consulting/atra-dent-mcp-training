#!/usr/bin/env python3
"""Strips comments from source files so that only the code remains.

Not a pattern-based text replacement but a scanner per language family: string
literals, Java text blocks, template literals, regex literals, heredocs and YAML
block scalars are recognised and left untouched. Comments that a compiler or a
linter evaluates (shebang, @ts-expect-error, svelte-ignore, eslint-disable, ...)
are kept as well; --also-directives strips those too.

A dry run is the default. Files are written only with --write or --destination.
"""

from __future__ import annotations

import argparse
import bisect
import difflib
import re
import subprocess
import sys
from pathlib import Path

Span = tuple[int, int]

KEYWORDS_BEFORE_REGEX = {
    "return", "typeof", "instanceof", "in", "of", "new", "delete", "void",
    "throw", "case", "do", "else", "yield", "await",
}

DIRECTIVES = re.compile(
    r"""^(?://|/\*|\#|<!--)\s*
        (?: !                      # Shebang
          | @ts-                   # @ts-ignore, @ts-expect-error, @ts-nocheck
          | ts-ignore
          | eslint
          | prettier-ignore
          | svelte-ignore
          | stylelint
          | noinspection
          | jshint | jslint | global\s
          | type:\s | noqa | pylint | mypy | flake8
          | -\*-                   # coding-Zeile in Python
          | shellcheck
          | yaml-language-server
          | @vite-ignore | @__PURE__ | @preserve | @license
          | language=
          | @formatter:
        )""",
    re.VERBOSE,
)


def _string_end(text: str, i: int, quote: str, *, multiline: bool, limit: int) -> int:
    """i zeigt auf das oeffnende Zeichen; liefert den Index hinter dem Ende."""
    j = i + len(quote)
    while j < limit:
        c = text[j]
        if c == "\\":
            j += 2
            continue
        if not multiline and c == "\n":
            return j
        if text.startswith(quote, j):
            return j + len(quote)
        j += 1
    return limit


def _regex_allowed(marker: str, word: str) -> bool:
    if word:
        return word in KEYWORDS_BEFORE_REGEX
    return marker == "" or marker in "(,=:[!&|?{};+-*%^~<>"


def _regex_end(text: str, i: int, limit: int) -> int | None:
    """Ende eines /.../flags-Literals oder None, wenn es keines ist."""
    j = i + 1
    in_klasse = False
    while j < limit:
        c = text[j]
        if c == "\\":
            j += 2
            continue
        if c == "\n":
            return None
        if in_klasse:
            if c == "]":
                in_klasse = False
        elif c == "[":
            in_klasse = True
        elif c == "/":
            j += 1
            while j < limit and text[j].isalpha():
                j += 1
            return j
        j += 1
    return None


def _to_line_end(text: str, i: int, limit: int) -> int:
    j = text.find("\n", i)
    return limit if j == -1 or j > limit else j


def spans_c(
    text: str,
    *,
    start: int = 0,
    end: int | None = None,
    template: bool = False,
    regex_literale: bool = False,
    textblock: bool = False,
    zeilenkommentar: bool = True,
) -> list[Span]:
    """Java, TypeScript, JavaScript, CSS: // und /* ... */."""
    limit = len(text) if end is None else end
    spans: list[Span] = []
    i = start
    stapel: list = []
    marker = ""
    word = ""
    while i < limit:
        c = text[i]

        if stapel and stapel[-1] == "template":
            if c == "\\":
                i += 2
            elif c == "`":
                stapel.pop()
                marker, word = "`", ""
                i += 1
            elif c == "$" and i + 1 < limit and text[i + 1] == "{":
                stapel.append(["expr", 0])
                marker, word = "{", ""
                i += 2
            else:
                i += 1
            continue

        if textblock and text.startswith('"""', i):
            i = _string_end(text, i, '"""', multiline=True, limit=limit)
            marker, word = '"', ""
            continue

        if c in "\"'":
            i = _string_end(text, i, c, multiline=False, limit=limit)
            marker, word = c, ""
            continue

        if template and c == "`":
            stapel.append("template")
            i += 1
            continue

        if c == "/" and i + 1 < limit:
            if zeilenkommentar and text[i + 1] == "/":
                j = _to_line_end(text, i, limit)
                spans.append((i, j))
                i = j
                continue
            if text[i + 1] == "*":
                j = text.find("*/", i + 2)
                j = limit if j == -1 else min(j + 2, limit)
                spans.append((i, j))
                i = j
                continue
            if regex_literale and _regex_allowed(marker, word):
                j = _regex_end(text, i, limit)
                if j is not None:
                    i = j
                    marker, word = "/", ""
                    continue

        if stapel and c in "{}" and isinstance(stapel[-1], list):
            oben = stapel[-1]
            if c == "{":
                oben[1] += 1
            elif oben[1] > 0:
                oben[1] -= 1
            else:
                stapel.pop()
                marker, word = "`", ""
                i += 1
                continue

        if not c.isspace():
            marker = c
            word = word + c if (c.isalnum() or c in "_$") else ""
        i += 1
    return spans


BLOCK_SCALAR = re.compile(r"[|>][-+]?\d*[ \t]*(\#[^\n]*)?(\n|$)")
HEREDOC_MARKER = re.compile(r"[A-Za-z_][A-Za-z0-9_]*")


def _heredoc_marker(text: str, i: int, limit: int) -> tuple[str | None, int]:
    j = i + 2
    if j < limit and text[j] == "-":
        j += 1
    while j < limit and text[j] in " \t":
        j += 1
    if j < limit and text[j] in "\"'":
        quote = text[j]
        k = text.find(quote, j + 1)
        return (None, i + 2) if k == -1 else (text[j + 1:k], k + 1)
    m = HEREDOC_MARKER.match(text, j, limit)
    return (None, i + 2) if m is None else (m.group(0), m.end())


def _skip_heredocs(text: str, i: int, markers: list[str], limit: int) -> int:
    for marker in markers:
        while i < limit:
            end = _to_line_end(text, i, limit)
            line = text[i:end]
            i = min(end + 1, limit)
            if line.strip() == marker:
                break
    return i


def _block_scalar_end(text: str, i: int, limit: int) -> int:
    zeilenstart = text.rfind("\n", 0, i) + 1
    kopf = text[zeilenstart:i]
    einzug = len(kopf) - len(kopf.lstrip(" "))
    j = _to_line_end(text, i, limit)
    j = min(j + 1, limit)
    while j < limit:
        end = _to_line_end(text, j, limit)
        line = text[j:end]
        if line.strip() == "" or (len(line) - len(line.lstrip(" "))) > einzug:
            j = min(end + 1, limit)
            continue
        break
    return j


def spans_hash(
    text: str,
    *,
    heredoc: bool = False,
    blockskalare: bool = False,
    dreifachstrings: bool = False,
    nur_zeilenanfang: bool = False,
) -> list[Span]:
    """Shell, YAML, Python, Properties: # bis Zeilenende."""
    limit = len(text)
    spans: list[Span] = []
    i = 0
    zeilenanfang = True
    vorher_leer = True
    offene_heredocs: list[str] = []
    while i < limit:
        c = text[i]

        if c == "\n":
            i += 1
            if offene_heredocs:
                i = _skip_heredocs(text, i, offene_heredocs, limit)
                offene_heredocs = []
            zeilenanfang = vorher_leer = True
            continue

        if dreifachstrings and (text.startswith('"""', i) or text.startswith("'''", i)):
            i = _string_end(text, i, text[i:i + 3], multiline=True, limit=limit)
            zeilenanfang = vorher_leer = False
            continue

        if c in "\"'":
            i = _string_end(text, i, c, multiline=False, limit=limit)
            zeilenanfang = vorher_leer = False
            continue

        if heredoc and c == "\\":
            i += 2
            zeilenanfang = vorher_leer = False
            continue

        if heredoc and text.startswith("<<", i) and not text.startswith("<<<", i):
            marker, weiter = _heredoc_marker(text, i, limit)
            if marker is not None:
                offene_heredocs.append(marker)
            i = weiter
            zeilenanfang = vorher_leer = False
            continue

        if c == "#" and (zeilenanfang if nur_zeilenanfang else vorher_leer):
            j = _to_line_end(text, i, limit)
            spans.append((i, j))
            i = j
            continue

        if blockskalare and c in "|>" and BLOCK_SCALAR.match(text, i, limit):
            i = _block_scalar_end(text, i, limit)
            zeilenanfang = vorher_leer = True
            continue

        vorher_leer = c.isspace()
        if not c.isspace():
            zeilenanfang = False
        i += 1
    return spans


ELEMENT_OPEN = re.compile(r"<(script|style)\b[^>]*>", re.IGNORECASE)


def spans_markup(text: str, *, embedded: bool = True) -> list[Span]:
    """HTML, Svelte, XML: <!-- ... -->, dazu eingebettetes script/style."""
    limit = len(text)
    spans: list[Span] = []
    i = 0
    while i < limit:
        i = text.find("<", i)
        if i == -1:
            break
        if text.startswith("<!--", i):
            j = text.find("-->", i + 4)
            j = limit if j == -1 else j + 3
            spans.append((i, j))
            i = j
            continue
        if text.startswith("<![CDATA[", i):
            j = text.find("]]>", i)
            i = limit if j == -1 else j + 3
            continue
        m = ELEMENT_OPEN.match(text, i) if embedded else None
        if m is not None:
            art = m.group(1).lower()
            content = m.end()
            zu = re.compile(rf"</{art}\s*>", re.IGNORECASE).search(text, content)
            inhalt_ende = zu.start() if zu else limit
            if art == "script":
                spans += spans_c(text, start=content, end=inhalt_ende,
                                 template=True, regex_literale=True)
            else:
                spans += spans_c(text, start=content, end=inhalt_ende,
                                 zeilenkommentar=False)
            i = inhalt_ende
            continue
        i += 1
    return spans


SPRACHEN: dict[str, tuple] = {
    ".java": (spans_c, dict(textblock=True)),
    ".ts": (spans_c, dict(template=True, regex_literale=True)),
    ".tsx": (spans_c, dict(template=True, regex_literale=True)),
    ".js": (spans_c, dict(template=True, regex_literale=True)),
    ".mjs": (spans_c, dict(template=True, regex_literale=True)),
    ".cjs": (spans_c, dict(template=True, regex_literale=True)),
    ".jsx": (spans_c, dict(template=True, regex_literale=True)),
    ".css": (spans_c, dict(zeilenkommentar=False)),
    ".scss": (spans_c, {}),
    ".less": (spans_c, {}),
    ".svelte": (spans_markup, dict(embedded=True)),
    ".html": (spans_markup, dict(embedded=True)),
    ".htm": (spans_markup, dict(embedded=True)),
    ".vue": (spans_markup, dict(embedded=True)),
    ".xml": (spans_markup, dict(embedded=False)),
    ".svg": (spans_markup, dict(embedded=False)),
    ".xsd": (spans_markup, dict(embedded=False)),
    ".yaml": (spans_hash, dict(blockskalare=True)),
    ".yml": (spans_hash, dict(blockskalare=True)),
    ".sh": (spans_hash, dict(heredoc=True)),
    ".bash": (spans_hash, dict(heredoc=True)),
    ".zsh": (spans_hash, dict(heredoc=True)),
    ".py": (spans_hash, dict(dreifachstrings=True)),
    ".properties": (spans_hash, dict(nur_zeilenanfang=True)),
}

DATEINAMEN: dict[str, tuple] = {
    "Dockerfile": (spans_hash, dict(nur_zeilenanfang=True)),
    "Makefile": (spans_hash, dict(nur_zeilenanfang=True)),
}


def language_for(path: Path) -> tuple | None:
    return SPRACHEN.get(path.suffix.lower()) or DATEINAMEN.get(path.name)


def _merge(spans: list[Span]) -> list[Span]:
    zusammen: list[Span] = []
    for a, b in sorted(spans):
        if zusammen and a <= zusammen[-1][1]:
            zusammen[-1] = (zusammen[-1][0], max(zusammen[-1][1], b))
        else:
            zusammen.append((a, b))
    return zusammen


def apply_spans(text: str, spans: list[Span], *, collapse_blank_lines: bool = False) -> tuple[str, int]:
    """Entfernt die Spans, laesst Zeilenumbrueche stehen und raeumt die Zeilen auf.

    Liefert den neuen Text und die Zahl der ganz entfallenen Zeilen.
    """
    spans = _merge(spans)
    if not spans:
        return text, 0

    umbrueche = [m.start() for m in re.finditer("\n", text)]
    zeile_von = lambda pos: bisect.bisect_right(umbrueche, pos)

    stuecke: list[str] = []
    beruehrt: set[int] = set()
    letzte = 0
    for a, b in spans:
        stuecke.append(text[letzte:a])
        stuecke.append("\n" * text.count("\n", a, b))
        for z in range(zeile_von(a), zeile_von(max(a, b - 1)) + 1):
            beruehrt.add(z)
        letzte = b
    stuecke.append(text[letzte:])

    endet_mit_umbruch = text.endswith("\n")
    lines = "".join(stuecke).split("\n")
    result: list[str] = []
    removed_lines = 0
    for nummer, line in enumerate(lines):
        if nummer in beruehrt:
            if line.strip() == "":
                removed_lines += 1
                continue
            line = line.rstrip()
        result.append(line)

    if collapse_blank_lines:
        collapsed: list[str] = []
        for line in result:
            if line.strip() == "" and collapsed and collapsed[-1].strip() == "":
                removed_lines += 1
                continue
            collapsed.append(line)
        result = collapsed

    stripped = "\n".join(result)
    if endet_mit_umbruch and not stripped.endswith("\n"):
        stripped += "\n"
    return stripped, removed_lines


def strip_comments(text: str, path: Path, *, also_directives: bool = False,
                    collapse_blank_lines: bool = False) -> tuple[str, int, int]:
    """Liefert (neuer Text, Zahl der Kommentare, Zahl der entfallenen Zeilen)."""
    language = language_for(path)
    if language is None:
        return text, 0, 0
    scanner, optionen = language
    spans = scanner(text, **optionen)
    if not also_directives:
        spans = [s for s in spans if not DIRECTIVES.match(text[s[0]:s[1]].strip())]
    stripped, removed_lines = apply_spans(text, spans, collapse_blank_lines=collapse_blank_lines)
    return stripped, len(spans), removed_lines


def collect_files(paths: list[Path], only: list[str]) -> list[Path]:
    found: list[Path] = []
    for path in paths:
        if path.is_file():
            found.append(path)
        elif path.is_dir():
            found += _in_directory(path)
        else:
            print(f"nicht gefunden: {pfad}", file=sys.stderr)
    passend = [p for p in found if language_for(p) is not None]
    if only:
        extensions = {e if e.startswith(".") else "." + e for e in only}
        passend = [p for p in passend if p.suffix.lower() in extensions]
    return sorted(set(passend))


SKIP = {".git", "node_modules", "target", "build", "dist",
                 ".svelte-kit", ".venv", "__pycache__", "generated"}


def _in_directory(directory: Path) -> list[Path]:
    try:
        roh = subprocess.run(
            ["git", "ls-files", "-z", "--cached", "--others", "--exclude-standard", "--", "."],
            cwd=directory, capture_output=True, check=True, text=True).stdout
        return [directory / name for name in roh.split("\0") if name]
    except (subprocess.CalledProcessError, FileNotFoundError):
        return [p for p in directory.rglob("*")
                if p.is_file() and not (SKIP & set(p.parts))]


def main() -> int:
    parser = argparse.ArgumentParser(
        prog="strip-comments.py",
        description="Strips comments from source files so that only the code remains.",
        add_help=False)
    parser.add_argument("paths", nargs="*", default=["."], type=Path,
                        help="files or directories (default: .)")
    parser.add_argument("--write", action="store_true",
                        help="change files in place")
    parser.add_argument("--destination", type=Path, metavar="DIR",
                        help="write the result as a copy under DIR instead of overwriting")
    parser.add_argument("--stdout", action="store_true",
                        help="write the result to stdout (only with exactly one file)")
    parser.add_argument("--diff", action="store_true", help="show a unified diff")
    parser.add_argument("--only", action="append", default=[], metavar="EXT",
                        help="only this extension, repeatable (e.g. --only java)")
    parser.add_argument("--also-directives", action="store_true",
                        help="also strip effective comments (shebang, @ts-expect-error, "
                             "svelte-ignore, eslint-disable, ...)")
    parser.add_argument("--collapse-blank-lines", action="store_true",
                        help="collapse runs of blank lines into a single one")
    parser.add_argument("--quiet", action="store_true", help="print only the summary line")
    parser.add_argument("-h", "--help", action="help", help="this help")
    args = parser.parse_args()

    paths = [Path(p) for p in (args.paths or [Path(".")])]
    files = collect_files(paths, args.only)
    if args.stdout and len(files) != 1:
        print("--stdout needs exactly one file", file=sys.stderr)
        return 2

    changed = comments = lines = 0
    for file in files:
        try:
            text = file.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        stripped, count, removed_lines = strip_comments(
            text, file,
            also_directives=args.also_directives,
            collapse_blank_lines=args.collapse_blank_lines)

        if args.stdout:
            sys.stdout.write(stripped)
            return 0
        if stripped == text:
            continue

        changed += 1
        comments += count
        lines += removed_lines
        if args.diff:
            sys.stdout.writelines(difflib.unified_diff(
                text.splitlines(keepends=True), stripped.splitlines(keepends=True),
                fromfile=str(file), tofile=str(file), n=1))
        elif not args.quiet:
            print(f"{file}: {count} comments, {removed_lines} lines")

        if args.destination:
            destination = args.destination / file
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_text(stripped, encoding="utf-8")
        elif args.write:
            file.write_text(stripped, encoding="utf-8")

    where = ("written to " + str(args.destination)) if args.destination else (
        "written" if args.write else "dry run, nothing written")
    print(f"{changed} of {len(files)} files, {comments} comments, "
          f"{lines} lines removed ({where})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
