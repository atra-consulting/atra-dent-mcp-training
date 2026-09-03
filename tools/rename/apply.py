"""Apply a reviewed rename map through jdtls-resolved references.

Usage:
    python3 tools/rename/apply.py <map.json> [--roots a,b,c] [--dry-run]

The map is a list of records:
    {"old": "<identifier>", "new": "<identifier>",
     "file": "<path of the declaring file, repo-relative>",
     "decl": "<substring that identifies the declaration line>"}

Why several roots: importing the root aggregator pom fails, and a partially
imported workspace answers reference queries from a partial index without
saying so. Each module is therefore imported on its own. Because a symbol may
be declared in one module and used in another, every root is queried first and
nothing is written until all of them have answered. Renaming as we went would
break the very compilation the later queries depend on.
"""
import sys, os, json, collections
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from rename import Renamer, apply_edits

REPO = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
DEFAULT_ROOTS = ["produktmodell", "rechenkern", "kernsystem", "wissen/dienst", "agenten"]


def sources(root):
    for dirpath, dirnames, filenames in os.walk(os.path.join(REPO, root)):
        dirnames[:] = [d for d in dirnames if d not in ("target", "bin", ".settings", "node_modules")]
        for f in filenames:
            if f.endswith(".java"):
                yield os.path.join(dirpath, f)


def anchors_in(path, name, decl=None):
    """Every line/column where `name` stands as a whole word."""
    out = []
    for i, line in enumerate(open(path, encoding="utf-8").read().split("\n")):
        if decl and decl not in line:
            continue
        col = 0
        while True:
            col = line.find(name, col)
            if col < 0:
                break
            before = line[col - 1] if col else " "
            after = line[col + len(name):col + len(name) + 1] or " "
            if not (before.isalnum() or before == "_") and not (after.isalnum() or after == "_"):
                out.append((i, col))
            col += len(name)
    return out


def anchor_in(path, name, decl=None):
    a = anchors_in(path, name, decl)
    return a[0] if a else None


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    dry = "--dry-run" in sys.argv
    roots = DEFAULT_ROOTS
    for a in sys.argv[1:]:
        if a.startswith("--roots="):
            roots = a.split("=", 1)[1].split(",")
    records = json.load(open(args[0]))

    edits, seen, asked = collections.defaultdict(list), set(), collections.Counter()
    for root in roots:
        wanted = [r for r in records
                  if any(anchor_in(p, r["old"]) for p in sources(root))]
        if not wanted:
            continue
        print(f"\n--- {root}: {len(wanted)} symbol(s) present ---", flush=True)
        data = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".jdtls", root.replace("/", "_"))
        renamer = Renamer(os.path.join(REPO, root), data)
        for rec in wanted:
            declfile = os.path.join(REPO, rec["file"])
            if declfile.startswith(os.path.join(REPO, root)):
                if "line" in rec:
                    lines = open(declfile, encoding="utf-8").read().split("\n")
                    ln = rec["line"] - 1
                    candidates = [(declfile, (ln, c))
                                  for (l, c) in anchors_in(declfile, rec["old"]) if l == ln]
                else:
                    candidates = [(declfile, s) for s in anchors_in(declfile, rec["old"], rec["decl"])]
            else:
                candidates = [(p, s) for p in sources(root) for s in anchors_in(p, rec["old"])]
            refs, path = [], None
            typename = os.path.basename(rec["file"])[:-len(".java")]
            for cand_path, spot in candidates:
                declared_in = renamer.defines(cand_path, spot[0], spot[1] + 1)
                if not declared_in:
                    continue
                base = os.path.basename(declared_in.split("?")[0])
                if base in (typename + ".java", typename + ".class"):
                    path = cand_path
                    refs = renamer.refs(cand_path, spot[0], spot[1] + 1)
                    break
            if not refs:
                print(f"    {rec['old']}: no anchor in {root} resolves to "
                      f"{os.path.basename(declfile)} ({len(candidates)} tried)")
                continue
            asked[rec["old"]] += len(refs)
            for ref in refs:
                p = ref["uri"].replace("file://", "")
                s = ref["range"]["start"]
                key = (p, s["line"], s["character"])
                if key in seen:
                    continue
                line = open(p, encoding="utf-8").read().split("\n")[s["line"]]
                if line[s["character"]:s["character"] + len(rec["old"])] != rec["old"]:
                    continue
                seen.add(key)
                edits[p].append((s["line"], s["character"],
                                 s["character"] + len(rec["old"]), rec["new"]))
        renamer.c.p.terminate()

    total = sum(len(v) for v in edits.values())
    print(f"\n{len(records)} renames -> {total} edits in {len(edits)} files")
    for rec in records:
        if not asked[rec["old"]]:
            print(f"  WARNING no references found for {rec['old']}")
    if not total:
        raise SystemExit("ABORT: nothing resolved -- the workspaces did not import.")
    if dry:
        print("dry run, nothing written")
        return
    print(f"applied {apply_edits(edits)} edits")
    verify(records)


def verify(records):
    """A rename is done only when the old identifier is gone from the tree."""
    import subprocess
    leftovers = []
    for old in {r["old"] for r in records}:
        out = subprocess.run(["git", "grep", "-nw", old, "--", "*.java"],
                             cwd=REPO, capture_output=True, text=True).stdout.strip()
        if out:
            leftovers.append((old, out.split("\n")))
    if leftovers:
        print("\nThe old identifier still occurs elsewhere:")
        for old, lines in leftovers:
            print(f"  {old}: {len(lines)}")
        print("Some of those are string literals or unrelated symbols of the same\n"
              "name, which a grep cannot tell apart. Build the reactor -- the\n"
              "compiler names every call site that is genuinely stale:\n"
              "    mvn install -DskipTests")
    else:
        print("verified: no old identifier remains")


if __name__ == "__main__":
    main()
