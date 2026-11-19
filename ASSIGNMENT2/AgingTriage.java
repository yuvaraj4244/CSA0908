import java.lang.management.ManagementFactory;
import java.util.*;

/**
 * Q7 - Day-4 design challenge.
 * Chosen solution: Q6 triage queue WITH aging (fair rule: a patient's effective severity drops by one level every T ticks waited).
 *
 * ORIGINAL : effective severity changes with time, so a PriorityQueue's heap invariant is invalid after every tick
 *            => the queue is rebuilt from all waiting patients every tick  (O(n) per tick).
 * REDESIGN : five FIFO ArrayDeques (one per level).  Each queue is ordered by "time of entering that level", so the only
 *            patients that can become eligible for promotion are at the HEAD of a queue.  Promote heads whose level-time
 *            has reached T (O(1) per promotion), admit = O(1), serve = scan 5 heads = O(1).
 * Both must produce exactly the same service order (checked below).
 */
public class AgingTriage {
    static final com.sun.management.ThreadMXBean TMX = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    static long sink;

    static final class P {
        final int id, sev, at;      // id = arrival order, sev = original severity, at = arrival tick
        int level;                  // redesign only: current level
        int entered;                // redesign only: tick when it entered the current level
        P(int id, int sev, int at) { this.id = id; this.sev = sev; this.at = at; this.level = sev; this.entered = at; }
    }

    // ---------------------------- ORIGINAL ----------------------------
    static final class Original {
        final int T; long comparisons; long rebuilds; final List<P> waiting = new ArrayList<>();
        Original(int T) { this.T = T; }
        void admit(P p) { waiting.add(p); }
        P serve(int now) {
            // effective level and the tick at which the patient entered it, as functions of 'now'
            Comparator<P> aged = (x, y) -> {
                comparisons++;
                int kx = Math.min(x.sev - 1, (now - x.at) / T), ky = Math.min(y.sev - 1, (now - y.at) / T);
                int lx = x.sev - kx, ly = y.sev - ky;
                if (lx != ly) return Integer.compare(lx, ly);
                int ex = x.at + kx * T, ey = y.at + ky * T;
                if (ex != ey) return Integer.compare(ex, ey);
                return Integer.compare(x.id, y.id);
            };
            PriorityQueue<P> q = new PriorityQueue<>(Math.max(1, waiting.size()), aged);   // keys changed => rebuild
            q.addAll(waiting); rebuilds++;
            P best = q.poll();
            waiting.remove(best);                                                           // O(n) as well
            return best;
        }
    }

    // ---------------------------- REDESIGN ----------------------------
    static final class Redesign {
        final int T; long promotions, headChecks;
        @SuppressWarnings("unchecked") final ArrayDeque<P>[] q = new ArrayDeque[6];
        final List<P> due = new ArrayList<>();
        Redesign(int T) { this.T = T; for (int i = 1; i <= 5; i++) q[i] = new ArrayDeque<>(); }
        void admit(P p) { q[p.level].addLast(p); }
        void promote(int now) {                       // only heads can be due: each deque is ordered by entry time
            due.clear();
            for (int lv = 5; lv >= 2; lv--) {
                ArrayDeque<P> d = q[lv];
                while (true) { headChecks++; P h = d.peekFirst(); if (h == null || h.entered + T > now) break; due.add(d.pollFirst()); }
            }
            if (due.size() > 1) due.sort(Comparator.comparingInt(p -> p.id));    // same tie rule as the original (by arrival id)
            for (P p : due) { p.level--; p.entered = now; q[p.level].addLast(p); promotions++; }
        }
        P serve() { for (int lv = 1; lv <= 5; lv++) if (!q[lv].isEmpty()) return q[lv].pollFirst(); return null; }
    }

    static P[] preload(int n, long seed) {
        Random r = new Random(seed); P[] a = new P[n];
        for (int i = 0; i < n; i++) a[i] = new P(i, 1 + r.nextInt(5), 0);
        return a;
    }

