import java.lang.management.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

/** Q6 Part A (Student B): hostel wallet transfers - deadlock, its four conditions, lock ordering, tryLock alternative. */
public class WalletLab {
    static class Account {
        final int id; long balance; final ReentrantLock lock = new ReentrantLock();
        Account(int id, long b) { this.id = id; balance = b; }
    }

    // ---- version 1: DEADLOCK-PRONE (locks 'from' then 'to') ----
    static void transferUnsafe(Account from, Account to, long amt, long pauseMs) throws InterruptedException {
        synchronized (from) {                                   // lock #1 (held ...)
            if (pauseMs > 0) Thread.sleep(pauseMs);             // widens the window so the deadlock is reliable
            synchronized (to) {                                 // ... while waiting for lock #2  => hold and wait
                if (from.balance >= amt) { from.balance -= amt; to.balance += amt; }
            }
        }
    }
    // ---- version 2: consistent lock ordering (always the smaller id first) ----
    static void transferOrdered(Account from, Account to, long amt, long pauseMs) throws InterruptedException {
        Account first = from.id < to.id ? from : to, second = first == from ? to : from;
        synchronized (first) {
            if (pauseMs > 0) Thread.sleep(pauseMs);
            synchronized (second) {
                if (from.balance >= amt) { from.balance -= amt; to.balance += amt; }
            }
        }
    }
    // ---- version 3 (alternative): tryLock with timeout and back-off, no ordering needed ----
    static long retries;
    static boolean transferTry(Account from, Account to, long amt) throws InterruptedException {
        for (int attempt = 0; attempt < 1000; attempt++) {
            if (from.lock.tryLock(2, TimeUnit.MILLISECONDS)) {
                try {
                    if (to.lock.tryLock(2, TimeUnit.MILLISECONDS)) {
                        try { if (from.balance >= amt) { from.balance -= amt; to.balance += amt; } return true; }
                        finally { to.lock.unlock(); }
                    }
                } finally { from.lock.unlock(); }
            }
            synchronized (WalletLab.class) { retries++; }
            Thread.sleep(ThreadLocalRandom.current().nextInt(1, 4));       // random back-off breaks the symmetry
        }
        return false;
    }

    static String detect() {
        ThreadMXBean m = ManagementFactory.getThreadMXBean();
        long[] ids = m.findDeadlockedThreads();
        if (ids == null) return null;
        StringBuilder sb = new StringBuilder();
        for (ThreadInfo ti : m.getThreadInfo(ids)) sb.append("   ").append(ti.getThreadName()).append(" is BLOCKED on ").append(ti.getLockName()).append(" which is held by ").append(ti.getLockOwnerName()).append('\n');
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Java " + System.getProperty("java.version"));
        System.out.println("\n=== 1. Two transfers that reliably deadlock: T1: A -> B, T2: B -> A (50 ms pause between the two locks) ===");
        Account A = new Account(1, 1000), B = new Account(2, 1000);
        Thread t1 = new Thread(() -> { try { transferUnsafe(A, B, 100, 50); } catch (InterruptedException e) { } }, "T1(A->B)");
        Thread t2 = new Thread(() -> { try { transferUnsafe(B, A, 100, 50); } catch (InterruptedException e) { } }, "T2(B->A)");
        t1.setDaemon(true); t2.setDaemon(true); t1.start(); t2.start();
        t1.join(1000); t2.join(1000);
        System.out.println("after 1 s: T1 alive=" + t1.isAlive() + " (state " + t1.getState() + "), T2 alive=" + t2.isAlive() + " (state " + t2.getState() + ")");
        System.out.println("JVM deadlock detector (ThreadMXBean.findDeadlockedThreads):");
        System.out.print(detect());
        System.out.println("balances unchanged: A=" + A.balance + " B=" + B.balance);

        System.out.println("\n=== 2. Same two transfers with the consistent ordering rule (lock the account with the smaller id first) ===");
        Account C = new Account(1, 1000), D = new Account(2, 1000);
        Thread u1 = new Thread(() -> { try { transferOrdered(C, D, 100, 50); } catch (InterruptedException e) { } }, "T1(C->D)");
        Thread u2 = new Thread(() -> { try { transferOrdered(D, C, 100, 50); } catch (InterruptedException e) { } }, "T2(D->C)");
        u1.setDaemon(true); u2.setDaemon(true); u1.start(); u2.start(); u1.join(2000); u2.join(2000);
        System.out.println("both finished: " + (!u1.isAlive() && !u2.isAlive()) + "; balances C=" + C.balance + " D=" + D.balance + " (sum " + (C.balance + D.balance) + ", expected 2000)");

        System.out.println("\n=== 3. Random transfers among 5 accounts, 8 threads x 2,000 transfers, 20 trials per version (declared deadlocked if not finished in 3 s) ===");
        for (String ver : new String[]{"unsafe (from, then to)", "ordered (smaller id first)", "tryLock + back-off"}) {
            int dead = 0, conserved = 0; double tot = 0; retries = 0;
            for (int trial = 0; trial < 20; trial++) {
                Account[] acc = new Account[5]; for (int i = 0; i < 5; i++) acc[i] = new Account(i, 1000);
                Thread[] th = new Thread[8]; long t0 = System.nanoTime();
                for (int i = 0; i < 8; i++) { final long seed = trial * 100L + i; th[i] = new Thread(() -> {
                    Random r = new Random(seed);
                    try { for (int k = 0; k < 2000; k++) { int f = r.nextInt(5), g = r.nextInt(5); if (f == g) continue;
                        if (ver.startsWith("unsafe")) transferUnsafe(acc[f], acc[g], 10, 0); else if (ver.startsWith("ordered")) transferOrdered(acc[f], acc[g], 10, 0); else transferTry(acc[f], acc[g], 10); }
                    } catch (InterruptedException e) { } });
                    th[i].setDaemon(true); th[i].start(); }
                boolean alive = false; long dl = System.currentTimeMillis() + 3000;
                for (Thread t : th) { t.join(Math.max(1, dl - System.currentTimeMillis())); if (t.isAlive()) alive = true; }
                if (alive) dead++; else { tot += System.nanoTime() - t0; long sum = 0; for (Account x : acc) sum += x.balance; if (sum == 5000) conserved++; }
            }
            int fin = 20 - dead;
            System.out.printf("%-28s deadlocked trials: %2d of 20 | finished trials: %2d, total money conserved in %2d | mean time of finished trials: %s%n", ver, dead, fin, conserved, fin == 0 ? "n/a" : String.format("%.1f ms", tot / fin / 1e6));
            System.out.println("CSV_TRIALS," + ver + "," + dead + "," + fin + "," + conserved + "," + (fin == 0 ? -1 : tot / fin / 1e6) + "," + retries);
        }
    }
}
