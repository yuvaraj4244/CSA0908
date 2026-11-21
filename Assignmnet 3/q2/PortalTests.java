import java.io.*;
import java.nio.file.*;
import java.time.LocalTime;
import java.util.*;

/** Q2 Student B: one scenario per exception, chaining evidence, lost-cause experiment, test table. */
public class PortalTests {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("exam-storage");
        Storage storage = new Storage(dir);
        ExamService service = new ExamService(storage, LocalTime.of(17, 0));
        service.register("S001", "CSA09");
        ExamController ctl = new ExamController(service);
        LocalTime inTime = LocalTime.of(16, 30), late = LocalTime.of(17, 5);

        System.out.println("=== Scenario table ===");
        String[][] rows = new String[7][];
        int i = 0;
        rows[i++] = run(ctl, "1 valid submission",                "S001", "CSA09", "a1.pdf", 120, inTime);
        rows[i++] = run(ctl, "2 window closed (ExamClosed)",       "S001", "CSA09", "a2.pdf", 120, late);
        rows[i++] = run(ctl, "3 not registered (NotRegistered)",   "S999", "CSA09", "a3.pdf", 120, inTime);
        rows[i++] = run(ctl, "4 file too large (AnswerTooLarge)",  "S001", "CSA09", "a4.pdf", 800, inTime);
        rows[i++] = run(ctl, "5 storage failure (Storage)",        "S001", "CSA09", "missing_dir/a5.pdf", 120, inTime);
        rows[i++] = run(ctl, "6 blank student id (unchecked)",     " ",    "CSA09", "a6.pdf", 120, inTime);
        System.out.printf("%n%-36s | %-24s | %-62s | %s%n", "Scenario", "Exception thrown", "Message shown to the student", "Logged?");
        for (int k = 0; k < 6; k++) System.out.printf("%-36s | %-24s | %-62s | %s%n", rows[k][0], rows[k][1], rows[k][2], rows[k][3]);

        System.out.println("\n=== Chaining evidence (storage failure): walking getCause() ===");
        try { service.submit("S001", "CSA09", "missing_dir/a5.pdf", 120, inTime); }
        catch (ExamException e) {
            int depth = 0;
            for (Throwable t = e; t != null; t = t.getCause())
                System.out.println("  ".repeat(depth++) + "level " + depth + ": " + t.getClass().getName() + ": " + t.getMessage());
            System.out.println("\n--- full chained stack trace (printStackTrace) ---");
            e.printStackTrace(System.out);
        }

        System.out.println("\n=== What is lost when the exception is re-thrown WITHOUT its cause ===");
        storage.losesCause = true;
        try { service.submit("S001", "CSA09", "missing_dir/a5.pdf", 120, inTime); }
        catch (ExamException e) {
            System.out.println("getCause() = " + e.getCause());
            StringWriter sw = new StringWriter(); e.printStackTrace(new PrintWriter(sw));
            System.out.println("trace contains \"Caused by\"? " + sw.toString().contains("Caused by") + "   (trace length " + sw.toString().length() + " chars)");
            System.out.println("--- stack trace without cause ---");
            System.out.print(sw);
            System.out.println("--> lost: the fact that it was a FileNotFoundException/NoSuchFile, the exact path, the OS message and the frames inside java.nio.");
        }
        storage.losesCause = false;
        System.out.println("\n=== Controller log (what the operators see) ===");
        ctl.log.forEach(s -> System.out.println(s.lines().limit(4).reduce("", (a, b) -> a + "  " + b + "\n") + "  ..."));
    }

    static String[] run(ExamController c, String label, String st, String course, String file, long kb, LocalTime t) {
        int logBefore = c.log.size();
        String thrown;
        try { c.service.submit(st, course, file, kb, t); thrown = "(none)"; }
        catch (ExamException | RuntimeException e) { thrown = e.getClass().getSimpleName(); }
        String msg = c.submit(st, course, file, kb, t);
        return new String[]{label, thrown, msg, c.log.size() > logBefore ? "yes" : "no"};
    }
}
