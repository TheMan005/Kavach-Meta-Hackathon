#!/usr/bin/env python3
"""Score real prediction JSONL; never invent predictions for missing model runs."""
import argparse
import json
from collections import Counter
from pathlib import Path

POSITIVE = {'strong_indicators', 'some_indicators'}
VALID = POSITIVE | {'no_clear_indicators', 'insufficient_evidence'}


def score(rows, predictions):
    counts = Counter(total=len(rows))
    for row in rows:
        p = predictions.get(row['id'])
        if p is None:
            counts['missing'] += 1
            continue
        status = p.get('status')
        if status in {'unable_to_assess', 'cancelled'}:
            counts[status] += 1
            continue
        if status != 'assessed' or p.get('risk') not in VALID:
            raise ValueError(f"Invalid prediction for {row['id']}")
        if p['risk'] == 'insufficient_evidence':
            counts['abstentions'] += 1
            continue
        expected, actual = row['expected_risk'] in POSITIVE, p['risk'] in POSITIVE
        counts['tp' if expected and actual else 'fn' if expected else 'fp' if actual else 'tn'] += 1
    def ratio(n, d):
        return {'numerator': n, 'denominator': d, 'value': n / d if d else None}
    return dict(counts=dict(counts), precision=ratio(counts['tp'], counts['tp'] + counts['fp']),
                recall_assessed=ratio(counts['tp'], counts['tp'] + counts['fn']),
                false_positive_rate_assessed=ratio(counts['fp'], counts['fp'] + counts['tn']),
                recall_all_positive_cases=ratio(counts['tp'], sum(r['expected_risk'] in POSITIVE for r in rows)),
                coverage=ratio(sum(counts[k] for k in ('tp', 'fp', 'tn', 'fn')), len(rows)))


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('predictions', type=Path, help='JSONL: id, status, risk, suspected_pattern; actual model runs only')
    p.add_argument('--dataset', type=Path, default=Path('evaluation/held_out.jsonl'))
    args = p.parse_args()
    rows = [json.loads(s) for s in args.dataset.read_text().splitlines() if s.strip()]
    predictions = {}
    for line in args.predictions.read_text().splitlines():
        item = json.loads(line)
        if item['id'] in predictions:
            raise ValueError('Duplicate prediction ID')
        predictions[item['id']] = item
    if set(predictions) - {r['id'] for r in rows}:
        raise ValueError('Unknown prediction IDs')
    report = {'overall': score(rows, predictions), 'by_language': {lang: score([r for r in rows if r['language'] == lang], predictions) for lang in sorted({r['language'] for r in rows})},
              'failures': [{'id': r['id'], 'expected': r['expected_risk'], 'prediction': predictions.get(r['id'])} for r in rows if predictions.get(r['id'], {}).get('risk') != r['expected_risk']],
              'warning': 'Synthetic, provisional labels. Not evidence of real-world protection.'}
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
