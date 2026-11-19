import java.lang.management.ManagementFactory;
import java.util.*;

/** Q1 - String vs StringBuilder vs StringBuffer (plus join / StringJoiner / format) for e-ticket generation. */
public class TicketBench {
    static final com.sun.management.ThreadMXBean TMX =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    static long sink; // defeats dead-code elimination

    interface Builder { int build(String[] f); }

    static long allocated() { return TMX.getThreadAllocatedBytes(Thread.currentThread().getId()); }

    static String[] fields(int n) {
        String[] f = new String[n];
        for (int i = 0; i < n; i++) f[i] = "FIELD" + i + "=VALUE" + i;
        return f;
    }

    static final Map<String, Builder> APPROACHES = new LinkedHashMap<>();
    static {
        APPROACHES.put("String +", f -> { String s = ""; for (String x : f) s = s + x + "|"; return s.length(); });
        APPROACHES.put("StringBuilder", f -> { StringBuilder s = new StringBuilder(); for (String x : f) s.append(x).append('|'); return s.toString().length(); });
        APPROACHES.put("StringBuffer", f -> { StringBuffer s = new StringBuffer(); for (String x : f) s.append(x).append('|'); return s.toString().length(); });
        APPROACHES.put("String.join", f -> String.join("|", f).length());
        APPROACHES.put("StringJoiner", f -> { StringJoiner j = new StringJoiner("|"); for (String x : f) j.add(x); return j.toString().length(); });
        APPROACHES.put("String.format", f -> { StringBuilder s = new StringBuilder(); for (String x : f) s.append(String.format("%s|", x)); return s.length(); });
    }

    /** returns {avg ms per pass, allocated bytes per pass} */
    static double[] measure(Builder b, String[] f, int tickets, int warm, int reps) {
        for (int w = 0; w < warm; w++) for (int t = 0; t < tickets; t++) sink += b.build(f);
        long tot = 0;
        for (int r = 0; r < reps; r++) {
            long t0 = System.nanoTime();
            for (int t = 0; t < tickets; t++) sink += b.build(f);
            tot += System.nanoTime() - t0;
        }
        long a0 = allocated();
        for (int t = 0; t < tickets; t++) sink += b.build(f);
        long bytes = allocated() - a0;
        return new double[]{tot / (double) reps / 1e6, bytes};
    }

    static void workload(String name, int nFields, int tickets, int warm, int reps, Set<String> skip) {
        System.out.println("\n=== " + name + ": " + tickets + " ticket(s) x " + nFields + " fields (avg of " + reps + " runs, " + warm + " warm-up) ===");
        System.out.printf("%-15s %14s %18s %22s%n", "Approach", "time (ms)", "allocated (MB)", "intermediate Strings*");
        String[] f = fields(nFields);
        for (var e : APPROACHES.entrySet()) {
            if (skip.contains(e.getKey())) continue;
            double[] m = measure(e.getValue(), f, tickets, warm, reps);
            String inter = e.getKey().equals("String +") ? "~" + (long) tickets * nFields
                    : e.getKey().equals("String.format") ? "~" + (long) tickets * nFields + " (per-field format)" : "~" + tickets + " (final only)";
            System.out.printf("%-15s %14.3f %18.2f %22s%n", e.getKey(), m[0], m[1] / 1048576.0, inter);
            System.out.println("CSV," + name + "," + e.getKey() + "," + nFields + "," + tickets + "," + m[0] + "," + m[1]);
        }
    }

    static void memoryBeforeAfter(int nFields) {
        System.out.println("\n=== Heap used before/after building ONE ticket of " + nFields + " fields (Runtime, after gc) ===");
        String[] f = fields(nFields);
        for (var e : new String[]{"String +", "StringBuilder"}) {
            System.gc();
            long before = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long a0 = allocated();
            sink += APPROACHES.get(e).build(f);
            long alloc = allocated() - a0;
            long after = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            System.out.printf("%-14s heap before=%d KB, after=%d KB (garbage not yet collected), thread-allocated=%.1f MB%n",
                    e, before / 1024, after / 1024, alloc / 1048576.0);
        }
    }

    // ---- Design decision: shared booking log with several threads ----
    static void threadSafety() throws Exception {
        System.out.println("\n=== Shared booking log: 4 threads x 20,000 appends of 'x' (expected length 80,000), 30 trials ===");
        for (String kind : new String[]{"StringBuilder", "StringBuffer"}) {
            int wrong = 0, crashed = 0;
            for (int trial = 0; trial < 30; trial++) {
                final Appendable[] holder = new Appendable[1];
                final StringBuilder sb = new StringBuilder();
                final StringBuffer sf = new StringBuffer();
                final boolean[] crash = {false};
                Thread[] ts = new Thread[4];
                for (int i = 0; i < 4; i++) {
                    ts[i] = new Thread(() -> {
                        try {
                            for (int k = 0; k < 20000; k++) { if (kind.equals("StringBuilder")) sb.append('x'); else sf.append('x'); }
                        } catch (RuntimeException ex) { crash[0] = true; }
                    });
                    ts[i].start();
                }
                for (Thread t : ts) t.join();
                int len = kind.equals("StringBuilder") ? sb.length() : sf.length();
                if (crash[0]) crashed++;
                else if (len != 80000) wrong++;
            }
            System.out.printf("%-14s trials with lost updates=%d, trials with exception=%d (of 30)%n", kind, wrong, crashed);
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Java " + System.getProperty("java.version"));
        workload("W1 one short ticket", 50, 1, 20000 / 1, 20, Set.of());   // repeated many times through warm-up
        workload("W2 many tickets", 50, 10_000, 3, 10, Set.of());
        // W3: one very large ticket, doubling n to expose growth order
        for (int n : new int[]{5_000, 10_000, 20_000, 40_000})
            workload("W3 one huge ticket", n, 1, 3, 10, Set.of("String.format"));
        memoryBeforeAfter(20_000);
        threadSafety();
        System.out.println("\n(sink=" + sink + ")");
    }
}
