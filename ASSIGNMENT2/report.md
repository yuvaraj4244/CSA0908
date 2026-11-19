---
title: "CSA09 – Programming in Java: Assignment 2 (CO2) – Report"
---

| | |
|---|---|
| **Name** | ______________________ |
| **Register number** | ______________________ |
| **GitHub username** | yuvaraj4244 |
| **GitHub repository** | https://github.com/yuvaraj4244/CSA09-\<RegisterNumber\>-Assignment2 |
| **Java version** | OpenJDK 21.0.12 (javac 21.0.12) |
| **Machine** | 2 vCPU Linux container, 8 GB RAM, default JVM flags except `-Xmx` (1–3 GB) |

**How to read this report.** Every question follows the sequence *Predict → Design → Implement → Measure → Analyse → Evaluate*. Predictions were written before the programs were run. All timings are `System.nanoTime()` means over repeated runs after JIT warm-up (counts are stated under each table); allocation is the exact per-thread allocated byte count from `com.sun.management.ThreadMXBean`. Raw data is in `Qn/raw_data*.csv`, the full console output in `Qn/output*.txt`, and the source in `Qn/*.java`. Absolute numbers depend on the machine; the *shape* of each curve (how cost grows with n) is the evidence.

**Honest limits of the evidence.** These are single-JVM micro-benchmarks. Small sizes (n ≤ 1,000) are dominated by JIT and cache noise; I draw conclusions only from trends across sizes, and I flag the few anomalies I saw.

# Q1 – Building text under load: String, StringBuilder or StringBuffer?

## 1. Prediction (before implementation)

Let a ticket have *n* fields of average length *L* characters.

* **String +** : `String` is immutable, so `s = s + x + "|"` creates a *new* String and copies every character built so far. The *i*-th iteration copies about *i·L* characters, so the total is *L·n(n+1)/2*, i.e. **O(n²·L) characters copied** and about **n intermediate String objects** per ticket (javac compiles one `+` expression to one concatenation, so one new String per loop iteration).
* **StringBuilder** : mutable `char`/`byte` buffer that doubles when full, so each character is copied a constant number of times on average: **O(n·L)** time; zero intermediate Strings (one builder, a few buffer reallocations, one final String).
* **StringBuffer** : same algorithm, but every method is `synchronized`; expected ≈ StringBuilder, possibly slightly slower.
* **Expected behaviour as n grows:** for 50 fields (≈ 800 characters) the difference should be small in absolute terms; it should become dramatic as n grows, with the time of `+` roughly **quadrupling each time n doubles**, while StringBuilder only doubles.

## 2. Workloads

| | Workload | Purpose |
|---|---|---|
| W1 | one short ticket, 50 fields | typical single booking |
| W2 | 10,000 tickets × 50 fields | busy server, many small tickets |
| W3 (my own) | one huge ticket with 5,000 / 10,000 / 20,000 / 40,000 fields (n doubling) | isolates the growth order |

Approaches implemented (`Q1/TicketBench.java`): `String +`, `StringBuilder`, `StringBuffer`, plus the alternatives `String.join`, `StringJoiner`, and `String.format` (one call per field). W1: 20,000 warm-up passes then 20 timed runs; W2/W3: 3 warm-up passes then the mean of 10 timed runs.

## 3. Results

**W1 – one ticket of 50 fields**

| Approach | time per ticket (µs) | allocated per ticket (KB) |
|---|---|---|
| String + | 4.42 | 21.2 |
| StringBuilder | 1.60 | 3.2 |
| StringBuffer | 1.58 | 3.2 |
| String.join | 1.98 | 1.0 |
| StringJoiner | 0.43 | 1.3 |
| String.format | 4.35 | 14.5 |


**W2 – 10,000 tickets × 50 fields**

| Approach | time for 10,000 tickets (ms) | allocated (MB) | vs StringBuilder |
|---|---|---|---|
| String + | 29.76 | 206.8 | 4.3× |
| StringBuilder | 6.85 | 30.7 | 1.0× |
| StringBuffer | 7.71 | 31.1 | 1.1× |
| String.join | 2.32 | 9.7 | 0.3× |
| StringJoiner | 5.49 | 12.8 | 0.8× |
| String.format | 31.71 | 152.8 | 4.6× |


**W3 – one huge ticket (n doubles each row)**

| fields n | String + (ms) | StringBuilder (ms) | StringBuffer (ms) | String.join (ms) | StringJoiner (ms) | String + alloc (MB) | StringBuilder alloc (MB) |
|---|---|---|---|---|---|---|---|
| 5,000 | 36.9 | 0.077 | 0.079 | 0.031 | 0.068 | 229 | 0.37 |
| 10,000 | 153.8 | 0.144 | 0.293 | 0.074 | 0.122 | 934 | 0.75 |
| 20,000 | 526.7 | 0.617 | 0.632 | 0.193 | 0.327 | 3,870 | 1.52 |
| 40,000 | 2,318.5 | 0.570 | 0.788 | 0.393 | 0.589 | 16,035 | 3.07 |


![Q1 – time against ticket size (log-log axes).](charts/q1.png){width=75%}

Estimated intermediate Strings per ticket: `String +` ≈ n (50 per W1/W2 ticket, so ≈ 500,000 for W2); `String.format` ≈ n as well (one formatted String per field); StringBuilder / StringBuffer / join / StringJoiner create **no intermediate Strings** — only the final one.

Memory before/after one 20,000-field ticket (`Runtime` heap, after `System.gc()`): `String +` heap grew 3.5 MB → 102 MB (garbage not yet collected) with **3,870 MB allocated in total**; StringBuilder 3.0 MB → 5.2 MB with **1.5 MB allocated**.

## 4. Prediction versus observation

* **Supported:** `String +` allocates about 4× more every time n doubles (229 → 934 → 3,870 → 16,035 MB), the exact signature of O(n²) copying. Its time grows 4.2×, 3.4×, 4.4× per doubling; StringBuilder's allocation exactly doubles (0.37 → 0.75 → 1.52 → 3.07 MB), i.e. O(n).
* **Supported:** with 50 fields the absolute difference per ticket is only a few microseconds (W1) — "negligible" for *one* ticket — but with 10,000 tickets `String +` is 4.3× slower than StringBuilder and allocates 6.7× more (W2). A ticket of 40,000 fields costs 2.3 s with `+` versus under a millisecond.
* **Partly different from my prediction:** StringBuffer was not measurably slower than StringBuilder on one thread (W2: 7.7 ms vs 6.8 ms; W3 within noise). The JVM makes uncontended locking very cheap; the cost of `synchronized` appears under contention, not here. `String.join` was the fastest (it computes the final length first and allocates once). `String.format` per field was as slow as `+` (≈ 32 ms) because every call parses the format string.
* **Anomaly:** StringBuilder at 40,000 fields (0.57 ms) is faster than expected relative to 20,000 fields (0.62 ms); at sub-millisecond scale this is JIT/GC noise, not a real effect.

## 5. Design decision (multithreaded booking server)

