import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Q3 alternative concept: time and peak file handles for f store files (m = 200 lines each): try-with-resources, manual finally, NO closing. */
public class BenchMain {
    static String noClose(List<String> files, String out) throws IOException {
        StringBuilder sb = new StringBuilder(); double total = 0;
        for (String f : files) {
            TrackedReader r = new TrackedReader(f);       // never closed
            try { total += SalesReport.parseStore(r, f); } catch (IOException e) { sb.append("fail\n"); }
        }
        return sb.append(total).toString();
    }
    interface Gen { String run(List<String> f, String out, Log l) throws IOException; }
    public static void main(String[] a) throws Exception {
        System.out.println("Java " + System.getProperty("java.version"));
        System.out.printf("%-8s %-22s %12s %16s %18s%n", "f files", "version", "time (ms)", "peak open handles", "left open at end (ALL good files)");
        for (int f : new int[]{100, 500, 2000}) {
            Path dir = Files.createTempDirectory("bench"); List<String> files = new ArrayList<>();
            for (int i = 0; i < f; i++) { Path p = dir.resolve("s" + i + ".txt"); StringBuilder sb = new StringBuilder(); for (int k = 0; k < 200; k++) sb.append("item").append(k).append(",3,2.50\n"); Files.writeString(p, sb.toString()); files.add(p.toString()); }
            Object[][] vs = {{"try-with-resources", (Gen) SalesReport::withResources}, {"manual finally", (Gen) SalesReport::manualFinally}, {"no close at all", (Gen) (fl, o, l) -> noClose(fl, o)}};
            for (Object[] v : vs) {
                double tot = 0; int reps = 5; String stats = "";
                for (int r = 0; r < reps + 2; r++) {
                    Tracker.reset(); long t0 = System.nanoTime();
                    ((Gen) v[1]).run(files, dir.resolve("out.txt").toString(), new Log());
                    long t = System.nanoTime() - t0; if (r >= 2) tot += t;
                    stats = Tracker.max + "," + Tracker.current;
                }
                System.out.printf("%-8d %-22s %12.2f %16s %18s%n", f, v[0], tot / reps / 1e6, stats.split(",")[0], stats.split(",")[1]);
                System.out.println("CSV," + f + "," + v[0] + "," + tot / reps / 1e6 + "," + stats);
                if (v[0].equals("no close at all")) System.gc();
            }
        }
    }
}
