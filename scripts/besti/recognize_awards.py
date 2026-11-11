"""Apply the maintainer's school recognition policy without losing raw medals."""
from pathlib import Path
import collections
import json

out = Path(__file__).resolve().parents[2] / 'docs/besti'
read = lambda name: json.loads((out / name).read_text(encoding='utf-8'))
policy, records, meta = read('award-recognition.json'), read('records.json'), read('metadata.json')
for r in records:
    level = policy['levels'].get(r['family'], {}).get(r['tier'])
    for original, prefix in [('award', 'award'), ('equivalentAward', 'equivalentAward')]:
        grade = policy['grades'].get(r.get(original))
        r.pop(prefix + 'Level', None)
        r.pop(prefix + 'Grade', None)
        if grade and level:
            r[prefix + 'Level'], r[prefix + 'Grade'] = level, grade
        elif r.get(original):
            raise ValueError(f'Missing recognition rule for {r["uid"]}: {r["family"]} {r["tier"]}')
    r['title'] = r['event'] + (f' · {r["awardLevel"]}{r["awardGrade"]}' if r.get('awardLevel') else '')

meta['recognitionSource'] = policy['source']
meta['recognizedAwards'] = dict(collections.Counter(r['awardLevel'] + r['awardGrade'] for r in records if r.get('awardLevel')))
meta['recognizedEquivalents'] = dict(collections.Counter(r['equivalentAwardLevel'] + r['equivalentAwardGrade'] for r in records if r.get('equivalentAwardLevel')))
for name, data in [('records.json', records), ('metadata.json', meta)]:
    (out / name).write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n', encoding='utf-8')
print(json.dumps({k:meta[k] for k in ['recognizedAwards','recognizedEquivalents']},ensure_ascii=False))
