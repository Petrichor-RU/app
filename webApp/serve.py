#!/usr/bin/env python3
"""Serve a Kotlin/Wasm distribution with the headers required by SQLite OPFS."""

import argparse
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer


class CrossOriginIsolatedHandler(SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Cross-Origin-Opener-Policy", "same-origin")
        self.send_header("Cross-Origin-Embedder-Policy", "require-corp")
        self.send_header("Cross-Origin-Resource-Policy", "same-origin")
        super().end_headers()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", help="Kotlin/Wasm distribution directory")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8080)
    args = parser.parse_args()

    server = ThreadingHTTPServer(
        (args.host, args.port),
        lambda *handler_args: CrossOriginIsolatedHandler(
            *handler_args, directory=args.directory
        ),
    )
    print(f"Serving {args.directory} at http://{args.host}:{args.port}/")
    server.serve_forever()


if __name__ == "__main__":
    main()
