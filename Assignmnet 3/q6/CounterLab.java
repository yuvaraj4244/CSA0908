import java.util.*;

/** Q6 Part B lab (Student A): size tracing, while vs if, notify vs notifyAll. */
public class CounterLab {
    static class Counter {
        final int[] buffer; int count, in, out;
        final boolean useWhile, useNotifyAll; int minSeen = 0, maxSeen = 0; final List<Integer> trace = new ArrayList<>();
        Counter(int cap, boolean useWhile, boolean useNotifyAll) { buffer = new int[cap]; this.useWhile = useWhile; this.useNotifyAll = useNotifyAll; }
        void wake() { if (useNotifyAll) notifyAll(); else notify(); }
        void record() { minSeen = Math.min(minSeen, count); maxSeen = Math.max(maxSeen, count); if (trace.size() < 4000) trace.add(count); }
        synchronized void put(int t) throws InterruptedException {
            if (useWhile) { while (count == buffer.length) wait(); } else { if (count == buffer.length) wait(); }
            buffer[in] = t; in = (in + 1) % buffer.length; count++; record(); wake();
        }
        synchronized int take() throws InterruptedException {
            if (useWhile) { while (count == 0) wait(); } else { if (count == 0) wait(); }
            int t = buffer[out]; out = (out + 1) % buffer.length; count--; record(); wake();
            return t;
        }
    }

    /** one run: p producers each put 'tokens' tokens 1..tokens; c consumers each take (p*tokens/c). returns {sum, hung?1:0, min, max} */
    static long[] run(int cap, boolean w, boolean na, int p, int c, int tokens, long timeoutMs) throws Exception {
        Counter ctr = new Counter(cap, w, na);
        long[] sums = new long[c];
        List<Thread> all = new ArrayList<>();
        for (int i = 0; i < p; i++) { Thread t = new Thread(() -> { try { for (int k = 1; k <= tokens; k++) ctr.put(k); } catch (InterruptedException e) { } }); t.setDaemon(true); all.add(t); }
        int each = p * tokens / c;
        for (int i = 0; i < c; i++) { final int id = i; Thread t = new Thread(() -> { try { for (int k = 0; k < each; k++) sums[id] += ctr.take(); } catch (InterruptedException e) { } }); t.setDaemon(true); all.add(t); }
        for (Thread t : all) t.start();
        long deadline = System.currentTimeMillis() + timeoutMs; boolean hung = false;
        for (Thread t : all) { t.join(Math.max(1, deadline - System.currentTimeMillis())); if (t.isAlive()) hung = true; }
        long sum = 0; for (long s : sums) sum += s;
        synchronized (ctr) { return new long[]{sum, hung ? 1 : 0, ctr.minSeen, ctr.maxSeen}; }
    }

    public static void main(String[] a) throws Exception {
        System.out.println("Java " + System.getProperty("java.version"));
        // ---- 1. counter size over time, assignment configuration ----
        Counter c = null;
        {
            Counter ctr = new Counter(5, true, true);
            Thread cook = new Thread(() -> { try { for (int t = 1; t <= 20; t++) ctr.put(t); } catch (InterruptedException e) { } });
            Thread[] st = new Thread[2]; for (int i = 0; i < 2; i++) { st[i] = new Thread(() -> { try { for (int k = 0; k < 10; k++) ctr.take(); } catch (InterruptedException e) { } }); }
            cook.start(); st[0].start(); st[1].start(); cook.join(); st[0].join(); st[1].join();
            System.out.println("\n=== 1. Counter size after each of the 40 operations (1 cook, 2 students, capacity 5), one run ===");
            System.out.println(ctr.trace);
            System.out.println("min=" + ctr.minSeen + " max=" + ctr.maxSeen);
        }
        System.out.println("\n=== 2. Same check over many runs: size always within [0, capacity]? (while + notifyAll, 1 cook x 20 tokens, 2 students x 10, 500 runs) ===");
        long min = 0, max = 0, wrong = 0, hung = 0;
        for (int r = 0; r < 500; r++) { long[] x = run(5, true, true, 1, 2, 20, 2000); min = Math.min(min, x[2]); max = Math.max(max, x[3]); if (x[0] != 210) wrong++; if (x[1] == 1) hung++; }
        System.out.println("smallest size ever seen = " + min + ", largest = " + max + ", wrong sums = " + wrong + ", hung runs = " + hung + "  (500 runs)");

        System.out.println("\n=== 3. Variants (1 cook, 2 students, capacity 5; 500 runs each; run declared hung after 200 ms) ===");
        System.out.printf("%-28s %-12s %-12s %-12s %-22s%n", "variant", "correct sum", "wrong sum", "hung", "size range seen");
        String[] names = {"while + notifyAll", "while + notify", "if + notifyAll", "if + notify"};
        boolean[][] cfg = {{true, true}, {true, false}, {false, true}, {false, false}};
        for (int v = 0; v < 4; v++) {
            int ok = 0, bad = 0, h = 0; long mn = 0, mx = 0;
            for (int r = 0; r < 500; r++) { long[] x = run(5, cfg[v][0], cfg[v][1], 1, 2, 20, 200); mn = Math.min(mn, x[2]); mx = Math.max(mx, x[3]); if (x[1] == 1) h++; else if (x[0] == 210) ok++; else bad++; }
            System.out.printf("%-28s %-12d %-12d %-12d [%d, %d]%n", names[v], ok, bad, h, mn, mx);
            System.out.println("CSV_VARIANT," + names[v] + "," + ok + "," + bad + "," + h + "," + mn + "," + mx);
        }

        System.out.println("\n=== 4. notify() with SEVERAL producers AND several consumers (2 cooks x 20 tokens, 2 students x 20; 300 runs, hung after 200 ms) ===");
        System.out.printf("%-12s %-28s %-12s %-12s %-12s%n", "capacity", "variant", "correct", "wrong", "hung");
        for (int cap : new int[]{1, 5}) for (int v : new int[]{0, 1}) {
            int ok = 0, bad = 0, h = 0;
            for (int r = 0; r < 300; r++) { long[] x = run(cap, cfg[v][0], cfg[v][1], 2, 2, 20, 200); if (x[1] == 1) h++; else if (x[0] == 420) ok++; else bad++; }
            System.out.printf("%-12d %-28s %-12d %-12d %-12d%n", cap, names[v], ok, bad, h);
            System.out.println("CSV_NOTIFY," + cap + "," + names[v] + "," + ok + "," + bad + "," + h);
        }
    }
}
