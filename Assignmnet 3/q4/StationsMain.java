import java.util.*;

/** Q4 shared work + Student A/B evidence: start/sleep/join/isAlive, run() vs start(), 15 runs of the finishing order. */
public class StationsMain {
    static final int SCRIPTS = 15, SLEEP_MS = 4;

    public static void main(String[] args) throws Exception {
        System.out.println("Java " + System.getProperty("java.version") + ", cores=" + Runtime.getRuntime().availableProcessors());

        System.out.println("\n=== 1. Extending Thread: states and join() ===");
        StationThread[] st = new StationThread[4];
        for (int i = 0; i < 4; i++) st[i] = new StationThread("Station-" + (i + 1), SCRIPTS, SLEEP_MS);
        System.out.println("before start(): state=" + st[0].getState() + " isAlive=" + st[0].isAlive());
        for (StationThread t : st) t.start();
        Thread.sleep(10);
        System.out.println("10 ms later   : state=" + st[0].getState() + " isAlive=" + st[0].isAlive() + "   (sleeping inside work(): TIMED_WAITING)");
        for (StationThread t : st) t.join();                       // main WAITS here
        System.out.println("after join()  : state=" + st[0].getState() + " isAlive=" + st[0].isAlive());
        int total = 0; for (StationThread t : st) total += t.scanned();
        System.out.println("Total scripts scanned: " + total + " (expected " + 4 * SCRIPTS + ")  finish order: " + Scan.finishOrder);

        System.out.println("\n=== 2. Implementing Runnable (wrapped in new Thread) ===");
        Scan.finishOrder.clear();
        StationTask[] tasks = new StationTask[4]; Thread[] ts = new Thread[4];
        for (int i = 0; i < 4; i++) { tasks[i] = new StationTask("Station-" + (i + 1), SCRIPTS, SLEEP_MS); ts[i] = new Thread(tasks[i], "Station-" + (i + 1)); ts[i].start(); }
        for (Thread t : ts) t.join();
        total = 0; for (StationTask t : tasks) total += t.scanned;
        System.out.println("Total scripts scanned: " + total + " (expected " + 4 * SCRIPTS + ")  finish order: " + Scan.finishOrder);

        System.out.println("\n=== 3. run() instead of start(): no new thread is created ===");
        Scan.finishOrder.clear();
        Thread probe = new Thread(() -> System.out.println("   body executed by thread: " + Thread.currentThread().getName()), "Station-X");
        System.out.println("calling probe.run():");  probe.run();
        System.out.println("calling probe.start():"); probe.start(); probe.join();
        long t0 = System.nanoTime();
        StationThread[] r = new StationThread[4];
        for (int i = 0; i < 4; i++) { r[i] = new StationThread("Station-" + (i + 1), SCRIPTS, SLEEP_MS); r[i].run(); }   // WRONG: sequential, in main
        long seq = System.nanoTime() - t0;
        t0 = System.nanoTime();
        StationThread[] p = new StationThread[4];
        for (int i = 0; i < 4; i++) { p[i] = new StationThread("Station-" + (i + 1), SCRIPTS, SLEEP_MS); p[i].start(); }
        for (StationThread t : p) t.join();
        long par = System.nanoTime() - t0;
        System.out.printf("4 stations via run():   %.0f ms (sequential, all in main; started? state of Station-1 = %s)%n", seq / 1e6, r[0].getState());
        System.out.printf("4 stations via start(): %.0f ms (concurrent)%n", par / 1e6);

        System.out.println("\n=== 4. Order in which the stations finish: 15 runs (equal batches of " + SCRIPTS + " scripts) ===");
        Map<String, Integer> firstCount = new TreeMap<>(); Set<String> distinct = new LinkedHashSet<>();
        for (int run = 1; run <= 15; run++) {
            Scan.finishOrder.clear();
            Thread[] th = new Thread[4];
            for (int i = 0; i < 4; i++) { th[i] = new Thread(new StationTask("S" + (i + 1), SCRIPTS, SLEEP_MS)); th[i].start(); }
            for (Thread t : th) t.join();
            String order = String.join(" < ", Scan.finishOrder);
            System.out.printf("run %2d: %s%n", run, order);
            distinct.add(order); firstCount.merge(Scan.finishOrder.peek(), 1, Integer::sum);
        }
        System.out.println("distinct finishing orders seen in 15 runs: " + distinct.size() + "; first finisher counts: " + firstCount);
    }
}
