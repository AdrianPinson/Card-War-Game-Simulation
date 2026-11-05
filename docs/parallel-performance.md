# Parallel batch measurement

Measured November 5, 2026 in a shared Linux execution container, OpenJDK 17.

```sh
mvn clean verify
python scripts/benchmark_parallel.py --games 1000 --workers 4 --repeats 3
```

Workload: two seats, seeds 42–1041, three face-down cards, cycle detection enabled,
1,500-round cap. One warm-up pair is discarded; run order alternates for the three
measured pairs. Each timing starts a separate JVM and includes startup and JSON output.

| Workers | Median wall time |
| --- | ---: |
| 1 | 3.401 s |
| 4 | 2.189 s |

The observed sequential/parallel ratio was **1.55×** for this
workload. Every parsed JSON report was identical across worker counts. Separate tests
compare ordered per-seed results and CSV bytes across 1, 2, 4, and 16 workers.

These are local measurements rather than a universal speed claim. Shared CPU allocation,
GC, per-game cycle-state memory, worker count, JVM startup, and game lengths affect results.
The sample is small and has no timing confidence interval. Smaller jobs may be slower
with extra workers. Rendering, replay writing, and CSV file I/O are not benchmarked.
