import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Predicate;

/** Checked exception: the file exists but its content breaks the format rules. */
class CorruptFileException extends Exception {
    CorruptFileException(String id, String why) { super(id + ": " + why); }
}

enum Status { OK, CORRUPT, MISSING, IO_ERROR, UNEXPECTED, TIMEOUT }

record Result(String id, Status status, int total, String detail) {
    static Result ok(String id, int total) { return new Result(id, Status.OK, total, ""); }
    static Result failed(String id, Status s, String detail) { return new Result(id, s, 0, detail); }
}

/** Outcome of a whole run. All counts are derived from the result map, so they cannot drift from the data. */
class Summary {
    final int requested; final Map<String, Result> results; final long millis;
    Summary(int requested, Map<String, Result> results, long millis) { this.requested = requested; this.results = results; this.millis = millis; }
    long count(Status s) { return results.values().stream().filter(r -> r.status() == s).count(); }
    long scoreSum() { return results.values().stream().filter(r -> r.status() == Status.OK).mapToLong(Result::total).sum(); }
    long passed() { return results.values().stream().filter(r -> r.status() == Status.OK && r.total() >= 40).count(); }
    boolean consistent() { long s = 0; for (Status st : Status.values()) s += count(st); return s == requested && results.size() == requested; }
    String text() {
        StringBuilder sb = new StringBuilder();
        sb.append("SEMESTER RESULT PROCESSING SUMMARY\n");
        sb.append("files requested : ").append(requested).append('\n');
        sb.append("processed OK    : ").append(count(Status.OK)).append("  (passed ").append(passed()).append(", failed ").append(count(Status.OK) - passed()).append(", sum of totals ").append(scoreSum()).append(")\n");
        sb.append("corrupt         : ").append(count(Status.CORRUPT)).append('\n');
        sb.append("missing         : ").append(count(Status.MISSING)).append('\n');
        sb.append("io error        : ").append(count(Status.IO_ERROR)).append('\n');
        sb.append("timeout         : ").append(count(Status.TIMEOUT)).append('\n');
        sb.append("unexpected      : ").append(count(Status.UNEXPECTED)).append('\n');
        sb.append("OK + failures = ").append(count(Status.OK) + count(Status.CORRUPT) + count(Status.MISSING) + count(Status.IO_ERROR) + count(Status.TIMEOUT) + count(Status.UNEXPECTED))
          .append(" (must equal ").append(requested).append(": ").append(consistent() ? "YES" : "NO").append(")\n");
        results.values().stream().filter(r -> r.status() != Status.OK).sorted(Comparator.comparing(Result::id)).limit(15)
               .forEach(r -> sb.append("   ").append(r.status()).append(' ').append(r.id()).append(r.detail().isEmpty() ? "" : "  - " + r.detail()).append('\n'));
        return sb.toString();
    }
}

/** Q7: concurrent, fault-tolerant processing with ExecutorService + Callable + Future + ConcurrentHashMap. */
public class ScoreProcessor {
    static final int QUESTIONS = 10, MAX_PER_Q = 10;

    /** reads, validates and totals one file. Throws NoSuchFileException (missing), CorruptFileException / CharacterCodingException (corrupt) */
    static int process(Path dir, String id) throws IOException, CorruptFileException {
        List<String> lines = Files.readAllLines(dir.resolve(id + ".txt"));       // UTF-8, strict: bad bytes -> MalformedInputException
        if (lines.isEmpty()) throw new CorruptFileException(id, "empty file");
        if (!lines.get(0).equals("STUDENT:" + id)) throw new CorruptFileException(id, "bad header '" + lines.get(0) + "'");
        if (lines.size() != QUESTIONS + 1) throw new CorruptFileException(id, "expected " + QUESTIONS + " score lines but found " + (lines.size() - 1));
        int total = 0;
        for (int q = 1; q <= QUESTIONS; q++) {
            String[] p = lines.get(q).split(",");
            if (p.length != 2 || !p[0].equals("Q" + q)) throw new CorruptFileException(id, "bad line " + (q + 1) + " '" + lines.get(q) + "'");
            int s;
            try { s = Integer.parseInt(p[1].trim()); }
            catch (NumberFormatException e) { throw new CorruptFileException(id, "non-numeric score on line " + (q + 1) + " '" + p[1] + "'"); }
            if (s < 0 || s > MAX_PER_Q) throw new CorruptFileException(id, "score out of range on line " + (q + 1) + ": " + s);
            total += s;
        }
        return total;
    }

    /** One task per file. Expected failures are recorded here; anything else propagates through the Future (safety net in the collector). */
    static Callable<Result> task(Path dir, String id, ConcurrentMap<String, Result> results, LongAdder done, long latencyNs, Predicate<String> bugHook) {
        return () -> {
            try {
                if (latencyNs > 0) LockSupport.parkNanos(latencyNs);               // simulated slow storage
                if (bugHook != null && bugHook.test(id)) throw new IllegalStateException("injected bug for " + id);
                Result r = Result.ok(id, process(dir, id));
                results.put(id, r); return r;
            } catch (NoSuchFileException e) {
                Result r = Result.failed(id, Status.MISSING, "no such file"); results.put(id, r); return r;
            } catch (CorruptFileException | CharacterCodingException e) {
                Result r = Result.failed(id, Status.CORRUPT, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()); results.put(id, r); return r;
            } catch (IOException e) {
                Result r = Result.failed(id, Status.IO_ERROR, e.toString()); results.put(id, r); return r;
            } finally { done.increment(); }
        };
    }

    static Summary run(Path dir, List<String> ids, int threads, long latencyNs, Predicate<String> bugHook, long timeoutMs) throws InterruptedException {
        ConcurrentMap<String, Result> results = new ConcurrentHashMap<>(Math.max(16, ids.size() * 2));
        LongAdder done = new LongAdder();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        long t0 = System.nanoTime();
        Map<String, Future<Result>> futures = new LinkedHashMap<>();
        try {
            for (String id : ids) futures.put(id, pool.submit(task(dir, id, results, done, latencyNs, bugHook)));
            for (Map.Entry<String, Future<Result>> e : futures.entrySet()) {
                String id = e.getKey();
                try { e.getValue().get(timeoutMs, TimeUnit.MILLISECONDS); }
                catch (ExecutionException ex) { results.putIfAbsent(id, Result.failed(id, Status.UNEXPECTED, String.valueOf(ex.getCause()))); }   // a task died with an unexpected exception
                catch (TimeoutException ex) { e.getValue().cancel(true); results.putIfAbsent(id, Result.failed(id, Status.TIMEOUT, "exceeded " + timeoutMs + " ms")); }
                catch (CancellationException ex) { results.putIfAbsent(id, Result.failed(id, Status.UNEXPECTED, "cancelled")); }
            }
        } catch (InterruptedException ie) {
            pool.shutdownNow();
            for (String id : ids) results.putIfAbsent(id, Result.failed(id, Status.UNEXPECTED, "run interrupted"));
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(30, TimeUnit.SECONDS)) pool.shutdownNow();
        }
        return new Summary(ids.size(), results, (System.nanoTime() - t0) / 1_000_000);
    }
}
