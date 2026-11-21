import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

/** Q5 lab: reproduce the race reliably, verify the fixes, compare strategies, static synchronization, 20-screen decision. */
public class BookingLab {
    static volatile double sink;
    static void spin(int n) { double x = 0; for (int k = 0; k < n; k++) x += Math.sqrt(k); sink = x; }

    /** one screen; wins[s] counts how many customers were TOLD "you got seat s" (must never exceed 1) */
    static abstract class Screen {
        final boolean[] booked; final AtomicIntegerArray wins; int bookingsMade;      // bookingsMade deliberately a plain int, like the assignment
        final int work;   // simulated non-critical work per booking (pricing, QR code, email) in loop iterations
        Screen(int seats, int work) { booked = new boolean[seats]; wins = new AtomicIntegerArray(seats); this.work = work; }
        abstract boolean book(int seat);
        int doubleBookings() { int d = 0; for (int i = 0; i < wins.length(); i++) d += Math.max(0, wins.get(i) - 1); return d; }
        int distinctSold() { int c = 0; for (int i = 0; i < wins.length(); i++) if (wins.get(i) > 0) c++; return c; }
    }
    static class Unsync extends Screen {
        final boolean widen;
        Unsync(int s, int w, boolean widen) { super(s, w); this.widen = widen; }
        boolean book(int seat) {
            spin(work);
            if (!booked[seat]) { if (widen) Thread.yield(); booked[seat] = true; bookingsMade++; wins.incrementAndGet(seat); return true; }
            return false;
        }
    }
    static class SyncMethod extends Screen {            // intrinsic lock = this (the whole method is locked, including the non-critical work)
        SyncMethod(int s, int w) { super(s, w); }
        synchronized boolean book(int seat) {
            spin(work);
            if (!booked[seat]) { Thread.yield(); booked[seat] = true; bookingsMade++; wins.incrementAndGet(seat); return true; }
            return false;
        }
    }
    static class SyncBlock extends Screen {             // only the check-then-act is locked
        SyncBlock(int s, int w) { super(s, w); }
        boolean book(int seat) {
            spin(work);                                  // outside the lock
            synchronized (this) {                        // critical section: check + act
                if (!booked[seat]) { Thread.yield(); booked[seat] = true; bookingsMade++; wins.incrementAndGet(seat); return true; }
            }
            return false;
        }
    }
    static class Atomic extends Screen {
        final AtomicBoolean[] a;
        Atomic(int s, int w) { super(s, w); a = new AtomicBoolean[s]; for (int i = 0; i < s; i++) a[i] = new AtomicBoolean(); }
        final AtomicInteger made = new AtomicInteger();
        boolean book(int seat) { spin(work); if (a[seat].compareAndSet(false, true)) { Thread.yield(); made.incrementAndGet(); wins.incrementAndGet(seat); return true; } return false; }
    }
    static class Locked extends Screen {
        final ReentrantLock lock = new ReentrantLock();
        Locked(int s, int w) { super(s, w); }
        boolean book(int seat) {
            spin(work);
            lock.lock();
            try { if (!booked[seat]) { Thread.yield(); booked[seat] = true; bookingsMade++; wins.incrementAndGet(seat); return true; } return false; }
            finally { lock.unlock(); }
        }
    }
    static class Chm extends Screen {
        final ConcurrentHashMap<Integer, Boolean> map = new ConcurrentHashMap<>();
        Chm(int s, int w) { super(s, w); }
        boolean book(int seat) { spin(work); if (map.putIfAbsent(seat, Boolean.TRUE) == null) { Thread.yield(); wins.incrementAndGet(seat); return true; } return false; }
    }

    static Screen make(String kind, int seats, int work) {
        return switch (kind) {
            case "unsynchronized" -> new Unsync(seats, work, true);
            case "synchronized method" -> new SyncMethod(seats, work);
            case "synchronized block" -> new SyncBlock(seats, work);
            case "AtomicBoolean[] CAS" -> new Atomic(seats, work);
            case "ReentrantLock" -> new Locked(seats, work);
            default -> new Chm(seats, work);
        };
    }
    static final String[] KINDS = {"unsynchronized", "synchronized method", "synchronized block", "AtomicBoolean[] CAS", "ReentrantLock", "ConcurrentHashMap"};

