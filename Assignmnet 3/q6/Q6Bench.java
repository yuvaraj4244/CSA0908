import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

/** Q6 alternative concept: ArrayBlockingQueue(5) vs wait/notifyAll counter; synchronized lock ordering vs tryLock(timeout). */
public class Q6Bench {
    static class WN { final int[] b = new int[5]; int count, in, out;
        synchronized void put(int t) throws InterruptedException { while (count == 5) wait(); b[in] = t; in = (in + 1) % 5; count++; notifyAll(); }
        synchronized int take() throws InterruptedException { while (count == 0) wait(); int t = b[out]; out = (out + 1) % 5; count--; notifyAll(); return t; } }
    interface Chan { void put(int t) throws InterruptedException; int take() throws InterruptedException; }

    static double pipe(Chan ch, int producers, int consumers, int tokens) throws Exception {
        long[] sums = new long[consumers]; List<Thread> ts = new ArrayList<>();
        for (int i = 0; i < producers; i++) ts.add(new Thread(() -> { try { for (int k = 0; k < tokens; k++) ch.put(k & 7); } catch (InterruptedException e) { } }));
        int each = producers * tokens / consumers;
        for (int i = 0; i < consumers; i++) { final int id = i; ts.add(new Thread(() -> { try { for (int k = 0; k < each; k++) sums[id] += ch.take(); } catch (InterruptedException e) { } })); }
        long t0 = System.nanoTime(); for (Thread t : ts) t.start(); for (Thread t : ts) t.join();
        return (System.nanoTime() - t0) / (double) (producers * tokens);
    }

    public static void main(String[] a) throws Exception {
        System.out.println("Java " + System.getProperty("java.version") + ", cores=" + Runtime.getRuntime().availableProcessors());
        System.out.println("\n=== time per token through a capacity-5 counter (ns/token), 500,000 tokens, mean of 5 runs after 2 warm-ups ===");
        System.out.printf("%-30s %-18s %-18s%n", "design", "1 producer/1 consumer", "1 producer/2 consumers");
        for (String d : new String[]{"wait()/notifyAll() (TokenCounter)", "ArrayBlockingQueue(5)"}) {
            double[] r = new double[2];
            for (int cfg = 0; cfg < 2; cfg++) { double tot = 0;
                for (int rep = 0; rep < 7; rep++) {
                    Chan ch;
                    if (d.startsWith("wait")) { WN w = new WN(); ch = new Chan() { public void put(int t) throws InterruptedException { w.put(t); } public int take() throws InterruptedException { return w.take(); } }; }
                    else { ArrayBlockingQueue<Integer> q = new ArrayBlockingQueue<>(5); ch = new Chan() { public void put(int t) throws InterruptedException { q.put(t); } public int take() throws InterruptedException { return q.take(); } }; }
                    double v = pipe(ch, 1, cfg == 0 ? 1 : 2, 500_000); if (rep >= 2) tot += v; }
                r[cfg] = tot / 5; }
            System.out.printf("%-30s %-18.0f %-18.0f%n", d, r[0], r[1]);
            System.out.println("CSV_PIPE," + d + "," + r[0] + "," + r[1]);
        }
        System.out.println("\n=== time per transfer (ns), 100 accounts, 200,000 transfers per thread, mean of 5 runs ===");
        System.out.printf("%-34s %-16s %-16s%n", "design", "1 thread", "4 threads");
        for (String d : new String[]{"synchronized + lock ordering", "ReentrantLock.tryLock(timeout) + retry"}) {
            double[] r = new double[2];
            for (int cfg = 0; cfg < 2; cfg++) { int threads = cfg == 0 ? 1 : 4; double tot = 0;
                for (int rep = 0; rep < 7; rep++) {
                    WalletLab.Account[] acc = new WalletLab.Account[100]; for (int i = 0; i < 100; i++) acc[i] = new WalletLab.Account(i, 1_000_000);
                    Thread[] th = new Thread[threads]; long t0 = System.nanoTime();
                    for (int i = 0; i < threads; i++) { final long seed = i; th[i] = new Thread(() -> { Random rnd = new Random(seed);
                        try { for (int k = 0; k < 200_000; k++) { int f = rnd.nextInt(100), g = rnd.nextInt(100); if (f == g) continue;
                            if (d.startsWith("sync")) WalletLab.transferOrdered(acc[f], acc[g], 1, 0); else WalletLab.transferTry(acc[f], acc[g], 1); } } catch (InterruptedException e) { } }); th[i].start(); }
                    for (Thread t : th) t.join();
                    if (rep >= 2) tot += (System.nanoTime() - t0) / (200_000.0 * threads) * threads; }
                r[cfg] = tot / 5; }
            System.out.printf("%-34s %-16.0f %-16.0f%n", d, r[0], r[1]);
            System.out.println("CSV_XFER," + d + "," + r[0] + "," + r[1]);
        }
    }
}
