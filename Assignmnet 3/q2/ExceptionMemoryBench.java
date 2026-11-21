import java.io.*;
import java.lang.management.ManagementFactory;

/** Q2 alternative concept: time and space cost of capturing stack traces and of chaining. */
public class ExceptionMemoryBench {
    static final com.sun.management.ThreadMXBean TMX = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    static long sink;
    static long alloc() { return TMX.getThreadAllocatedBytes(Thread.currentThread().getId()); }

    static class Ex extends Exception { Ex(String m, Throwable c, boolean trace) { super(m, c, true, trace); } }

    static Throwable build(int depth, int chain, boolean trace) {
        if (depth > 0) return build(depth - 1, chain, trace);
        Throwable t = new Ex("root", null, trace);
        for (int i = 1; i < chain; i++) t = new Ex("level" + i, t, trace);
        return t;
    }

    public static void main(String[] a) {
        System.out.println("Java " + System.getProperty("java.version"));
        System.out.println("\n=== creating one exception: ns and bytes allocated (mean of 100,000), by call depth and chain length ===");
        System.out.printf("%-7s %-7s | %-26s | %-26s | %-14s%n", "depth", "chain", "with stack trace", "no stack trace (4-arg ctor)", "bytes per frame");
        for (int depth : new int[]{10, 100, 1000}) for (int chain : new int[]{1, 3}) {
            double[] w = new double[2], n = new double[2];
            for (int mode = 0; mode < 2; mode++) {
                boolean trace = mode == 0; int iters = depth >= 1000 ? 20_000 : 100_000;
                for (int i = 0; i < iters / 2; i++) sink += build(depth, chain, trace).hashCode();
                long a0 = alloc(), t0 = System.nanoTime();
                for (int i = 0; i < iters; i++) sink += build(depth, chain, trace).hashCode();
                double ns = (System.nanoTime() - t0) / (double) iters, by = (alloc() - a0) / (double) iters;
                if (trace) { w[0] = ns; w[1] = by; } else { n[0] = ns; n[1] = by; }
            }
            System.out.printf("%-7d %-7d | %9.0f ns %9.0f B | %9.0f ns %9.0f B | %10.1f%n", depth, chain, w[0], w[1], n[0], n[1], (w[1] - n[1]) / (depth * chain));
            System.out.println("CSV," + depth + "," + chain + "," + w[0] + "," + w[1] + "," + n[0] + "," + n[1]);
        }
        System.out.println("\n=== materialising the trace: getStackTrace() and printStackTrace() size, depth 100 ===");
        for (int chain : new int[]{1, 2, 5}) {
            Throwable t = build(100, chain, true);
            long a0 = alloc();
            int frames = 0; for (Throwable x = t; x != null; x = x.getCause()) frames += x.getStackTrace().length;
            long bytes = alloc() - a0;
            StringWriter sw = new StringWriter(); t.printStackTrace(new PrintWriter(sw));
            System.out.printf("chain=%d: %d StackTraceElements, ~%d bytes allocated to materialise them, printed trace = %d characters%n", chain, frames, bytes, sw.toString().length());
        }
        System.out.println("(sink=" + sink + ")");
    }
}
