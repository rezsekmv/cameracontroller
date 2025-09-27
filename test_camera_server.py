#!/usr/bin/env python3
"""
Simple test camera server to simulate the camera API for widget testing.
Supports digest authentication and motion detection toggle.
"""

import http.server
import socketserver
import hashlib
import secrets
import time
import urllib.parse
import socket
from threading import Lock

# Global state
motion_enabled = False
state_lock = Lock()

class CameraHTTPRequestHandler(http.server.BaseHTTPRequestHandler):
    
    def do_GET(self):
        print(f"[{time.strftime('%H:%M:%S')}] {self.command} {self.path} from {self.client_address[0]}")
        
        # Parse the URL and query parameters
        parsed_url = urllib.parse.urlparse(self.path)
        query_params = urllib.parse.parse_qs(parsed_url.query)
        
        # Check for digest authentication
        auth_header = self.headers.get('Authorization')
        if not auth_header or not auth_header.startswith('Digest'):
            self.send_digest_challenge()
            return
            
        # Validate digest auth (simplified - just check if it looks right)
        if not self.validate_digest_auth(auth_header):
            self.send_digest_challenge()
            return
        
        # Handle different endpoints
        if parsed_url.path == '/cgi-bin/configManager.cgi':
            action = query_params.get('action', [''])[0]
            
            if action == 'getConfig':
                self.handle_get_config(query_params)
            elif action == 'setConfig':
                self.handle_set_config(query_params)
            else:
                self.send_error(400, "Invalid action")
        else:
            self.send_error(404, "Not found")
    
    def send_digest_challenge(self):
        """Send 401 with digest challenge"""
        nonce = secrets.token_hex(16)
        realm = "Camera"
        
        challenge = f'Digest realm="{realm}", nonce="{nonce}", algorithm=MD5, qop="auth"'
        
        self.send_response(401)
        self.send_header('WWW-Authenticate', challenge)
        self.send_header('Content-Type', 'text/plain')
        self.end_headers()
        self.wfile.write(b'401 Unauthorized')
        print(f"[{time.strftime('%H:%M:%S')}] Sent digest challenge")
    
    def validate_digest_auth(self, auth_header):
        """Simple digest auth validation - just check format"""
        # In real implementation, you'd validate the response hash
        # For testing, just check if it contains expected fields
        required_fields = ['username=', 'realm=', 'nonce=', 'response=']
        return all(field in auth_header for field in required_fields)
    
    def handle_get_config(self, query_params):
        """Handle getConfig request - return motion detection status"""
        global motion_enabled
        
        name = query_params.get('name', [''])[0]
        if name != 'MotionDetect':
            self.send_error(400, "Invalid config name")
            return
        
        with state_lock:
            enabled_str = "true" if motion_enabled else "false"
        
        response_body = f"""table.MotionDetect[0].Enable={enabled_str}
table.MotionDetect[0].Sensitivity=3
table.MotionDetect[0].Threshold=15
"""
        
        self.send_response(200)
        self.send_header('Content-Type', 'text/plain')
        self.send_header('Content-Length', str(len(response_body)))
        self.end_headers()
        self.wfile.write(response_body.encode())
        
        print(f"[{time.strftime('%H:%M:%S')}] Returned motion status: {enabled_str}")
    
    def handle_set_config(self, query_params):
        """Handle setConfig request - toggle motion detection"""
        global motion_enabled
        
        # Extract the motion detection setting from query
        # URL format: /cgi-bin/configManager.cgi?action=setConfig&MotionDetect[].Enable=true
        motion_param = None
        for key, values in query_params.items():
            if 'MotionDetect' in key and 'Enable' in key:
                motion_param = values[0] if values else None
                break
        
        if motion_param is None:
            self.send_error(400, "Missing MotionDetect Enable parameter")
            return
        
        # Update the motion detection state
        new_state = motion_param.lower() == 'true'
        with state_lock:
            motion_enabled = new_state
        
        # Simulate processing delay
        time.sleep(0.1)
        
        response_body = "OK"
        
        self.send_response(200)
        self.send_header('Content-Type', 'text/plain')
        self.send_header('Content-Length', str(len(response_body)))
        self.end_headers()
        self.wfile.write(response_body.encode())
        
        print(f"[{time.strftime('%H:%M:%S')}] Set motion detection to: {new_state}")
    
    def log_message(self, format, *args):
        """Override to reduce noise"""
        pass

def get_local_ip():
    """Get the local IP address of this computer"""
    try:
        # Connect to a remote address to determine local IP
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(("8.8.8.8", 80))
            local_ip = s.getsockname()[0]
        return local_ip
    except Exception:
        return "127.0.0.1"

def main():
    PORT = 8081
    HOST = "0.0.0.0"
    LOCAL_IP = get_local_ip()
    
    print("🎥 Camera Test Server")
    print("=" * 50)
    print(f"Starting server on {HOST}:{PORT}")
    print(f"Motion detection: {'ON' if motion_enabled else 'OFF'}")
    print()
    print("📱 Configure your Android app with:")
    print(f"  IP Address: {LOCAL_IP}:{PORT}")
    print("  Username: ipc")
    print("  Password: pass")
    print()
    print("🧪 Test URLs:")
    print(f"  Get status: http://ipc:pass@{LOCAL_IP}:{PORT}/cgi-bin/configManager.cgi?action=getConfig&name=MotionDetect")
    print(f"  Turn ON:    http://ipc:pass@{LOCAL_IP}:{PORT}/cgi-bin/configManager.cgi?action=setConfig&MotionDetect[].Enable=true")
    print(f"  Turn OFF:   http://ipc:pass@{LOCAL_IP}:{PORT}/cgi-bin/configManager.cgi?action=setConfig&MotionDetect[].Enable=false")
    print()
    print("💡 Tips:")
    print(f"  • Use {LOCAL_IP}:{PORT} in your app (not localhost)")
    print("  • Make sure your phone and computer are on the same WiFi")
    print("  • Check firewall settings if connection fails")
    print()
    print("Press Ctrl+C to stop")
    print("=" * 50)
    
    with socketserver.TCPServer((HOST, PORT), CameraHTTPRequestHandler) as httpd:
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\n\n🛑 Server stopped")

if __name__ == "__main__":
    main()
