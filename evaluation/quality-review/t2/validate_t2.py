"""Validate proposal traceability and coverage; never adjudicates clinical meaning."""
from collections import Counter
import hashlib
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT/'evaluation/quality-review'))
from analyze_baseline import read_json, verify_manifest


def load_bundle():
    return read_json(ROOT/'evaluation/quality-review/disputes.json')


def require(condition, message):
    if not condition:
        raise ValueError(message)


def pointer(value, path):
    for part in path.strip('/').split('/'):
        value = value[int(part)] if isinstance(value, list) else value[part]
    return value


def validate_bundle(bundle):
    require(bundle['historicalAuditChanged'] is False and bundle['clinicalReview'] is False,
            'No historical or clinical adjudication may be claimed')
    require(bundle['status'] == 'proposals_prepared_adjudication_open', 'Proposals remain open')
    verify_manifest(ROOT, {'sha256': bundle['sources']})
    read = lambda p: read_json(ROOT/p)
    source = read('evaluation/audits/2026-10-28-gemini38-v3-revision2/dispute-register.json')
    audit = read('evaluation/audits/2026-10-28-gemini38-v3-revision2/semantic-audit.json')
    audit_index = {(r['split'], r['round'], r['caseId']): r for r in audit['reviews']}
    expected = {(r['split'], r['round'], r['caseId']): r for r in source['entries']}
    cases = {(c['split'], c['id']): c for c in read('evaluation/next/development.json') + read('evaluation/corpus/v3/reserved.json')}
    responses = [json.loads(line) for line in (ROOT/'evaluation/runs/2026-10-28-gemini38-v3/responses.jsonl').read_text().splitlines()]
    records = {(r['split'], r['round'], r['caseId']): r for r in responses}
    observed, groups, patches = {}, set(), []
    for group in bundle['groups']:
        key = group['split'], group['caseId']
        require(key not in groups, 'Duplicate scenario')
        groups.add(key)
        case = cases[key]
        expected_corpus = 'evaluation/next/development.json' if group['split'] == 'development' else 'evaluation/corpus/v3/reserved.json'
        require(group['sourceCorpus'] == expected_corpus, 'Wrong source corpus')
        require(group['clinicalNote'] == case['request']['clinicalNote'], 'Changed note')
        require(group['originGroup'] == case['originGroup'], 'Changed origin')
        require(group['evaluationUse'] == 'exposed_development_only_not_new_reserved', 'Exposed case cannot be new reserved')
        require(group['review']['clinicalHumanReview'] is False and group['review']['blindedToGold'] is False, 'Review provenance changed')
        require(bool(group['issues']), 'No decision or blocker')
        for issue in group['issues']:
            require(issue['status'] == 'proposed_not_adjudicated', 'Unreviewed final ruling')
            require(bool(issue['requiredReviewer']) and bool(issue['admissibleReadings']), 'Missing reviewer or alternatives')
            note = group['clinicalNote']
            for evidence in issue['evidence']:
                require(0 <= evidence['start'] < evidence['end'] <= len(note), 'Invalid span')
                require(note[evidence['start']:evidence['end']] == evidence['quote'], 'Evidence differs from note')
            if issue['target'].startswith('/'):
                require(pointer(case['expected'], issue['target']) == issue['originalReference'], 'Original reference changed')
            if issue['proposedReference'] is not None:
                require(issue['proposedReference'] == {'value': 'unknown', 'evidence': []} and issue['ruleId'] == 'R1', 'Unexpected reference proposal')
                patches.append(dict(caseId=group['caseId'], split=group['split'], originGroup=group['originGroup'],
                                    sourceCorpus=group['sourceCorpus'], path='/expected'+issue['target'],
                                    before=issue['originalReference'], after=issue['proposedReference'], ruleId=issue['ruleId'], reason=issue['reason']))
        for attempt in group['historicalAttempts']:
            aid = attempt['split'], attempt['round'], attempt['caseId']
            require(aid[:1] == key[:1] and aid[2] == key[1], 'Attempt assigned to wrong scenario')
            require(aid not in observed and aid in expected, 'Duplicate or foreign attempt')
            require(all(attempt.get(k) == v for k,v in expected[aid].items()), 'Historical dispute edited')
            prior = audit_index[aid]
            require(attempt['historicalAuditDecision'] == prior['decision'] and attempt['historicalAuditNotes'] == prior['notes'], 'Historical audit edited')
            for new, old in (('historicalInvalidEntities', 'invalidEntities'), ('historicalInvalidContexts', 'invalidContexts'), ('historicalInvalidAttributes', 'invalidAttributes')):
                require(attempt[new] == prior.get(old, []), 'Historical rejected evidence edited')
            digest = hashlib.sha256(json.dumps(records[aid], ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
            require(digest == attempt['recordSha256'], 'Response hash differs')
            observed[aid] = attempt
    require(set(observed) == set(expected), 'Missing historical attempts')
    require(groups == {(r['split'],r['caseId']) for r in source['entries']}, 'Missing or foreign scenarios')
    proposal = read('evaluation/quality-review/t2/reference-proposals.json')
    order = lambda p: (p['split'], p['caseId'], p['path'])
    require(proposal['status'] == 'not_effective_not_a_campaign_corpus', 'Reference proposals not effective')
    require(sorted(proposal['changes'], key=order) == sorted(patches, key=order), 'Patchset and registry differ')
    counts = Counter(r['classification'] for r in observed.values())
    require(counts['indeterminado_por_referencia'] == source['unresolvedAttempts'], 'Pending count differs')
    return dict(groups=len(groups), historicalAttempts=len(observed),
                historicalPendingAttempts=counts['indeterminado_por_referencia'],
                historicalRejectedAttempts=counts['respaldo_rechazado_con_referencia_disputada'])


if __name__ == '__main__':
    print(json.dumps(validate_bundle(load_bundle()), indent=2))
