#!/usr/bin/env python3
"""Apply reviewed text-runner changes to an ExecuTorch v1.5.1 checkout before building its AAR."""
import argparse
import subprocess
from pathlib import Path

COMMIT = '3b60683923245cf472b7323426920e15623ba361'


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('checkout', type=Path)
    args = p.parse_args()
    root = args.checkout.resolve()
    actual = subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip()
    if actual != COMMIT:
        raise SystemExit('Refusing to patch an unreviewed upstream version.')
    runner = root / 'extension/llm/runner/text_llm_runner.cpp'
    build = root / 'scripts/build_android_library.sh'
    source = runner.read_text()
    original = '''        if (!config.warming) {
          llm::safe_printf(piece.c_str());
          fflush(stdout);
        }
'''
    if source.count(original) != 1:
        raise SystemExit('Runner differs from reviewed source; inspect manually.')
    source = source.replace(original, '        // Kavach: generated text is delivered only through the callback.\n')
    source = source.replace('''  if (!config.warming) {
    printf("\\n");
  }
''', '')
    build_source = build.read_text()
    anchor = '    -DEXECUTORCH_ENABLE_EVENT_TRACER='
    if build_source.count(anchor) != 1:
        raise SystemExit('Build script differs from reviewed source; inspect manually.')
    build_source = build_source.replace(anchor, '    -DEXECUTORCH_ENABLE_LOGGING=OFF \\\n' + anchor)
    runner.write_text(source)
    build.write_text(build_source)
    print('Patched runner stdout and disabled ExecuTorch logging. Build, audit and smoke-test the resulting AAR; this script is not a runtime privacy certification.')


if __name__ == '__main__':
    main()
