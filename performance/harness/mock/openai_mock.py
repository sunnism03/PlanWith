#!/usr/bin/env python3
import json
import os
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HOST = os.environ.get("MOCK_HOST", "127.0.0.1")
PORT = int(os.environ.get("MOCK_PORT", "18080"))
DELAY_MS = int(os.environ.get("MOCK_DELAY_MS", "25"))


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        body = json.loads(self.rfile.read(length) or b"{}")
        content = ""
        messages = body.get("messages") or []
        if messages:
            content = str(messages[-1].get("content", ""))

        if "4가지 카테고리" in content:
            response_content = "일정 자동 기입"
        else:
            response_content = json.dumps(
                {
                    "events": [
                        {
                            "title": "dry-run-event",
                            "startDate": "2026-08-01",
                            "startTime": "09:00:00",
                            "endDate": "2026-08-01",
                            "endTime": "10:00:00",
                        }
                    ]
                },
                ensure_ascii=False,
            )

        time.sleep(DELAY_MS / 1000)
        payload = {
            "choices": [
                {"index": 0, "message": {"role": "assistant", "content": response_content}}
            ]
        }
        encoded = json.dumps(payload, ensure_ascii=False).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def log_message(self, fmt, *args):
        return


if __name__ == "__main__":
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()