*Test:* 4 threads × 20,000 appends of one character to one shared log (expected length 80,000), 30 trials. **StringBuilder: 2 trials lost updates and 1 trial threw an exception; StringBuffer: 0 problems.** A non-synchronised builder corrupts its internal `count`/array when threads race.

* **Per-ticket text → `StringBuilder`.** The ticket is built inside one request, in a local variable confined to one thread. No sharing means no need for synchronisation, and it is the fastest general-purpose choice (O(n)).
* **Shared booking log → `StringBuffer` is the minimal correct choice, with one caveat:** each *call* is atomic, but `log.append(a).append(b)` is two calls and another thread may interleave between them. So each thread should first build its complete log line in its own StringBuilder and then do **one** `log.append(line)` on the shared StringBuffer (or use a lock, or better a `ConcurrentLinkedQueue`/logging framework for a production server).

## 6. Alternative concept and complexity

* **`String.join(sep, fields)`** / **`StringJoiner`**: the collection is traversed once; `String.join` sizes the result exactly and copies each character once → **O(n·L) time, O(n·L) space**, no intermediate Strings. Fastest in my W2/W3 measurements (2.3 ms vs 6.8 ms for 10,000 tickets). Best when all fields are available as an array/collection; the builder is better when the text is built conditionally in a loop.
* **`String.format`** is for one-off readable templates (`"PNR %s Coach %s Berth %d"`); inside a per-field loop it re-parses the pattern each time, so it is slow (measured ≈ same as `+`).

| Approach | Time to build a ticket of *n* fields (each *L* chars) | Extra space created |
|---|---|---|
| repeated `+` | Σ i·L = **O(n²·L)** characters copied | n intermediate Strings; total garbage **O(n²·L)**; live memory ≈ 2 Strings |
| StringBuilder | **O(n·L)** amortised (capacity doubles, each char copied ≤ ~2 times) | 1 buffer ≤ 2× final size + 1 final String → **O(n·L)**, no intermediate Strings |
| StringBuffer | O(n·L) + constant lock cost per call | same as StringBuilder |
| join / StringJoiner | O(n·L) | O(n·L), one allocation of the exact size (join) |

## 7. Evidence-based recommendation

Use **StringBuilder** per ticket (or `String.join` when the fields are already in a collection); never `+` in a loop. Use a **StringBuffer (or a lock) only for the shared log**, appending whole lines.

# Q2 – When is ArrayList an appropriate engineering choice?

## 1. Prediction

* **ArrayList** (array): add at end O(1) amortised; insert/remove at the front or middle must shift elements with `System.arraycopy` → O(n); `get(k)` O(1); iteration O(n) and very fast (contiguous memory, cache-friendly).
* **LinkedList** (doubly linked nodes): insert/remove at the *front* O(1); at the middle O(1) to link **but O(n) to walk to the position**; `get(k)` O(n); iteration O(n) but slower (pointer chasing).
* **ArrayDeque** (circular array): O(1) at both ends; no index operations.

## 2. Experiments (`Q2/PlaylistBench.java`)

Four sizes: **n = 1,000; 10,000; 100,000; 400,000**. Each operation is measured on a freshly built list (setup excluded): *add at end* builds n elements from empty; *insert/remove at front or middle* and *get* use K = 1,000 operations (`get` at pre-generated random indexes); *iterate* sums all elements with for-each. Mean of 7 runs after 3 warm-ups; values are **ns per operation**. `Array[]` is a plain array with manual element shifting (the "alternative concept" of the question).

| n | structure | add end | insert front | insert middle | remove front | remove middle | get k-th | iterate (per elem) |
|---|---|---|---|---|---|---|---|---|
| 1,000 | ArrayList | 84.1 | 62.4 | 67.3 | 50.3 | 50.1 | 28.8 | 112.8 |
| 1,000 | LinkedList | 61.0 | 133.3 | 1,017.3 | 36.3 | 324.1 | 434.4 | 93.1 |
| 1,000 | ArrayDeque | 99.8 | 64.4 | n/a | 27.2 | n/a | n/a | 30.3 |
| 1,000 | Array[] | 13.2 | 1,337.9 | 930.0 | 893.8 | 475.6 | 14.9 | 13.9 |
| 10,000 | ArrayList | 25.2 | 261.7 | 138.0 | 213.8 | 130.2 | 25.8 | 13.5 |
| 10,000 | LinkedList | 17.8 | 16.2 | 6,733.3 | 16.1 | 6,080.7 | 3,172.4 | 8.4 |
| 10,000 | ArrayDeque | 15.4 | 44.8 | n/a | 29.1 | n/a | n/a | 17.8 |
| 10,000 | Array[] | 9.0 | 9,259.4 | 4,645.7 | 11,075.4 | 5,881.0 | 14.6 | 11.9 |
| 100,000 | ArrayList | 4.7 | 7,729.9 | 3,764.8 | 7,081.8 | 3,613.4 | 29.3 | 2.5 |
| 100,000 | LinkedList | 8.2 | 17.8 | 68,721.3 | 25.4 | 65,460.7 | 40,176.5 | 5.7 |
| 100,000 | ArrayDeque | 15.2 | 322.3 | n/a | 26.0 | n/a | n/a | 2.5 |
| 100,000 | Array[] | 3.0 | 95,013.7 | 46,609.0 | 121,731.6 | 64,867.7 | 15.7 | 2.1 |
| 400,000 | ArrayList | 62.5 | 30,421.2 | 14,720.1 | 29,724.7 | 14,899.7 | 91.7 | 1.7 |
| 400,000 | LinkedList | 32.9 | 63.6 | 382,284.0 | 11.1 | 284,567.4 | 149,026.7 | 4.9 |
| 400,000 | ArrayDeque | 29.7 | 1,867.9 | n/a | 32.1 | n/a | n/a | 1.9 |
| 400,000 | Array[] | 9.5 | 2,575,248.8 | 1,333,069.2 | 2,558,669.8 | 1,279,948.4 | 15.2 | 1.2 |


![Q2 – the two operations that separate the structures most (log-log axes).](charts/q2.png){width=95%}

## 3. Observations

* ArrayList insert/remove at front or middle grows **linearly with n** (front: 62 ns → 262 ns → 7,730 ns → 30,421 ns); at 400,000 songs one front insertion costs 30 µs.
* LinkedList is O(1) at the front (≈ 16–133 ns at every size) **but its middle insert/remove is much worse than ArrayList's** (68,721 ns vs 3,765 ns at 100,000), and `get(k)` is 40,177 ns vs 29 ns. Walking nodes scattered in memory costs far more than ArrayList's bulk `arraycopy`, even though both are O(n). Its asymptotic advantage exists only at the very front.
* ArrayDeque matches LinkedList at the ends (remove front ≈ 26–32 ns) with a smaller constant. (Its `addFirst` figure at 100k–400k, 322 and 1,868 ns, is inflated by one array growth falling inside the 1,000 timed operations; I believe this is the cause but did not isolate it.)
* Iteration: ArrayList and ArrayDeque ≈ 2–3 ns per element at 100,000+; LinkedList ≈ 5 ns.
* The manual array confirms the theory: its shifting cost is the same O(n) as ArrayList but slower per element (a Java loop versus the intrinsic `arraycopy`), e.g. 95,014 ns vs 7,730 ns for a front insertion at 100,000.
* Small-size rows (n = 1,000) are noisy (JIT warm-up) — e.g. ArrayList `add end` 84 ns vs 5 ns at 100,000.

