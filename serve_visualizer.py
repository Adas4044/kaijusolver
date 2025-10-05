#!/usr/bin/env python3
"""
Simple HTTP server to view the visualizer
"""
import http.server
import socketserver
import webbrowser
import os

PORT = 8888

class MyHTTPRequestHandler(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        # Add CORS headers to allow file access
        self.send_header('Access-Control-Allow-Origin', '*')
        super().end_headers()

def main():
    os.chdir(os.path.dirname(os.path.abspath(__file__)))

    Handler = MyHTTPRequestHandler

    with socketserver.TCPServer(("", PORT), Handler) as httpd:
        print(f"🌐 Server started at http://localhost:{PORT}")
        print(f"📊 Opening visualizer in browser...")
        print(f"\n✨ Press Ctrl+C to stop the server\n")

        # Open browser
        webbrowser.open(f'http://localhost:{PORT}/visualizer.html')

        # Serve forever
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\n\n👋 Server stopped.")

if __name__ == "__main__":
    main()
