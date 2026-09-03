#!/usr/bin/env python3
import re
import sys

PATTERNS = [
    (rb"/CreationDate\([^)]*\)", b"/CreationDate()"),
    (rb"/ModDate\([^)]*\)", b"/ModDate()"),
    (rb"<xmp:CreateDate>[^<]*</xmp:CreateDate>", b"<xmp:CreateDate></xmp:CreateDate>"),
    (rb"<xmp:ModifyDate>[^<]*</xmp:ModifyDate>", b"<xmp:ModifyDate></xmp:ModifyDate>"),
    (rb"<stEvt:when>[^<]*</stEvt:when>", b"<stEvt:when></stEvt:when>"),
    (rb"<stEvt:instanceID>[^<]*</stEvt:instanceID>", b"<stEvt:instanceID></stEvt:instanceID>"),
    (rb"<xmpMM:InstanceID>[^<]*</xmpMM:InstanceID>", b"<xmpMM:InstanceID></xmpMM:InstanceID>"),
    (rb"<xmpMM:DocumentID>[^<]*</xmpMM:DocumentID>", b"<xmpMM:DocumentID></xmpMM:DocumentID>"),
    (rb"/ID\[\([^)]*\)\([^)]*\)\]", b"/ID[]"),
]


def normalize(data: bytes) -> bytes:
    for pattern, replacement in PATTERNS:
        data = re.sub(pattern, replacement, data)
    return data


def main() -> int:
    fresh_path, committed_path = sys.argv[1], sys.argv[2]
    with open(fresh_path, "rb") as handle:
        fresh = normalize(handle.read())
    with open(committed_path, "rb") as handle:
        committed = normalize(handle.read())
    if fresh == committed:
        return 0
    for offset, (a, b) in enumerate(zip(fresh, committed)):
        if a != b:
            print(f"    erster Unterschied bei Byte {offset}", file=sys.stderr)
            break
    else:
        print(f"    Laenge weicht ab: {len(fresh)} statt {len(committed)} Byte", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
