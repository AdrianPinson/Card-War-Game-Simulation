#!/usr/bin/env python3
"""Benchmark real CLI batches and reject any report disagreement across worker counts."""
import argparse
import json
from pathlib import Path
import statistics
import subprocess
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--games', type=int, default=1000)
parser.add_argument('--rounds', type=int, default=1500)
parser.add_argument('--workers', type=int, default=4)
parser.add_argument('--repeats', type=int, default=3)
args = parser.parse_args()
if args.repeats < 1:
    parser.error('repeats must be positive')
root = Path(__file__).resolve().parents[1]
reference = None
timings = {1: [], args.workers: []}
for repeat in range(args.repeats + 1):
    # Alternate order to reduce consistent first-run bias; the first pair warms up.
    order = [1, args.workers] if repeat % 2 == 0 else [args.workers, 1]
    for workers in order:
        command = ['java', '-jar', str(root / 'target/war-simulation.jar'), 'simulate',
                   '--games', str(args.games), '--max-rounds', str(args.rounds),
                   '--seed', '42', '--workers', str(workers)]
        started = time.perf_counter()
        result = subprocess.run(command, capture_output=True, text=True, check=True)
        elapsed = time.perf_counter() - started
        report = json.loads(result.stdout)
        if reference is None:
            reference = report
        if report != reference:
            raise AssertionError('Reports differ across worker counts')
        if repeat:
            timings[workers].append(elapsed)
medians = {str(k): statistics.median(v) for k, v in timings.items()}
print(json.dumps({'games': args.games, 'roundCap': args.rounds, 'repeats': args.repeats,
                  'medianSecondsIncludingJvmStartup': medians,
                  'sequentialOverParallelRatio': medians['1'] / medians[str(args.workers)],
                  'reportEquality': True}, indent=2))
