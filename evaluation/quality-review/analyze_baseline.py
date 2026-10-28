"""Offline diagnostic of frozen automatic scores. Never calls a provider or rescoring gate."""
import argparse
from collections import Counter, defaultdict
import hashlib
import json
from pathlib import Path

FAMILIES = ('diagnoses', 'medications', 'procedures')
ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent


def read_json(path):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f'Duplicate JSON key: {key}')
            result[key] = value
        return result
    return json.loads(Path(path).read_text(), object_pairs_hook=unique)


def verify_manifest(root, manifest):
    for relative, expected in manifest['sha256'].items():
        path = (root / relative).resolve()
        if not path.is_relative_to(root.resolve()):
            raise ValueError('Input outside repository')
        if hashlib.sha256(path.read_bytes()).hexdigest() != expected:
            raise ValueError(f'Changed baseline input: {relative}')


def analyze_baseline(scores, cases, rounds=(1, 2, 3)):
    """Count each mismatch entity once, each differing dimension separately.

    Cases must cover exactly the scores in every expected round, across splits.
    Alignment is inferred from TP + unique CONTEXT_MISMATCH predictions in the
    historical scorer, not from a new semantic judgment or a relaxed release gate.
    """
    if not cases or not rounds or len(set(rounds)) != len(rounds) or any(type(r) is not int or r < 1 for r in rounds):
        raise ValueError('Nonempty cases and unique positive integer rounds required')
    index = {}
    for case in cases:
        key = (case['split'], case['id'])
        if key in index:
            raise ValueError(f'Duplicate case: {key}')
        index[key] = case
    expected = {(split, rnd, cid) for split, cid in index for rnd in rounds}
    actual = set()
    groups = defaultdict(list)
    for score in scores:
        key = (score['split'], score['round'], score['id'])
        if key in actual or key not in expected:
            raise ValueError(f'Duplicate or foreign score: {key}')
        actual.add(key)
        case = index[(score['split'], score['id'])]
        groups[(score['split'], case['cohort'], score['round'])].append(score)
    if actual != expected:
        raise ValueError('Missing baseline scores')
    result = []
    for (split, cohort, rnd), rows in sorted(groups.items()):
        families = {}
        for family in FAMILIES:
            dimensions, issues = Counter(), Counter()
            totals = Counter(tp=0, fp=0, fn=0)
            mismatches = 0
            for row in rows:
                counts = row['entities'][family]
                if set(counts) != {'tp', 'fp', 'fn'} or any(type(n) is not int or n < 0 for n in counts.values()):
                    raise ValueError('Invalid entity counts')
                totals.update(counts)
                seen = set()
                for issue in row['issues']:
                    if issue.get('family') != family:
                        continue
                    issues[issue['code']] += 1
                    if issue['code'] == 'CONTEXT_MISMATCH':
                        pi = issue['prediction']
                        if pi in seen:
                            raise ValueError('Duplicate context mismatch prediction')
                        seen.add(pi)
                        if issue['expected'].keys() != issue['actual'].keys():
                            raise ValueError('Incompatible context fields')
                        differences = [k for k, value in issue['expected'].items() if value != issue['actual'][k]]
                        # Semantic audit can invalidate an entity with equal state;
                        # its issue still counts, but adds no differing dimensions.
                        dimensions.update(differences)
                if len(seen) > counts['fp'] or len(seen) > counts['fn']:
                    raise ValueError('Context mismatch exceeds incorrect entities')
                mismatches += len(seen)
            families[family] = dict(entities=dict(totals), alignedByAutomaticScorer=totals['tp'] + mismatches,
                                    contextMismatchEntities=mismatches,
                                    contextMismatchDimensions=dict(sorted(dimensions.items())),
                                    issues=dict(sorted(issues.items())))
        # Flat family rows make cohort and denominators explicit for every metric.
        for family in FAMILIES:
            result.append(dict(split=split, cohort=cohort, round=rnd, family=family,
                               cases=len(rows), **families[family],
                               operationalFailureCases=sum(r.get('operationalFailures', 0) for r in rows),
                               invariantViolationCases=sum(r.get('invariantViolations', 0) for r in rows),
                               semanticAttribution='not_determined_by_scores'))
    return dict(schemaVersion=1, scope='frozen automatic scores; not clinical accuracy or release decision', groups=result)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.parse_args()
    manifest = read_json(HERE / 'baseline-manifest.json')
    verify_manifest(ROOT, manifest)
    cases = read_json(ROOT / 'evaluation/next/development.json') + read_json(ROOT / 'evaluation/corpus/v3/reserved.json')
    scores = read_json(ROOT / 'evaluation/runs/2026-10-28-gemini38-v3/scores.json')
    report = analyze_baseline(scores, cases)
    report['baselineManifestSha256'] = hashlib.sha256((HERE / 'baseline-manifest.json').read_bytes()).hexdigest()
    print(json.dumps(report, indent=2, ensure_ascii=False))


if __name__ == '__main__':
    main()