    /** runs the scenario; returns the served ids */
    static int[] runOriginal(int n, int ticks, int T, long[] stats) {
        Original o = new Original(T); for (P p : preload(n, 1)) o.admit(p);
        Random r = new Random(2); int id = n; int[] out = new int[ticks];
        long a0 = TMX.getThreadAllocatedBytes(Thread.currentThread().getId());
        for (int t = 1; t <= ticks; t++) { o.admit(new P(id++, 1 + r.nextInt(5), t)); out[t - 1] = o.serve(t).id; }
        stats[0] = o.comparisons; stats[1] = TMX.getThreadAllocatedBytes(Thread.currentThread().getId()) - a0; return out;
    }
    static int[] runRedesign(int n, int ticks, int T, long[] stats) {
        Redesign d = new Redesign(T); for (P p : preload(n, 1)) d.admit(p);
        Random r = new Random(2); int id = n; int[] out = new int[ticks];
        long a0 = TMX.getThreadAllocatedBytes(Thread.currentThread().getId());
        for (int t = 1; t <= ticks; t++) { d.promote(t); d.admit(new P(id++, 1 + r.nextInt(5), t)); out[t - 1] = d.serve().id; }
        stats[0] = d.promotions + d.headChecks; stats[1] = TMX.getThreadAllocatedBytes(Thread.currentThread().getId()) - a0; return out;
    }

    public static void main(String[] args) {
        System.out.println("Java " + System.getProperty("java.version"));
        final int TICKS = 500, T = 20;
        System.out.println("Scenario: n patients waiting at tick 0; then " + TICKS + " ticks of (promote/age, admit 1, serve 1); aging step T=" + T + " ticks.");
        System.out.println("Mean of 3 measured runs after 1 warm-up.\n");
        System.out.printf("%-8s | %-11s %14s %16s %14s | %-11s %12s %16s %14s | %-8s %-9s%n",
                "n", "ORIG ms", "ms/tick", "comparisons", "alloc MB", "REDESIGN ms", "ms/tick", "ops (checks+prom)", "alloc MB", "speed-up", "same order");
        for (int n : new int[]{1_000, 5_000, 20_000, 50_000, 100_000}) {
            long[] so = new long[2], sr = new long[2];
            int[] a = runOriginal(n, 50, T, new long[2]); int[] b = runRedesign(n, 50, T, new long[2]);   // warm-up
            double to = 0, tr = 0;
            int[] oa = null, ob = null;
            for (int rep = 0; rep < 3; rep++) {
                long t0 = System.nanoTime(); oa = runOriginal(n, TICKS, T, so); to += System.nanoTime() - t0;
                t0 = System.nanoTime(); ob = runRedesign(n, TICKS, T, sr); tr += System.nanoTime() - t0;
            }
            to /= 3e6; tr /= 3e6;
            boolean same = Arrays.equals(oa, ob);
            System.out.printf("%-8d | %-11.1f %14.3f %16d %14.1f | %-11.3f %12.5f %16d %14.2f | %-8.0f %-9s%n",
                    n, to, to / TICKS, so[0], so[1] / 1048576.0, tr, tr / TICKS, sr[0], sr[1] / 1048576.0, to / tr, same);
            System.out.println("CSV," + n + "," + to + "," + so[0] + "," + so[1] + "," + tr + "," + sr[0] + "," + sr[1] + "," + same);
        }
        // small hand-checkable case: n=8 patients, T=2
        System.out.println("\nSmall check (8 patients, T=2, 6 ticks, no new arrivals): service order of both designs");
        int[] sevs = {5, 3, 5, 1, 4, 5, 2, 3};
        Original o = new Original(2); Redesign d = new Redesign(2);
        for (int i = 0; i < 8; i++) { o.admit(new P(i, sevs[i], 0)); d.admit(new P(i, sevs[i], 0)); }
        StringBuilder so = new StringBuilder(), sd = new StringBuilder();
        for (int t = 1; t <= 8; t++) { so.append(o.serve(t).id).append(' '); d.promote(t); sd.append(d.serve().id).append(' '); }
        System.out.println("original  : " + so + "\nredesigned: " + sd + "   equal=" + so.toString().equals(sd.toString()));
        System.out.println("(sink=" + sink + ")");
    }
}
