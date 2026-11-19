import java.util.*;

/** Q6 - correctness check, growth, alternatives, starvation + aging, deployment decision. Uses Patient from Triage.java. */
public class TriageStudy {
    static long sink;
    static long cmpCount;

    static final Comparator<Patient> RULE = Comparator.comparingInt((Patient p) -> p.severity).thenComparingLong(p -> p.arrival);
    static final Comparator<Patient> COUNTING = (a, b) -> { cmpCount++; return RULE.compare(a, b); };

    // ---------- alternative 1: sorted LinkedList (insert at correct position) ----------
    static void insertSorted(LinkedList<Patient> l, Patient p) {
        ListIterator<Patient> it = l.listIterator();
        while (it.hasNext()) { if (RULE.compare(it.next(), p) > 0) { it.previous(); break; } }
        it.add(p);
    }

    // ---------- alternative 2: five FIFO queues, one per severity ----------
    static class FiveQueues {
        @SuppressWarnings("unchecked") final ArrayDeque<Patient>[] q = new ArrayDeque[6];
        FiveQueues() { for (int i = 1; i <= 5; i++) q[i] = new ArrayDeque<>(); }
        void admit(Patient p) { q[p.severity].addLast(p); }
        Patient next() { for (int i = 1; i <= 5; i++) if (!q[i].isEmpty()) return q[i].pollFirst(); return null; }
    }

    static Patient[] patients(int n, long seed) {
        Random r = new Random(seed);
        Patient[] a = new Patient[n];
        for (int i = 0; i < n; i++) a[i] = new Patient("P" + i, 1 + r.nextInt(5), i);
        return a;
    }