## 4. Answer: under what usage pattern is ArrayList reasonable, and when to stop recommending it?

**Reasonable when** the playlist is mostly appended to, read by position, and iterated, and edits at the front or middle are *occasional* (human-driven: a few per second at most). My own data: even at 400,000 songs a front insertion costs 30 µs, so a user editing one song at a time never notices; at 10,000 songs (a very large real playlist) it costs 0.26 µs. ArrayList also has the best iteration speed and `get(k)`, the memory per element is the smallest (one 4-byte reference slot, up to 50% unused capacity) and nothing else is simpler.

**Stop recommending it when** *many* front/middle edits happen on a *large* list — bulk operations such as "insert 100,000 songs at the front one by one" (100,000 × 7.7 µs ≈ 0.8 s and growing quadratically), or any queue-like usage that removes from the front (n = 100,000: 7,082 ns vs 26 ns for ArrayDeque). My operating boundary: ArrayList shifted roughly 13 elements per nanosecond here, so the cost is (number of front/middle edits) × n ÷ 13 per ns. It stops being reasonable when a *batch* of edits times n reaches seconds — for example 10⁵ front insertions into a 10⁵-song list (≈ 0.8 s) — or when edits are continuous and n exceeds ~10⁵–10⁶.

LinkedList is *not* the fix for the middle: it was 18× slower than ArrayList for middle insertion at 100,000. It should only be considered for front-heavy patterns where ArrayDeque is not enough, which is rare.

## 5. Changed condition: the "Up Next" queue

Songs are added at both ends and removed from the front → a **double-ended queue**. Re-evaluation: ArrayList is wrong for *this* structure (front removal O(n)); LinkedList works but allocates a 24-byte node per song; **ArrayDeque is the right choice**: O(1) `addFirst`/`addLast`/`pollFirst` (26 ns removal vs 7,082 ns for ArrayList at 100,000 songs), compact, cache-friendly. The playlist itself (random access by index, `get(k)`) stays an ArrayList. Final design: **`ArrayList<Song>` for the playlist, `ArrayDeque<Song>` for Up Next.**

## 6. Alternative concept and complexity

| Operation | ArrayList | LinkedList | ArrayDeque | Plain array, manual shifting |
|---|---|---|---|---|
| add at end | O(1) amortised (O(n) on resize) | O(1) | O(1) amortised | O(1) while capacity remains; O(n) to grow |
| insert at front | **O(n)** | **O(1)** | **O(1)** amortised | O(n) |
| insert in the middle | O(n) (shift) | O(n) (walk) + O(1) link | not supported | O(n) (shift) |
| get k-th | **O(1)** | **O(n)** | not supported | O(1) |
| iterate whole list | O(n), fastest | O(n), slower | O(n), fast | O(n) |
| Space per element | 1 reference slot (4 B with compressed oops), plus ≤ 50% spare capacity | 1 node ≈ 24 B (header 12 + item 4 + prev 4 + next 4) | 1 slot (4 B) plus spare capacity ≤ 100% | 1 slot, fixed capacity |

If the playlist were a **plain array with manual shifting**, every insertion/removal at position *p* needs a loop shifting n − p elements (O(n)), plus a full copy to a larger array when capacity is exhausted (O(n)); `get` O(1). That is what ArrayList already does internally (with `arraycopy`), so ArrayList is the plain array plus growth logic — measured manual shifting was 12× slower than ArrayList at 100,000 songs (95 µs vs 7.7 µs).

# Q3 – Engineering the hashCode–equals contract

## 1. Designed tests and predictions (made before running `Q3/ContractTests.java`)

| # | Test | Prediction | Reason |
|---|---|---|---|
| 1 | `BadStudent` a, d added, then **the same object a** again | size **2** | same reference → same identity hash → same bucket, and `==` short-circuits |
| 2 | `BadStudent` Arun, Divya, **new** Arun (equal by `equals`, distinct objects) | `equals()` alone suggests 2 (the *naive* prediction, printed by the program); the contract violation should make it **3** | `Object.hashCode()` is identity-based, so the two equal Aruns get different hashes, go to different buckets, and `equals()` is never even called |
| 2b | same scans with the corrected class | **2** | equal objects share a hash code |
| 3 (own) | key field used in `hashCode()` changed *after* insertion | the object becomes unreachable: `contains` false, `remove` false; re-adding gives **2** entries | the entry stays in the bucket of the old hash; lookups compute the new hash |
| 4 (own) | `TreeSet` with a comparator on `name` only (inconsistent with `equals`) | two different students with the same name → size **1** (HashSet: 2) | TreeSet never calls `equals`; `compare() == 0` means "duplicate" |

## 2. Observed results

```
Test 1  predicted=2 observed=2  [MATCH]
Test 2  predicted=2 (by equals) observed=3  [DIFFERS]   hashCodes 5a07e868 vs 76ed5528
Test 2b predicted=2 observed=2  [MATCH]
Test 3  after mutation: contains=false, remove=false, size=1; after re-add size=2
Test 4  TreeSet(by name) size 1, HashSet size 2
Contract check (corrected class): equal objects -> equal hashCodes: PASS
1000 equal BadStudent objects produced 1000 distinct hashCodes (contract needs 1)
```

**The supplied program (`Q3/before` and `Q3/Attendance.java`):**

| Version | Output |
|---|---|
| Before TODO 1 (no `hashCode`) | `Unique students: 3` |
| After TODO 1 (`hashCode` added) | `Unique students: 2` (matches the expected output) |

**Why they differ:** without `hashCode()` the two "Arun" objects inherit different identity hash codes, so `HashSet` stores them in different buckets and never compares them with `equals()`.

## 3. Inquiry

* **Why duplicates appear:** the contract says *equal objects must have equal hash codes*. The original class overrides `equals` only, so it violates this.
* **How HashSet locates an element:** it computes `h = hashCode()`, spreads it (`h ^ (h >>> 16)`), picks the bucket `(table.length − 1) & h`, and then, only within that bucket, compares the stored hash and then uses `==` or `equals()`. A wrong hash means looking in the wrong bucket; `equals()` alone can never rescue it.
* **Changing a hashCode field after insertion:** the element stays in the old bucket; `contains/remove` search the new bucket and fail (Test 3: `contains=false`, `remove=false`), so it is a "ghost" that cannot be removed and a duplicate can be added. Hash keys must therefore be immutable (`final` fields).
* **Correction and verification:** make the fields `final` and implement `hashCode()` from exactly the fields used in `equals()` — `return regNo.hashCode();` (equivalently `Objects.hash(regNo)`). Verified by (i) Test 2b = 2, (ii) the output of the supplied program 3 → 2, (iii) the contract assertion "x.equals(y) ⇒ x.hashCode()==y.hashCode()", PASS.

