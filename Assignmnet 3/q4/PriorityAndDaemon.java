import java.util.*;

/** Q4 Student B: thread priorities (CPU-bound stations) and a daemon thread. usage: java PriorityAndDaemon [priority|daemon|nodaemon] */
public class PriorityAndDaemon {
    static volatile double sink;
    static long cpuWork(long iters) { double x = 0; for (long k = 0; k < iters; k++) x += Math.sqrt(k); sink = x; return iters; }

    public static void main(String[] a) throws Exception {
        String mode = a.length > 0 ? a[0] : "priority";
        if (mode.equals("priority")) {
            System.out.println("CPU-bound stations (no sleeping), 4 threads on " + Runtime.getRuntime().availableProcessors() + " cores, 20 runs each");
            for (boolean prio : new boolean[]{false, true}) {
                Map<String, Integer> first = new TreeMap<>(); long sumFinishMax = 0, sumFinishMin = 0;
                for (int run = 0; run < 20; run++) {
                    Scan.finishOrder.clear();
                    long[] done = new long[4]; long start = System.nanoTime();
                    Thread[] th = new Thread[4];
                    for (int i = 0; i < 4; i++) {
                        final int id = i;
                        th[i] = new Thread(() -> { cpuWork(120_000_000L); done[id] = System.nanoTime() - start; Scan.finishOrder.add("S" + (id + 1)); }, "S" + (i + 1));
                        if (prio) th[i].setPriority(i == 0 ? Thread.MAX_PRIORITY : i == 3 ? Thread.MIN_PRIORITY : Thread.NORM_PRIORITY);
                    }
                    for (Thread t : th) t.start();
                    for (Thread t : th) t.join();
                    first.merge(Scan.finishOrder.peek(), 1, Integer::sum);
                    sumFinishMax += done[0]; sumFinishMin += done[3];
                }
                System.out.printf("%-38s first finisher counts over 20 runs: %-28s mean finish S1=%.0f ms, S4=%.0f ms%n",
                        prio ? "S1=MAX(10), S2,S3=NORM(5), S4=MIN(1):" : "all NORM priority (5):", first, sumFinishMax / 20 / 1e6, sumFinishMin / 20 / 1e6);
            }
        } else {
            boolean daemon = mode.equals("daemon");
            StationThread s = new StationThread("Station-D", 40, 25);   // needs about 1 second
            s.setDaemon(daemon);
            s.start();
            System.out.println("main: " + (daemon ? "Station-D is a DAEMON" : "Station-D is a normal (user) thread") + "; main() ends now, isAlive=" + s.isAlive());
            // main returns here without join()
            Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("JVM exiting: finished stations = " + Scan.finishOrder + " (scanned=" + s.scanned() + ")")));
        }
    }
}
