"""Serves the UI mockups and forwards API, icon, video and shared-script requests to the running
Jongrai server, so the mockups run on the real engine without any back-end change.

    python3 mockups/serve.py            # mockups on http://localhost:8090, Jongrai on :8080
    python3 mockups/serve.py 8090 9000  # if Jongrai runs on another port
"""
import http.server
import pathlib
import sys
import urllib.error
import urllib.request

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8090
UPSTREAM = f"http://localhost:{sys.argv[2] if len(sys.argv) > 2 else 8080}"
FORWARDED = ("/api/", "/icons/", "/video/", "/app.js")
PASS_UP = ("If-None-Match", "If-Modified-Since", "Range")
PASS_DOWN = ("Cache-Control", "ETag", "Last-Modified", "Expires", "Content-Range", "Accept-Ranges")


class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(pathlib.Path(__file__).parent), **kwargs)

    def do_GET(self):
        if self.path.startswith(FORWARDED):
            self.forward()
        else:
            super().do_GET()

    def end_headers(self):
        if not self.path.startswith(FORWARDED):  # mockup files: always revalidate, so edits show on reload
            self.send_header("Cache-Control", "no-cache")
        super().end_headers()

    def do_POST(self):
        self.forward()

    def forward(self):
        body = self.rfile.read(int(self.headers.get("Content-Length") or 0)) if self.command == "POST" else None
        req = urllib.request.Request(UPSTREAM + self.path, data=body, method=self.command)
        if body is not None:
            req.add_header("Content-Type", self.headers.get("Content-Type", "application/x-www-form-urlencoded"))
        for h in PASS_UP:
            if self.headers.get(h):
                req.add_header(h, self.headers[h])
        try:
            with urllib.request.urlopen(req, timeout=120) as r:
                self.reply(r.status, r.headers, r.read())
        except urllib.error.HTTPError as e:  # 304s, and 400/409 bodies that carry the board
            self.reply(e.code, e.headers, e.read())
        except urllib.error.URLError:
            self.reply(502, {"Content-Type": "application/json"}, b'{"error":"Jongrai is not running on ' + UPSTREAM.encode() + b'."}')

    def reply(self, code, headers, data):
        self.send_response(code)
        self.send_header("Content-Type", headers.get("Content-Type") or "application/octet-stream")
        for h in PASS_DOWN:
            if headers.get(h):
                self.send_header(h, headers[h])
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


if __name__ == "__main__":
    print(f"Mockups on http://localhost:{PORT}  (API from {UPSTREAM})")
    http.server.ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
