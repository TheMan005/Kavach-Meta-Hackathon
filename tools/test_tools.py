import json
import unittest
from pathlib import Path
from evaluate import score

ROOT = Path(__file__).resolve().parents[1]

class ToolsTest(unittest.TestCase):
    def test_dataset_is_balanced_unique_and_separate(self):
        rows = [json.loads(s) for s in (ROOT / 'evaluation/held_out.jsonl').read_text().splitlines()]
        self.assertEqual(48, len(rows))
        self.assertEqual(48, len({r['id'] for r in rows}))
        demos = json.loads((ROOT / 'app/src/main/assets/demos.json').read_text())
        self.assertFalse({d['text'] for d in demos} & {r['text'] for r in rows})
        for language, size in [('en', 20), ('hi', 20), ('hinglish', 8)]:
            subset = [r for r in rows if r['language'] == language]
            self.assertEqual(size, len(subset))
            self.assertEqual(size // 2, sum(r['expected_risk'] in {'strong_indicators', 'some_indicators'} for r in subset))
            self.assertTrue(all(r['synthetic'] and r['split'] == 'held_out' for r in subset))
    def test_missing_and_abstentions_visible(self):
        rows = [dict(id=str(i), expected_risk='strong_indicators') for i in range(4)]
        report = score(rows, {'0': dict(status='assessed', risk='strong_indicators'), '1': dict(status='assessed', risk='insufficient_evidence'), '2': dict(status='unable_to_assess')})
        self.assertEqual(1, report['counts']['missing'])
        self.assertEqual(1, report['counts']['abstentions'])
        self.assertEqual(1, report['counts']['unable_to_assess'])
        self.assertEqual(.25, report['recall_all_positive_cases']['value'])
    def test_no_predictions_means_no_accuracy_claim(self):
        report = score([dict(id='a', expected_risk='no_clear_indicators')], {})
        self.assertIsNone(report['precision']['value'])
        self.assertIsNone(report['false_positive_rate_assessed']['value'])
    def test_confusion_counts(self):
        rows = [dict(id=str(i), expected_risk='strong_indicators' if i < 2 else 'no_clear_indicators') for i in range(4)]
        pred = {str(i): dict(status='assessed', risk='some_indicators' if i % 2 == 0 else 'no_clear_indicators') for i in range(4)}
        report = score(rows, pred)
        self.assertEqual(.5, report['precision']['value'])
        self.assertEqual(.5, report['recall_assessed']['value'])
        self.assertEqual(.5, report['false_positive_rate_assessed']['value'])

if __name__ == '__main__':
    unittest.main()
