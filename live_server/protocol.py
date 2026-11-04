"""Bounded wire protocol; no model dependencies, logging, history, or file writes."""
import base64
import json
import re
import uuid

MODEL_ID = 'muse-spark-1.3'
PROVIDER = 'meta-model-api'
MAX_BODY = 1_010_000
MAX_JPEG = 750_000
INDICATORS = {'authority_impersonation', 'arrest_threat', 'enforced_availability', 'isolation', 'urgent_transfer', 'otp_request', 'remote_access', 'account_linking'}
RISKS = {'strong_indicators', 'some_indicators', 'no_clear_indicators', 'insufficient_evidence'}
PATTERNS = {'digital_arrest', 'impersonation', 'payment_fraud', 'account_takeover', 'other', 'unclear'}
REASONS = {'no_readable_text', 'unsupported_language', 'insufficient_visual_evidence', 'invalid_model_output'}


def strict_json(raw):
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ValueError('Duplicate JSON key')
            result[key] = value
        return result
    return json.loads(raw, object_pairs_hook=pairs, parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Invalid number')))


def exact(obj, fields):
    if not isinstance(obj, dict) or set(obj) != set(fields):
        raise ValueError('Unexpected schema')


def bounded(value, limit, allow_empty=False):
    if not isinstance(value, str) or len(value.encode('utf-16-le')) // 2 > limit or (not allow_empty and not value.strip()):
        raise ValueError('Invalid text')
    return value


def parse_request(body):
    if len(body) > MAX_BODY:
        raise ValueError('Request too large')
    obj = strict_json(body)
    exact(obj, ['schema_version', 'session_id', 'frame_id', 'image_jpeg'])
    if type(obj['schema_version']) is not int or obj['schema_version'] != 1:
        raise ValueError('Unsupported schema')
    session = bounded(obj['session_id'], 36)
    if str(uuid.UUID(session)) != session:
        raise ValueError('Invalid session')
    frame = obj['frame_id']
    if type(frame) is not int or not 1 <= frame <= 2**31 - 1:
        raise ValueError('Invalid frame')
    encoded = bounded(obj['image_jpeg'], 1_000_000)
    jpeg = base64.b64decode(encoded, validate=True)
    if not 4 <= len(jpeg) <= MAX_JPEG or not jpeg.startswith(b'\xff\xd8') or not jpeg.endswith(b'\xff\xd9'):
        raise ValueError('Expected a bounded JPEG, not a URL or another media type')
    return session, frame, jpeg


def unable(reason='invalid_model_output'):
    return dict(status='unable_to_assess', reason=reason, visible_text='', assessment=None)


def validate_model_output(raw):
    if len(raw.encode('utf-8')) > 14_000:
        raise ValueError('Output too long')
    obj = strict_json(raw)
    exact(obj, ['status', 'reason', 'visible_text', 'assessment'])
    if obj['status'] == 'unable_to_assess':
        if obj['reason'] not in REASONS or obj['visible_text'] != '' or obj['assessment'] is not None:
            raise ValueError('Invalid abstention')
        return obj
    if obj['status'] != 'assessed' or obj['reason'] is not None:
        raise ValueError('Invalid status')
    text = bounded(obj['visible_text'], 4000)
    if re.search('[\u0900-\u097f]', text):
        return unable('unsupported_language')
    validate_assessment(obj['assessment'], text)
    return obj


def validate_assessment(a, text):
    exact(a, ['schema_version', 'status', 'risk', 'suspected_pattern', 'evidence', 'limitations'])
    if type(a['schema_version']) is not int or a['schema_version'] != 1 or a['status'] != 'assessed' or a['risk'] not in RISKS or a['suspected_pattern'] not in PATTERNS:
        raise ValueError('Invalid assessment')
    if not isinstance(a['evidence'], list) or len(a['evidence']) > 8:
        raise ValueError('Invalid evidence')
    for e in a['evidence']:
        exact(e, ['message_id', 'quote', 'indicator'])
        if e['message_id'] != 'm1' or e['indicator'] not in INDICATORS or bounded(e['quote'], 400) not in text:
            raise ValueError('Evidence not supported by model-read text')
    if a['risk'] in {'strong_indicators', 'some_indicators'} and not a['evidence']:
        raise ValueError('Warning without evidence')
    if a['risk'] == 'no_clear_indicators' and a['evidence']:
        raise ValueError('Inconsistent evidence')
    if not isinstance(a['limitations'], list) or len(a['limitations']) > 4:
        raise ValueError('Invalid limitations')
    for note in a['limitations']:
        bounded(note, 200, allow_empty=True)
    return a


def envelope(result, session, frame, provider):
    if provider != PROVIDER:
        raise ValueError('Only Meta Model API is supported')
    result = validate_model_output(json.dumps(result))
    return dict(schema_version=1, session_id=session, frame_id=frame, model_id=MODEL_ID, model_provider=PROVIDER, **result)
