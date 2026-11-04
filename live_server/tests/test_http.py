"""Loopback transport tests with an injected test double; no model predictions are claimed."""
import base64
import http.client
import io
import json
import threading
import unittest
from PIL import Image
from live_server.protocol import MODEL_ID, PROVIDER, unable
from live_server.server import VisionServer

TOKEN = 'synthetic_test_token_' + 'a' * 32
SESSION = '11111111-1111-4111-8111-111111111111'


class TestOnlyEngine:
    model_id = MODEL_ID
    provider = PROVIDER
    def __init__(self):
        self.calls = 0
    def assess(self, image):
        self.calls += 1
        return unable('no_readable_text')


class HttpTest(unittest.TestCase):
    def setUp(self):
        self.engine = TestOnlyEngine()
        self.server = VisionServer(('127.0.0.1', 0), self.engine, TOKEN)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown(); self.server.server_close(); self.thread.join(timeout=2)

    def request(self, body, token=TOKEN, path='/v1/frames'):
        conn = http.client.HTTPConnection(*self.server.server_address, timeout=5)
        try:
            conn.request('POST', path, body=body, headers={'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json'})
            response = conn.getresponse()
            return response.status, json.loads(response.read())
        finally:
            conn.close()

    def frame(self, width=64):
        out = io.BytesIO()
        Image.new('RGB', (width, 64), color='black').save(out, format='JPEG')
        return json.dumps(dict(schema_version=1, session_id=SESSION, frame_id=1, image_jpeg=base64.b64encode(out.getvalue()).decode()))

    def test_valid_frame_binds_response_and_meta_provenance(self):
        status, body = self.request(self.frame())
        self.assertEqual(200, status)
        self.assertEqual(SESSION, body['session_id'])
        self.assertEqual(MODEL_ID, body['model_id'])
        self.assertEqual('unable_to_assess', body['status'])
        self.assertEqual(1, self.engine.calls)

    def test_unauthorized_request_never_reaches_model(self):
        status, _ = self.request(self.frame(), token='wrong')
        self.assertEqual(401, status); self.assertEqual(0, self.engine.calls)

    def test_oversized_image_rejected_before_model(self):
        status, _ = self.request(self.frame(width=961))
        self.assertEqual(400, status); self.assertEqual(0, self.engine.calls)

    def test_bad_json_does_not_echo_payload(self):
        status, body = self.request('private_canary_text_not_json')
        self.assertEqual(400, status)
        self.assertNotIn('private_canary', json.dumps(body))
        self.assertEqual(0, self.engine.calls)

    def test_no_inference_queue(self):
        self.server.inference_lock.acquire()
        try:
            status, _ = self.request(self.frame())
            self.assertEqual(503, status); self.assertEqual(0, self.engine.calls)
        finally:
            self.server.inference_lock.release()

    def test_frame_reaches_muse_adapter_and_returns_validated_result(self):
        from unittest.mock import patch
        from live_server.meta_engine import MetaVisionEngine
        from live_server.tests.test_meta_api import response, KEY
        self.server.engine = MetaVisionEngine(KEY)
        with patch.object(self.server.engine, '_post', return_value=response()) as post:
            status, body = self.request(self.frame())
        self.assertEqual(200, status)
        self.assertEqual(PROVIDER, body['model_provider'])
        self.assertEqual(MODEL_ID, post.call_args.args[0]['model'])

    def test_audio_route_runs_meta_pipeline(self):
        from unittest.mock import patch
        from live_server.meta_engine import MetaVisionEngine
        from live_server.tests.test_audio import request, provider_output, KEY
        self.server.engine = MetaVisionEngine(KEY)
        with patch.object(self.server.engine, '_request', return_value={'transcript': 'Send money now'}) as asr, patch.object(self.server.engine, '_post', return_value=provider_output()) as spark:
            status, body = self.request(request(), path='/v1/audio')
        self.assertEqual(200, status)
        self.assertEqual('muse-voice-transcribe-1.0', body['asr_model'])
        self.assertEqual('Send money now', body['transcript'])
        asr.assert_called_once(); spark.assert_called_once()

    def test_audio_auth_and_size_rejected_before_provider(self):
        from unittest.mock import patch
        from live_server.meta_engine import MetaVisionEngine
        from live_server.tests.test_audio import request, KEY
        self.server.engine = MetaVisionEngine(KEY)
        with patch.object(self.server.engine, 'assess_audio') as engine:
            status, _ = self.request(request(), token='wrong', path='/v1/audio')
            self.assertEqual(401, status)
            status, _ = self.request(request(b'too short'), path='/v1/audio')
            self.assertEqual(400, status); engine.assert_not_called()

    def test_audio_provider_error_is_sanitized(self):
        from unittest.mock import patch
        from live_server.meta_engine import MetaVisionEngine, MetaAPIError
        from live_server.tests.test_audio import request, KEY
        self.server.engine = MetaVisionEngine(KEY)
        with patch.object(self.server.engine, 'assess_audio', side_effect=MetaAPIError('private_canary')):
            status, body = self.request(request(), path='/v1/audio')
        self.assertEqual(503, status)
        self.assertNotIn('private_canary', json.dumps(body))

    def test_text_route_runs_spark_and_preserves_evidence(self):
        from unittest.mock import patch
        from live_server.meta_engine import MetaVisionEngine
        from live_server.tests.test_text import request, upstream, TEXT, ID
        from live_server.tests.test_meta_api import KEY
        self.server.engine = MetaVisionEngine(KEY)
        with patch.object(self.server.engine, '_post', return_value=upstream()) as post:
            status, body = self.request(request(), path='/v1/text')
        self.assertEqual(200, status); self.assertEqual(ID, body['request_id'])
        self.assertEqual(TEXT, body['assessment']['evidence'][0]['quote']); post.assert_called_once()

    def test_text_unauthorized_or_invalid_never_reaches_model(self):
        from unittest.mock import patch
        from live_server.meta_engine import MetaVisionEngine
        from live_server.tests.test_text import request
        from live_server.tests.test_meta_api import KEY
        self.server.engine = MetaVisionEngine(KEY)
        with patch.object(self.server.engine, 'assess_text') as engine:
            status, _ = self.request(request(), token='wrong', path='/v1/text'); self.assertEqual(401, status)
            status, _ = self.request(request(''), path='/v1/text'); self.assertEqual(400, status)
            engine.assert_not_called()

    def test_other_model_profile_rejected(self):
        self.engine.model_id = 'other/vendor'
        with self.assertRaises(ValueError):
            VisionServer(('127.0.0.1', 0), self.engine, TOKEN)


if __name__ == '__main__':
    unittest.main()
