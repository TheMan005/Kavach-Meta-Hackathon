import base64
import io
import json
import struct
import unittest
import wave
from unittest.mock import patch
from live_server.audio_protocol import *
from live_server.meta_engine import MetaVisionEngine, MetaAPIError
from live_server.tests.test_meta_api import KEY, response
from live_server.tests.test_protocol import assessed

SESSION = '11111111-1111-4111-8111-111111111111'
PCM = struct.pack('<h', 2000) * 240_000


def request(pcm=PCM):
    return json.dumps(dict(schema_version=1, session_id=SESSION, chunk_id=1, audio_pcm=base64.b64encode(pcm).decode())).encode()


def provider_output(text='Send money now'):
    value = response(); value['output'][1]['content'][0]['text'] = json.dumps(assessed(text))
    return value


class AudioTest(unittest.TestCase):
    def setUp(self): self.engine = MetaVisionEngine(KEY)

    def test_pcm_size_session_and_chunk_are_strict(self):
        self.assertEqual((SESSION, 1, PCM), parse_audio_request(request()))
        for pcm in [b'', PCM[:-2], PCM + b'00']:
            with self.assertRaises(ValueError): parse_audio_request(request(pcm))
        obj = json.loads(request()); obj['chunk_id'] = True
        with self.assertRaises(ValueError): parse_audio_request(json.dumps(obj).encode())
        obj['chunk_id'] = 1; obj['session_id'] = 'not-uuid'
        with self.assertRaises(ValueError): parse_audio_request(json.dumps(obj).encode())

    def test_silence_never_calls_meta(self):
        with patch.object(self.engine, '_request') as call:
            self.assertEqual(audio_unable('silent_audio'), self.engine.assess_audio(bytes(PCM_BYTES)))
            call.assert_not_called()

    def test_multipart_uses_only_meta_asr_and_real_wav_then_spark(self):
        with patch.object(self.engine, '_request', return_value={'transcript': 'Send money now'}) as asr, patch.object(self.engine, '_post', return_value=provider_output()) as spark:
            result = self.engine.assess_audio(PCM)
        path, body, content_type = asr.call_args.args
        self.assertEqual('/v1/asr/transcribe', path)
        self.assertIn(ASR_MODEL.encode(), body); self.assertNotIn(KEY.encode(), body)
        self.assertTrue(content_type.startswith('multipart/form-data; boundary=Kavach'))
        start = body.index(b'RIFF'); length = int.from_bytes(body[start+4:start+8], 'little') + 8
        with wave.open(io.BytesIO(body[start:start+length])) as wav:
            self.assertEqual((1, 2, 24000, 240000), (wav.getnchannels(), wav.getsampwidth(), wav.getframerate(), wav.getnframes()))
        payload = spark.call_args.args[0]
        self.assertEqual(MODEL_ID, payload['model']); self.assertFalse(payload['store'])
        self.assertEqual('Send money now', payload['input'][0]['content'][0]['text'])
        self.assertEqual('Send money now', result['transcript'])
        self.assertEqual(ASR_MODEL, audio_envelope(result, SESSION, 1)['asr_model'])

    def test_blank_transcript_skips_scam_model(self):
        with patch.object(self.engine, '_request', return_value={'transcript': ' '}), patch.object(self.engine, '_post') as spark:
            self.assertEqual(audio_unable('no_speech'), self.engine.assess_audio(PCM)); spark.assert_not_called()

    def test_invalid_and_oversized_asr_fail_closed(self):
        for result in [{'error': 'canary'}, {'transcript': 3}, {'transcript': 'x'*4001}]:
            with patch.object(self.engine, '_request', return_value=result), patch.object(self.engine, '_post') as spark:
                self.assertEqual(audio_unable('invalid_model_output'), self.engine.assess_audio(PCM)); spark.assert_not_called()

    def test_transcript_rewriting_is_rejected(self):
        with patch.object(self.engine, '_request', return_value={'transcript': 'Hello, how are you?'}), patch.object(self.engine, '_post', return_value=provider_output()):
            self.assertEqual(audio_unable('invalid_model_output'), self.engine.assess_audio(PCM))

    def test_hindi_profile_abstains_without_spark_call(self):
        with patch.object(self.engine, '_request', return_value={'transcript': 'पैसे भेजो'}), patch.object(self.engine, '_post') as spark:
            self.assertEqual(audio_unable('unsupported_language'), self.engine.assess_audio(PCM)); spark.assert_not_called()

    def test_provider_failure_never_becomes_safe(self):
        with patch.object(self.engine, '_request', side_effect=MetaAPIError('Unavailable')), self.assertRaises(MetaAPIError):
            self.engine.assess_audio(PCM)

    def test_abstention_cannot_include_transcript_or_prediction(self):
        invalid = audio_unable('no_speech'); invalid['transcript'] = 'private'
        with self.assertRaises(ValueError): audio_envelope(invalid, SESSION, 1)

if __name__ == '__main__': unittest.main()
