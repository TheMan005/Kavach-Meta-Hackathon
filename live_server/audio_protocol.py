"""Bounded ten-second mono PCM chunks and transcript assessments; no persistence."""
import base64
import json
import math
import struct
import uuid
from .protocol import strict_json, exact, bounded, MODEL_ID, PROVIDER, validate_model_output

ASR_MODEL = 'muse-voice-transcribe-1.0'
PCM_BYTES = 480_000  # 24 kHz, 16-bit mono, exactly ten seconds
REASONS = {'no_speech', 'silent_audio', 'unsupported_language', 'invalid_model_output'}


def parse_audio_request(body):
    obj = strict_json(body)
    exact(obj, ['schema_version', 'session_id', 'chunk_id', 'audio_pcm'])
    if type(obj['schema_version']) is not int or obj['schema_version'] != 1:
        raise ValueError('Invalid schema')
    session = bounded(obj['session_id'], 36)
    if str(uuid.UUID(session)) != session:
        raise ValueError('Invalid session')
    chunk = obj['chunk_id']
    if type(chunk) is not int or not 1 <= chunk <= 1000:
        raise ValueError('Invalid chunk')
    pcm = base64.b64decode(bounded(obj['audio_pcm'], 640_000), validate=True)
    if len(pcm) != PCM_BYTES:
        raise ValueError('Expected exactly ten seconds of 24 kHz mono PCM16')
    return session, chunk, pcm


def audio_unable(reason):
    if reason not in REASONS:
        raise ValueError('Unknown reason')
    return dict(status='unable_to_assess', reason=reason, transcript='', assessment=None)


def silent(pcm):
    return math.sqrt(sum(s[0] ** 2 for s in struct.iter_unpack('<h', pcm)) / (len(pcm) // 2)) < 80


def audio_envelope(result, session, chunk):
    exact(result, ['status', 'reason', 'transcript', 'assessment'])
    if result['status'] == 'unable_to_assess':
        if result != audio_unable(result['reason']):
            raise ValueError('Invalid abstention')
    else:
        # Validate against the exact ASR transcript; model cannot rewrite its evidence source.
        checked = validate_model_output(json.dumps(dict(status=result['status'], reason=result['reason'],
            visible_text=result['transcript'], assessment=result['assessment'])))
        if checked['status'] != 'assessed':
            raise ValueError('Unsupported assessment')
    return dict(schema_version=1, session_id=session, chunk_id=chunk,
        asr_model=ASR_MODEL, model_id=MODEL_ID, model_provider=PROVIDER, **result)