## 4. Alternative concept and complexity

`TreeSet<Student>` with a `Comparator` on `regNo`, or `HashMap<String, Student>` keyed by `regNo`. (A comparator that is *consistent with equals* is required — Test 4 shows what happens otherwise.)

| Structure | Time per scan (n unique students) | Space for n unique students |
|---|---|---|
| `HashSet<Student>` | O(1) average (needs a correct `hashCode`), O(n) worst case in theory | n nodes (≈ 32 B each) + table (4 B per slot) |
| `TreeSet<Student>` (Comparator on regNo) | **O(log n)**, needs no `hashCode`, keeps students sorted | n entries (≈ 40 B each); no table |
| `HashMap<String,Student>` keyed by regNo | O(1) average; `String` already has a correct `equals/hashCode`, so the contract bug cannot occur | same as HashSet, plus allows lookup *by regNo* |

Measured (n scans of n unique students; ns per scan):

| n | structure | ns per scan | allocated (MB, incl. resize garbage) |
|---|---|---|---|
| 10,000 | HashSet<Student> | 224.2 | 0.7 |
| 10,000 | TreeSet<Student>(regNo) | 849.0 | 0.4 |
| 10,000 | HashMap<String,Student> | 35.0 | 0.4 |
| 100,000 | HashSet<Student> | 85.8 | 5.1 |
| 100,000 | TreeSet<Student>(regNo) | 209.2 | 3.8 |
| 100,000 | HashMap<String,Student> | 40.5 | 5.1 |
| 1,000,000 | HashSet<Student> | 81.1 | 46.5 |
| 1,000,000 | TreeSet<Student>(regNo) | 250.5 | 38.1 |
| 1,000,000 | HashMap<String,Student> | 70.7 | 46.5 |


(The "allocated" figure includes garbage created while the hash table resized, so it is larger than the live size.) HashSet/HashMap are 3–3.5× faster than TreeSet at 10⁶ students (81 / 71 vs 251 ns), matching O(1) versus O(log n). `HashMap<String,Student>` is the most robust choice because it moves the contract into `String`, which is already correct.

# Q4 – Choosing a Map for a pharmacy inventory

## 1. Design reasoning before implementation

| Map | put / get | Iteration | Natural report |
|---|---|---|---|
| `HashMap` | O(1) average | O(n + capacity), **unspecified order** | neither report |
| `TreeMap` (red-black tree) | O(log n) | O(n), **sorted by key** | **code order** |
| `LinkedHashMap` | O(1) average | O(n), **insertion order** | **order they were added** |

Extra work when the wrong map is chosen: for the *code-order* report from a HashMap/LinkedHashMap, copy the entries and sort them (O(n log n) time, O(n) extra space); for the *insertion-order* report from a HashMap/TreeMap, an additional list or insertion counter must be maintained (O(n) extra space) and a TreeMap would have to be sorted by that counter (O(n log n)).

**Acceptable response time (my definition):** a lookup is acceptable if it takes under **1 ms on average** (a pharmacist cannot perceive anything under ~100 ms; 1 ms leaves a 100× safety margin for the rest of the system). The evening reports are batch jobs: acceptable if they finish within **1 second**.

## 2. Implementation and measurement (`Q4/PharmacyBench.java`)

Medicine codes `MED0000000…` inserted in **shuffled order** (so insertion order ≠ code order). n = **1,000; 10,000; 100,000; 1,000,000**. 100,000 random lookups per run. Mean of 5 runs (3 at n = 1,000,000) after warm-up. The *code-order report* and *insertion-order report* columns include whatever extra work the map needs (copy + sort, or a side list). `Hashtable` and a sorted array with `Arrays.binarySearch` are included for comparison.

| n | structure | build (ms) | 100k lookups (ms) | ns / lookup | code-order report (ms) | insertion-order report (ms) |
|---|---|---|---|---|---|---|
| 1,000 | HashMap | 0.19 | 4.80 | 48 | 0.62 | 0.05 |
| 1,000 | TreeMap | 0.51 | 12.81 | 128 | 0.09 | 0.05 |
| 1,000 | LinkedHashMap | 0.18 | 2.86 | 29 | 0.44 | 0.07 |
| 1,000 | Hashtable | 0.21 | 3.64 | 36 | 0.18 | 0.04 |
| 1,000 | sorted array + binarySearch | 0.44 | 16.76 | 168 | 0 (already sorted) | n/a (separate list) |
| 10,000 | HashMap | 0.80 | 3.13 | 31 | 2.22 | 0.24 |
| 10,000 | TreeMap | 2.71 | 20.98 | 210 | 0.36 | 0.13 |
| 10,000 | LinkedHashMap | 0.66 | 3.57 | 36 | 3.02 | 0.21 |
| 10,000 | Hashtable | 0.74 | 3.85 | 38 | 1.73 | 0.17 |
| 10,000 | sorted array + binarySearch | 3.99 | 21.54 | 215 | 0 (already sorted) | n/a (separate list) |
| 100,000 | HashMap | 15.36 | 5.70 | 57 | 19.62 | 1.62 |
| 100,000 | TreeMap | 69.00 | 62.98 | 630 | 6.52 | 1.53 |
| 100,000 | LinkedHashMap | 16.54 | 6.19 | 62 | 46.29 | 1.14 |
| 100,000 | Hashtable | 12.46 | 4.96 | 50 | 11.29 | 0.83 |
| 100,000 | sorted array + binarySearch | 58.13 | 42.92 | 429 | 0 (already sorted) | n/a (separate list) |
| 1,000,000 | HashMap | 183.29 | 9.06 | 91 | 487.33 | 29.39 |
| 1,000,000 | TreeMap | 1,476.24 | 136.56 | 1,366 | 144.04 | 23.45 |
| 1,000,000 | LinkedHashMap | 213.10 | 17.02 | 170 | 754.11 | 20.64 |
| 1,000,000 | Hashtable | 150.00 | 9.66 | 97 | 382.58 | 19.17 |
| 1,000,000 | sorted array + binarySearch | 745.95 | 138.19 | 1,382 | 0 (already sorted) | n/a (separate list) |


Array insertion cost (200 insertions into the sorted array, shifting with `System.arraycopy`):

| n | array insert with shifting (ns/op) |
|---|---|
| 1,000 | 3,302 |
| 10,000 | 1,187 |
| 100,000 | 890 |
| 1,000,000 | 7,414 |


![Q4 – cost of one lookup against inventory size (log-log axes).](charts/q4.png){width=75%}

**Output-order check (program verifies it):** raw iteration of `TreeMap` is code-sorted; of `LinkedHashMap` is insertion-ordered; of `HashMap` is neither — confirmed for n = 1,000 and 1,000,000. After the extra work, every produced report matched its required order (`code:Y/ins:Y` in `Q4/output.txt`).

## 3. Scalability inquiry and decision

