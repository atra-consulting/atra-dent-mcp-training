#!/usr/bin/env python3
import sys
from pathlib import Path


def _split_flow_fields(content: str) -> list[str]:
    fields: list[str] = []
    current: list[str] = []
    depth = 0
    in_quotes = False
    for char in content:
        if char == '"':
            in_quotes = not in_quotes
            current.append(char)
        elif in_quotes:
            current.append(char)
        elif char in "{[":
            depth += 1
            current.append(char)
        elif char in "}]":
            depth -= 1
            current.append(char)
        elif char == "," and depth == 0:
            fields.append("".join(current))
            current = []
        else:
            current.append(char)
    if current:
        fields.append("".join(current))
    return fields


def _parse_fields(fields: list[str]) -> dict[str, str]:
    result: dict[str, str] = {}
    for field in fields:
        if ":" not in field:
            continue
        key, _, value = field.partition(":")
        result[key.strip()] = value.strip().strip('"')
    return result


def _list_items(lines: list[str], start: int) -> list[dict[str, str]]:
    items: list[dict[str, str]] = []
    item_lines: list[str] | None = None
    item_indent = 0

    def flush() -> None:
        if item_lines is None:
            return
        header = item_lines[0]
        if header.startswith("{") and header.endswith("}"):
            items.append(_parse_fields(_split_flow_fields(header[1:-1])))
        else:
            items.append(_parse_fields(item_lines))

    for line in lines[start:]:
        if not line.strip():
            continue
        indent = len(line) - len(line.lstrip(" "))
        if indent == 0:
            break
        stripped = line.strip()
        if stripped.startswith("- "):
            flush()
            item_indent = indent
            item_lines = [stripped[2:].strip()]
            continue
        if item_lines is not None and indent == item_indent + 2:
            item_lines.append(stripped)
    flush()
    return items


def _find_list(text: str, key: str) -> list[dict[str, str]]:
    lines = text.splitlines()
    for index, line in enumerate(lines):
        if line.strip() == f"{key}:":
            return _list_items(lines, index + 1)
    return []


def excerpt_numbers(root: Path) -> set[str]:
    text = (root / "wissen" / "daten" / "goae-auszug.yaml").read_text(encoding="utf-8")
    return {item["nummer"] for item in _find_list(text, "positionen") if item.get("nummer")}


def case_numbers(root: Path) -> set[str]:
    numbers: set[str] = set()
    for case_file in sorted((root / "submissions" / "rechnungen" / "cases").glob("*.yaml")):
        text = case_file.read_text(encoding="utf-8")
        for item in _find_list(text, "positionen"):
            if item.get("art") == "goae" and item.get("nummer"):
                numbers.add(item["nummer"])
    return numbers


def main() -> int:
    root = Path(sys.argv[1])
    excerpt = excerpt_numbers(root)
    cases = case_numbers(root)

    if not excerpt:
        print("  FEHLER     goae-auszug.yaml enthaelt keine Nummer", file=sys.stderr)
        return 1
    if not cases:
        print("  FEHLER     keine GOAe-Nummer unter submissions/rechnungen/cases/ gefunden", file=sys.stderr)
        return 1

    missing_from_excerpt = cases - excerpt
    missing_from_cases = excerpt - cases

    ok = True
    if missing_from_excerpt:
        print("  FEHLT im Auszug:  " + ", ".join(sorted(missing_from_excerpt)), file=sys.stderr)
        ok = False
    if missing_from_cases:
        print("  UNGENUTZT im Auszug: " + ", ".join(sorted(missing_from_cases)), file=sys.stderr)
        ok = False

    if not ok:
        return 1
    print(f"  OK         {len(excerpt)} Nummer(n) auf beiden Seiten gleich")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
