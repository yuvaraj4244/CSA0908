---
title: "CSA0908 – Programming in Java: Assignment 3 (CO3) – Joint Report"
---

| | |
|---|---|
| **Course** | CSA0908 – Programming in Java (CSA09) |
| **Student A** | C. Yuvaraju – Register No. 192424354 – GitHub: yuvaraj4244 |
| **Student B** | ______________________ – Register No. ______________ – GitHub: ______________ |
| **GitHub repository (Student A)** | https://github.com/yuvaraj4244/CSA09-192424354-Assignment3 |
| **GitHub repository (Student B)** | ______________________________ |
| **Java version** | OpenJDK 21.0.12 (`javac 21.0.12`) |
| **Machine** | 2 vCPU Linux container; JVM default flags (`-Xmx` 1–3 GB where stated) |

**Structure of this report.** For every question the report gives the shared group work, the **Student A** section, the **Student B** section, the joint answer, the *Complete the Code* run (where the question has one) and the *Alternative Concept and Complexity Analysis*. Source code, console outputs, screenshots and raw measurements are in the repository folders `Q1` … `Q7` (`output*.txt`, `screenshots/`, `raw_data*.csv`).

**Measurement notes.** Timings use `System.nanoTime()`; memory uses `ThreadMXBean.getThreadAllocatedBytes` or `/proc/self/status`. They are single-JVM micro-benchmarks on a 2-core container: absolute numbers will differ on another machine, the *growth patterns* are the evidence. Concurrency results (race counts, finishing orders) are non-deterministic by nature, so they are reported over repeated runs.

# Q1 – Handling Failure in a Bank ATM Simulation (6 marks, K4)

## Shared group work

**Failure points of a withdrawal, and their classification** (program: `Q1/Atm.java`)