* **All structures meet the 1 ms criterion by a very wide margin**: the slowest lookup measured was **1.4 µs** (TreeMap and the sorted array at 1,000,000 medicines) — about 700× under the limit. A pharmacy with a few hundred lookups a day is therefore not performance-constrained by any of them.
* HashMap/LinkedHashMap lookups are 8–15× faster than TreeMap at large n (62 vs 630 ns at 100,000; 91–170 vs 1,366 ns at 1,000,000), as O(1) vs O(log n) predicts; the growth of the hash-map lookups from 57–62 ns (100,000) to 91–170 ns (1,000,000) is mostly cache misses (memory latency), not algorithmic.
* **Deployed choice: `LinkedHashMap<String, Medicine>`.** It gives O(1) lookups and **the insertion-order report for free** (1.1 ms at 100,000). The *code-order report* needs one copy-and-sort per evening: 46 ms at 100,000 and 754 ms at 1,000,000, within my 1-second batch limit. `TreeMap` would produce the code-order report faster (6.5 ms) but would need a separate structure for the insertion-order report, and costs 10× more per lookup, so there is no gain that matters.
* **Where the remaining cost comes from:** for lookups, String hash computation (cached after the first call) and cache misses walking the table and nodes; for the evening report, the sort comparisons on String codes (O(n log n)), not iteration.
* **Why `Hashtable` is not preferred in new code:** it is a legacy class (pre-Collections Framework); *every* method is `synchronized` (coarse lock, so poor scalability under threads and a needless cost single-threaded), it forbids `null` keys/values, and it offers no ordering. Its single-thread speed in my test was similar to HashMap (97 vs 91 ns at 1,000,000) — the lock is cheap when uncontended — but the design is obsolete. Use `HashMap` (single thread) or `ConcurrentHashMap` (concurrent access).

## 4. Alternative concept: sorted array + `Arrays.binarySearch()`

| Operation | Sorted array of Medicine | HashMap | TreeMap |
|---|---|---|---|
| Lookup | **O(log n)** binary search (measured 168 → 1,382 ns, 1k → 1M) | O(1) average | O(log n) |
| Insert | **O(n)** (find position O(log n), then shift n − p elements) — measured 7.4 µs at 1M | O(1) average | O(log n) |
| Code-order report | **O(n)** — already sorted | O(n log n) (copy + sort) | O(n) |
| Insertion-order report | needs a side list | needs a side list | needs a side list |
| Initial build | O(n log n) (sort) | O(n) | O(n log n) |
| Space | **smallest**: one 4-byte reference per medicine, no per-entry nodes | ≈ 32 B node + 4 B table slot per entry | ≈ 40 B node per entry |

The sorted array is a good *read-mostly* design (smallest memory, cache-friendly, code-order report for free), but its O(n) insertion makes it unsuitable if stock items are added often; it gives O(log n) lookup like TreeMap but with worse inserts. For this pharmacy, hash lookups dominate, so a LinkedHashMap remains the better fit.

# Q5 – Deriving a generic ranking component from a principle

## 1. Design derived from the principle

> *A ranking is correct only if the comparison is consistent: if A ranks above B and B above C, then A above C, and equal items compare as equal.*

This is exactly the requirement that a `Comparator` be a **strict weak ordering**: (i) *antisymmetric* `sign(compare(a,b)) = −sign(compare(b,a))`, (ii) *transitive*, (iii) *"equal" is transitive too* (`compare(a,b)=0 ∧ compare(b,c)=0 ⇒ compare(a,c)=0`). I derived the design from that:

* **Comparable<Team> or Comparator?** Teams can legitimately be ordered in several ways (by points, by goal difference, by name, "most improved"…), and the same ranking component will be reused for players, colleges and quiz teams that each need *their own* rule. `Comparable` hard-wires **one** natural ordering inside the class. I therefore chose **separate `Comparator` objects** passed to a generic `Leaderboard<T>`; `Team` stays a plain data class. (I also implemented a `Comparable<CTeam>` variant to compare: it works, but cannot express a second ranking without editing the class.)
* **Multi-field comparison:** the comparator is built as a *lexicographic chain*: `Comparator.comparingInt(points).reversed().thenComparing(comparingInt(goalDiff).reversed()).thenComparing(name)`. Each key comparator is a total preorder; a lexicographic composition of total preorders is again one (compare the first key; only if equal move to the next), and the final key (name) makes distinct teams unequal. Therefore the principle holds by construction.
  * `comparingInt` uses `Integer.compare`, never subtraction (overflow, see §3).
  * `.reversed()` flips only the key it is attached to, so it is applied to *that key's* comparator before chaining (otherwise it would reverse the whole chain).
* **Generic class with bounded types:** `Leaderboard<T>` takes a `Comparator<? super T>` — it works for any T without bounding T. If the component should rank by *natural* order instead, the bound is `T extends Comparable<? super T>` (`RankedList<T>` in `Q5/RankingTests.java`).
* **Wildcards:** `Comparator<? super T>` accepts a comparator written for T or any *supertype* (a consumer → "super"; shown: `Leaderboard<String>` accepts a `Comparator<Object>`); `Collection<? extends T>` accepts a list of any *subtype* (a producer → "extends"; `RankedList.addAll(List<? extends T>)`, `printTop(List<? extends Team>)`).

## 2. Complete-the-code run (`Q5/LeaderboardDemo.java`)

TODO 1: `List<T> copy = new ArrayList<>(items); copy.sort(order); return copy;` (a sorted *copy*; `items` is untouched).
TODO 2: the comparator chain above.

```
Observed : [Eagles(9,6), Bisons(9,4), Panthers(9,4), Cobras(6,1)]
Expected : [Eagles(9,6), Bisons(9,4), Panthers(9,4), Cobras(6,1)]     -> identical
```

**Why Bisons ranks above Panthers:** both have 9 points and a goal difference of 4, so the first two levels compare as *equal*; the final tie-breaker, name in A–Z order, puts "Bisons" before "Panthers".

## 3. Test data

**Data set A** – ten teams with ties at every level (three teams with 12 points and +5 goal difference, two with 9/+3, two with 4/−1). Hand-derived ranking: Hawks, Lions, Tigers (12,+5 → name) > Wolves (12,+2) > Bears (9,+7) > Foxes, Ravens (9,+3) > Dolphins, Sharks (4,−1) > Owls. Observed output identical; and the order is the same for **200 random shuffles of the input** (order-independent — a necessary property of a correct ranking).

**Data set B** – designed to break faulty comparators:

| Faulty comparator | Failure observed | Principle violated |
|---|---|---|
| Points compared by **subtraction** `y.points − x.points` | `Big(1,500,000,000)` vs `Neg(−1,500,000,000)` gives +1,294,967,296 (overflow), so **the team with fewer points is ranked first** | antisymmetry/consistency |
| **Ignores the tie-breakers** (points only) | the same three teams give `[Panthers, Eagles, Bisons]` or `[Bisons, Eagles, Panthers]` depending on **input order**; Eagles (better goal difference) is not ranked first | equal items must truly be equal; a ranking must not depend on input order |
| "Within 2 points counts as equal" | A≈B and B≈C but A≠C (A vs C = 1): equality not transitive; `List.sort` on 2,000 random teams threw **`IllegalArgumentException: Comparison method violates its general contract!`** | transitivity |

