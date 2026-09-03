import json, subprocess, threading, sys, os, time, queue

class LSP:
    def __init__(self, root, data_dir):
        self.p = subprocess.Popen(
            ["jdtls", "-data", data_dir],
            cwd=root, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL)
        self.root = root
        self.id = 0
        self.responses = {}
        self.notes = queue.Queue()
        threading.Thread(target=self._read, daemon=True).start()

    def _read(self):
        f = self.p.stdout
        while True:
            line = f.readline()
            if not line: return
            if line.startswith(b"Content-Length:"):
                n = int(line.split(b":")[1])
                f.readline()
                msg = json.loads(f.read(n))
                if "id" in msg and "method" not in msg:
                    self.responses[msg["id"]] = msg
                else:
                    self.notes.put(msg)

    def _send(self, obj):
        b = json.dumps(obj).encode()
        self.p.stdin.write(b"Content-Length: %d\r\n\r\n" % len(b) + b)
        self.p.stdin.flush()

    def req(self, method, params, timeout=600):
        self.id += 1
        i = self.id
        self._send({"jsonrpc":"2.0","id":i,"method":method,"params":params})
        t0 = time.time()
        while time.time()-t0 < timeout:
            if i in self.responses: return self.responses.pop(i)
            time.sleep(0.05)
        raise TimeoutError(method)

    def note(self, method, params):
        self._send({"jsonrpc":"2.0","method":method,"params":params})

    def wait_ready(self, timeout=900):
        t0 = time.time()
        while time.time()-t0 < timeout:
            try:
                m = self.notes.get(timeout=1)
            except queue.Empty:
                continue
            if m.get("method") == "language/status":
                p = m.get("params", {})
                print(f"  status: {p.get('type')} {str(p.get('message'))[:70]}", flush=True)
                if p.get("type") in ("ServiceReady","Started"):
                    return True
        return False
