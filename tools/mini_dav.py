# -*- coding: utf-8 -*-
"""最小 WebDAV 服务器：MKCOL / PUT / GET + Basic Auth，用于小满同步验收。
用法: python tools/mini_dav.py  (监听 0.0.0.0:8790, 根目录 build/davroot, 账号 xiaoman/xm-pass)
"""
import base64, os, sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = 8790
ROOT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "build", "davroot")
USER, PASS = "xiaoman", "xm-pass"

class H(BaseHTTPRequestHandler):
    def log_message(self, fmt, *a):
        print("%s %s" % (self.command, self.path), flush=True)

    def auth_ok(self):
        h = self.headers.get("Authorization", "")
        if not h.startswith("Basic "):
            return False
        try:
            u, p = base64.b64decode(h[6:]).decode("utf-8").split(":", 1)
        except Exception:
            return False
        return u == USER and p == PASS

    def deny(self):
        self.send_response(401)
        self.send_header("WWW-Authenticate", 'Basic realm="dav"')
        self.send_header("Content-Length", "0")
        self.end_headers()

    def local(self):
        p = self.path.split("?")[0]
        return os.path.join(ROOT, *[_ for _ in p.split("/") if _])

    def do_MKCOL(self):
        if not self.auth_ok(): return self.deny()
        os.makedirs(self.local(), exist_ok=True)
        self.send_response(201); self.send_header("Content-Length", "0"); self.end_headers()

    def do_PUT(self):
        if not self.auth_ok(): return self.deny()
        n = int(self.headers.get("Content-Length", 0))
        data = self.rfile.read(n)
        path = self.local()
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "wb") as f:
            f.write(data)
        print("PUT ->", path, len(data), "bytes", flush=True)
        self.send_response(201); self.send_header("Content-Length", "0"); self.end_headers()

    def do_GET(self):
        if not self.auth_ok(): return self.deny()
        path = self.local()
        if not os.path.isfile(path):
            self.send_response(404); self.send_header("Content-Length", "0"); self.end_headers()
            return
        with open(path, "rb") as f:
            data = f.read()
        self.send_response(200)
        self.send_header("Content-Type", "application/octet-stream")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_PROPFIND(self):
        # 简单返回 207 多状态（当前传输层可能不用，但补上无害）
        if not self.auth_ok(): return self.deny()
        body = b'<?xml version="1.0"?><D:multistatus xmlns:D="DAV:"></D:multistatus>'
        self.send_response(207)
        self.send_header("Content-Type", 'text/xml; charset="utf-8"')
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

if __name__ == "__main__":
    os.makedirs(ROOT, exist_ok=True)
    print(f"mini DAV on 0.0.0.0:{PORT} root={ROOT} user={USER}", flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), H).serve_forever()
