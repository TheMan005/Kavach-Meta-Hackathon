"""Small, authenticated reference server. Run with python -m live_server.server; no publishing."""
import errno
import hmac
import io
import json
import os
import re
import socket
import ssl
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from .protocol import MAX_BODY, MODEL_ID, PROVIDER, parse_request, envelope


class VisionServer(ThreadingHTTPServer):
    daemon_threads = True
    request_queue_size = 4

    def __init__(self, address, engine, token, tls_context=None):
        if not re.fullmatch('[A-Za-z0-9_-]{32,256}', token):
            raise ValueError('Set a random 32–256 character KAVACH_LIVE_TOKEN')
        if engine.model_id != MODEL_ID or engine.provider != PROVIDER:
            raise ValueError('Only the pinned Meta model profile is allowed')
        self.engine, self.token = engine, token
        self.inference_lock = threading.BoundedSemaphore(1)
        self.clients = threading.BoundedSemaphore(4)
        self.tls_context = tls_context
        super().__init__(address, Handler)

    def get_request(self):
        conn, address = super().get_request()
        conn.settimeout(5)
        if self.tls_context:
            try:
                conn = self.tls_context.wrap_socket(conn, server_side=True)
            except Exception:
                conn.close()
                raise
        return conn, address

    def process_request(self, request, client_address):
        if not self.clients.acquire(blocking=False):
            request.close()
            return
        try:
            super().process_request(request, client_address)
        except Exception:
            self.clients.release()
            raise

    def process_request_thread(self, request, client_address):
        try:
            super().process_request_thread(request, client_address)
        finally:
            self.clients.release()

    def handle_error(self, request, client_address):
        pass  # Never dump request headers/body or model output into crash logs.


class Handler(BaseHTTPRequestHandler):
    server_version = 'KavachLive/1'

    def log_message(self, *_):
        pass

    def reply(self, status, payload):
        data = json.dumps(payload, ensure_ascii=False, separators=(',', ':')).encode('utf-8')
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(data)))
        self.send_header('Cache-Control', 'no-store')
        self.send_header('Connection', 'close')
        self.end_headers()
        self.wfile.write(data)
        self.close_connection = True

    def authorized(self):
        header = self.headers.get('Authorization', '')
        return len(header) <= 300 and hmac.compare_digest(header.encode(), ('Bearer ' + self.server.token).encode())

    def do_GET(self):
        if not self.authorized():
            self.reply(401, {'error': 'unauthorized'})
        elif self.path == '/health':
            self.reply(200, {'schema_version': 1, 'model_id': MODEL_ID, 'model_provider': PROVIDER, 'configured': True, 'upstream_verified': False, 'audio': 'speakerphone_microphone_chunks', 'asr_model': 'muse-voice-transcribe-1.0'})
        else:
            self.reply(404, {'error': 'not_found'})

    def do_POST(self):
        if not self.authorized():
            self.reply(401, {'error': 'unauthorized'}); return
        if self.path not in {'/v1/frames', '/v1/audio', '/v1/text'}:
            self.reply(404, {'error': 'not_found'}); return
        if not self.server.inference_lock.acquire(blocking=False):
            self.reply(503, {'error': 'busy'}); return
        try:
            if self.headers.get_content_type() != 'application/json' or self.headers.get('Transfer-Encoding'):
                raise ValueError('Unsupported request')
            lengths = self.headers.get_all('Content-Length', [])
            if len(lengths) != 1 or not lengths[0].isdigit() or not 0 < int(lengths[0]) <= MAX_BODY:
                raise ValueError('Invalid length')
            length = int(lengths[0])
            body = self.rfile.read(length)
            if len(body) != length:
                raise ValueError('Incomplete request')
            if self.path == '/v1/text':
                from .text_protocol import parse_text_request, text_envelope
                request_id, text, sender = parse_text_request(body)
                response = text_envelope(self.server.engine.assess_text(text, sender), request_id, text)
            elif self.path == '/v1/audio':
                from .audio_protocol import parse_audio_request, audio_envelope
                session, chunk, pcm = parse_audio_request(body)
                response = audio_envelope(self.server.engine.assess_audio(pcm), session, chunk)
            else:
                session, frame, jpeg = parse_request(body)
                from PIL import Image
                with Image.open(io.BytesIO(jpeg)) as original:
                    if original.format != 'JPEG' or not 1 <= original.width <= 960 or not 1 <= original.height <= 960:
                        raise ValueError('Unsupported image size or format')
                    original.load()
                    with original.convert('RGB') as image:
                        result = self.server.engine.assess(image)
                response = envelope(result, session, frame, self.server.engine.provider)
            if len(json.dumps(response, ensure_ascii=False).encode()) > 16_384:
                raise ValueError('Oversized result')
            self.reply(200, response)
        except (ValueError, TypeError, KeyError, OSError):
            self.reply(400, {'error': 'invalid_request_or_output'})
        except Exception:
            self.reply(503, {'error': 'unable_to_assess'})
        finally:
            self.server.inference_lock.release()


def main():
    host = os.environ.get('KAVACH_BIND', '127.0.0.1')
    port = int(os.environ.get('KAVACH_PORT', '8765'))
    cert, key = os.environ.get('KAVACH_TLS_CERT'), os.environ.get('KAVACH_TLS_KEY')
    if host not in {'127.0.0.1', 'localhost', '::1'} and not (cert and key):
        raise SystemExit('Non-loopback binding requires a TLS certificate and private key. No public deployment is configured.')
    token = os.environ.get('KAVACH_LIVE_TOKEN', '')
    if not re.fullmatch('[A-Za-z0-9_-]{32,256}', token):
        raise SystemExit('Set a random server access token. See docs/LIVE_VIDEO.md.')
    from .meta_engine import MetaVisionEngine, api_key_from_env
    try:
        engine = MetaVisionEngine(api_key_from_env())
    except ValueError as error:
        raise SystemExit(str(error)) from None
    tls = None
    if cert and key:
        tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        tls.minimum_version = ssl.TLSVersion.TLSv1_2
        tls.load_cert_chain(cert, key)
    try:
        server = VisionServer((host, port), engine, token, tls)
    except OSError as error:
        if error.errno == errno.EADDRINUSE:
            raise SystemExit('Could not start: this port is already in use. Stop the existing server or change KAVACH_PORT.') from None
        raise SystemExit('Could not bind the server. Check KAVACH_BIND, KAVACH_PORT and network permissions.') from None
    address, bound_port = server.server_address[:2]
    display_address = f'[{address}]' if ':' in address else address
    print(f'Kavach backend listening on {"https" if tls else "http"}://{display_address}:{bound_port}', flush=True)
    print('Configured: Muse Spark 1.3 + Muse Voice Transcribe 1.0 (Meta Model API).', flush=True)
    print('Meta API access has NOT been tested. Waiting for authenticated app requests.', flush=True)
    print('Request content and credentials are not logged. Press Ctrl+C to stop.', flush=True)
    # Lifecycle messages only: no access, request, transcript or prediction logs.
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
        print('Kavach backend stopped.', flush=True)


if __name__ == '__main__':
    main()