| # | Failure point | Exception | Error / checked / unchecked | Where it is caught |
|---|---|---|---|---|
| 1 | user types letters for the account number | `InputMismatchException` | unchecked | catch 4 (multi-catch `InputMismatch \| NumberFormat`) |
| 2 | user types letters for the amount | `NumberFormatException` | unchecked | catch 4 (same multi-catch) |
| 3 | no input at all (end of input) | `NoSuchElementException` (parent of #1) | unchecked | catch 5 |
| 4 | negative amount / not a multiple of 100 | `InvalidAmountException` (user-defined, extends `IllegalArgumentException`) | unchecked | catch 3 |
| 5 | amount greater than the balance | `InsufficientFundsException` (user-defined) | **checked** | catch 1 |
| 6 | account number not on the card file | `AccountNotFoundException` (user-defined) | **checked** | catch 2 (multi-catch `AccountNotFound \| CardData`) |
| 7 | card record corrupt (e.g. `1001,abc`) | `CardDataException` (user-defined, wraps the `NumberFormatException`) | **checked** | catch 2 |
| 8 | card-reader file is missing | `FileNotFoundException` | **checked** (`IOException` subclass) | catch 6 |
| 9 | any other read failure | `IOException` | **checked** | catch 7 |
| 10 | bug: null file name passed to the method | `NullPointerException` | unchecked | **deliberately not caught** (propagates) |
| 11 | JVM problems (`OutOfMemoryError`, `StackOverflowError`) | `Error` | Error | not caught – the program cannot recover |

The withdrawal is implemented with `try`, seven `catch` clauses (two of them multi-catch) and a `finally` that ejects the card and closes the reader.

## Student A – Exception design

**Throwable hierarchy relevant to the ATM**

![Q1 – part of the Throwable hierarchy used by the ATM (user-defined classes are the grey-bordered InsufficientFunds/AccountNotFound/CardData [checked] and InvalidAmount [unchecked]).](diagrams/q1_hierarchy.png){width=95%}

**Table: failure point → exception type → checked/unchecked → where caught** – see the table above (columns 2–5).

**Justification of the order of the catch blocks.**

1. **Specific before general, because the compiler enforces it.** A `catch` for a subclass placed after its superclass is *unreachable*; `javac` rejects it (evidence in Student B's section). Two pairs in this program are related: `InputMismatchException` is a subclass of `NoSuchElementException`, and `FileNotFoundException` is a subclass of `IOException`. So `InputMismatchException` (in the multi-catch, catch 4) comes **before** `NoSuchElementException` (catch 5), and `FileNotFoundException` (catch 6) comes **before** `IOException` (catch 7).
2. **Unrelated types may be in any order.** `InsufficientFundsException`, `AccountNotFoundException|CardDataException`, `InvalidAmountException` are unrelated siblings; I ordered them by importance (business decline first).
3. **Multi-catch only for unrelated types with the *same* handling.** `catch (AccountNotFoundException | CardDataException e)` and `catch (InputMismatchException | NumberFormatException e)`: both parts of each pair need the same recovery. A multi-catch containing two related types (e.g. `FileNotFoundException | IOException`) does not compile.
4. **Different recovery ⇒ different catch.** Each catch shows a different message/action (decline, retype, call the bank), which a single broad catch cannot do.

**What `finally` guarantees in this program.** The `finally` block runs *after* the try block completes normally, *after any catch block*, and even when an exception is **not caught by any catch** (T11 below) or when the method returns from inside the `try`. In the ATM it guarantees that (a) the card-reader `BufferedReader` is closed (no leaked file handle), (b) the card is ejected (message printed) and (c) the `finallyRuns` counter is incremented – measured: **finally ran in 11 of 11 tests**. It does *not* run if the JVM is terminated (`System.exit`, power failure) or the thread is killed/never returns.

## Student B – Testing and tracing

**Test inputs and observed path** (`Q1/AtmTests.java`, output in `Q1/output_tests.txt`)

| Test | Input | Intended path | Catch block that handled it (observed) | `finally` executed? |
|---|---|---|---|---|
| T1 valid withdrawal | `1001 500` | success | none (success) – dispensed 500 | YES |
| T2 letters in account | `abcd 500` | `InputMismatchException` | catch 4 `InputMismatch \| NumberFormat` | YES |
| T3 letters in amount | `1001 five` | `NumberFormatException` | catch 4 `InputMismatch \| NumberFormat` | YES |
| T4 negative amount | `1001 -200` | `InvalidAmountException` | catch 3 | YES |
| T5 more than balance | `1002 500` | `InsufficientFundsException` | catch 1 | YES |
| T6 card-reader file missing | `1001 500`, file absent | `FileNotFoundException` | catch 6 | YES |
| T7 unknown account | `9999 500` | `AccountNotFoundException` | catch 2 `AccountNotFound \| CardData` | YES |
| T8 corrupt card file | `1001 500`, `1001,abc` | `CardDataException` | catch 2 `AccountNotFound \| CardData` | YES |
| T9 no input | (empty) | `NoSuchElementException` | catch 5 | YES |
| T10 amount 150 (not ×100) | `1001 150` | `InvalidAmountException` | catch 3 | YES |
| T11 bug: null file name | `1001 500`, `null` | `NullPointerException` | **none – propagated to the caller** | YES |

Eleven inputs, ten different catch/paths (T2/T3 share the multi-catch, T7/T8 share one; T4/T10 share one), every one with `finally` executed.

**Stack trace of an uncaught exception** (`Q1/AtmUncaught.java`, `Q1/output_uncaught.txt`) and an explanation of every line:

```
   [finally] card ejected, reader closed                      <- finally ran BEFORE the exception left withdraw()
Exception in thread "main" java.lang.NullPointerException     <- type of exception; no message because NPE here has none
	at java.base/java.io.FileInputStream.<init>(FileInputStream.java:144)  <- where it was THROWN (top frame): the FileInputStream
                                                                             constructor dereferences the null file name
	at java.base/java.io.FileInputStream.<init>(FileInputStream.java:106)  <- the one-argument constructor delegating to it
	at java.base/java.io.FileReader.<init>(FileReader.java:60)             <- FileReader(String) creates the FileInputStream
	at Atm.withdraw(Atm.java:46)                                           <- MY code: new FileReader(cardFile) with cardFile == null
	at AtmUncaught.main(AtmUncaught.java:7)                                <- the caller; nobody caught it, so the thread "main" ends
```
Reading from the top: the first line is the point of failure, each line below is the caller of the line above, the last is where the call chain began.

**Broader catch before a narrower one.** `Q1/bad_order/BadOrder.java` (output `Q1/output_bad_order.txt`) does not compile:

```
BadOrder.java:12: error: exception FileNotFoundException has already been caught
        } catch (FileNotFoundException e) {      // narrow (subclass) afterwards  -> unreachable
BadOrder.java:16: error: exception InputMismatchException has already been caught
        } catch (InputMismatchException e) {     // child afterwards -> unreachable
2 errors
```
Java turns the mistake into a compile-time error, because the second clause could never execute.

## Joint design question – *Should the ATM catch every exception with a single `catch (Exception e)`?*

**Normally no.** What is lost:

* **Different recovery actions are merged.** "Insufficient funds" (tell the customer), "card-reader missing" (call the bank, take the ATM out of service) and "typed letters" (ask again) all become one message.
* **Programming bugs are hidden.** `catch (Exception e)` also catches `NullPointerException`, `IndexOutOfBounds…` etc.; the bug (T11) would be reported to the customer as an ordinary failure and never reach the developer.
* **Diagnostic information is lost** unless the handler logs the exception; and it swallows `InterruptedException` and `RuntimeException`s that should propagate.
* The compiler can no longer tell the reader which checked exceptions are expected.

**It is acceptable** only at a *boundary* where the program must not die and where the exception is logged with its stack trace and then reported in a generic way – e.g. the top-level loop of the ATM which must always return the card, or a thread's `run()` method – and *in addition to* specific catches deeper down, never instead of them.

## Alternative Concept and Complexity Analysis – no exceptions, error codes

**Solution** (`Q1/ErrorCostBench.java`): validate inputs in advance with `Scanner.hasNextInt()` (and `hasNext()`), and return codes: `0` success, `-1` not a number / no input, `-2` bad amount, `-3` card reader missing, `-4` unknown account, `-5` insufficient funds.

```
int withdraw(Scanner in, String file) {
    if (!in.hasNext())     return -1;       // check 1
    if (!in.hasNextLong()) return -1;       // check 2
    long acct = in.nextLong();
    if (!in.hasNextInt())  return -2;       // check 3
    int amt = in.nextInt();
    if (amt <= 0 || amt % 100 != 0) return -2;   // check 4
    Map<Long,Long> bal = loadOrNull(file);  if (bal == null) return -3;   // check 5 (file/corrupt)
    Long b = bal.get(acct);  if (b == null) return -4;                     // check 6
    if (amt > b) return -5;                                                // check 7
    return 0;
}
```

**Readability and number of checks.** Seven explicit `if (…) return code` checks are needed in this one method, and **every caller** must test the returned code (nothing forces it to; an ignored `-3` looks like any other value). The error "travels" only one level: each intermediate method must forward the code. With exceptions the happy path is a straight line (no checks), the 7 failure kinds are handled once, in the `catch` clauses, and the compiler forces callers to handle the checked ones. The error code carries no message, no cause and no stack trace.

**Time and space of creating/throwing versus returning a code** (measured, 200,000 failed calls after warm-up, failure raised at call depth *d*):

| depth d | new exception (ns / bytes) | exception without stack trace (ns / bytes) | return error code (ns / bytes) | exception ÷ code |
|---|---|---|---|---|
| 1 | 474 / 728 | 100 / 40 | 41 / 0 | 12× |
| 10 | 1,297 / 728 | 486 / 40 | 14 / 0 | 91× |
| 50 | 3,558 / 1,416 | 1,743 / 40 | 47 / 0 | 76× |
| 200 | 13,542 / 4,856 | 6,926 / 40 | 960 / 0 | 14× |

![Q1 – cost of one failure against call depth (log-log axes).](charts/q1.png){width=70%}

* **Creating an exception costs O(d) time and O(d) space** (d = stack depth): the `Throwable` constructor calls `fillInStackTrace()`, which walks all d frames and stores them (728 B → 4,856 B from depth 10 to 200). Unwinding to the `catch` is also O(d). The exception object stays alive until it is garbage collected.
* **An error code is O(1)** to create (no allocation) and O(d) only if it must be passed up d frames by explicit `return`s (each a cheap compare). The depth-200 code figure (960 ns) is a noisy outlier; the other three rows show 14–47 ns.
* **No-stack-trace exceptions** (4-argument constructor) remove most of the cost (40 B, about 4× faster) but also remove the trace.
* **The success path is free:** 100 million iterations took 49.9 ms with a `try/catch` and 44.7 ms without (≈ 0.5 ns per iteration, within JIT noise). Exceptions are therefore appropriate for *exceptional* failures; they should not be used for normal control flow in a hot loop (a failed login after 200 frames costs ≈ 14 µs – irrelevant for an ATM, significant for 10⁷ calls per second).

**Conclusion:** for an ATM (a few failures per minute) the exception design is clearer and safer; the extra microseconds are irrelevant. Error codes are only preferable in hot paths where failure is common and very cheap checks suffice.

# Q2 – User-Defined Exceptions and Propagation in an Online Examination Portal (7 marks, K4)

## Shared group work

* **Hierarchy:** `ExamException` (checked, given base class) with subclasses `ExamClosedException`, `NotRegisteredException`, `AnswerTooLargeException` and `SubmissionStorageException`; plus the unchecked `InvalidRequestException` (extends `IllegalArgumentException`).
* **Three layers:** `ExamController` → `ExamService` (rules: window, registration, size) → `Storage` (writes the file). `throws` is declared on `Storage.save()` and `ExamService.submit()`; the controller catches `ExamException`.
* **Chaining:** `Storage.save()` wraps the `IOException` in `SubmissionStorageException("Could not store …", cause)` so the original cause is not lost.
* Code: `Q2/portal/Portal.java`, tests in `Q2/portal/PortalTests.java`, output `Q2/output_portal.txt`.

## Student A – Exception hierarchy and propagation design

**Class diagram**

![Q2 – exception hierarchy of the portal.](diagrams/q2_hierarchy.png){width=70%}

**Reason for each choice (checked / unchecked).**

| Exception | Kind | Reason |
|---|---|---|
| `ExamException` (base) | checked | the portal's *expected, recoverable* business failures; the compiler forces every layer to deal with them |
| `ExamClosedException` | checked | the student did nothing wrong in code – the situation (window closed) must be reported; the caller can recover by showing a message |
| `NotRegisteredException` | checked | same: expected rule violation, the user can act (contact the exam cell) |
| `AnswerTooLargeException` | checked | the student can fix it (compress the file) – a recoverable condition |
| `SubmissionStorageException` | checked | infrastructure failure the caller should consciously handle (retry, tell the student, alert operators); wraps the low-level `IOException` as its **cause** |
| `InvalidRequestException` | **unchecked** | a *programming/caller error* (null or blank student id): not something a user can recover from at runtime, so forcing `throws` on every method would only add noise; it should surface as a bug |

**Propagation path of one exception – the storage failure** (the sequence diagram follows the real stack trace in Student B's section):

![Q2 – propagation of a storage failure from the storage layer to the controller.](diagrams/q2_path.png){width=80%}

**Where it is finally handled and what the student sees.** It is handled in **`ExamController.submit()`**, the top layer, in `catch (ExamException e)`. The student sees only: *"We could not save your answer. Please try again in a minute."* – no file names, no `NoSuchFileException`, no stack trace. The full chained stack trace is written to the operators' log. The intermediate layer `ExamService` does not catch it (it just declares `throws ExamException`), because it cannot do anything useful about it.

## Student B – Failure scenarios and chaining evidence

**One scenario per exception, with observed behaviour** (`Q2/output_portal.txt`)

| Scenario | Exception thrown | Message shown to the student | Logged? |
|---|---|---|---|
| 1 valid submission | (none) | Submission accepted: a1.pdf | no |
| 2 window closed | `ExamClosedException` | Sorry, the exam window is closed. | no |
| 3 not registered | `NotRegisteredException` | You are not registered for CSA09. Contact the exam cell. | no |
| 4 file too large | `AnswerTooLargeException` | Your file is too large (limit 500 KB). Please compress it and retry. | no |
| 5 storage failure | `SubmissionStorageException` | We could not save your answer. Please try again in a minute. | **yes** |
| 6 blank student id | `InvalidRequestException` (unchecked) | Invalid request. | **yes** |

**Exception chaining with `getCause()` and the full chained stack trace** (storage failure):

```
level 1: SubmissionStorageException: Could not store missing_dir/a5.pdf
  level 2: java.nio.file.NoSuchFileException: /tmp/exam-storage…/missing_dir/a5.pdf

SubmissionStorageException: Could not store missing_dir/a5.pdf
	at Storage.save(Portal.java:41)
	at ExamService.submit(Portal.java:61)
	at PortalTests.main(PortalTests.java:29)
Caused by: java.nio.file.NoSuchFileException: /tmp/exam-storage…/missing_dir/a5.pdf
	at java.base/sun.nio.fs.UnixException.translateToIOException(UnixException.java:92)
	…
	at java.base/java.nio.file.Files.writeString(Files.java:3727)
	at Storage.save(Portal.java:37)
	... 2 more
```
The `Caused by:` block shows the *original* failure with its own stack trace down to the exact line in `Storage.save` and the operating-system reason.

**What is lost when the exception is re-thrown without its cause:**

```
getCause() = null
trace contains "Caused by"? false   (trace length 177 chars)
SubmissionStorageException: Could not store missing_dir/a5.pdf
	at Storage.save(Portal.java:40)
	at ExamService.submit(Portal.java:61)
	at PortalTests.main(PortalTests.java:40)
```
Lost: the fact that it was a "no such file" error, the exact path, the OS message and all frames inside `java.nio` (the trace shrinks to 177 characters). The operator cannot tell a full disk from a missing folder.

## Joint conclusion – *why throw a meaningful `SubmissionStorageException` instead of letting an `IOException` travel unchanged to the user interface?*

1. **Abstraction / layering.** The controller should not know that storage uses files (`java.io`); tomorrow it may be a database or cloud store. A domain exception hides the implementation but, through the cause, keeps it for diagnosis.
2. **Meaning.** `SubmissionStorageException: Could not store a2.pdf` says *what the system was doing*; a bare `IOException: Disk quota exceeded` does not.
3. **One catch for the layer:** `catch (ExamException e)` handles all portal failures; with raw `IOException` the UI would have to catch an unrelated type from the java.io package.
4. **Nothing is lost:** because of chaining, the original `IOException` and its trace stay reachable by `getCause()` for the log.

## Complete the Code (`Q2/StorageDemo.java`)

TODO 1 completed: `throw new SubmissionStorageException("Could not store " + fileName, e);`

```
Observed:  Saved answer01.pdf
           Error: Could not store answer02.pdf
           Cause: Disk quota exceeded            -> identical to the expected output
```
**What would be printed in the `Cause` line if the `IOException` were not passed to the constructor?** Nothing – the line is never printed. `getCause()` would return `null`, so `e.getCause().getMessage()` throws a `NullPointerException` and the program crashes (`Q2/nocause/`, `Q2/output_nocause.txt`):

```
Saved answer01.pdf
Error: Could not store answer02.pdf
Exception in thread "main" java.lang.NullPointerException: Cannot invoke "java.lang.Throwable.getMessage()"
  because the return value of "ExamException.getCause()" is null
	at StorageDemo.main(StorageDemo.java:35)
```

## Alternative Concept and Complexity Analysis – unchecked custom exceptions

**What changes if the custom exceptions extend `RuntimeException`** (`Q2/unchecked/`):

* **Method signatures:** the `throws SubmissionStorageException` / `throws ExamException` clauses can be dropped from `Storage.save()`, `ExamService.submit()` and every other method in the chain.
* **Calling code:** no caller is *forced* to `try/catch`; `main` in `StorageDemoUnchecked` compiles without any handler. If nobody catches, the exception terminates the thread with its full chained trace (observed, `Q2/output_unchecked.txt`: `Exception in thread "main" SubmissionStorageException … Caused by: java.io.IOException: Disk quota exceeded`).
* **Trade-off:** less boilerplate and no signature changes when a new failure is added deep in the call chain, but the compiler no longer reminds the programmer that the failure exists – a forgotten handler becomes a runtime crash.

**Time and space of capturing a stack trace for each exception, and effect of chaining** (`Q2/portal/ExceptionMemoryBench.java`, mean of 100,000 creations; the cost is identical for checked and unchecked exceptions, because both run the `Throwable` constructor):

| stack depth | chain length | with stack trace (ns / bytes) | no stack trace, 4-argument constructor (ns / bytes) |
|---|---|---|---|
| 10 | 1 | 900 / 728 | 88 / 40 |
| 10 | 3 | 2,221 / 2,280 | 93 / 216 |
| 100 | 1 | 4,195 / 2,792 | 610 / 40 |
| 100 | 3 | 9,914 / 8,472 | 473 / 216 |
| 1000 | 1 | 38,421 / 22,056 | 7,581 / 40 |
| 1000 | 3 | 113,730 / 66,264 | 7,816 / 216 |

![Q2 – memory and time of creating an exception against stack depth and chain length.](charts/q2.png){width=95%}

* **Time:** capturing a stack trace is **O(d)** (d = depth of the call stack at the `new`): ×10 depth gives roughly ×5–×10 time (900 → 4,195 → 38,421 ns). Without the trace the creation is almost constant (≈ 90 ns at depth 10).
* **Space:** each exception holds its own stack trace – **about 22–70 bytes per frame**; at depth 1000 one exception is 22 KB.
* **Chaining:** a chain of *k* exceptions each created at depth *d* holds *k* separate traces, i.e. **O(k·d)** memory (chain 3 → 3× the bytes: 728 → 2,280 B; 22,056 → 66,264 B), and the whole chain is retained as long as the outermost exception is reachable (for example in a log queue). Materialising the traces with `getStackTrace()` allocates more (depth 100: 102 elements ≈ 5.9 KB for one exception, 204 elements ≈ 11.5 KB for chain 2, 510 elements ≈ 28.7 KB for chain 5), and the printed text grows ≈ 6.3–6.5 KB (the repeated “… n more” lines are compressed).
* **Practical consequence:** the chained cause costs memory proportional to its depth but is what makes the failure diagnosable; do not create exceptions in tight loops for ordinary control flow.

# Q3 – Resource Safety, Debugging and Robust Code (7 marks, K4)

## Shared group work

*Daily Sales Report Generator* (`Q3/SalesReport.java`): reads store files `storeN.txt` (lines `product,qty,price`), writes a summary. Three versions were implemented and compared:

1. **manual (close in try body)** – `try { …parse…; r.close(); } catch …` – the `close()` is *not* in a `finally` (the typical "too many open files" bug);
2. **manual (close in finally)** – `try/catch/finally` with `close()` in the `finally` (and a nested try for the `close()` itself);
3. **try-with-resources**.

An instrumentation layer (`Tracker`, `TrackedReader`, `TrackedWriter`) counts every open and close.

**Do both versions produce the same report?** Hand-checked data: store1 = 415.00, store2 = 115.00, store4 = 150.00 → 680.00. All three versions: `opened=4 closed=4 LEFT OPEN=0` and **identical report, total 680.00 = hand check** (`Q3/output_report.txt`). On the failing input (store3 has a bad line, store5 does not exist) all three also produced identical reports.

## Student A – Debugging and experimental analysis

**Breakpoints and step-through (jdb, `Q3/output_jdb.txt`).** Breakpoints were set at the line that parses a store file (line 64), the line that closes it (line 65) and the `catch` line (line 68) of the manual version. Trace of the session:

```
Breakpoint hit: SalesReport.manualNaive(), line=64   double s = parseStore(r, f);   <- throws on a corrupt line ...
   f = "stores/store1.txt"   Tracker.current = "2"
Breakpoint hit: line=65   r.close();                                                 <- store1 OK, closed on the next line
Breakpoint hit: line=64   f = "stores/store3.txt"    Tracker.current = "2"          <- store3 (corrupt) parsing starts
Breakpoint hit: line=68   catch block: f = "stores/store3.txt"
   Tracker.closed = "1"                                                              <- the CLOSE on line 65 was SKIPPED
opened=3 closed=2 LEFT OPEN=1 peak open=2
```
**The point at which the manual version leaks a file:** after `parseStore(...)` throws on line 64, control jumps straight to the `catch` on line 68 – the `r.close()` on line 65 is never executed, so the reader of `store3.txt` stays open.

**Input that makes the manual version fail part-way:** `store3.txt` with a corrupt third line (`x` instead of a price) and a non-existent `store5.txt`.

**Files left open for the same failing input**

| Version | opened | closed | **left open** | peak open |
|---|---|---|---|---|
| manual (close in try body) | 5 | 4 | **1** | 3 |
| manual (close in finally) | 5 | 5 | 0 | 2 |
| try-with-resources | 5 | 5 | 0 | 2 |

**Stress test – every file corrupt, low file-descriptor limit (`Q3/StressMain.java`, `Q3/output_stress.txt`):** with N = 300 corrupt files the manual version opened 60 files, closed **1**, **left 59 open**, and the process then ran out of descriptors: **241 failures were reported as "Too many open files"** instead of "bad record" – this is the symptom described in the problem statement. The finally and try-with-resources versions: opened 301, closed 301, left open 0, peak open 2, **300 failures correctly reported as "bad record"**.

**Logging which file caused the failure** (`java.util.logging`-style log lines):

```
LOG FAILED file=stores/store3.txt cause=BadRecordException: stores/store3.txt: line 3: java.lang.NumberFormatException: For input string: "x"
LOG FAILED file=stores/store5.txt cause=java.io.FileNotFoundException: stores/store5.txt (No such file or directory)
```
The failure is also written into the report line for that store (`store3.txt: FAILED (bad record - line 3 …)`), and the total says *"3 of 5 stores"*, so the report is no longer silently incomplete.

## Student B – Design and correctness

**Resources opened and where each must be closed**

| Resource | Opened | Must be closed |
|---|---|---|
| a `BufferedReader` (over `FileReader` → `FileInputStream`, one OS file handle) per store file | when that store is processed | immediately after that store is parsed – on success **and** on failure |
| the summary `BufferedWriter` (`TrackedWriter`) | once, at the start of the report | at the end of the report (flushes buffered text), also when a store fails |

**Why the manual version can leak when an exception occurs.** `close()` is an ordinary statement executed *after* the code that may throw. An exception transfers control to the `catch`; statements after the throwing statement in the try block are skipped, so `close()` is skipped (line 64 → line 68 above). Only code in `finally` runs on every path.

**How try-with-resources guarantees closing, including order.** The compiler translates `try (A a = …; B b = …) { body }` into nested try/finally blocks: the resources are closed **automatically, after the body and before any `catch`**, **in reverse order of declaration**, whether the body ends normally or by exception. Evidence (`Q3/output_report.txt`, section 4a): resources A (opened first) and B (opened second) → `body runs`, `close B`, `close A`.

**Suppressed exceptions.** If the body throws an exception *and* a `close()` also throws, the **body exception is the one that propagates** and the `close()` exceptions are attached to it as *suppressed* exceptions (`getSuppressed()`); nothing is lost. Evidence (4b): `caught: exception from the body`, `suppressed: close() of B failed`, `suppressed: close() of A failed`. With manual `try/finally` the opposite happens (4c): the exception thrown by `close()` **replaces** the original body exception – `caught: close() of A failed (suppressed: 0)` – the original failure is lost.

## Joint engineering decision – *when is try-with-resources not sufficient to make the report generator robust?*

(Evidence: `Q3/output_report.txt`, section 5.)

1. **Partial output.** TWR closes the writer correctly, but if the program fails *while writing*, the summary file is left truncated: observed `plain file now contains: [DAILY SALES REPORT\nstore1.txt: 415.00 … FAILED (bad record - line 3: java.lang.NumberFormatException: For input strin]` – a resource closed correctly, data still wrong. The robust solution is to write to a temporary file and then **atomically move it**; the observed atomic version left yesterday's complete report intact.
2. **It closes, but does not decide the failure policy** – whether one bad store aborts or is skipped and reported is a separate design decision (`catch` around each store).
3. **Resources acquired outside the TWR header** – e.g. `try (BufferedReader r = new BufferedReader(new FileReader(f)))` is fine because the object that holds the handle is a resource itself, but `new Wrapper(new Stream())` where the wrapper constructor throws leaks the inner stream; each resource should be declared separately.
4. **`close()` that itself fails** (a flush error on the writer) can lose data silently if the exception is suppressed or ignored; it needs checking.
5. **Non-`AutoCloseable` resources** (locks, executors in older code) and **limits across threads** (many concurrent report generators) still need explicit handling and limits.

## Alternative Concept and Complexity Analysis

If try-with-resources were replaced by explicit `close()` in `finally`, or by no closing at all (`f` store files, `L` total lines):

| Version | Time | Max file handles open at once (space) | When a failure occurs |
|---|---|---|---|
| try-with-resources | O(L) | **2** (1 reader + the writer) | all closed; handles held = 0 afterwards |
| explicit `close()` in `finally` | O(L) (same; slightly less code safety) | **2** | all closed; but a `close()` exception can replace the real one |
| no closing at all | O(L) | **f** (grows without bound; freed only when the garbage collector runs) | every opened file is left open: up to **f** handles |
| close in try body (bug) | O(L) | 2 + number of failures | one handle leaks **per failure** |

Measured (`Q3/output_bench.txt`; f good files, mean time ms, peak open handles, left open at the end):

| f | try-with-resources | manual finally | no close at all |
|---|---|---|---|
| 100 | 23.1 ms, peak 2, left 0 | 11.4 ms, peak 2, left 0 | 8.0 ms, **peak 100, left 100** |
| 500 | 25.4 ms, peak 2, left 0 | 18.9 ms, peak 2, left 0 | 17.8 ms, **peak 500, left 500** |
| 2000 | 74.7 ms, peak 2, left 0 | 66.2 ms, peak 2, left 0 | 107.1 ms, **peak 2000, left 2000** |

![Q3 – time and peak open handles against the number of store files.](charts/q3.png){width=95%}

Time is O(L) for all versions – the differences at small *f* are JIT/warm-up noise. The difference that matters is **space (handles)**: O(1) for TWR/finally and **O(f)** for no closing; for f = 2000 the no-close version holds 2000 descriptors – beyond a typical `ulimit` it fails with "Too many open files" (as in the stress test). A "hold-all-readers" design that is perfectly closed still needs f handles at the peak (3 for 3 stores) – opening all files at once is the wrong design even when everything is closed.

# Q4 – Thread Creation and the Thread Life Cycle (7 marks, K4)

## Shared group work

Four scanner stations, each a thread that processes a batch and reports its count (`Q4/Stations.java`, `Q4/StationsMain.java`). Implemented once by **extending `Thread`** and once by **implementing `Runnable`**, using `start()`, `sleep()`, `join()` and `isAlive()`. Observed: 60 scripts scanned in total in both versions (`Total scripts scanned: 60 (expected 60)`).

```
before start(): state=NEW isAlive=false
10 ms later   : state=TIMED_WAITING isAlive=true   (sleeping inside work())
after join()  : state=TERMINATED isAlive=false
```

## Student A – Thread design

**Two ways of creating a thread and when each is preferred.**

| | `extends Thread` | `implements Runnable` |
|---|---|---|
| What you write | a *thread* that is its own task | a *task*; a `Thread` (or executor) runs it |
| Class inheritance | uses up the one superclass | class can still extend another class |
| Reuse with executors / thread pools | not possible without wrapping | **directly** (`executor.submit(runnable)`) |
| Separation of what from how | mixed | task is separate from the way it is executed |
| Preferred when | you really need to specialise thread behaviour | **almost always** (and with lambdas) |

**Thread life-cycle diagram – the states a station passes through**

![Q4 – thread life cycle. A station passes NEW → RUNNABLE → TIMED_WAITING (while sleep()) → RUNNABLE → TERMINATED; main, while waiting in join(), is WAITING.](diagrams/q4_lifecycle.png){width=85%}

Each station: **NEW** (constructed) → **RUNNABLE** (after `start()`) → **TIMED_WAITING** (during `sleep(ms)`, observed) → **RUNNABLE** → **TERMINATED** (`run()` returned; observed after `join()`). The main thread is in **WAITING** while it is blocked in `join()`.

**Why calling `run()` instead of `start()` does not create a new thread – with evidence.** `start()` asks the JVM to create a new OS thread that then calls `run()`; calling `run()` is an ordinary method call executed by the calling thread. Observed:

```
calling probe.run():     body executed by thread: main
calling probe.start():   body executed by thread: Station-X
4 stations via run():   252 ms (sequential, all in main; state of Station-1 = NEW)
4 stations via start(): 64 ms (concurrent)
```
The thread-name proves it, `Station-1` stays in state `NEW` (never started), and the four stations ran one after the other (252 ms ≈ 4 × 63 ms) instead of together (64 ms).

**How `join()` ensures the final total is printed only after all stations finish.** `t.join()` blocks the calling thread (main goes to WAITING) until `t` reaches **TERMINATED**. Main calls `join()` on all four stations before it reads the counters, so the total `60 (expected 60)` can only be computed after every station's last write; without `join()` main could print a partial total.

## Student B – Behavioural experiments

**Fifteen runs – order in which the stations finish** (`Q4/output_stations.txt`; equal batches of 15 scripts)

| Run | Finishing order | Run | Finishing order | Run | Finishing order |
|---|---|---|---|---|---|
| 1 | S3 < S1 < S2 < S4 | 6 | S1 < S4 < S3 < S2 | 11 | S1 < S4 < S3 < S2 |
| 2 | S2 < S3 < S1 < S4 | 7 | S1 < S3 < S4 < S2 | 12 | S1 < S4 < S2 < S3 |
| 3 | S1 < S4 < S2 < S3 | 8 | S1 < S2 < S3 < S4 | 13 | S1 < S2 < S3 < S4 |
| 4 | S1 < S2 < S3 < S4 | 9 | S1 < S2 < S3 < S4 | 14 | S4 < S2 < S3 < S1 |
| 5 | S1 < S3 < S2 < S4 | 10 | S1 < S4 < S3 < S2 | 15 | S1 < S2 < S4 < S3 |

**9 different finishing orders in 15 runs**; first finisher: S1 12 times, S2, S3, S4 once each. (S1 is started first so it usually wins, but nothing guarantees it.)

**Thread priorities** (`Q4/PriorityAndDaemon.java`, CPU-bound stations, 4 threads on 2 cores, 20 runs each):

| Configuration | First finisher over 20 runs | Mean finish time |
|---|---|---|
| all NORM (5) | S1=16, S2=2, S4=2 | S1 544 ms, S4 503 ms |
| S1 = MAX (10), S2, S3 = NORM, S4 = MIN (1) | S1=18, S2=1, S3=1 | S1 401 ms, S4 498 ms |

**Observation:** the finishing order barely changed (S1 first in 16 vs 18 of 20 runs; S1 already starts first), and the low-priority S4 was not delayed (503 → 498 ms). The only visible difference is S1's mean time (544 → 401 ms); with 20 runs I cannot attribute this to priority rather than run-to-run noise. Priority is only a *hint* to the scheduler and, to my knowledge, HotSpot on Linux does not map Java priorities onto OS priorities by default, so a program must not depend on it for correctness.

**Daemon thread** (`Q4/output_nodaemon.txt`, `Q4/output_daemon.txt`):

```
Station-D normal (user) thread: main() ends, isAlive=true  ->  JVM exiting: finished stations = [Station-D] (scanned=40)
Station-D DAEMON:               main() ends, isAlive=true  ->  JVM exiting: finished stations = []          (scanned=0)
```
A normal thread keeps the JVM alive until it finishes (scan completed: 40). A daemon thread does not: when `main()` ended and no user thread was left, the JVM exited and killed the daemon mid-work (scanned = 0). A daemon station must therefore never be used for work that must complete.

**Explanation using the thread scheduler.** The OS/JVM scheduler decides which RUNNABLE thread runs on which of the 2 cores and for how long (time-slicing); thread start-up cost, sleep wake-up times, the number of cores, other load and JIT compilation vary from run to run, so the order is different each time. Priorities are only hints; a daemon status changes only whether the JVM waits for the thread.

## Joint conclusion – *why can the same program produce different output orders, and why must a correct program not depend on that order?*

The output order depends on the scheduler's decisions and on timing, which the program does not control (nine different orders in 15 identical runs). A program whose *result* depends on that order is therefore correct only by luck on some runs – a race condition. A correct program obtains its result by synchronisation (`join`, locks, queues) – the *total* (60) is the same in every run whatever the order.

## Alternative Concept and Complexity Analysis – `ExecutorService` with a fixed thread pool

**Solution:**

```
ExecutorService pool = Executors.newFixedThreadPool(p);
List<Future<Integer>> parts = new ArrayList<>();
for (int s = 0; s < stations; s++) parts.add(pool.submit(() -> scan(batch[s])));   // Callable returns the count
int total = 0; for (Future<Integer> f : parts) total += f.get();                // get() replaces join()
pool.shutdown();
```
The pool creates *p* worker threads once and reuses them for all tasks; the task is separate from the thread.

**Completion time for *n* scripts with *p* threads (each script takes *c*):** the work is done in ⌈n/p⌉ batches, so **T(p) ≈ ⌈n/p⌉ · c**. Measured, n = 40 scripts, c = 20 ms (`Q4/output_pool.txt`, mean of 5 runs):

| p | predicted ⌈n/p⌉·c (ms) | measured (ms) | ratio |
|---|---|---|---|
| 1 | 800 | 809.7 | 1.01 |
| 2 | 400 | 404.7 | 1.01 |
| 4 | 200 | 202.5 | 1.01 |
| 8 | 100 | 102.3 | 1.02 |
| 16 | 60 | 62.6 | 1.04 |
| 40 | 20 | 27.6 | 1.38 |

![Q4 – predicted versus measured completion time of the fixed pool.](charts/q4.png){width=70%}

The model fits within 4% up to 16 threads (the scripts "sleep", so they do not compete for the 2 cores); with 40 threads the thread start-up overhead shows (+38%). Creating Thread objects yourself for each of n scripts needs n threads and gives T ≈ c only if the machine can run n threads.

**Memory per thread** (`Q4/PoolBench.java`; default thread stack = 1 MB reserved on 64-bit Linux): m parked threads of your own versus a fixed pool of 4 handling m tasks:

| m tasks | approach | peak threads | VmRSS increase | VmSize (virtual) increase |
|---|---|---|---|---|
| 200 | m Thread objects | 206 | 19.3 MB | 160.1 MB |
| 200 | fixed pool of 4 | 10 | 0.3 MB | 0 |
| 1,000 | m Thread objects | 1,006 | 31.9 MB | 960.0 MB |
| 1,000 | fixed pool of 4 | 10 | 0.3 MB | 0 |
| 4,000 | m Thread objects | 4,006 | 74.4 MB | 3,960 MB |
| 4,000 | fixed pool of 4 | 10 | 0.1 MB | 0 |

**Space:** memory for threads is **O(number of threads) × stack size** – about 1 MB of *reserved* virtual memory per thread (3,960 MB for 4,000 threads) – so *p* pool threads cost p MB regardless of how many tasks run, whereas one thread per task costs m MB. The pool bounds memory and amortises thread creation; its price is that tasks wait in the queue (⌈n/p⌉ batches).

# Q5 – Race Conditions in a Cinema Seat-Booking System (6 marks, K4)

## Shared group work

Implementations (`Q5/BookingLab.java`, program `Q5/SeatBooking.java`): **(1)** unsynchronised, **(2)** `synchronized` method, **(3)** `synchronized` block, plus alternatives **(4)** `AtomicBoolean[]` with `compareAndSet`, **(5)** `ReentrantLock`, **(6)** `ConcurrentHashMap.putIfAbsent`.

**The synchronised versions never double-book** (200 trials each, 100 threads released together, 10 seats): 

| Version | Trials with a double booking (of 200) | Total double bookings | Max "seats sold" (must be 10) |
|---|---|---|---|
| unsynchronized | **41** | **72** | **15** |
| synchronized method | 0 | 0 | 10 |
| synchronized block | 0 | 0 | 10 |
| `AtomicBoolean[]` CAS | 0 | 0 | 10 |
| `ReentrantLock` | 0 | 0 | 10 |
| `ConcurrentHashMap` | 0 | 0 | 10 |

## Student A – Producing and explaining the race

**A test that reliably reproduces double booking.** The program in the assignment starts a thread and immediately moves on; the first threads often finish before the next ones start, so the race window is tiny. My test (`BookingLab`, part A) creates 100 threads that all wait on a `CountDownLatch` and are **released at the same instant**, and repeats it 200 times; every user threads race for the same 10 seats.

**Double bookings over repeated runs:** unsynchronised: **41 of 200 trials (20.5%)** had at least one double booking, **72 double bookings in total**, and "seats sold" reached **15** (10 seats!). The synchronised versions: 0 of 200 each.

**Step-by-step race with two threads, T1 and T2, both booking seat 3:**

| Step | T1 | T2 | `booked[3]` | `bookingsMade` |
|---|---|---|---|---|
| 1 | `if (!booked[3])` → reads *false*, test passes | | false | 0 |
| 2 | *(preempted)* | `if (!booked[3])` → reads *false*, test passes | false | 0 |
| 3 | | `booked[3] = true` | true | 0 |
| 4 | | `bookingsMade++` | true | 1 |
| 5 | `booked[3] = true` (already true) | | true | 1 |
| 6 | `bookingsMade++` | | true | **2** |

Seat 3 was sold **twice** (counter 2 for one seat). The counter itself is also unsafe: `bookingsMade++` is read-modify-write (three steps), so two increments can also be lost.

**The exact critical section:** the three statements `if (!booked[seat]) { booked[seat] = true; bookingsMade++; }` – the **check-then-act** on `booked[seat]` together with the update of the shared counter. They must execute as one atomic unit.

## Student B – Comparing synchronisation strategies

**Time to process 10,000 bookings** (2,000 seats, 8 threads, mean of 7 runs after 3 warm-ups, `Q5/output_lab.txt`):

| Version | No extra work: total ms (ns/booking) | With 3,000-iteration pricing/QR work per booking: total ms (ns/booking) | Double bookings |
|---|---|---|---|
| synchronized method | 6.71 (671) | 113.09 (11,309) | 0 |
| synchronized block (critical part only) | 6.03 (603) | 52.48 (5,248) | 0 |
| `AtomicBoolean[]` CAS | 1.51 (151) | 34.11 (3,411) | 0 |
| `ReentrantLock` | 6.06 (606) | 49.79 (4,979) | 0 |
| `ConcurrentHashMap` | 9.41 (941) | 36.11 (3,611) | 0 |

![Q5 – time per booking with 8 threads, for each strategy.](charts/q5.png){width=80%}

**Locking the whole method versus only the critical section.** When the booking is only the check-and-set (no other work) the two are nearly identical (671 vs 603 ns). When each booking also does work that need not be locked (computing the price, generating the ticket QR code – simulated by 3,000 loop iterations), the synchronized **method** holds the lock during that work, so the threads queue up: **113 ms**; the synchronized **block** holds it only around the check-and-set: **52 ms**, 2.2× faster. Lock the smallest region that makes the invariant atomic.

**Static synchronisation, and when it would be needed here.** `static synchronized` locks the *class object* (`SeatBooking.class`), shared by all screens, whereas an instance `synchronized` method locks only `this`. It is needed when the shared data is **static** (belongs to the class, not the object). Experiment C (a static total-bookings counter shared by all screens): with instance `synchronized` methods **20 of 20 runs lost updates** (final 850,711 instead of 1,000,000) because different screen objects use *different* locks; with `static synchronized` 0 of 20 lost updates. For the seat arrays – which are per screen – an instance lock is the right granularity; a static lock would needlessly serialise all screens.

**The intrinsic lock used.**

* `synchronized void book(int seat)` – the intrinsic lock (monitor) of the **object `this`** (the `SeatBooking` instance of that screen).
* `synchronized (this) { … }` block – the same monitor, but held only inside the block (or a private `final Object lock` for tighter encapsulation).
* `static synchronized` – the monitor of the `Class` object.

## Joint engineering decision – *which strategy for a cinema with 20 screens, and why?*

Experiment D (20 client threads, one per screen, 200 bookings each, 50 µs simulated payment/DB write per booking):

| Strategy | Total time | Bookings per second |
|---|---|---|
| one **global** lock for the whole cinema | 493.9 ms | 8,099 |
| one lock **per screen** (synchronised block) | 24.5 ms | 163,360 |
| per-seat CAS (payment after winning the seat) | 26.9 ms | 148,844 |

**Decision: a lock per screen** (a `synchronized` block on the screen object – or per-seat atomic CAS if booking rates are very high). Different screens are independent, so they should never wait for each other: the global lock is **20× slower** in this test. Keep the payment/DB work **outside** the critical section (or reserve the seat atomically first, pay afterwards, release on failure), never use a `static` lock for per-screen data, and keep the critical section to the check-and-set.

## Complete the Code (`Q5/SeatBooking.java`)

TODO 1 completed: the method is made atomic with the `synchronized` keyword: `synchronized void book(int seat) { … }`.

* **Before (TODO 1 without `synchronized`), 10 runs** (`Q5/output_before_10runs.txt`): `Seats sold: 10` in all 10 runs – the race is possible but so rare for this tiny program (each thread finishes in a few microseconds, before the next thread is created) that it did not appear. My reproduction test above makes it appear in 20.5% of trials.
* **After (synchronized):** `Seats sold: 10` in all 10 runs (`Q5/output_after_10runs.txt`) – the expected output.

**Why the unsynchronised version may sell more than 10 seats:** two threads can both pass the check `!booked[seat]` before either sets it (steps 1–2 in the table above); both then execute the booking and increment, so a seat is sold twice and `bookingsMade` exceeds 10 (observed up to 15). It may also sell *fewer* seats than counted because two `bookingsMade++` can overwrite each other (lost update).

## Alternative Concept and Complexity Analysis

| Solution | How a booking works | Time per booking under contention (measured, 8 threads, no extra work) | Extra space for *s* seats |
|---|---|---|---|
| `synchronized` method/block | monitor lock around check-and-set | 603–671 ns | `boolean[s]` = *s* bytes; no extra |
| `AtomicBoolean[]` + `compareAndSet(false,true)` | the CAS *is* the check-then-act; no lock, retries only on conflict | **151 ns** (fastest) | *s* `AtomicBoolean` objects + *s* references (≈ 16–20 B per seat, estimate for 64-bit HotSpot) instead of 1 B |
| `ReentrantLock` | explicit lock/unlock in `try/finally` | 606 ns | one lock object (constant) + `boolean[s]` |
| `ConcurrentHashMap.putIfAbsent(seat, id)` | atomic insert; returns non-null if already taken | 941 ns | one hash entry per *booked* seat (≈ 50–60 B each, estimate) + table |

**Complexity:** every solution is O(1) time per booking (single seat); under contention the cost is waiting: lock-based versions block (the waiting thread sleeps), CAS spins/retries; space is O(s) for all of them, with the constants shown (`boolean[]` smallest; `AtomicBoolean[]` about 16–20× larger per seat). **Pseudocode of the CAS version:** `boolean book(seat) { return booked[seat].compareAndSet(false, true); }` – the boolean result tells whether this thread won the seat; the counter becomes an `AtomicInteger`/`LongAdder`. The space figures marked "estimate" were not measured.

# Q6 – Deadlock and Inter-Thread Communication (7 marks, K4)

## Shared group work

**Part A – hostel wallet transfers** (`Q6/WalletLab.java`): a transfer locks both accounts. Implemented as (i) an unsafe version (lock *from*, then *to*), (ii) a version with a consistent lock-ordering rule (lock the account with the smaller id first), and (iii) `tryLock` with back-off. **Part B – mess token counter** (`Q6/TokenCounter.java`): a producer–consumer counter of capacity 5 with `wait()`, `notify()`, `notifyAll()`.

## Student A – Producer–consumer design

**Why `wait()` must be called inside a loop and inside a `synchronized` block.**

* *Inside `synchronized`:* `wait()` atomically releases the object's monitor and puts the thread to sleep; it may be called only by the thread that holds the monitor, otherwise `IllegalMonitorStateException` is thrown. It also makes "test the condition, then wait" atomic – no `notify` can slip in between the test and the wait.
* *Inside a loop (`while (count == buffer.length) wait();`):* when a thread wakes, the condition may no longer hold: (a) with `notifyAll()` several threads wake but only one can use the token or space, (b) the JVM permits *spurious wake-ups*, (c) another thread may have run between the notify and the re-acquisition of the monitor. The loop re-tests the condition every time; `if` would continue as if it were true.

**What goes wrong with `if` instead of `while`** – experiment (`Q6/CounterLab.java`, 1 cook, 2 students, capacity 5, 500 runs each):

| Variant | Correct sum | Wrong sum | Hung | Counter size range seen |
|---|---|---|---|---|
| `while` + `notifyAll` | 500 | 0 | 0 | [0, 5] |
| `while` + `notify` | 500 | 0 | 0 | [0, 5] |
| **`if` + `notifyAll`** | 437 | **63** | 0 | **[−19, 5]** |
| **`if` + `notify`** | 459 | **41** | 0 | **[−14, 5]** |

With `if`, a consumer woken by `notifyAll` goes ahead although the other consumer already took the token: the count becomes **negative** (−19: tokens "taken" that were never produced) and the sums are wrong. The assignment's own program run with `if` in both methods, 30 runs (`Q6/output_if_30runs.txt`): **3 of 30 runs wrong** (sums 90, 135, 205 instead of 210); with `while`, 210 every time.

**`notify()` versus `notifyAll()` with several consumers** (and producers): `notify()` wakes *one arbitrary* waiter – it may wake a thread of the *wrong kind*, which sees its condition still false and goes back to sleep, while the thread that could have run is never woken. In my 1-cook/2-students design with capacity 5 `notify()` happened to work (500/500) because a notify always finds a thread whose condition is satisfied; with 2 cooks and 2 students and **capacity 1**: `notify()` **hung in 300 of 300 runs**, `notifyAll()` was correct in 300 of 300 (output table in `Q6/output_counterlab.txt`). `notifyAll()` is therefore the safe default.

**Counter size over time (it never exceeds 5 or drops below 0)** – recorded after each of the 40 operations in one run: `[1, 2, 3, 4, 5, 4, 3, 2, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 2, 3, 4, 5, 4, 3, 2, 1, 0, 1, 2, 3, 4, 5, 4, 3, 2, 1, 0]` – min 0, max 5; over 500 runs: smallest 0, largest 5, wrong sums 0, hung runs 0.

**`sleep()` versus `wait()`.**

| | `Thread.sleep(ms)` | `Object.wait()` |
|---|---|---|
| Class | static method of `Thread` | instance method of `Object` |
| Monitor | **keeps** any locks it holds | **releases** the monitor while waiting |
| Must hold the monitor? | no | **yes** (`synchronized`) |
| Wakes when | the time elapses (or interrupt) | `notify`/`notifyAll` (or timeout/interrupt) |
| Purpose | pause | wait for a *condition* to change |

## Student B – Deadlock analysis and prevention

**A pair of transfers that reliably deadlocks** (`Q6/output_wallet.txt`): T1 transfers A → B, T2 transfers B → A; each locks its *from* account, pauses 50 ms, then asks for its *to* account.

```
after 1 s: T1 alive=true (state BLOCKED), T2 alive=true (state BLOCKED)
JVM deadlock detector (ThreadMXBean.findDeadlockedThreads):
   T1(A->B) is BLOCKED on WalletLab$Account@b4c966a which is held by T2(B->A)
   T2(B->A) is BLOCKED on WalletLab$Account@312b1dae which is held by T1(A->B)
balances unchanged: A=1000 B=1000
```

**The four conditions for deadlock present in this program**

| Condition | Where it appears |
|---|---|
| **Mutual exclusion** | an account's monitor can be held by only one thread at a time |
| **Hold and wait** | each thread holds its *from* account while waiting for the *to* account |
| **No pre-emption** | a monitor cannot be taken away from the thread holding it |
| **Circular wait** | T1 waits for B (held by T2), T2 waits for A (held by T1) |

**Prevention with a consistent lock-ordering rule:** always lock the account with the **smaller id first**, whichever is the sender. Result: the same two transfers both finish and the balances stay consistent (C = 1000, D = 1000, sum 2000). Stress test, 8 threads × 2,000 random transfers among 5 accounts, 20 trials per version:

| Version | Deadlocked trials | Finished | Money conserved | Mean time of finished trials |
|---|---|---|---|---|
| unsafe (from, then to) | **20 of 20** | 0 | – | – |
| ordered (smaller id first) | **0 of 20** | 20 | 20 of 20 | 4.1 ms |
| `tryLock` + back-off | 0 of 20 | 20 | 20 of 20 | 22.8 ms (368 retries) |

**Why the rule works.** Give every account a number and require every thread to acquire locks in increasing order. A circular wait would need a cycle T1 → T2 → … → T1 in which each thread holds a lock with a smaller number than the one it waits for; following the cycle once round would give a number smaller than itself – impossible. The rule removes the *circular wait* condition, so deadlock cannot occur (the other three conditions remain).

## Joint conclusion – *what do deadlock and producer–consumer have in common, and how does each design ensure progress?*

Both are **coordination problems between threads that need resources held by other threads**: a thread may have to wait, and correctness requires that the waiting ends. In deadlock the waiting threads wait *for each other* forever; in producer–consumer a thread waits for a *condition* (space/token) that another thread must create. **Progress** is ensured, respectively, by (a) **breaking the circular-wait condition** – a global lock-ordering rule so that a cycle cannot form – and (b) **state-based waiting**: `while (!condition) wait();` plus `notifyAll()` after every state change, so every waiter is told when the condition may have changed (and re-checks it). Observed: 0 deadlocks in 20 ordered trials versus 20 of 20 unsafe; no hang in 500 `while + notifyAll` runs.

## Complete the Code (`Q6/TokenCounter.java`)

```java
public synchronized void put(int token) throws InterruptedException {
    while (count == buffer.length) wait();     // TODO 1: wait while full
    buffer[in] = token; in = (in + 1) % buffer.length; count++;
    notifyAll();                               // wake waiting threads
}
public synchronized int take() throws InterruptedException {
    while (count == 0) wait();                 // TODO 2: wait while empty
    int token = buffer[out]; out = (out + 1) % buffer.length; count--;
    notifyAll();
    return token;
}
```
Output (5 runs, `Q6/output_tokencounter.txt`): `Sum of tokens collected: 210 (expected 210)` every time – identical to the expected output.

**Changing `while` to `if` in both methods, running several times** (30 runs): `Sum of tokens collected: 210 (expected 210)` in most runs, but **wrong sums in 3 of 30 runs** (90, 135, 205); in the 500-run lab the counter size even reached −19. Explanation: after `wait()` returns, `if` does not test the condition again. With two students and `notifyAll()`, both wake when one token arrives; the first takes it, the second continues without a token and reads stale data/decrements `count` below 0, so tokens are duplicated or missed and the sum is wrong. With `while` the second student re-tests `count == 0` and waits again. The failure is intermittent – it depends on thread timing – which is why it passes in most runs.

## Alternative Concept and Complexity Analysis

**Solution with `ArrayBlockingQueue<Integer>(5)`:** `queue.put(t)` blocks while full, `queue.take()` blocks while empty – the queue contains the lock, the two conditions and the wait loop, so no `wait/notify` code is written. **Transfers with `tryLock`:** each transfer tries `from.lock.tryLock(timeout)` then `to.lock.tryLock(timeout)`; if the second fails it releases the first, backs off a random short time and retries – no global order is needed, and a deadlock turns into a timeout/retry.

Measured (`Q6/output_bench.txt`, mean of 5 runs after warm-up; 500,000 tokens; 200,000 transfers per thread over 100 accounts):

| Design | ns per token, 1 producer / 1 consumer | ns per token, 1 producer / 2 consumers |
|---|---|---|
| `wait()`/`notifyAll()` (TokenCounter) | 5,319 | 5,459 |
| `ArrayBlockingQueue(5)` | 5,268 | 8,424 |

| Design | ns per transfer, 1 thread | ns per transfer, 4 threads |
|---|---|---|
| `synchronized` + lock ordering | 61 | 290 |
| `ReentrantLock.tryLock(timeout)` + retry | 60 | 509 |

![Q6 – cost per token (left) and per transfer (right).](charts/q6.png){width=95%}

* **Time:** per `put`/`take` both designs are O(1) work; the ≈ 5 µs per token is dominated by thread hand-over (a blocked thread must be descheduled and woken), not by the data structure. The blocking queue was slower with two consumers (8.4 vs 5.5 µs). For transfers, ordered locking is O(1) per transfer with at most two lock acquisitions and no retries; `tryLock` costs the same without contention but ≈ 1.75× more at 4 threads (509 vs 290 ns), plus retries (368 retries in the 20-trial stress test, 22.8 vs 4.1 ms).
* **Space:** the hand-written counter needs `int[5]` + 3 ints (plus the object's monitor); `ArrayBlockingQueue` needs an `Object[5]`, one `ReentrantLock` and two `Condition` objects (constant extra, boxed `Integer` tokens). Per-account locks in the `tryLock` design are one `ReentrantLock` per account (O(accounts)) versus one id comparison with intrinsic monitors.
* **Less error-prone:** the **`ArrayBlockingQueue`** (no condition loop, no `notify` vs `notifyAll` choice to get wrong). For transfers, the **ordering rule** is simpler to reason about and deterministic; `tryLock` needs careful release-on-failure, back-off and a retry limit and has livelock potential.

# Q7 – Design a Concurrent, Fault-Tolerant Processor from First Principles (10 marks, K5)

## Stage 1 – Interpret the problem (joint, 2 marks)

**Tasks that can run in parallel / data that is shared.** Reading, validating and totalling each of the 10,000 answer-script files is **independent** of every other file ⇒ one task per file, run in parallel. **Shared data:** the *collection of results* (every task adds one) and the *summary counts*. Nothing else is shared: each task has its own local variables.

**Every failure that can occur and how each must be handled**

| Failure | Detected as | Handling |
|---|---|---|
| file listed but missing | `NoSuchFileException` | result `MISSING`; continue |
| file present but invalid format (bad header, wrong number of lines, non-numeric / out-of-range score, truncated, empty) | `CorruptFileException` (user-defined checked) | result `CORRUPT` with the reason; continue |
| invalid bytes (not UTF-8) | `MalformedInputException` (`CharacterCodingException`) | result `CORRUPT` |
| other I/O failure | `IOException` | result `IO_ERROR` |
| a bug / unexpected runtime exception inside a task | `ExecutionException` from `Future.get()` | result `UNEXPECTED` (kept with the exception text) |
| a task that hangs or is too slow | `TimeoutException` from `Future.get(timeout)` | cancel the task, result `TIMEOUT` |
| the run is interrupted | `InterruptedException` | `shutdownNow()`; remaining ids marked `UNEXPECTED` so the summary stays complete |

**Requirements as measurable statements**

1. R1 – Every one of the 10,000 listed ids ends with **exactly one** result: *OK + CORRUPT + MISSING + IO_ERROR + TIMEOUT + UNEXPECTED = requested*, checked by the program (`consistent()`).
2. R2 – A failing file never stops the run: all 10,000 ids are processed in every run, including with injected failures.
3. R3 – The counts equal the expected counts for a known data set (small hand-checked set of 12; generated set of 10,000: ok = 9,300, corrupt = 500, missing = 200).
4. R4 – Total time for 10,000 files on 4 threads **< 1 s** (stretch: faster than 1 thread by ≥ 2×).
5. R5 – No file handle remains open (each file is read with `Files.readAllLines`, which closes it) and the pool is shut down (`shutdown` + `awaitTermination`).
6. R6 – No single task may block the run for more than the configured timeout.

## Stage 2 – Design (Student A, 3 marks)

**Design with `ExecutorService`, `Callable` and `Future`.** A fixed thread pool of *p* threads; for every id a `Callable<Result>` is submitted; the main thread keeps the `Future`s and calls `get(timeout)` on each of them to collect completion and failures.

**Choice of the concurrent collection – `ConcurrentHashMap<String, Result>` (id → result).** It is safe for concurrent `put` from all workers without an external lock (lock-free reads, fine-grained locking/CAS on the bins, so threads working on different ids almost never contend); the **key = id guarantees one entry per file** (a duplicate id overwrites instead of being counted twice) and `putIfAbsent` lets the collector add a result *only if the task did not*, which is what makes the summary complete when a task fails or times out. Alternatives rejected: `Collections.synchronizedList` (one global lock), `CopyOnWriteArrayList` (O(n) copy per add ⇒ O(n²) for 10,000 results), `ConcurrentLinkedQueue` (no lookup by id, duplicates possible).

**Exception handling so that one failure does not stop the run.** *Expected* failures (missing file, corrupt content, I/O error) are caught **inside the task** and turned into a `Result` with a status – they are data, not control flow. An *unexpected* exception thrown by a task is captured by the `Future` and surfaces in the collector as `ExecutionException` → `Result(UNEXPECTED)`; a `TimeoutException` → `cancel(true)` and `Result(TIMEOUT)`. The collector loop has its own `try/catch` **inside** the `for` loop, so the loop continues with the next `Future`. `finally` shuts the pool down.

**Class diagram**

![Q7 – classes and collaboration.](diagrams/q7_classes.png){width=95%}

**Pseudocode**

```
run(dir, ids, p, timeout):
    results = new ConcurrentHashMap<id, Result>
    pool    = newFixedThreadPool(p)
    futures = []
    for id in ids:  futures[id] = pool.submit( task(id) )          # Callable<Result>
    for (id, f) in futures:                                          # try/catch is INSIDE the loop
        try:    f.get(timeout)
        catch ExecutionException   -> results.putIfAbsent(id, UNEXPECTED(cause))
        catch TimeoutException     -> f.cancel(true); results.putIfAbsent(id, TIMEOUT)
        catch CancellationException-> results.putIfAbsent(id, UNEXPECTED)
    finally: pool.shutdown(); pool.awaitTermination(...)
    return Summary(requested = |ids|, results)                       # counts derived from the map

task(id):  try:  total = process(id); results.put(id, OK(total))
           catch NoSuchFile -> results.put(id, MISSING)
           catch Corrupt | CharacterCoding -> results.put(id, CORRUPT(reason))
           catch IOException -> results.put(id, IO_ERROR)
```

## Stage 3 – Implement and measure (Student B, 3 marks)

Code: `Q7/ScoreProcessor.java`, `Q7/Q7Main.java`, data generator `Q7/GenerateData.java`.

**Injected corrupt and missing files.** The generator creates 10,000 ids; every 50th is *listed but absent* (200 missing); every 20th (offset 7) is *corrupt* (500), in six kinds in turn: bad header, non-numeric score, score out of range, truncated file, empty file, invalid UTF-8 bytes. Expected (computed by the generator, independent of the processor): **ok = 9,300, corrupt = 500, missing = 200**, sum of totals = 465,007.

**Summary counts verified on a small hand-checked set** (`Q7/handset`, 12 ids; worked out *by hand* before running: H01–H08 valid with totals 100, 50, 40, 30, 50, 55, 0, 90 ⇒ sum 415, 6 with total ≥ 40; H09 non-numeric score and H10 truncated ⇒ 2 corrupt; H11, H12 absent ⇒ 2 missing):

```
files requested : 12
processed OK    : 8  (passed 6, failed 2, sum of totals 415)
corrupt         : 2     missing : 2     io error / timeout / unexpected : 0
OK + failures = 12 (must equal 12: YES)
   CORRUPT H09  - H09: non-numeric score on line 3 'abc'
   CORRUPT H10  - H10: expected 10 score lines but found 3
   MISSING H11 / H12  - no such file
MATCHES HAND-CHECKED EXPECTATION: PASS      (with pools of 1 thread and of 4 threads)
```

**Fault injection** (`Q7/output_handset.txt`): H05 made to throw `IllegalStateException` (a bug) and H06 made to hang 2 s with a 300 ms limit:

```
processed OK : 6 ... corrupt 2, missing 2, timeout 1, unexpected 1;  OK + failures = 12: YES
   UNEXPECTED H05 - java.lang.IllegalStateException: injected bug for H05
   TIMEOUT    H06 - exceeded 300 ms
```
The run completed with a complete, consistent summary.

**Total time with pools of 1, 2, 4, 8 (and 16) threads** – 10,000 files, mean of 5 runs after 1 warm-up, every run's summary verified against the expected counts (`Q7/output_bench.txt`):

| Threads | Local cached files – time (ms) | speed-up | min–max (ms) | Simulated 0.5 ms I/O per file – time (ms) | speed-up | Summary correct? |
|---|---|---|---|---|---|---|
| 1 | 219.0 | 1.00 | 167–251 | 6,386.2 | 1.00 | yes |
| 2 | 120.0 | 1.83 | 109–138 | 3,164.8 | 2.02 | yes |
| 4 | 72.4 | 3.02 | 62–78 | 1,568.2 | 4.07 | yes |
| 8 | **59.8** | 3.66 | 57–65 | 779.0 | 8.20 | yes |
| 16 | 69.6 | 3.15 | 60–78 | **390.4** | 16.36 | yes |

![Q7 – total time for 10,000 files against pool size (left: real local files; right: simulated slow storage).](charts/q7.png){width=95%}

**Failures that were not handled correctly.** In the final measured runs none: all 50 measured runs (5 pool sizes × 5 runs × 2 storage models) produced summaries with exactly 9,300 OK, 500 corrupt and 200 missing, `consistent() = true`. Known weaknesses found by inspection (not observed failures): (a) `cancel(true)` can stop a task only if it is in an interruptible operation (`sleep`, `park`) – a task stuck in a non-interruptible blocking file read (e.g. a hung network drive) keeps its pool thread busy; (b) a task cancelled *after* it had already stored its own result is recorded with its own result, not TIMEOUT, because the collector uses `putIfAbsent`; (c) the timeout is applied to each `Future.get` in turn, so it limits waiting after the previous futures, not the task's own run time. The generated data set has every valid file ≥ 40 in total, so "passed" is not discriminating there – the hand-checked set (6 of 8 passed) covers that.

## Stage 4 – Defend the design (joint, 2 marks)

**Recommended pool size and why more threads did not always help.** For *local, cached* files: **8 threads (4 × the 2 cores)** – 59.8 ms, the best measured; 16 threads were *slower* (69.6 ms). The task is a mixture of CPU (parsing, validation) and short file system calls: with 2 cores, speed-up cannot continue past the point where the cores are saturated (observed 3.66× with 8 threads), and extra threads add context switching, cache pressure and contention on the executor's work queue. For *slow storage* (simulated 0.5 ms wait per file) the threads are mostly waiting, so the speed-up stays **nearly linear up to 16 threads (16.4×)** and a larger pool would still help until the storage itself saturates. **Rule of thumb:** pool ≈ cores × (1 + wait time / compute time); measure instead of guessing, and make the pool size a configuration value.

**How the design guarantees a complete and correct summary.** (1) The summary is **derived from the result map**, not from separate counters that could drift. (2) Every id has exactly one entry: tasks record their own result; whatever a task cannot record (unexpected exception, timeout, cancellation, interrupt) is recorded by the collector with `putIfAbsent`. (3) `consistent()` checks *map size = requested* and *sum of all status counts = requested*, and the program prints that check. (4) Counts were verified against hand-computed expected values and against generator-computed expected values in 50 runs.

**One remaining limitation.** The whole result map lives in memory and the run is not restartable: if the JVM dies at file 9,000, all results are lost (no checkpoint); and a task blocked in non-interruptible I/O cannot be cancelled (weakness (a)). A production version would persist results incrementally and use per-task timeouts at the I/O layer.

## Alternative Concept and Complexity Analysis – parallel streams, or one thread per file

**Solution with parallel streams:** `ids.parallelStream().forEach(id -> results.put(id, one(dir, id)))`. **Solution with one thread per file:** `for (id : ids) new Thread(() -> results.put(id, one(dir,id))).start()` followed by `join()` for all.

**Total time and memory (threads × stack size)** – 10,000 real local files (`Q7/output_alt_bench.txt`, mean of 3 runs after 1 warm-up, fresh JVM; compare the rows with each other, not with the table above):

| Approach | Total time (ms) | Peak threads | Reserved stack memory (threads × 1 MB) |
|---|---|---|---|
| ExecutorService fixed pool, p = 2 | 182.0 | 2 (+main) | ≈ 2 MB |
| ExecutorService fixed pool, p = 4 | 116.0 | 4 (+main) | ≈ 4 MB |
| parallel stream (common pool, parallelism = cores − 1 + caller) | 94.4 | 2 | ≈ 2 MB |
| **one thread per file** (all alive together) | **4,278.1** | **10,007** | **≈ 9,943 MB virtual** (measured VmPeak increase) |

* **Time.** Pool and parallel stream are ≈ O(N·c / min(p, cores)); the parallel stream is fast here because it avoids per-task `Future` objects. Its parallelism is **fixed by the machine** (here 2) and cannot be raised, so with slow storage it would be limited to 2 concurrent reads (predicted 10,000 × 0.5 ms ÷ 2 ≈ 2.5 s, against the measured 0.39 s for a pool of 16 – prediction, not measured). One thread per file is **36–45× slower** than the pool (4.3 s vs 0.1 s): creating and starting 10,000 threads costs far more than the work in each thread.
* **Memory.** Memory for threads = number of threads × stack size (default ≈ 1 MB reserved on 64-bit Linux): ≈ 4 MB for a pool of 4 versus ≈ **10 GB** for 10,000 threads; a larger machine or a container with a lower limit would fail with `OutOfMemoryError: unable to create native thread`.
* **Failure in one file** (`Q7/output_alt_fail.txt`, 12 files, H05 throws `IllegalStateException`):

| Approach | Results in the summary (expected 12) | What happened |
|---|---|---|
| parallel stream, no catch in the lambda | **7 of 12** (varies per run) | the exception aborts the whole pipeline: the *terminal operation* throws and the remaining files are not reliably processed – the summary is **incomplete** |
| parallel stream, `try/catch` inside the lambda | 12 of 12 | correct, but only because the programmer remembered the catch |
| one thread per file, no catch | **11 of 12** | the thread dies, the uncaught-exception handler prints a trace, and H05 is **silently missing** from the summary |
| `ExecutorService` + `Future` (chosen) | **12 of 12** | the exception is captured by the `Future`, recorded as `UNEXPECTED`; `consistent = true` |

**Conclusion:** the `ExecutorService` design gives control of the number of threads (bounded memory), per-task results, timeouts and failure isolation. The parallel stream is a good, simple choice for pure CPU-bound work when failures are handled inside the lambda; one thread per file does not scale.

# Individual Contribution Record

The assignment defines the primary responsibilities below; both students executed and understand every question and every program (the programs were run in each student's own repository).

| Question | Student A – primary responsibility | Student B – primary responsibility | Joint |
|---|---|---|---|
| Q1 | Exception design: hierarchy, failure table, catch-order justification, `finally` guarantee | Testing and tracing: ≥ 5 test inputs, stack trace explanation, broad-before-narrow demonstration | Shared implementation, `catch (Exception)` discussion, alternative analysis |
| Q2 | Exception hierarchy diagram, checked/unchecked reasons, propagation path, final handling and message | Failure scenarios, chaining with `getCause()`, information lost without cause, test table | Shared implementation, joint conclusion, Complete the Code, alternative analysis |
| Q3 | Debugging: breakpoints, failing input, counting open files, logging | Design and correctness: resources, why the leak happens, TWR guarantees, suppressed exceptions | Both versions, same-report check, engineering decision |
| Q4 | Thread design: Thread vs Runnable, life-cycle diagram, `run()` vs `start()`, `join()` | Behavioural experiments: 10+ runs, priorities, daemon thread, scheduler explanation | Both implementations, joint conclusion, pool analysis |
| Q5 | Producing and explaining the race, critical section | Comparing strategies, timings, static synchronisation, intrinsic locks | Three versions, 20-screen decision, Complete the Code, alternatives |
| Q6 | Producer–consumer design: `wait` in loop, `notify` vs `notifyAll`, size log, `sleep` vs `wait` | Deadlock analysis and prevention: four conditions, lock ordering | Parts A and B, joint conclusion, Complete the Code, alternatives |
| Q7 | Stage 2 – Design (3 marks) | Stage 3 – Implement and measure (3 marks) | Stage 1 interpretation (2) and Stage 4 defence (2) |

# Reproduction

```
cd Qn
javac *.java
java <MainClass>     # see README.md for the main class of every task
# Q7 data set: java GenerateData data 10000 ; java Q7Main bench data ; java Q7Main handset
```
