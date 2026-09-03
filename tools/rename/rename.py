"""Semantic rename driven by jdtls textDocument/references."""
import sys, os, json, time, collections
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from lsp import LSP

class Renamer:
    def __init__(self, root, data):
        self.root=root
        self.c=LSP(root,data)
        self.c.req("initialize",{"processId":os.getpid(),"rootUri":"file://"+root,
          "capabilities":{"workspace":{"applyEdit":True,"symbol":{}},
            "textDocument":{"references":{},"documentSymbol":{}}},
          "initializationOptions":{"settings":{"java":{"import":{"maven":{"enabled":True}},
            "autobuild":{"enabled":True}}}}},timeout=600)
        self.c.note("initialized",{}); self.c.wait_ready(1800)
        time.sleep(30)
        self.opened=set()

    def open(self,path):
        if path in self.opened: return
        self.c.note("textDocument/didOpen",{"textDocument":{"uri":"file://"+path,
            "languageId":"java","version":1,"text":open(path,encoding="utf-8").read()}})
        self.opened.add(path); time.sleep(1.2)

    def defines(self, path, line, char):
        """Where the identifier at this position is declared, or None.

        An anchor found by text alone may sit inside a string literal, or on a
        different symbol that happens to share the name. Only the compiler can
        tell, so ask it before trusting the anchor."""
        self.open(path)
        r=self.c.req("textDocument/definition",{"textDocument":{"uri":"file://"+path},
            "position":{"line":line,"character":char}},timeout=300)
        res=r.get("result") or []
        if isinstance(res,dict): res=[res]
        if not res: return None
        loc=res[0]
        return (loc.get("uri") or loc.get("targetUri","")).replace("file://","")

    def refs(self, path, line, char):
        self.open(path)
        r=self.c.req("textDocument/references",{"textDocument":{"uri":"file://"+path},
            "position":{"line":line,"character":char},
            "context":{"includeDeclaration":True}},timeout=300)
        return r.get("result") or []

def apply_edits(edits):
    """edits: {abs_path: [(line, startchar, endchar, newtext)]}"""
    changed=0
    for path, es in edits.items():
        lines=open(path,encoding="utf-8").read().split("\n")
        for (ln,a,b,new) in sorted(es, key=lambda e:(-e[0],-e[1])):
            lines[ln]=lines[ln][:a]+new+lines[ln][b:]
        open(path,"w",encoding="utf-8").write("\n".join(lines))
        changed+=len(es)
    return changed
