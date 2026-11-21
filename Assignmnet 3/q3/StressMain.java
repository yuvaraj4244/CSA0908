import java.io.*;
import java.nio.file.*;
import java.util.*;

/** usage: java StressMain <naive|finally|twr> <N>   - N store files, EVERY one corrupt; run under a low "ulimit -n". */
public class StressMain {
    public static void main(String[] a) throws Exception {
        String ver = a[0]; int n = Integer.parseInt(a[1]);
        Path dir = Paths.get("stores_big"); Files.createDirectories(dir);
        List<String> files = new ArrayList<>();
        for (int i = 0; i < n; i++) { Path p = dir.resolve("s" + i + ".txt"); Files.writeString(p, "a,1,2.00\nb,x,3.00\n"); files.add(p.toString()); }
        Tracker.reset(); Log log = new Log(); String rep;
        try {
            rep = switch (ver) { case "naive" -> SalesReport.manualNaive(files, "stress_out.txt", log);
                                 case "finally" -> SalesReport.manualFinally(files, "stress_out.txt", log);
                                 default -> SalesReport.withResources(files, "stress_out.txt", log); };
        } catch (Throwable t) { System.out.println(ver + ": run aborted with " + t); System.out.println(Tracker.stats()); return; }
        long tooMany = rep.lines().filter(l -> l.contains("Too many open files")).count();
        long bad = rep.lines().filter(l -> l.contains("bad record")).count();
        System.out.printf("%-8s N=%d  %s  | failures reported as 'bad record'=%d, failures reported as 'Too many open files'=%d%n", ver, n, Tracker.stats(), bad, tooMany);
    }
}
