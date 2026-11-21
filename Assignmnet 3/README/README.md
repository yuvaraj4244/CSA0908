# CSA0908 – Programming in Java · Assignment 3 (CO3)

| | |
|---|---|
| Name | C. Yuvaraju |
| Register number | 192424354 |
| Course code | CSA0908 (CSA09 – Programming in Java) |
| GitHub | yuvaraj4244 |
| Java version | OpenJDK 21.0.12 (`javac 21.0.12`) |

Repository name required by the assignment: `CSA09-192424354-Assignment3`.
Pair work: this repository contains every task, executed in full; the full joint report (Student A and Student B sections) is in `report/`.

## Layout
One folder per task (`Q1` … `Q7`): Java source, console output (`output*.txt`), raw measurements (`raw_data*.csv`), input files, and output screenshots (`screenshots/`). The report is in `report/` (`CSA0908_Assignment3_Report.docx`, `.pdf`, `.md`) with diagrams and charts.

## How to compile and run
```
cd Qn
javac *.java
java <MainClass>
```
| Task | Folder / main class(es) | Result |
|---|---|---|
| Q1 ATM | `Q1`: `AtmTests`, `AtmUncaught`, `ErrorCostBench` (+ `Q1/bad_order` for the broad-before-narrow compile error) | withdrawal with try / multi-catch / finally; every test input reaches the intended catch block and `finally` always runs; error-code vs exception cost measured |
| Q2 Exam portal | `Q2`: `StorageDemo` (**TODO 1 completed**); `Q2/nocause` (cause omitted), `Q2/unchecked`, `Q2/portal`: `PortalTests`, `ExceptionMemoryBench` | `Saved answer01.pdf` / `Error: Could not store answer02.pdf` / `Cause: Disk quota exceeded` (matches expected); without the cause the Cause line throws `NullPointerException` |
| Q3 Sales report | `Q3`: `ReportMain`, `DebugMain`, `StressMain`, `BenchMain` | manual `finally` version and try-with-resources produce the same report; open-file counts, jdb session, stress test, benchmark |
| Q4 Threads | `Q4`: `StationsMain`, `PriorityAndDaemon`, `PoolBench` | Thread vs Runnable, life-cycle states, `run()` vs `start()`, 15-run finishing orders, priorities, daemon thread, fixed pool |
| Q5 Seat booking | `Q5`: `SeatBooking` (**TODO 1 completed**, `Seats sold: 10`), `BookingLab`; `Q5/before` = unsynchronised | race reproduced in the lab; synchronised method/block never double-book; strategy timings; 20-screen decision |
| Q6 Deadlock / producer–consumer | `Q6`: `TokenCounter` (**TODO 1+2 completed**, `Sum of tokens collected: 210 (expected 210)`), `CounterLab`, `WalletLab`, `Q6Bench`; `Q6/if_variant` = `while`→`if` | `if` variant gave a wrong sum (205) in one of 30 runs; wallet deadlock reproduced and prevented by lock ordering |
| Q7 Fault-tolerant processor | `Q7`: `GenerateData`, `Q7Main`, `AltBench` | ExecutorService + Callable/Future; hand-checked set passes; 10,000-file timings for 1/2/4/8 threads; parallel-stream and thread-per-file alternatives |

Q7 data: `java GenerateData data 10000` (git-ignored), then `java Q7Main bench data`, `java Q7Main handset`.

## Notes
* Timings are means of repeated runs on a 2-core container; concurrency results are non-deterministic, so they are reported over repeated runs.
* One commit per task, then a final commit for data, diagrams, screenshots and the report.
