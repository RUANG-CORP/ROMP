import csv

weights = {'must': 3.0, 'should': 2.0, 'could': 1.0}
total_weight = 0.0
achieved_weight = 0.0

with open('replica/features.csv', mode='r', encoding='utf-8') as f:
    reader = csv.DictReader(f)
    for row in reader:
        priority = row['priority'].strip().lower()
        if priority not in weights:
            continue
        w = weights[priority]
        total_weight += w
        if row['clone'].strip().lower() == 'yes':
            achieved_weight += w

score = (achieved_weight / total_weight) * 100.0 if total_weight > 0 else 0.0
print(f"Features Parity Score: {score:.1f}% ({achieved_weight:.1f}/{total_weight:.1f} points)")