## 4. Correctness inquiry — why my comparator satisfies the principle (by reasoning)

Let *key₁(t)* = points, *key₂(t)* = goal difference, *key₃(t)* = name; the comparator is *c(a,b)* = the first non-zero among `Integer.compare(key₁(b), key₁(a))`, `Integer.compare(key₂(b), key₂(a))`, `key₃(a).compareTo(key₃(b))`.
* **Transitivity:** suppose *a* ranks above *b* (first differing key at level *i*, *a* better) and *b* above *c* (first differing level *j*). If *i < j*, then *a* and *b* are better at level *i* and *b*, *c* are equal at level *i*, so *a* beats *c* at level *i*. If *i > j*, symmetrically *a* = *b* at level *j* and *b* beats *c* there, so *a* beats *c*. If *i = j*, *key(a) > key(b) > key(c)* at that level and each of `Integer.compare`/`String.compareTo` is itself transitive; so *a* beats *c*.
* **Equal items compare as equal, consistently:** *c(a,b) = 0* iff all three keys are equal; equality of key tuples is transitive.
* **Antisymmetry:** `Integer.compare` and `compareTo` are antisymmetric, so the first non-zero level gives opposite signs when arguments are swapped.
* Subtraction fails (i) because `y.points − x.points` can overflow and flip the sign.

## 5. Alternatives and complexity

| Approach | Time | Extra space | Reuse |
|---|---|---|---|
| **One comparator chain** + one `List.sort` | 1 sort: **O(n log n)** comparisons, each costing up to 3 key comparisons | TimSort temporary array ≤ n/2 → O(n) | easy: the comparator is a value that can be passed to a `Leaderboard<T>`, `TreeSet`, `PriorityQueue`, `binarySearch` |
| **`Comparable<Team>`** | 1 sort: O(n log n) | O(n) | only one natural ordering; bound `T extends Comparable<? super T>` |
| **Three stable sorts** (name, then goal difference, then points) | **3 sorts**: 3 × O(n log n) = still O(n log n) but ≈ 3× the constant | O(n) at a time (temp array reused sequentially) | fragile: correct only if each sort is *stable* and the passes run from the **least significant key to the most significant**; cannot be passed to a TreeSet/PriorityQueue |

Measured (mean of 7 runs after 3 warm-ups; `Collections.sort` / `List.sort`; the three approaches produced identical orderings at every size):

| n | one comparator chain (ms) | three stable sorts (ms) | Comparable<Team> (ms) | 3 sorts ÷ chain |
|---|---|---|---|---|
| 10,000 | 3.0 | 9.6 | 2.8 | 3.21× |
| 100,000 | 44.0 | 78.4 | 26.4 | 1.78× |
| 1,000,000 | 931.1 | 1,453.6 | 491.2 | 1.56× |


The chain is 1.6–3.2× faster than three sorts, consistent with the 3× constant predicted; `Comparable<Team>` is fastest (fewer lambda layers) but least flexible. **The comparator chain is the one easiest to reuse for players and colleges**: it is data (a value), composable with `thenComparing`, and carries no assumption about stability.

# Q6 – Priority, order and fairness in a queue (hospital triage)

## 1. Design

* **Why a plain Queue is not sufficient:** a `LinkedList`/`ArrayDeque` queue is FIFO: it serves by arrival only; a critical patient (S1) who arrives after a minor one (S5) would wait behind them. Priority order is required.
* **How a PriorityQueue with a Comparator enforces the rule:** the comparator `severity ascending, then arrival ascending` defines "next"; the binary heap keeps the highest-priority element at the root, so `poll()` returns it.
* **How arrival order is preserved among equal severities:** `PriorityQueue` is *not* stable, so equal-severity patients could come out in any order. A monotone **arrival counter** (`counter++` in `admit`) is stored in each `Patient` and used as the tie-breaker (`thenComparingLong(p -> p.arrival)`): the comparison of two equal-severity patients is decided by the counter, which makes the order total and FIFO.
* **Removing the next patient / displaying the list:** `poll()` removes the head (O(log n)). The waiting list is displayed by **sorting a copy** (`queue.stream().sorted(rule)`), never by iterating the queue, since a heap only guarantees the root.

## 2. Complete-the-code run (`Q6/Triage.java`)

TODO 1: `new PriorityQueue<>(Comparator.comparingInt((Patient p) -> p.severity).thenComparingLong(p -> p.arrival))`

```
Observed: Meena(S1) Kumar(S1) Lata(S2) Ravi(S3) John(S3) Asha(S5)
Expected: Meena(S1) Kumar(S1) Lata(S2) Ravi(S3) John(S3) Asha(S5)      -> identical
```

**Counter explained:** Ravi (counter 0) and John (counter 2) both have S3; Ravi's smaller arrival number makes him compare "smaller", so he is served first; Meena (1) before Kumar (4) for the same reason.

## 3. Correctness check (8 patients, worked out by hand — `Q6/TriageStudy.java`)

Arrival order: Ravi S3, Meena S1, John S3, Asha S5, Kumar S1, Lata S2, Divya S2, Imran S4.
Hand-derived treatment order: **Meena, Kumar** (S1, arrival 1 then 4) → **Lata, Divya** (S2, 5 then 6) → **Ravi, John** (S3, 0 then 2) → **Imran** (S4) → **Asha** (S5).
Program output (`poll()` order): `Meena(S1) Kumar(S1) Lata(S2) Divya(S2) Ravi(S3) John(S3) Imran(S4) Asha(S5)` — **matches**.

**Why printing the PriorityQueue does not show treatment order:** `toString()` iterates the underlying array of the binary heap, which is only *partially* ordered (each parent ≤ its children). Observed: `[Meena(S1), Kumar(S1), Lata(S2), Imran(S4), Ravi(S3), John(S3), Divya(S2), Asha(S5)]` — Imran (S4) is printed before Ravi (S3) and Divya (S2) is near the end.

## 4. Growth investigation

**Prediction (before experiments):** `add` = sift-up: O(log n) worst case but O(1) on average (about 2 comparisons for random data); `poll` = remove root, move last element to the root and sift down: O(log n) comparisons (~2·log₂ n). Cost per operation should therefore grow only slowly with n.

Queue sizes 1,000 / 10,000 / 100,000 / 1,000,000, severities random, comparisons counted by an instrumented comparator, mean of 5 runs after 2 warm-ups:

| n | add (ns/op) | poll (ns/op) | comparisons / add | comparisons / poll | 2·log₂ n |
|---|---|---|---|---|---|
| 1,000 | 63.9 | 152.6 | 1.82 | 15.02 | 19.9 |
| 10,000 | 46.1 | 124.5 | 1.84 | 21.64 | 26.6 |
| 100,000 | 39.2 | 206.1 | 1.82 | 28.30 | 33.2 |
| 1,000,000 | 39.9 | 437.2 | 1.81 | 34.91 | 39.9 |


