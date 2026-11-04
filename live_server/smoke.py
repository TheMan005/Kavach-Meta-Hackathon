"""Explicit one-request, synthetic-only Meta API smoke test. May incur API charges."""
import json
from PIL import Image, ImageDraw, ImageFont
from .meta_engine import MetaVisionEngine, MetaAPIError, api_key_from_env


def main():
    try:
        engine = MetaVisionEngine(api_key_from_env())
        with Image.new('RGB', (900, 540), 'white') as image:
            draw = ImageDraw.Draw(image)
            font = ImageFont.load_default(size=30)
            draw.multiline_text((30, 30), 'I am a police officer.\nYou are under digital arrest.\nStay on camera. Do not tell your family.\nTransfer money immediately to avoid jail.', fill='black', font=font, spacing=18)
            result = engine.assess(image)
        # No image, evidence text or key printed; receipt is actual provider metadata.
        print(json.dumps({'synthetic_input': True, 'receipt': engine.last_receipt,
                          'status': result['status'], 'reason': result['reason'],
                          'risk': (result['assessment'] or {}).get('risk')}, indent=2))
        if result['status'] != 'assessed':
            raise SystemExit('API returned no validated assessment; investigate before a demo.')
    except (ValueError, MetaAPIError) as error:
        raise SystemExit(str(error)) from None


if __name__ == '__main__':
    main()
