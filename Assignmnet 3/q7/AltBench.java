import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.CharacterCodingException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Q7 alternative concepts: (1) parallelStream, (2) one Thread per file - compared with the ExecutorService design.
 * usage: java AltBench fail            (small set, injected bug: how does a failure in ONE file behave?)
 *        java AltBench bench <dir>      (10,000 files: time, threads, memory)
 */
public class AltBench {
    static String rssKb() {
        try { for (String l : Files.readAllLines(Paths.get("/proc/self/status"))) if (l.startsWith("VmRSS") || l.startsWith("VmPeak")) System.out.print(l.replaceAll("\\s+", " ") + "; "); } catch (IOException e) { }
        return "";
    }
    static long vm(String key) {
        try { for (String l : Files.readAllLines(Paths.get("/proc/self/status"))) if (l.startsWith(key)) return Long.parseLong(l.replaceAll("[^0-9]", "")); } catch (IOException e) { }
        return -1;
    }

    /** processes one id; returns status text, never throws checked exceptions. 'bug' simulates an unexpected RuntimeException. */
    static Result one(Path dir, String id, String bugId) {
        try {
            if (id.equals(bugId)) throw new IllegalStateException("injected bug for " + id);
            return Result.ok(id, ScoreProcessor.process(dir, id));
        } catch (NoSuchFileException e) { return Result.failed(id, Status.MISSING, "no such file"); }
        catch (CorruptFileException | CharacterCodingException e) { return Result.failed(id, Status.CORRUPT, String.valueOf(e.getMessage())); }
        catch (IOException e) { return Result.failed(id, Status.IO_ERROR, e.toString()); }
    }

    static void fail() throws Exception {
        Path dir = Paths.get("handset");
        List<String> ids = Files.readAllLines(dir.resolve("manifest.txt"));
        System.out.println("=== Failure in ONE file (H05 throws IllegalStateException) - 12 files expected in the summary ===");

        // (1) parallel stream, exception NOT caught inside the lambda
        ConcurrentMap<String, Result> got = new ConcurrentHashMap<>();
        try {
            ids.parallelStream().forEach(id -> got.put(id, one(dir, id, "H05")));
            System.out.println("parallelStream: finished normally?!");
        } catch (RuntimeException e) {
            System.out.println("parallelStream (no catch in lambda): terminal operation threw " + e + "; results collected before the abort = " + got.size() + " of 12 (varies per run) -> summary INCOMPLETE");
        }
        // (1b) parallel stream, exception caught inside the lambda
        ConcurrentMap<String, Result> got2 = new ConcurrentHashMap<>();
        ids.parallelStream().forEach(id -> { try { got2.put(id, one(dir, id, "H05")); } catch (RuntimeException e) { got2.put(id, Result.failed(id, Status.UNEXPECTED, e.toString())); } });
        System.out.println("parallelStream (catch inside lambda): results = " + got2.size() + " of 12, H05 -> " + got2.get("H05").status());

        // (2) one raw thread per file, no handler
        ConcurrentMap<String, Result> got3 = new ConcurrentHashMap<>();
        List<Thread> ts = new ArrayList<>();
        for (String id : ids) { Thread t = new Thread(() -> got3.put(id, one(dir, id, "H05")), "file-" + id); t.setUncaughtExceptionHandler((th, ex) -> System.out.println("   [uncaught handler] " + th.getName() + " died: " + ex)); ts.add(t); t.start(); }
        for (Thread t : ts) t.join();
        System.out.println("thread-per-file (no try/catch in the thread body): results = " + got3.size() + " of 12; H05 present? " + got3.containsKey("H05") + " -> that file is silently MISSING from the summary");

        // (3) the chosen design for comparison
        Summary s = ScoreProcessor.run(dir, ids, 4, 0, id -> id.equals("H05"), 5000);
        System.out.println("ExecutorService + Future (chosen design): results = " + s.results.size() + " of 12; H05 -> " + s.results.get("H05").status() + "; consistent=" + s.consistent());
    }

    static void bench(Path dir) throws Exception {
        List<String> ids = Files.readAllLines(dir.resolve("manifest.txt"));
        java.lang.management.ThreadMXBean mx = ManagementFactory.getThreadMXBean();
        System.out.println("=== " + ids.size() + " files, real local files (OS page cache); time = mean of 3 runs after 1 warm-up; cores=" + Runtime.getRuntime().availableProcessors() + " ===");
        System.out.printf("%-34s %10s %14s %16s%n", "approach", "time (ms)", "peak threads", "extra VmPeak MB");

        // pool of 4 (reference)
        for (int th : new int[]{2, 4}) {
            ScoreProcessor.run(dir, ids, th, 0, null, 60000);
            double tot = 0; for (int i = 0; i < 3; i++) tot += ScoreProcessor.run(dir, ids, th, 0, null, 60000).millis;
            System.out.printf("%-34s %10.1f %14s %16s%n", "ExecutorService fixed pool, p=" + th, tot / 3, th + " (+main)", "~0");
            System.out.println("CSV_ALT,pool" + th + "," + tot / 3);
        }

        // parallel stream (common pool = cores - 1 workers + caller)
        Runnable ps = () -> { ConcurrentMap<String, Result> m = new ConcurrentHashMap<>(); ids.parallelStream().forEach(id -> m.put(id, one(dir, id, null))); if (m.size() != ids.size()) throw new IllegalStateException(); };
        ps.run();
        double tot = 0; for (int i = 0; i < 3; i++) { long t0 = System.nanoTime(); ps.run(); tot += (System.nanoTime() - t0) / 1e6; }
        System.out.printf("%-34s %10.1f %14s %16s%n", "parallelStream (common pool)", tot / 3, (ForkJoinPool.commonPool().getParallelism() + 1) + "", "~0");
        System.out.println("CSV_ALT,parallelStream," + tot / 3);

        // one thread per file - all 10,000 alive concurrently is forced by a start gate
        long vmPeak0 = vm("VmPeak");
        int peak0 = mx.getPeakThreadCount();
        double tt = 0; String note = "";
        for (int run = 0; run < 3; run++) {
            long t0 = System.nanoTime();
            ConcurrentMap<String, Result> m = new ConcurrentHashMap<>();
            CountDownLatch gate = new CountDownLatch(1);
            List<Thread> ts = new ArrayList<>(ids.size());
            try {
                for (String id : ids) { Thread t = new Thread(() -> { try { gate.await(); } catch (InterruptedException e) { return; } m.put(id, one(dir, id, null)); }); ts.add(t); t.start(); }
            } catch (OutOfMemoryError e) { note = "OutOfMemoryError after " + ts.size() + " threads: " + e.getMessage(); }
            gate.countDown();
            for (Thread t : ts) t.join();
            tt += (System.nanoTime() - t0) / 1e6;
            if (!note.isEmpty()) break;
        }
        int peakThreads = mx.getPeakThreadCount();
        System.out.printf("%-34s %10.1f %14d %16.0f %s%n", "one Thread per file (gate: all alive)", tt / 3, peakThreads, (vm("VmPeak") - vmPeak0) / 1024.0, note);
        System.out.println("CSV_ALT,threadPerFile," + tt / 3 + "," + peakThreads + "," + (vm("VmPeak") - vmPeak0) / 1024.0);
        System.out.println("default thread stack size (-XX:ThreadStackSize) is 1024 KB on 64-bit Linux -> reserved virtual memory = threads x 1 MB");
    }


    public static void main(String[] a) throws Exception {
        System.out.println("Java " + System.getProperty("java.version"));
        if (a[0].equals("fail")) fail(); else bench(Paths.get(a[1]));
    }
}