| n waiting | ns per (admit + serve) |
|---|---|
| 1,000 | 146.9 |
| 10,000 | 184.6 |
| 100,000 | 263.0 |
| 1,000,000 | 466.8 |


**Prediction vs observation:** `add` needs a constant ≈ 1.8 comparisons at every size (average-case O(1)); `poll` comparisons grow with log n (15 → 22 → 28 → 35, i.e. about 1.5–1.75·log₂ n; below the 2·log₂ n worst case) — **supported**. Times per operation increase with n more than comparison counts do (poll 272 ns at 1,000 → 461 ns at 1,000,000), because large heaps exceed the CPU cache; this is a hardware effect, not extra algorithmic work.

## 5. Inquiry: starvation and a fair rule

A severity-5 patient is served only when no patient with a smaller severity number is waiting. If severe cases keep arriving faster than they are served, there is always a more severe patient ahead, so the S5 patient **starves**.

*Experiment:* one S5 patient at t = 0; each tick 2 new patients (S1–S4) arrive but only 1 patient is served. **Plain PriorityQueue: the S5 patient was still waiting after 5,000 ticks (backlog 5,001).**

**Fair rule – aging:** the effective severity falls by one level for every *T* ticks waited (`max(1, severity − waited/T)`), ties broken by arrival. Observed: the S5 patient was served at **tick 200, 400 and 800 for T = 50, 100, 200** — exactly at 4·T, the time needed to reach level 1. The wait of any patient is therefore bounded by about (severity − 1)·T plus the queue of equally-aged patients. (Cost of keeping the priority queue correct while keys change with time: see Q7.)

## 6. Alternative concept and complexity

* **Sorted `LinkedList`** (insert each patient at its correct position): admit = O(n) (walk to the position), serve = O(1) (`removeFirst`). Space: one 24-byte node per patient.
* **Array of five FIFO queues** (one `ArrayDeque` per severity): admit = **O(1)** (append to the queue of its severity); serve = **O(1)** (check at most five queues: the first non-empty one, `pollFirst`); arrival order inside a severity is the natural FIFO order, so no counter is needed. Space: O(n) plus five arrays. Limitation: needs a small fixed number of levels.
* **PriorityQueue**: admit O(log n), serve O(log n); space one array slot per patient.

| n | PriorityQueue (ms) | sorted LinkedList (ms) | 5 FIFO queues (ms) |
|---|---|---|---|
| 5,000 | 1.84 | 31.59 | 0.35 |
| 10,000 | 1.86 | 127.56 | 0.20 |
| 20,000 | 3.91 | 774.59 | 0.39 |
| 40,000 | 8.79 | 9,325.76 | 0.60 |


All three produced **exactly the same service order** at every size. Sorted LinkedList admission becomes quadratic overall (3.9 s at 40,000 patients versus 8.8 ms for the PriorityQueue); the five-queue design is the fastest and simplest *if* severity has few discrete levels.

## 7. Deployment decision: 5,000 patients a day

5,000 patients a day is about one arrival every 17 seconds. The PriorityQueue processed a whole simulated day (backlog of 500, 5,000 arrivals, all served) in **0.65 ms** — about 130 ns per patient — and a queue of **one million** waiting patients still costs about 0.5 µs per admit + serve. **Yes, the design comfortably serves a city hospital.** The real constraints are not the data structure: the `PriorityQueue` is *not thread-safe* (it needs a lock or `PriorityBlockingQueue` for several nurses/terminals), it is in memory (needs persistence for crash recovery) and the starvation rule of §5 is needed for fairness.

# Q7 – Day-4 design challenge: diagnose, redesign and validate

**Chosen solution:** the Q6 triage queue **with the aging fair rule** (`Q7/AgingTriage.java`). Reason: building the fair rule in Q6 showed that the PriorityQueue does not remain valid when the priority itself changes with time.

## A. Diagnose the bottleneck (2 marks)

*Original implementation* (class `Original`): a patient's effective level is `sev − min(sev−1, waited/T)`. Because that key depends on the current tick, the heap invariant of a `PriorityQueue` is broken after every tick; the only correct way to keep using it is to **rebuild the PriorityQueue from all waiting patients at every tick** (and `waiting.remove(best)` is a further linear scan).

Evidence from the original (n patients waiting, 500 ticks, T = 20):

| n waiting | original ms (500 ticks) | original ms/tick | original comparisons | original alloc (MB) | redesign ms | redesign ms/tick | redesign ops | redesign alloc (MB) | speed-up | same order |
|---|---|---|---|---|---|---|---|---|---|---|
| 1,000 | 15.4 | 0.031 | 861,743 | 2.0 | 0.95 | 0.0019 | 8,052 | 0.06 | 16× | true |
| 5,000 | 69.3 | 0.139 | 4,891,670 | 9.6 | 1.32 | 0.0026 | 23,980 | 0.15 | 52× | true |
| 20,000 | 279.7 | 0.559 | 18,868,683 | 38.2 | 2.82 | 0.0056 | 83,844 | 0.51 | 99× | true |
| 50,000 | 723.0 | 1.446 | 46,257,782 | 95.4 | 5.20 | 0.0104 | 204,118 | 1.51 | 139× | true |
| 100,000 | 1,474.8 | 2.950 | 91,710,917 | 190.8 | 9.93 | 0.0199 | 404,124 | 2.82 | 148× | true |


* **Where most time and memory is spent:** in rebuilding the queue every tick: at n = 100,000 about **91.7 million comparator calls** for 500 ticks (≈ 183,000 per tick ≈ 1.8 per waiting patient) and **190 MB** of allocation (a new heap array of n references per tick).
* **Which operation grows significantly with data size:** the per-tick cost is **linear in n**: 0.031 ms/tick at n = 1,000 → 2.95 ms/tick at n = 100,000 (×95 for ×100 data); comparisons and allocation grow ×106 and ×95 likewise. The Q6 measurement of a normal poll was ≈ 0.3–0.5 µs; the aging rebuild made one tick several thousand times more expensive.
* **Work that is unnecessarily repeated:** almost nothing changes from one tick to the next — only the few patients whose waiting time just crossed a multiple of T change level — yet **every patient is re-evaluated, re-sorted into a new heap and re-allocated each tick** (repeated re-sorting and re-creation, a linear scan for the removal).
* **Characteristic of the chosen class that causes the limitation:** a `PriorityQueue` requires its keys to be fixed while an element is inside; it offers no way to re-prioritise an element in place (only remove O(n) + add), and its ordering information is only a partial order (heap) — nothing tells us *which* elements changed.

## B. Design objective (2 marks)

The redesign must:

