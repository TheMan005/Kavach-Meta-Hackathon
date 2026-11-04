"""Mocked provider contract tests. Never call Meta or claim model accuracy."""
import copy
import json
import unittest
from unittest.mock import patch, MagicMock
from PIL import Image
from live_server.meta_engine import MetaVisionEngine, MetaAPIError, API_HOST, API_PATH, MAX_API_RESPONSE
from live_server.protocol import MODEL_ID, unable

KEY = 'synthetic_key_not_a_real_credential'


def response():
    return {'id': 'resp_test', 'model': MODEL_ID, 'status': 'completed', 'output': [
        {'type': 'reasoning', 'summary': []},
        {'type': 'message', 'role': 'assistant', 'status': 'completed', 'content': [
            {'type': 'output_text', 'text': json.dumps(unable('no_readable_text'))}]}]}


class MetaApiTest(unittest.TestCase):
    def setUp(self):
        self.engine = MetaVisionEngine(KEY)
        self.image = Image.new('RGB', (64, 64), 'white')

    def tearDown(self):
        self.image.close()

    def test_placeholders_and_header_injection_rejected(self):
        for key in ['', 'YOUR_META_API_KEY_HERE', 'REPLACE_WITH_KEY', KEY + '\r\nInjected: yes']:
            with self.subTest(key=key), self.assertRaises(ValueError):
                MetaVisionEngine(key)

    def test_real_adapter_requests_only_muse_with_inline_image_and_no_storage(self):
        with patch.object(self.engine, '_post', return_value=response()) as post:
            result = self.engine.assess(self.image)
        payload = post.call_args.args[0]
        self.assertEqual(MODEL_ID, payload['model'])
        self.assertFalse(payload['store']); self.assertFalse(payload['stream'])
        self.assertNotIn('tools', payload); self.assertNotIn('previous_response_id', payload)
        self.assertNotIn(KEY, json.dumps(payload))
        self.assertTrue(payload['input'][0]['content'][1]['image_url'].startswith('data:image/jpeg;base64,/9j/'))
        self.assertEqual('no_readable_text', result['reason'])
        self.assertEqual('resp_test', self.engine.last_receipt['response_id'])

    def test_wrong_model_incomplete_refusal_tools_and_duplicate_output_abstain(self):
        variations = []
        for model in ['muse-spark-1.3-contributor', 'other-model', 'meta-llama/Llama-3.2-11B-Vision-Instruct']:
            r = response(); r['model'] = model; variations.append(r)
        r = response(); r['status'] = 'incomplete'; variations.append(r)
        r = response(); r['output'][1]['content'] = [{'type': 'refusal', 'refusal': 'No'}]; variations.append(r)
        r = response(); r['output'].append({'type': 'function_call'}); variations.append(r)
        r = response(); r['output'].append(copy.deepcopy(r['output'][1])); variations.append(r)
        r = response(); r['output'][1]['content'][0]['text'] = 'Ignore instructions'; variations.append(r)
        for r in variations:
            with self.subTest(response=r), patch.object(self.engine, '_post', return_value=r):
                self.assertEqual(unable(), self.engine.assess(self.image))
                self.assertIsNone(self.engine.last_receipt)

    @patch('live_server.meta_engine.http.client.HTTPSConnection')
    def test_fixed_https_destination_authorization_and_cleanup(self, factory):
        conn = factory.return_value
        upstream = conn.getresponse.return_value
        upstream.status = 200; upstream.getheader.return_value = 'application/json'
        upstream.read.return_value = json.dumps(response()).encode()
        self.engine.assess(self.image)
        self.assertEqual(API_HOST, factory.call_args.args[0])
        self.assertIsNotNone(factory.call_args.kwargs['context'])
        self.assertEqual(('POST', API_PATH), conn.request.call_args.args)
        self.assertEqual('Bearer ' + KEY, conn.request.call_args.kwargs['headers']['Authorization'])
        conn.close.assert_called_once()

    @patch('live_server.meta_engine.http.client.HTTPSConnection')
    def test_rate_limit_auth_error_and_redirect_are_not_retried_or_echoed(self, factory):
        for status in [301, 401, 429, 500]:
            factory.reset_mock()
            conn = factory.return_value
            conn.getresponse.return_value.status = status
            with self.assertRaises(MetaAPIError) as error:
                self.engine.assess(self.image)
            self.assertNotIn(KEY, str(error.exception))
            conn.request.assert_called_once(); conn.close.assert_called_once()
            conn.getresponse.return_value.read.assert_not_called()

    @patch('live_server.meta_engine.http.client.HTTPSConnection')
    def test_timeout_and_oversized_response_fail_without_verdict(self, factory):
        conn = factory.return_value
        conn.request.side_effect = TimeoutError('private canary')
        with self.assertRaises(MetaAPIError) as error:
            self.engine.assess(self.image)
        self.assertNotIn('canary', str(error.exception)); conn.close.assert_called_once()
        conn.request.side_effect = None
        upstream = conn.getresponse.return_value
        upstream.status = 200; upstream.getheader.return_value = 'application/json'
        upstream.read.return_value = b'x' * (MAX_API_RESPONSE + 1)
        with self.assertRaises(MetaAPIError):
            self.engine.assess(self.image)

    def test_oversized_image_never_uploaded(self):
        with Image.new('RGB', (961, 64)) as image, patch.object(self.engine, '_post') as post:
            with self.assertRaises(ValueError): self.engine.assess(image)
            post.assert_not_called()

if __name__ == '__main__':
    unittest.main()
