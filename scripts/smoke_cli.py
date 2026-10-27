#!/usr/bin/env python3
"""Exercise the actual standalone JAR, JSON/CSV exports and replay verification on each OS."""
import csv
import json
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / 'target/war-simulation.jar'


def main():
    with tempfile.TemporaryDirectory(prefix='War CLI check ') as temporary:
        cwd = Path(temporary)
        def run(*arguments, code=0):
            result = subprocess.run(['java', '-jar', str(JAR), *map(str, arguments)], cwd=cwd,
                                    capture_output=True, text=True, encoding='utf-8', timeout=60)
            if result.returncode != code:
                raise AssertionError(f'Exit {result.returncode}, expected {code}: {result.stderr}')
            return result
        first = run('play', '--seed', '9223372036854775807', '--names', 'Zoë "A",Jordan',
                    '--max-rounds', '500', '--trace', 'first.war', '--summary', 'game.json')
        game = json.loads(first.stdout)
        assert game['seed'] == '9223372036854775807'  # Exact 64-bit seed; no browser-number rounding.
        assert game['players'] == ['Zoë "A"', 'Jordan']
        assert json.loads((cwd / 'game.json').read_text(encoding='utf-8')) == game
        second = run('play', '--seed', '9223372036854775807', '--names', 'Zoë "A",Jordan',
                     '--max-rounds', '500', '--trace', 'second.war')
        assert json.loads(second.stdout) == game
        assert (cwd / 'first.war').read_bytes() == (cwd / 'second.war').read_bytes()
        replay = json.loads(run('replay', '--file', 'first.war').stdout)
        assert replay['result'] == game['result']
        content = (cwd / 'first.war').read_text(encoding='utf-8')
        (cwd / 'tampered.war').write_text(content.replace('initial ', 'changed ', 1), encoding='utf-8')
        assert 'Replay/export error' in run('replay', '--file', 'tampered.war', code=1).stderr
        batch = json.loads(run('simulate', '--games', '100', '--players', '3', '--seed', '-50',
                               '--max-rounds', '500', '--csv', 'batch.csv', '--summary', 'batch.json').stdout)
        with (cwd / 'batch.csv').open(encoding='utf-8', newline='') as source:
            rows = list(csv.DictReader(source))
        assert len(rows) == batch['games'] == 100
        assert [int(row['seed']) for row in rows] == list(range(-50, 50))
        assert sum(int(row['rounds']) for row in rows) == batch['totalRounds']
        assert sum(int(row['wars']) for row in rows) == batch['totalWars']
        assert sum(w['count'] for w in batch['wins']) + sum(batch['drawsByReason'].values()) == 100
        assert sum(row['status'] == 'WIN' for row in rows) == sum(w['count'] for w in batch['wins'])
        assert json.loads((cwd / 'batch.json').read_text(encoding='utf-8')) == batch
        assert json.loads(run('simulate', '--games', '100', '--players', '3', '--seed', '-50', '--max-rounds', '500').stdout) == batch
        run('play', '--players', '0', code=2)
        run('play', '--seed', 'not-a-number', code=2)
        run('simulate', '--games', '2', '--seed', '9223372036854775807', code=2)
        assert 'simulate' in run('help').stdout
        print('PASS: external-working-directory JAR, exact JSON/CSV totals, UTF-8 names, full-range seeds, reproducible replay, tamper rejection, and exit codes')


if __name__ == '__main__':
    main()
