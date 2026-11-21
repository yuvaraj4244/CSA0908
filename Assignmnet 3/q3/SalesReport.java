import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

// ---------- instrumentation: counts every open and close, and the peak number of open handles ----------
class Tracker {
    static final AtomicInteger opened = new AtomicInteger(), closed = new AtomicInteger(), current = new AtomicInteger(), max = new AtomicInteger();
    static void reset() { opened.set(0); closed.set(0); current.set(0); max.set(0); }
    static void onOpen() { opened.incrementAndGet(); int c = current.incrementAndGet(); max.accumulateAndGet(c, Math::max); }
    static void onClose() { closed.incrementAndGet(); current.decrementAndGet(); }
    static String stats() { return "opened=" + opened + " closed=" + closed + " LEFT OPEN=" + current + " peak open=" + max; }
}
class TrackedReader extends BufferedReader {
    private boolean isClosed;
    TrackedReader(String file) throws FileNotFoundException { super(new FileReader(file)); Tracker.onOpen(); }
    @Override public void close() throws IOException { if (!isClosed) { isClosed = true; Tracker.onClose(); } super.close(); }
}
class TrackedWriter extends BufferedWriter {
    private boolean isClosed;
    TrackedWriter(String file) throws IOException { super(new FileWriter(file)); Tracker.onOpen(); }
    @Override public void close() throws IOException { if (!isClosed) { isClosed = true; Tracker.onClose(); } super.close(); }
}
class BadRecordException extends IOException {
    BadRecordException(String file, int line, Throwable cause) { super(file + ": line " + line + ": " + cause, cause); }
}
class Log {
    final List<String> lines = new ArrayList<>();
    void add(String s) { lines.add(s); }
}

/** Q3 - Daily sales report generator in three versions: naive manual close, manual close in finally, try-with-resources. */
public class SalesReport {

    /** sum of qty*price of one store file; throws BadRecordException on a malformed line */
    static double parseStore(BufferedReader r, String name) throws IOException {
        double sum = 0; String line; int n = 0;
        while ((line = r.readLine()) != null) {
            n++;
            try {
                String[] p = line.split(",");
                sum += Integer.parseInt(p[1].trim()) * Double.parseDouble(p[2].trim());
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                throw new BadRecordException(name, n, e);
            }
        }
        return sum;
    }

    static String lineOk(String f, double s) { return Paths.get(f).getFileName() + ": " + String.format("%.2f", s); }
    static String lineFail(String f, Exception e) { return Paths.get(f).getFileName() + ": FAILED (" + shortReason(e) + ")"; }
    static String shortReason(Exception e) {
        if (e instanceof BadRecordException b) return "bad record - " + b.getMessage().substring(b.getMessage().indexOf("line"));
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    // ===================== VERSION 1: manual close, close() placed in the TRY body (the typical bug) =====================
    static String manualNaive(List<String> files, String out, Log log) throws IOException {
        StringBuilder sb = new StringBuilder("DAILY SALES REPORT\n"); double total = 0; int ok = 0;
        TrackedWriter w = new TrackedWriter(out);
        for (String f : files) {
            try {
                TrackedReader r = new TrackedReader(f);
                double s = parseStore(r, f);          // <-- throws on a corrupt line ...
                r.close();                            // <-- ... so this close() is skipped: the handle LEAKS
                sb.append(lineOk(f, s)).append('\n'); total += s; ok++;
            } catch (IOException e) {
                sb.append(lineFail(f, e)).append('\n'); log.add("FAILED file=" + f + " cause=" + e);
            }
        }
        sb.append(String.format("TOTAL (%d of %d stores): %.2f%n", ok, files.size(), total));
        w.write(sb.toString());
        w.close();
        return sb.toString();
    }

    // ===================== VERSION 2: manual close, correctly in finally =====================
    static String manualFinally(List<String> files, String out, Log log) throws IOException {
        StringBuilder sb = new StringBuilder("DAILY SALES REPORT\n"); double total = 0; int ok = 0;
        TrackedWriter w = null;
        try {
            w = new TrackedWriter(out);
            for (String f : files) {
                TrackedReader r = null;
                try {
                    r = new TrackedReader(f);
                    double s = parseStore(r, f);
                    sb.append(lineOk(f, s)).append('\n'); total += s; ok++;
                } catch (IOException e) {
                    sb.append(lineFail(f, e)).append('\n'); log.add("FAILED file=" + f + " cause=" + e);
                } finally {
                    if (r != null) { try { r.close(); } catch (IOException ce) { log.add("close failed for " + f + ": " + ce); } }
                }
            }
            sb.append(String.format("TOTAL (%d of %d stores): %.2f%n", ok, files.size(), total));
            w.write(sb.toString());
        } finally {
            if (w != null) { try { w.close(); } catch (IOException ce) { log.add("close failed for summary: " + ce); } }
        }
        return sb.toString();
    }

    // ===================== VERSION 3: try-with-resources =====================
    static String withResources(List<String> files, String out, Log log) throws IOException {
        StringBuilder sb = new StringBuilder("DAILY SALES REPORT\n"); double total = 0; int ok = 0;
        try (TrackedWriter w = new TrackedWriter(out)) {
            for (String f : files) {
                try (TrackedReader r = new TrackedReader(f)) {
                    double s = parseStore(r, f);
                    sb.append(lineOk(f, s)).append('\n'); total += s; ok++;
                } catch (IOException e) {
                    sb.append(lineFail(f, e)).append('\n'); log.add("FAILED file=" + f + " cause=" + e);
                }
            }
            sb.append(String.format("TOTAL (%d of %d stores): %.2f%n", ok, files.size(), total));
            w.write(sb.toString());
        }
        return sb.toString();
    }

    // ===================== design variant: all readers held at once (correctly closed!) =====================
    static int holdAll(List<String> files) throws IOException {
        List<TrackedReader> open = new ArrayList<>();
        try {
            for (String f : files) open.add(new TrackedReader(f));
            return Tracker.max.get();
        } finally {
            for (TrackedReader r : open) { try { r.close(); } catch (IOException ignore) { } }
        }
    }

    // ===================== atomic summary writing (when try-with-resources alone is not enough) =====================
    static void writeSummary(String text, Path out, boolean atomic, boolean crashHalfWay) throws IOException {
        Path target = atomic ? Paths.get(out + ".tmp") : out;
        try (BufferedWriter w = Files.newBufferedWriter(target)) {
            w.write(text.substring(0, text.length() / 2)); w.flush();
            if (crashHalfWay) throw new IOException("simulated crash while writing");
            w.write(text.substring(text.length() / 2));
        }
        if (atomic) Files.move(target, out, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
