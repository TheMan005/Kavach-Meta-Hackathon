"""User-reviewed text protocol. The exact original text is the evidence source."""
import uuid
from .protocol import strict_json, exact, bounded, validate_assessment, MODEL_ID, PROVIDER


def parse_text_request(body):
    if len(body) > 100_000:
        raise ValueError('Text request too large')
    obj = strict_json(body)
    exact(obj, ['schema_version', 'request_id', 'text', 'sender'])
    if type(obj['schema_version']) is not int or obj['schema_version'] != 1:
        raise ValueError('Invalid schema')
    request_id = bounded(obj['request_id'], 36)
    if str(uuid.UUID(request_id)) != request_id:
        raise ValueError('Invalid request ID')
    text = bounded(obj['text'], 8000)
    if obj['sender'] not in {'unspecified', 'known', 'unknown'}:
        raise ValueError('Invalid sender context')
    return request_id, text, obj['sender']


def text_envelope(result, request_id, text):
    validate_assessment(result, text)
    return dict(schema_version=1, request_id=request_id, model_id=MODEL_ID,
                model_provider=PROVIDER, assessment=result)
