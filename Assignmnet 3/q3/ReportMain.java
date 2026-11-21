import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Q3 driver: equivalence check, failing input, leak counts, logging, suppressed exceptions, closing order, atomic write. */
public class ReportMain {
    interface Gen { String run(List<String> f, String out, Log log) throws IOException; }

    static class Res implements AutoCloseable {
        final String name; final boolean failOnClose;
        Res(String name, boolean failOnClose) { this.name = name; this.failOnClose = failOnClose; System.out.println("   open  " + name); }
        @Override public void close() throws Exception {
            System.out.println("   close " + name);
            if (failOnClose) throw new IllegalStateException("close() of " + name + " failed");
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Java " + System.getProperty("java.version"));
        List<String> good = List.of("stores/store1.txt", "stores/store2.txt", "stores/store4.txt");
        List<String> bad = List.of("stores/store1.txt", "stores/store2.txt", "stores/store3.txt", "stores/store4.txt", "stores/store5.txt");
        Object[][] versions = {{"manual (close in try body)", (Gen) SalesReport::manualNaive}, {"manual (close in finally)", (Gen) SalesReport::manualFinally}, {"try-with-resources", (Gen) SalesReport::withResources}};

        System.out.println("\n=== 1. Same report on GOOD input (store1, store2, store4)? Hand-checked: 415.00 + 115.00 + 150.00 = 680.00 ===");
        String first = null; boolean allSame = true;
        for (Object[] v : versions) {
            Tracker.reset(); Log log = new Log();
            String rep = ((Gen) v[1]).run(good, "summary_" + ((String) v[0]).charAt(8) + ".txt", log);
            System.out.printf("%-28s %s%n", v[0], Tracker.stats());
            if (first == null) first = rep; else allSame &= first.equals(rep);
        }
        System.out.print(first);
        System.out.println("All three reports identical: " + allSame + "; total 680.00 matches hand check: " + first.contains("680.00"));

        System.out.println("\n=== 2. FAILING input: store3 has a corrupt line (3rd line), store5 does not exist ===");
        String ref = null; boolean same = true;
        for (Object[] v : versions) {
            Tracker.reset(); Log log = new Log();
            String rep = ((Gen) v[1]).run(bad, "summary_x.txt", log);
            System.out.printf("%-28s %s%n", v[0], Tracker.stats());
            if (ref == null) ref = rep; else same &= ref.equals(rep);
            if (v[0].equals("manual (close in try body)")) { System.out.println("   (report text)"); rep.lines().forEach(l -> System.out.println("   | " + l)); }
            if (v[0].equals("try-with-resources")) { System.out.println("   --- log: which file caused each failure ---"); log.lines.forEach(l -> System.out.println("   LOG " + l)); }
        }
        System.out.println("Reports identical for failing input too: " + same);

        System.out.println("\n=== 3. Peak open handles for f = 3 stores: sequential versions vs 'hold all readers at once' ===");
        Tracker.reset(); SalesReport.withResources(good, "summary_y.txt", new Log()); System.out.println("try-with-resources (one reader at a time): " + Tracker.stats());
        Tracker.reset(); SalesReport.holdAll(good); System.out.println("hold-all design (perfectly closed):         " + Tracker.stats());

        System.out.println("\n=== 4. Closing order and suppressed exceptions ===");
        System.out.println("-- (a) normal run, two resources: closing is in REVERSE order of opening");
        try (Res a = new Res("A (opened first)", false); Res b = new Res("B (opened second)", false)) { System.out.println("   body runs"); }
        System.out.println("-- (b) body throws AND close() throws: the BODY exception wins, the close exception is SUPPRESSED");
        try (Res a = new Res("A", true); Res b = new Res("B", true)) { throw new RuntimeException("exception from the body"); }
        catch (Exception e) {
            System.out.println("   caught: " + e.getMessage());
            for (Throwable s : e.getSuppressed()) System.out.println("   suppressed: " + s.getMessage());
        }
        System.out.println("-- (c) same situation with manual try/finally: the close exception REPLACES the body exception (original lost)");
        try {
            Res a = new Res("A", true);
            try { throw new RuntimeException("exception from the body"); } finally { a.close(); }
        } catch (Exception e) { System.out.println("   caught: " + e.getMessage() + "   (suppressed: " + e.getSuppressed().length + ")"); }

        System.out.println("\n=== 5. When try-with-resources alone is NOT enough: crash while writing the summary ===");
        String text = ref;
        Path plain = Paths.get("summary_plain.txt"), atomic = Paths.get("summary_atomic.txt");
        Files.writeString(plain, "YESTERDAY'S COMPLETE REPORT\n"); Files.writeString(atomic, "YESTERDAY'S COMPLETE REPORT\n");
        try { SalesReport.writeSummary(text, plain, false, true); } catch (IOException e) { System.out.println("plain write failed: " + e.getMessage()); }
        try { SalesReport.writeSummary(text, atomic, true, true); } catch (IOException e) { System.out.println("atomic write failed: " + e.getMessage()); }
        System.out.println("plain  file now contains: [" + Files.readString(plain).replace("\n", "\\n") + "]   <- truncated, incomplete report (resource closed correctly, data still wrong)");
        System.out.println("atomic file now contains: [" + Files.readString(atomic).replace("\n", "\\n") + "]   <- previous complete report still intact");
    }
}
