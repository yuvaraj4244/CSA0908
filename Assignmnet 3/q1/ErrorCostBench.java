import java.lang.management.ManagementFactory;
import java.util.*;

/**
 * Q1 alternative concept: exceptions vs error codes.
 * (a) AtmCodes - the same withdrawal with NO exceptions: hasNextLong()/hasNextInt() pre-checks and error codes (-1 ... -5).
 * (b) cost of creating+throwing an exception at different call depths vs returning an error code.
 */
public class ErrorCostBench {
    static final com.sun.management.ThreadMXBean TMX = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    static long sink;

    // ---------- (a) error-code version ----------
    static final int OK = 0, BAD_ACCT = -1, BAD_AMOUNT = -2, NO_FILE = -3, NO_ACCOUNT = -4, NO_FUNDS = -5, BAD_CARD = -6;
    static int withdrawCodes(Scanner in, String file, Map<Long, Long> cache) {
        if (!in.hasNextLong()) return BAD_ACCT;          // check 1
        long acct = in.nextLong();
        if (!in.hasNextInt()) return BAD_AMOUNT;         // check 2
        int amt = in.nextInt();
        if (amt <= 0 || amt % 100 != 0) return BAD_AMOUNT; // check 3
        if (!new java.io.File(file).exists()) return NO_FILE; // check 4  (racy: file can vanish after this test)
        if (cache == null) return BAD_CARD;              // check 5 (loading/parsing would need its own error codes)
        Long b = cache.get(acct);
        if (b == null) return NO_ACCOUNT;                // check 6
        if (amt > b) return NO_FUNDS;                    // check 7
        return OK;
    }

    // ---------- (b) cost of failing ----------
    static class CheapEx extends Exception { CheapEx() { super("x", null, false, false); } }   // no stack trace, no suppression

    static int failWithException(int depth) throws Exception { if (depth == 0) throw new Exception("fail"); return failWithException(depth - 1) + 1; }
    static int failWithCheap(int depth) throws CheapEx { if (depth == 0) throw new CheapEx(); return failWithCheap(depth - 1) + 1; }
    static int failWithCode(int depth) { if (depth == 0) return -1; int r = failWithCode(depth - 1); return r < 0 ? r : r + 1; }

    interface Fail { int run(int d) throws Exception; }
    static double[] time(Fail f, int depth, int iters) {
        for (int i = 0; i < iters / 2; i++) try { sink += f.run(depth); } catch (Exception e) { sink++; }
        long a0 = TMX.getThreadAllocatedBytes(Thread.currentThread().getId());
        long t0 = System.nanoTime();
        for (int i = 0; i < iters; i++) try { sink += f.run(depth); } catch (Exception e) { sink++; }
        long t = System.nanoTime() - t0;
        long a = TMX.getThreadAllocatedBytes(Thread.currentThread().getId()) - a0;
        return new double[]{t / (double) iters, a / (double) iters};
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Java " + System.getProperty("java.version"));
        // correctness of the error-code version on the same test inputs as AtmTests
        Map<Long, Long> cache = Map.of(1001L, 2500L, 1002L, 100L, 1003L, 0L);
        String[][] cases = {{"1001 500", "cards/accounts.txt"}, {"abcd 500", "cards/accounts.txt"}, {"1001 five", "cards/accounts.txt"},
                {"1001 -200", "cards/accounts.txt"}, {"1002 500", "cards/accounts.txt"}, {"1001 500", "cards/NO_SUCH_FILE.txt"}, {"9999 500", "cards/accounts.txt"}, {"", "cards/accounts.txt"}};
        System.out.println("=== error-code version on the same inputs ===");
        for (String[] c : cases) System.out.printf("input='%-10s' file=%-26s -> code %d%n", c[0], c[1], withdrawCodes(new Scanner(c[0]), c[1], cache));

        System.out.println("\n=== cost of one FAILED call (ns per call, allocated bytes per call); mean of 200,000 calls after warm-up ===");
        System.out.printf("%-8s | %-28s | %-28s | %-28s | %-18s%n", "depth", "new Exception (stack trace)", "exception w/o stack trace", "error code (return -1)", "slowdown ex/code");
        for (int depth : new int[]{1, 10, 50, 200}) {
            double[] ex = time(ErrorCostBench::failWithException, depth, 200_000);
            double[] ch = time(ErrorCostBench::failWithCheap, depth, 200_000);
            double[] cd = time(ErrorCostBench::failWithCode, depth, 200_000);
            System.out.printf("%-8d | %10.0f ns %10.0f B | %10.0f ns %10.0f B | %10.1f ns %10.0f B | %10.0fx%n", depth, ex[0], ex[1], ch[0], ch[1], cd[0], cd[1], ex[0] / cd[0]);
            System.out.println("CSV," + depth + "," + ex[0] + "," + ex[1] + "," + ch[0] + "," + ch[1] + "," + cd[0] + "," + cd[1]);
        }
        // the success path: try/catch costs nothing when nothing is thrown
        long t0 = System.nanoTime(); int s = 0;
        for (int i = 0; i < 100_000_000; i++) { try { s += i & 3; } catch (RuntimeException e) { s--; } }
        long t1 = System.nanoTime(); for (int i = 0; i < 100_000_000; i++) { s += i & 3; } long t2 = System.nanoTime();
        System.out.printf("%nSuccess path (nothing thrown), 100M iterations: with try/catch %.1f ms, without %.1f ms (sink %d)%n", (t1 - t0) / 1e6, (t2 - t1) / 1e6, s);
        System.out.println("(sink=" + sink + ")");
    }
}
