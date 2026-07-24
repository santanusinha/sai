#!/usr/bin/env python3
"""
capture_proxy.py — tiny OpenAI-compatible recording proxy.

Records every /chat/completions request body to a file (one JSON per line)
and returns a canned reply so `sai` completes fast. Provider-independent:
lets us inspect the EXACT payload each sentinel-ai version sends, which is
what prompt-caching keys on.

Usage:
    python3 capture_proxy.py <out.jsonl> <port>
"""
import json, sys, http.server, socketserver

OUT  = sys.argv[1]
PORT = int(sys.argv[2])

class H(http.server.BaseHTTPRequestHandler):
    def log_message(self, *a): pass

    def _json(self, obj, code=200):
        body = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if "models" in self.path:
            return self._json({"object": "list",
                               "data": [{"id": "cap", "object": "model"}]})
        self.send_response(404); self.end_headers()

    def do_POST(self):
        n   = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(n)
        try:
            obj = json.loads(raw)
            with open(OUT, "a") as f:
                f.write(json.dumps(obj) + "\n")
        except Exception:
            pass
        self._json({
            "id": "chatcmpl-cap", "object": "chat.completion",
            "created": 0, "model": "cap",
            "choices": [{"index": 0,
                         "message": {"role": "assistant", "content": "ok"},
                         "finish_reason": "stop"}],
            "usage": {"prompt_tokens": 100, "completion_tokens": 1,
                      "total_tokens": 101,
                      "prompt_tokens_details": {"cached_tokens": 0}},
        })

if __name__ == "__main__":
    with socketserver.ThreadingTCPServer(("127.0.0.1", PORT), H) as s:
        print(f"capture_proxy listening on 127.0.0.1:{PORT} -> {OUT}", flush=True)
        s.serve_forever()
