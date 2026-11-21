import java.nio.file.*;
import java.util.*;

/** Q7 Stage 3: verify on a hand-checked set, fault injection, then pool-size measurements. usage: java Q7Main handset | bench <dataDir> */
public class Q7Main {
    public static void main(String[] a) throws Exception {
        System.out.println("Java " + System.getProperty("java.version") + ", cores=" + Runtime.getRuntime().availableProcessors());
        if (a[0].equals("handset")) handset(); else bench(Paths.get(a[1]));
    }

    static void handset() throws Exception {
        Path dir = Paths.get("handset");
        List<String> ids = Files.readAllLines(dir.resolve("manifest.txt"));
        System.out.println("\n=== Hand-checked set: 12 ids. Worked out BY HAND before running: ===");
        System.out.println("H01..H08 valid: totals 100, 50, 40, 30, 50, 55, 0, 90  -> sum = 415; passed (>= 40): H01,H02,H03,H05,H06,H08 = 6");
        System.out.println("H09 corrupt (non-numeric score), H10 corrupt (truncated)  -> corrupt = 2");
        System.out.println("H11, H12 listed but no file -> missing = 2          => ok=8, corrupt=2, missing=2, 8+2+2=12");
        for (int threads : new int[]{1, 4}) {
            Summary s = ScoreProcessor.run(dir, ids, threads, 0, null, 5000);
            boolean pass = s.count(Status.OK) == 8 && s.count(Status.CORRUPT) == 2 && s.count(Status.MISSING) == 2 && s.scoreSum() == 415 && s.passed() == 6 && s.consistent();
            System.out.println("\n--- pool of " + threads + " thread(s) ---");
            System.out.print(s.text());
            System.out.println("MATCHES HAND-CHECKED EXPECTATION: " + (pass ? "PASS" : "FAIL"));
        }
        System.out.println("\n=== Fault injection: H05 throws IllegalStateException (bug), H06 hangs 2 s with a 300 ms limit ===");
        Summary s = ScoreProcessor.run(dir, ids, 4, 0, id -> { if (id.equals("H05")) return true; if (id.equals("H06")) { try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("interrupted"); } } return false; }, 300);
        System.out.print(s.text());
        System.out.println("Expected: ok=6 (H05, H06 no longer OK), corrupt=2, missing=2, unexpected+timeout=2, and the run still completed with a consistent summary: " + s.consistent());
    }

    static void bench(Path dir) throws Exception {
        List<String> ids = Files.readAllLines(dir.resolve("manifest.txt"));
        String[] exp = Files.readString(dir.resolve("expected_summary.txt")).split("\n");
        long expOk = Long.parseLong(exp[0].substring(3)), expCorrupt = Long.parseLong(exp[1].substring(8)), expMissing = Long.parseLong(exp[2].substring(8)), expSum = Long.parseLong(exp[3].substring(4)), expPassed = Long.parseLong(exp[4].substring(7));
        System.out.println("\n=== " + ids.size() + " files: expected ok=" + expOk + " corrupt=" + expCorrupt + " missing=" + expMissing + " sum=" + expSum + " passed=" + expPassed + " ===");
        long[] latencies = {0, 500_000};     // real (cached local files) and simulated slow storage (0.5 ms per file)
        for (long lat : latencies) {
            System.out.println("\n-- storage: " + (lat == 0 ? "local files, OS page cache (real)" : "simulated 0.5 ms I/O latency per file") + "; mean of 5 runs after 1 warm-up");
            System.out.printf("%-8s %-12s %-10s %-10s %-10s %s%n", "threads", "time (ms)", "speed-up", "min (ms)", "max (ms)", "summary correct?");
            double base = 0;
            for (int threads : new int[]{1, 2, 4, 8, 16}) {
                ScoreProcessor.run(dir, ids, threads, lat, null, 60_000);      // warm-up
                double tot = 0, min = 1e18, max = 0; boolean ok = true;
                for (int r = 0; r < 5; r++) {
                    Summary s = ScoreProcessor.run(dir, ids, threads, lat, null, 60_000);
                    tot += s.millis; min = Math.min(min, s.millis); max = Math.max(max, s.millis);
                    ok &= s.count(Status.OK) == expOk && s.count(Status.CORRUPT) == expCorrupt && s.count(Status.MISSING) == expMissing && s.scoreSum() == expSum && s.passed() == expPassed && s.consistent();
                }
                double mean = tot / 5; if (threads == 1) base = mean;
                System.out.printf("%-8d %-12.1f %-10.2f %-10.0f %-10.0f %s%n", threads, mean, base / mean, min, max, ok ? "YES (all 5 runs)" : "NO");
                System.out.println("CSV_POOL," + (lat == 0 ? "real" : "slow") + "," + threads + "," + mean + "," + min + "," + max + "," + ok);
            }
        }
    }
}
