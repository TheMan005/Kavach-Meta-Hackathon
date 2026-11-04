#!/usr/bin/env python3
"""Create integrity metadata for your locally exported model. No network or license acceptance."""
import argparse
import hashlib
import json
from pathlib import Path


def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--model', type=Path, required=True)
    p.add_argument('--tokenizer', type=Path, required=True)
    p.add_argument('--context', type=int, choices=[4096, 8192], default=4096)
    p.add_argument('--output', type=Path, default=Path('manifest.json'))
    args = p.parse_args()
    manifest = dict(schema_version=1, family='llama3.2', model_id='Llama-3.2-1B-Instruct',
                    model_version='3.2-QLoRA-INT4-local-export', runtime='1.5.1',
                    tokenizer_format='llama3-tiktoken', export_recipe='xnnpack-qlora-int4-eo8',
                    export_commit='3b60683923245cf472b7323426920e15623ba361', context_length=args.context,
                    digest_provenance='Computed locally; integrity only, not publisher authentication')
    for label, path in [('model', args.model), ('tokenizer', args.tokenizer)]:
        manifest[label + '_size'] = path.stat().st_size
        manifest[label + '_sha256'] = digest(path)
    args.output.write_text(json.dumps(manifest, indent=2) + '\n')
    print('Manifest written. Verify model provenance independently before importing.')


if __name__ == '__main__':
    main()