    public static void main(String[] args) {
        System.out.println("Java " + System.getProperty("java.version"));

        // ================= Correctness check (8 patients, hand-derived order) =================
        System.out.println("\n=== Correctness check: 8 patients ===");
        String[] names = {"Ravi", "Meena", "John", "Asha", "Kumar", "Lata", "Divya", "Imran"};
        int[] sev = {3, 1, 3, 5, 1, 2, 2, 4};
        PriorityQueue<Patient> pq = new PriorityQueue<>(RULE);
        for (int i = 0; i < 8; i++) pq.add(new Patient(names[i], sev[i], i));
        System.out.println("Arrival order : Ravi(S3) Meena(S1) John(S3) Asha(S5) Kumar(S1) Lata(S2) Divya(S2) Imran(S4)");
        System.out.println("Hand-derived  : Meena(S1) Kumar(S1) Lata(S2) Divya(S2) Ravi(S3) John(S3) Imran(S4) Asha(S5)");
        System.out.println("println(pq)   : " + pq + "   <- heap array order, NOT treatment order");
        StringBuilder sb = new StringBuilder();
        for (Patient p : pq.stream().sorted(RULE).toList()) sb.append(p).append(' ');
        System.out.println("Waiting list  : " + sb.toString().trim() + "   <- displayed by sorting a COPY (O(n log n)); queue untouched, size=" + pq.size());
        sb.setLength(0);
        Patient p; while ((p = pq.poll()) != null) sb.append(p).append(' ');
        String got = sb.toString().trim();
        System.out.println("poll() order  : " + got);
        System.out.println("matches hand-derived order: " + got.equals("Meena(S1) Kumar(S1) Lata(S2) Divya(S2) Ravi(S3) John(S3) Imran(S4) Asha(S5)"));

        // ================= Growth investigation =================
        System.out.println("\n=== Growth: PriorityQueue admit (add) and next (poll), random severities ===");
        System.out.printf("%-10s %12s %12s %14s %14s %12s%n", "n", "add ns/op", "poll ns/op", "cmp/add", "cmp/poll", "2*log2(n)");
        for (int n : new int[]{1_000, 10_000, 100_000, 1_000_000}) {
            Patient[] arr = patients(n, 11);
            double addT = 0, pollT = 0, cAdd = 0, cPoll = 0;
            int reps = 5;
            for (int rep = 0; rep < reps + 2; rep++) {
                PriorityQueue<Patient> q = new PriorityQueue<>(COUNTING);
                cmpCount = 0; long t0 = System.nanoTime();
                for (Patient x : arr) q.add(x);
                long t1 = System.nanoTime(); long c1 = cmpCount;
                long s = 0; while (!q.isEmpty()) s += q.poll().severity;
                long t2 = System.nanoTime(); long c2 = cmpCount - c1; sink += s;
                if (rep >= 2) { addT += (t1 - t0); pollT += (t2 - t1); cAdd = c1 / (double) n; cPoll = c2 / (double) n; }
            }
            System.out.printf("%-10d %12.1f %12.1f %14.2f %14.2f %12.1f%n", n, addT / reps / n, pollT / reps / n, cAdd, cPoll, 2 * Math.log(n) / Math.log(2));
            System.out.println("CSV," + n + "," + addT / reps / n + "," + pollT / reps / n + "," + cAdd + "," + cPoll);
        }

        // steady state: queue holds n waiting, then 200k (admit + next) pairs
        System.out.println("\n=== Steady state: n waiting, 200,000 x (admit one + serve one), ns per pair ===");
        for (int n : new int[]{1_000, 10_000, 100_000, 1_000_000}) {
            Patient[] arr = patients(n, 12);
            Random r = new Random(99);
            double tot = 0; int reps = 5;
            for (int rep = 0; rep < reps + 2; rep++) {
                PriorityQueue<Patient> q = new PriorityQueue<>(RULE);
                for (Patient x : arr) q.add(x);
                long id = n; long t0 = System.nanoTime();
                for (int i = 0; i < 200_000; i++) { q.add(new Patient("N", 1 + r.nextInt(5), id++)); sink += q.poll().severity; }
                if (rep >= 2) tot += System.nanoTime() - t0;
            }
            System.out.printf("n=%-9d %10.1f ns per (admit+serve)%n", n, tot / reps / 200_000);
            System.out.println("CSV_STEADY," + n + "," + tot / reps / 200_000);
        }

        // ================= Alternatives =================
        System.out.println("\n=== Alternatives: admit n patients then serve all (total ms, mean of 5) ===");
        System.out.printf("%-8s %16s %18s %18s%n", "n", "PriorityQueue", "sorted LinkedList", "5 FIFO queues");
        for (int n : new int[]{5_000, 10_000, 20_000, 40_000}) {
            Patient[] arr = patients(n, 13);
            double a = avg(() -> { PriorityQueue<Patient> q = new PriorityQueue<>(RULE); for (Patient x : arr) q.add(x); while (!q.isEmpty()) sink += q.poll().severity; });
            double b = avg(() -> { LinkedList<Patient> l = new LinkedList<>(); for (Patient x : arr) insertSorted(l, x); while (!l.isEmpty()) sink += l.removeFirst().severity; });
            double c = avg(() -> { FiveQueues f = new FiveQueues(); for (Patient x : arr) f.admit(x); Patient x; while ((x = f.next()) != null) sink += x.severity; });
            // identical serving order check
            PriorityQueue<Patient> q = new PriorityQueue<>(RULE); LinkedList<Patient> l = new LinkedList<>(); FiveQueues f = new FiveQueues();
            for (Patient x : arr) { q.add(x); insertSorted(l, x); f.admit(x); }
            boolean same = true; while (!q.isEmpty()) { Patient x = q.poll(); if (l.removeFirst() != x || f.next() != x) same = false; }
            System.out.printf("%-8d %16.2f %18.2f %18.2f   same service order: %s%n", n, a, b, c, same);
            System.out.println("CSV_ALT," + n + "," + a + "," + b + "," + c);
        }

        // ================= Starvation and aging =================
        System.out.println("\n=== Starvation: one S5 patient at t=0; every tick 2 new patients (S1..S4) arrive but only 1 is served (backlog grows); 5,000 ticks ===");
        Random r = new Random(4);
        PriorityQueue<Patient> plain = new PriorityQueue<>(RULE);
        plain.add(new Patient("Old-S5", 5, 0));
        long id = 2; boolean served = false;
        for (int t = 1; t <= 5_000; t++) { plain.add(new Patient("n", 1 + r.nextInt(4), id++)); plain.add(new Patient("n", 1 + r.nextInt(4), id++)); Patient x = plain.poll(); if (x.name.equals("Old-S5")) { served = true; System.out.println("plain PQ: S5 served at tick " + t); break; } }
        if (!served) System.out.println("plain PQ: S5 patient STILL WAITING after 5,000 ticks (backlog " + plain.size() + " patients)");

        for (int T : new int[]{50, 100, 200}) {
            r = new Random(4); id = 2;
            List<Patient> wait = new ArrayList<>(); wait.add(new Patient("Old-S5", 5, 0)); long servedAt = -1;
            for (int t = 1; t <= 5_000 && servedAt < 0; t++) {
                wait.add(new Patient("n", 1 + r.nextInt(4), id++)); wait.add(new Patient("n", 1 + r.nextInt(4), id++));
                final int now = t, TT = T;
                // aging rule: effective severity = max(1, severity - waited/T); ties by arrival. (arrival id/2 == arrival tick)
                Comparator<Patient> aged = Comparator.comparingInt((Patient x) -> Math.max(1, x.severity - (int) ((now - x.arrival / 2) / TT)))
                        .thenComparingLong(x -> x.arrival);
                PriorityQueue<Patient> q = new PriorityQueue<>(aged);   // rebuilt each tick, because keys change with time
                q.addAll(wait);
                Patient x = q.poll(); wait.remove(x);
                if (x.name.equals("Old-S5")) servedAt = t;
            }
            System.out.println("aging, +1 level every T=" + T + " ticks: S5 patient served at tick " + servedAt + "  (S5 reaches level 1 after " + 4 * T + " ticks)");
        }

        // ================= Deployment: 5,000 patients per day =================
        System.out.println("\n=== Deployment: a day of 5,000 arrivals (random severities), peak backlog 500 ===");
        Patient[] day = patients(5000, 21);
        double ms = avg(() -> { PriorityQueue<Patient> q = new PriorityQueue<>(RULE); int i = 0; for (; i < 500; i++) q.add(day[i]); for (; i < 5000; i++) { q.add(day[i]); sink += q.poll().severity; } while (!q.isEmpty()) sink += q.poll().severity; });
        System.out.printf("whole day processed in %.3f ms  => %.1f ns per patient (an arrival every ~17 s on average)%n", ms, ms * 1e6 / 5000);
        System.out.println("(sink=" + sink + ")");
    }

    static double avg(Runnable r) {
        for (int i = 0; i < 2; i++) r.run();
        long t = 0; for (int i = 0; i < 5; i++) { long t0 = System.nanoTime(); r.run(); t += System.nanoTime() - t0; }
        return t / 5.0 / 1e6;
    }
}
