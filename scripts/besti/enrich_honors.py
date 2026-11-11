"""Add sourced field sizes and separate unofficial equivalents to the archive.

Usage: python scripts/besti/enrich_honors.py PATH_TO_SOURCE_SNAPSHOTS
Requires rating-contests/, cpc-awards.json and standings-evidence.json.
Never converts equivalent medals into official awards.
"""
from pathlib import Path
import collections
import json
import re
import sys

root = Path(__file__).resolve().parents[2]
sources = Path(sys.argv[1])
out = root / 'docs/besti'
read = lambda p: json.loads(p.read_text(encoding='utf-8'))
records = read(out / 'records.json')
meta = read(out / 'metadata.json')
cpc = {r['awardId']: r for r in read(sources / 'cpc-awards.json')['data']}
replayed = {e['path']: e for e in read(sources / 'standings-evidence.json')}
medals = {'Gold': '金牌', 'Silver': '银牌', 'Bronze': '铜牌'}
evidence = []
for r in records:
    refs = r.get('sourceRefs', {})
    full_path = sources / 'rating-contests' / (refs.get('rating', '') + '.json')
    event = read(full_path) if full_path.exists() else None
    replay = replayed.get(refs.get('xcpcio'))
    cpc_row = cpc.get(refs.get('cpcfinder'))
    if event:
        r['totalTeams'] = len(event['teams'])
        r['totalOfficialTeams'] = sum(t.get('official') is True for t in event['teams'])
        r['teamCountSource'] = 'XCPCRating 完整比赛榜单'
        assert event['teamCount'] == len(event['teams'])
    elif replay:
        r['totalTeams'] = replay['totalTeams']
        r['totalOfficialTeams'] = replay['totalOfficialTeams']
        r['teamCountSource'] = 'XCPCIO 原始榜单'
    if cpc_row:
        # Keep numerator and denominator from the same standings snapshot.
        r['rank'] = cpc_row['rank']
        r['officialRank'] = cpc_row.get('officialRank') if r['official'] else None
        r['totalTeams'] = cpc_row['totalTeams']
        r['totalOfficialTeams'] = cpc_row['totalOfficialTeams']
        r['teamCountSource'] = 'CPCFinder 比赛榜单'
        r['location'] = cpc_row.get('place', '')
        r['notes'] = r.get('notes', '').replace('主表优先展示 XCPCRating 整理值', '主表采用 CPCFinder 对应榜单的排名与队伍总数')
    assert isinstance(r.get('totalTeams'), int) and r['totalTeams'] > 0, r['uid']
    assert 0 <= r['totalOfficialTeams'] <= r['totalTeams'], r['uid']
    assert not r.get('rank') or r['rank'] <= r['totalTeams'], r['uid']
    if r['official'] is False:
        r['award'] = ''
        r['officialRank'] = None
        r['equivalentAward'] = ''
        r['equivalentAwardChecked'] = False
        if replay:
            # Same-school names may use a translated alias. Prefer exact name,
            # otherwise match its score/rank only if unambiguous.
            stars = [t for t in replay['teams'] if t['unofficial']]
            selected = [t for t in stars if t['name'] == r['team']]
            if not selected and len(stars) == 1:
                selected = stars
            if len(selected) == 1 and replay['bounds']:
                team = selected[0]
                r['equivalentAward'] = medals.get(team['equivalentAward'], '')
                r['equivalentRank'] = team['equivalentRank']
                r['equivalentAwardChecked'] = True
                rule = '公布的奖牌数量' if isinstance(replay['medalRule'], dict) else '榜单指定的 CCPC 奖牌比例规则'
                r['equivalentAwardBasis'] = f'按 XCPCIO 原始榜单重放，在正式队伍中比较名次，再对照{rule}计算；仅为打星等效成绩，不代表实际授奖。'
                evidence.append({'uid': r['uid'], 'event': refs['xcpcio'], 'team': r['team'], 'allRank': team['rank'], 'equivalentRank': team['equivalentRank'], 'equivalentAward': r['equivalentAward'], 'medalRule': replay['medalRule'], 'bounds': replay['bounds']})
    assert not r.get('equivalentAward') or (not r['award'] and r['official'] is False)
    assert not re.search(r'丝绸之路|silk[\s_-]*road|srni', json.dumps({k:r.get(k) for k in ['event','title','sourceRefs']},ensure_ascii=False), re.I)

meta['equivalentMedals'] = dict(collections.Counter(r['equivalentAward'] for r in records if r.get('equivalentAward')))
meta['teamCountsAvailable'] = len(records)
meta['unofficialRecords'] = sum(r['official'] is False for r in records)
meta['equivalentAwardUnconfirmed'] = [r['uid'] for r in records if r['official'] is False and not r.get('equivalentAwardChecked')]
meta['updatedAt'] = '2026-11-11'
for name, value in [('records.json', records), ('metadata.json', meta), ('unofficial-equivalents.json', evidence)]:
    (out / name).write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({k:meta[k] for k in ['recordCount','medals','equivalentMedals','teamCountsAvailable','equivalentAwardUnconfirmed']},ensure_ascii=False))