1. make the **per-tick cost independent of the number of waiting patients** (O(1) amortised per patient-level change, instead of O(n) per tick);
2. **avoid re-scanning/re-allocating** the whole collection each tick (no per-tick object creation);
3. keep data in the required order **without re-sorting**;
4. **preserve exact correctness**: serve the lowest effective level first, ties by time in the level then arrival id — *identical service order to the original* on the same inputs;
5. measurable targets: **≥ 50× faster per tick at n = 50,000**, and allocation reduced by ≥ 90%.

## C. Redesign (4 marks)

**What changed:** the single PriorityQueue is replaced by **five FIFO `ArrayDeque`s — one per effective level** — with two fields added to each patient: `level` and `entered` (tick at which it entered its current level).

**Property exploited:** every patient needs *exactly the same time T* to climb one level. Each deque is filled in time order, so it is automatically ordered by *`entered`*; therefore **only the head of a deque can be the next patient due for promotion**. The queue never needs sorting.

```
class Redesign:  Deque<P>[] q[1..5]             // q[k] = patients currently at level k, ordered by 'entered'
  admit(p):      q[p.level].addLast(p)                                   // O(1)
  promote(now):  for level = 5 down to 2:                                // O(1 + number promoted)
                     while q[level] not empty and head.entered + T <= now: due.add(q[level].pollFirst())
                 sort 'due' by arrival id (tie rule; usually 0–1 elements)
                 for p in due: p.level--; p.entered = now; q[p.level].addLast(p)
  serve():       return first non-empty q[1..5].pollFirst()               // O(1): at most 5 checks
tick(now):       promote(now); admit(newcomer); serve()
```

* **Operations avoided or reduced:** the per-tick full rebuild, all comparator calls, the linear `remove`. Each patient is moved at most (severity − 1) ≤ 4 times in its whole life, so total promotion work is O(n) overall, not O(n) *per tick*.
* **Additional memory / preprocessing:** two ints per patient (`level`, `entered`) and five deques (O(n) total, array slack); no preprocessing.
* **New trade-offs:** (i) it relies on a *small, fixed* number of levels (five); a continuous priority could not be handled this way; (ii) correctness depends on the invariant "each deque is ordered by `entered`", which holds only because time is monotone; (iii) the tie-rule had to be stated precisely — *time of entering the current level, then arrival id* — and the original's comparator was written to implement the same rule so that the two designs can be compared exactly; (iv) a more complex class than one `PriorityQueue`.

## D. Prediction before implementing (1 mark)

* The original grows **linearly in n per tick** (as measured); the redesign's work is bounded by the **number of patients crossing a threshold**, not by n: over 500 ticks with T = 20 every preloaded patient is promoted (severity − 1) times, so total promotions ≈ Σ(sev − 1) ≈ 2·n, i.e. **O(n) in total across all 500 ticks**, not per tick; head checks are ≈ 4 per tick plus one per promotion.
* Predicted improvement measures: **operation count, wall-clock time and allocation**. Operation count: the original needs ≈ 1.8·n·500 comparisons, the redesign ≈ 2·n promotions (+ one head check each) + ≈ 2,000 other checks, so the operation ratio should be in the hundreds and *grow with n*. Wall-clock speed-up should also grow with n but be smaller than the operation ratio (a promotion costs more than one comparison, and loading the n patients is common to both). Allocation should fall by more than 90% because nothing is rebuilt per tick.
* *Note:* the redesign's design and these qualitative predictions follow from the Q6 evidence; the exact figures quoted in section F are measurements.

## E. Implementation and validation (2 marks)

Same test conditions for both: n patients preloaded at tick 0, then 500 ticks of *promote → admit one random newcomer → serve one*, T = 20, identical seeds; time = mean of 3 runs after one warm-up; allocation by `ThreadMXBean`. The program also compares the two service sequences. **At all five sizes the served-patient sequences were identical (`same order = true`)**, and an additional 8-patient check (T = 2) gave the same order for both: `3 6 1 7 4 0 2 5`.

| n waiting | original ms (500 ticks) | original ms/tick | original comparisons | original alloc (MB) | redesign ms | redesign ms/tick | redesign ops | redesign alloc (MB) | speed-up | same order |
|---|---|---|---|---|---|---|---|---|---|---|
| 1,000 | 15.4 | 0.031 | 861,743 | 2.0 | 0.95 | 0.0019 | 8,052 | 0.06 | 16× | true |
| 5,000 | 69.3 | 0.139 | 4,891,670 | 9.6 | 1.32 | 0.0026 | 23,980 | 0.15 | 52× | true |
| 20,000 | 279.7 | 0.559 | 18,868,683 | 38.2 | 2.82 | 0.0056 | 83,844 | 0.51 | 99× | true |
| 50,000 | 723.0 | 1.446 | 46,257,782 | 95.4 | 5.20 | 0.0104 | 204,118 | 1.51 | 139× | true |
| 100,000 | 1,474.8 | 2.950 | 91,710,917 | 190.8 | 9.93 | 0.0199 | 404,124 | 2.82 | 148× | true |


![Q7 – time per tick against patients waiting (log-log axes).](charts/q7.png){width=75%}

## F. Defend the final design (2 marks)

* **Prediction supported:** yes. The speed-up grows with n — 16× (n = 1,000), 52×, 99×, 139× and **148× at n = 100,000** — and the objective of 50× at n = 50,000 is met (139×). Allocation fell from 190.8 MB to 2.8 MB at n = 100,000 (**98.5% less**). Comparator calls fell from 91.7 million to 404,000 operations (head checks + promotions), a 227× reduction; my estimate above (≈ 450×) was too high because I did not count the one head check that accompanies each promotion — time (148×) improved less than the operation count, as predicted.
* **Magnitude:** 1,475 ms → 9.9 ms for 500 ticks at n = 100,000; per tick 2.95 ms → 0.020 ms. Most of the redesign's remaining 9.9 ms is the *one-time* work of loading the 100,000 patients and the burst of ≈ 200,000 initial promotions (they all cross the same thresholds at the same ticks) — i.e. it is O(n) once, not per tick.
* **New costs and trade-offs:** fixed five levels; extra fields; a stricter invariant; more code. Service order is exactly preserved (verified), so the rule is unchanged.
* **When the original is still preferable:** (i) **without aging**, priorities never change, and a plain `PriorityQueue` is simpler, general (any Comparator, any number of priority values) and costs O(log n) per operation — the right solution for plain triage (Q6); (ii) when the number of waiting patients is small (the original costs only 0.03 ms/tick at 1,000 patients; the 16× gain there is not worth extra complexity); (iii) a *cheaper* variant of the original — rebuilding only every *R* ticks — would cut its cost by R× but would let the order be stale by up to R ticks, so it changes the rule rather than preserving it.
* **Conclusion:** the redesign meets its stated objective by exploiting the structure of the problem (uniform aging time, FIFO order of entry) rather than by swapping one collection for another, and it is the better design whenever priorities change with time over a small number of levels.

# Reproduction

```
cd Qn
javac *.java
java <MainClass>          # TicketBench, PlaylistBench, Attendance / ContractTests,
                          # PharmacyBench, LeaderboardDemo / RankingTests,
                          # Triage / TriageStudy, AgingTriage
```