    /** releases `threads` threads at the same instant; thread i books seat (i % seats) */
    static int[] raceOnce(Screen s, int threads, int seats) throws Exception {
        CountDownLatch ready = new CountDownLatch(threads), go = new CountDownLatch(1);
        Thread[] t = new Thread[threads];
        for (int i = 0; i < threads; i++) { final int seat = i % seats; t[i] = new Thread(() -> { ready.countDown(); try { go.await(); } catch (InterruptedException e) { } s.book(seat); }); t[i].start(); }
        ready.await(); go.countDown();
        for (Thread x : t) x.join();
        return new int[]{s.doubleBookings(), s.distinctSold()};
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Java " + System.getProperty("java.version") + ", cores=" + Runtime.getRuntime().availableProcessors());

        // ---------- A: reproduce reliably ----------
        System.out.println("\n=== A. Reliable reproduction: 100 threads released together by a CountDownLatch, 10 seats (10 users per seat), 200 trials ===");
        System.out.printf("%-22s %-22s %-24s %-22s %-18s%n", "version", "trials with a double booking", "total double bookings", "max 'seats sold' (>10?)", "sold<10 (lost update)");
        for (String k : KINDS) {
            int bad = 0, totalDouble = 0, maxSold = 0, lost = 0;
            for (int trial = 0; trial < 200; trial++) {
                Screen s = make(k, 10, 0);
                int[] r = raceOnce(s, 100, 10);
                if (r[0] > 0) bad++;
                totalDouble += r[0];
                int sold = s instanceof Atomic a ? a.made.get() : s instanceof Chm ? s.distinctSold() : s.bookingsMade;
                maxSold = Math.max(maxSold, sold); if (sold < 10) lost++;
            }
            System.out.printf("%-22s %-22d %-24d %-22d %-18d%n", k, bad, totalDouble, maxSold, lost);
            System.out.println("CSV_REPRO," + k + "," + bad + "," + totalDouble + "," + maxSold);
        }

        // ---------- B: time for 10,000 bookings ----------
        System.out.println("\n=== B. Time to process 10,000 booking attempts (2,000 seats, 8 threads), mean of 7 runs after 3 warm-ups ===");
        for (int work : new int[]{0, 3000}) {
            System.out.println("-- non-critical work per booking = " + (work == 0 ? "none" : work + " iterations (pricing/QR code, outside the critical section if the version allows)"));
            System.out.printf("%-22s %12s %14s %16s%n", "version", "total ms", "ns / booking", "double bookings");
            for (String k : KINDS) {
                if (k.equals("unsynchronized")) continue;
                double tot = 0; int dbl = 0;
                for (int rep = 0; rep < 10; rep++) {
                    Screen s = make(k, 2000, work);
                    int[] seats = new Random(rep).ints(10_000, 0, 2000).toArray();
                    Thread[] t = new Thread[8]; CountDownLatch go = new CountDownLatch(1);
                    for (int i = 0; i < 8; i++) { final int id = i; t[i] = new Thread(() -> { try { go.await(); } catch (InterruptedException e) { } for (int q = id; q < 10_000; q += 8) s.book(seats[q]); }); t[i].start(); }
                    long t0 = System.nanoTime(); go.countDown(); for (Thread x : t) x.join(); long dt = System.nanoTime() - t0;
                    if (rep >= 3) tot += dt; dbl += s.doubleBookings();
                }
                System.out.printf("%-22s %12.2f %14.0f %16d%n", k, tot / 7 / 1e6, tot / 7 / 10_000, dbl);
                System.out.println("CSV_TIME," + work + "," + k + "," + tot / 7 / 1e6 + "," + dbl);
            }
        }

        // ---------- C: static synchronization ----------
        System.out.println("\n=== C. Static state shared by ALL screens: instance-synchronized vs static-synchronized vs AtomicLong ===");
        for (String mode : new String[]{"instance synchronized (locks 'this')", "static synchronized (locks the Class)", "AtomicLong"}) {
            int lostRuns = 0; long total = 0;
            for (int trial = 0; trial < 20; trial++) {
                Totals.reset();
                Thread[] t = new Thread[20];                         // 20 screens, one thread each, 50,000 sales each
                for (int i = 0; i < 20; i++) { final Totals screen = new Totals(); t[i] = new Thread(() -> { for (int k = 0; k < 50_000; k++) { if (mode.startsWith("instance")) screen.sellInstance(); else if (mode.startsWith("static")) Totals.sellStatic(); else Totals.sellAtomic(); } }); t[i].start(); }
                for (Thread x : t) x.join();
                long got = mode.equals("AtomicLong") ? Totals.atomic.get() : Totals.total;
                total = got; if (got != 20L * 50_000) lostRuns++;
            }
            System.out.printf("%-40s expected 1,000,000; last run total=%,d; runs with lost updates: %d of 20%n", mode, total, lostRuns);
        }

        // ---------- D: 20 screens decision ----------
        System.out.println("\n=== D. Cinema with 20 screens: 20 client threads (one per screen), 200 bookings each, 50 microsecond simulated payment/DB write per booking ===");
        System.out.printf("%-48s %12s %16s%n", "strategy", "total ms", "bookings/second");
        for (String strat : new String[]{"one GLOBAL lock for the whole cinema", "one lock PER SCREEN (synchronized block)", "per-seat CAS (payment after winning the seat)"}) {
            double tot = 0;
            for (int rep = 0; rep < 5; rep++) {
                Object global = new Object(); Object[] locks = new Object[20]; boolean[][] seats = new boolean[20][400]; AtomicBoolean[][] cas = new AtomicBoolean[20][400];
                for (int i = 0; i < 20; i++) { locks[i] = new Object(); for (int j = 0; j < 400; j++) cas[i][j] = new AtomicBoolean(); }
                Thread[] t = new Thread[20]; CountDownLatch go = new CountDownLatch(1);
                for (int i = 0; i < 20; i++) { final int sc = i; t[i] = new Thread(() -> {
                    try { go.await(); } catch (InterruptedException e) { }
                    for (int b = 0; b < 200; b++) {
                        int seat = b;   // distinct seats per screen
                        if (strat.startsWith("one GLOBAL")) { synchronized (global) { if (!seats[sc][seat]) { seats[sc][seat] = true; LockSupport.parkNanos(50_000); } } }
                        else if (strat.startsWith("one lock PER")) { synchronized (locks[sc]) { if (!seats[sc][seat]) { seats[sc][seat] = true; LockSupport.parkNanos(50_000); } } }
                        else { if (cas[sc][seat].compareAndSet(false, true)) LockSupport.parkNanos(50_000); }
                    } }); t[i].start(); }
                long t0 = System.nanoTime(); go.countDown(); for (Thread x : t) x.join(); long dt = System.nanoTime() - t0; if (rep > 0) tot += dt;
            }
            System.out.printf("%-48s %12.1f %16.0f%n", strat, tot / 4 / 1e6, 4000 / (tot / 4 / 1e9));
            System.out.println("CSV_SCREENS," + strat + "," + tot / 4 / 1e6);
        }
    }
}

class Totals {
    static long total; static final java.util.concurrent.atomic.AtomicLong atomic = new java.util.concurrent.atomic.AtomicLong();
    static void reset() { total = 0; atomic.set(0); }
    synchronized void sellInstance() { total++; }            // locks THIS screen only: two screens do not exclude each other
    static synchronized void sellStatic() { total++; }       // locks Totals.class: ONE lock shared by every screen
    static void sellAtomic() { atomic.incrementAndGet(); }
}
