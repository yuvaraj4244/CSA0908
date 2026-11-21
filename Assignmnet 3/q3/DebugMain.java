import java.util.*;
/** Small driver for the jdb session: one good store followed by the corrupt store3. */
public class DebugMain {
    public static void main(String[] a) throws Exception {
        String s = SalesReport.manualNaive(List.of("stores/store1.txt", "stores/store3.txt"), "summary_debug.txt", new Log());
        System.out.println(Tracker.stats());
    }
}
