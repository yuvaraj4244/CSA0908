import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Q4 alternative concept: ExecutorService with a fixed pool vs creating Thread objects yourself. */
public class PoolBench {
    static long status(String key) throws IOException {
        for (String l : java.nio.file.Files.readAllLines(java.nio.file.Paths.get("/proc/self/status"))) if (l.startsWith(key)) return Long.parseLong(l.replaceAll("\\D+", ""));
        return -1;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Java " + System.getProperty("java.version") + ", cores=" + Runtime.getRuntime().availableProcessors());
        final int n = 40, c = 20;       // 40 scripts, each takes 20 ms of (sleeping) scanner time
        System.out.println("\n=== completion time for n=" + n + " scripts of " + c + " ms each, fixed pool of p threads (mean of 5 runs) ===");
        System.out.printf("%-6s %-16s %-14s %-12s%n", "p", "predicted ceil(n/p)*c", "measured (ms)", "ratio");
        for (int p : new int[]{1, 2, 4, 8, 16, 40}) {
            double tot = 0;
            for (int r = 0; r < 6; r++) {
                ExecutorService ex = Executors.newFixedThreadPool(p);
                long t0 = System.nanoTime();
                List<Future<?>> fs = new ArrayList<>();
                for (int i = 0; i < n; i++) fs.add(ex.submit(() -> { Thread.sleep(c); return null; }));
                for (Future<?> f : fs) f.get();
                long t = System.nanoTime() - t0; ex.shutdown();
                if (r > 0) tot += t;
            }
            double meas = tot / 5 / 1e6; long pred = (long) Math.ceil(n / (double) p) * c;
            System.out.printf("%-6d %-21d %-14.1f %.2f%n", p, pred, meas, meas / pred);
            System.out.println("CSV_TIME," + p + "," + pred + "," + meas);
        }

        System.out.println("\n=== memory: m parked threads (own Thread objects) vs fixed pool of 4 handling m tasks ===");
        System.out.printf("%-8s %-28s %-14s %-14s %-12s%n", "m", "approach", "peak threads", "VmRSS +MB", "VmSize +MB");
        for (int m : new int[]{200, 1000, 4000}) {
            for (int mode = 0; mode < 2; mode++) {
                System.gc(); Thread.sleep(200);
                long rss0 = status("VmRSS"), vs0 = status("VmSize"); int peak;
                if (mode == 0) {
                    CountDownLatch go = new CountDownLatch(1); List<Thread> ts = new ArrayList<>();
                    for (int i = 0; i < m; i++) { Thread t = new Thread(() -> { try { go.await(); } catch (InterruptedException ignored) { } }); t.start(); ts.add(t); }
                    Thread.sleep(300);
                    peak = java.lang.management.ManagementFactory.getThreadMXBean().getThreadCount();
                    long rss = status("VmRSS") - rss0, vs = status("VmSize") - vs0;
                    go.countDown(); for (Thread t : ts) t.join();
                    System.out.printf("%-8d %-28s %-14d %-14.1f %-12.1f%n", m, "m Thread objects", peak, rss / 1024.0, vs / 1024.0);
                    System.out.println("CSV_MEM," + m + ",threads," + peak + "," + rss + "," + vs);
                } else {
                    ExecutorService ex = Executors.newFixedThreadPool(4); CountDownLatch done = new CountDownLatch(m);
                    for (int i = 0; i < m; i++) ex.submit(() -> { try { Thread.sleep(50); } catch (InterruptedException ignored) { } done.countDown(); });
                    Thread.sleep(300);
                    peak = java.lang.management.ManagementFactory.getThreadMXBean().getThreadCount();
                    long rss = status("VmRSS") - rss0, vs = status("VmSize") - vs0;
                    done.await(); ex.shutdown(); ex.awaitTermination(5, TimeUnit.SECONDS);
                    System.out.printf("%-8d %-28s %-14d %-14.1f %-12.1f%n", m, "fixed pool of 4", peak, rss / 1024.0, vs / 1024.0);
                    System.out.println("CSV_MEM," + m + ",pool4," + peak + "," + rss + "," + vs);
                }
            }
        }
    }
}
