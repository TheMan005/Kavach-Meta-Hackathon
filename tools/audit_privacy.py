#!/usr/bin/env python3
"""Audit the source manifest, or a merged XML manifest; --live selects the explicit screen-sharing flavor."""
import argparse
from pathlib import Path
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('manifest', type=Path, nargs='?')
parser.add_argument('--live', action='store_true')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
path = args.manifest or root / 'app/src/main/AndroidManifest.xml'
tree = ET.parse(path)
android = '{http://schemas.android.com/apk/res/android}'
permissions = [e.attrib.get(android + 'name') for e in tree.getroot() if e.tag.startswith('uses-permission')]
allowed = {'INTERNET', 'FOREGROUND_SERVICE', 'FOREGROUND_SERVICE_MEDIA_PROJECTION', 'POST_NOTIFICATIONS', 'RECORD_AUDIO', 'FOREGROUND_SERVICE_MICROPHONE'} if args.live else set()
for permission in permissions:
    assert permission and (permission.split('.')[-1] in allowed or permission.endswith('.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION')), permission
application = tree.find('application')
assert application.attrib[android + 'allowBackup'] == 'false'
services = application.findall('service')
if args.live:
    assert allowed <= {p.split('.')[-1] for p in permissions}
    assert len(services) == 2
    assert all(s.attrib[android + 'exported'] == 'false' for s in services)
    assert {s.attrib[android + 'foregroundServiceType'] for s in services} == {'mediaProjection', 'microphone'}
else:
    assert not services
for source_set in ['main', 'live' if args.live else 'offline']:
    for file in (root / f'app/src/{source_set}/java').rglob('*.kt'):
        text = file.read_text()
        if 'AudioRecord' in text:
            assert args.live and source_set == 'live' and file.name == 'AudioCaptureService.kt'
        for forbidden in ('Log.d(', 'Log.i(', 'Log.e(', 'println(', 'SavedStateHandle', 'rememberSaveable', 'RoomDatabase', 'Retrofit', 'SpeechRecognizer', 'AccessibilityService', 'VOICE_CALL', 'VOICE_DOWNLINK', 'VOICE_UPLINK', 'CAPTURE_AUDIO_OUTPUT'):
            assert forbidden not in text, (file, forbidden)
print(f'PASS: {path}; mode={"live" if args.live else "offline"}; permissions={permissions}; source logging/storage/capture patterns checked')
print('Static audit only. Live mode intentionally sends consented images or microphone clips through the configured backend to Meta. No direct call-stream capture.')
