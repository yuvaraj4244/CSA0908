import java.lang.management.ManagementFactory;
import java.util.*;

/** Q3 - Investigating the equals()/hashCode() contract with three designed tests, plus a TreeSet/HashMap alternative. */
public class ContractTests {

    /** The junior developer's class: equals() only. Mutable fields. */
    static class BadStudent {
        String regNo, name;
        BadStudent(String r, String n) { regNo = r; name = n; }
        @Override public boolean equals(Object o) { return o instanceof BadStudent b && regNo.equals(b.regNo); }
    }

    /** Corrected class: immutable key, hashCode uses the same field as equals. */
    static final class GoodStudent {
        private final String regNo, name;
        GoodStudent(String r, String n) { regNo = r; name = n; }
        @Override public boolean equals(Object o) { return this == o || (o instanceof GoodStudent g && regNo.equals(g.regNo)); }
        @Override public int hashCode() { return Objects.hash(regNo); }
        @Override public String toString() { return regNo + " " + name; }
    }

    /** hashCode is correct, but regNo is mutable. */
    static class MutableStudent {
        String regNo, name;
        MutableStudent(String r, String n) { regNo = r; name = n; }
        @Override public boolean equals(Object o) { return o instanceof MutableStudent m && regNo.equals(m.regNo); }
        @Override public int hashCode() { return regNo.hashCode(); }
    }

    static void show(String test, int predicted, int observed, String why) {
        System.out.printf("%-58s predicted=%d observed=%d  %s%n", test, predicted, observed, predicted == observed ? "[MATCH]" : "[DIFFERS]");
        System.out.println("      reason: " + why);
    }

    public static void main(String[] args) {
        System.out.println("Java " + System.getProperty("java.version"));

        System.out.println("\n--- TEST 1: scans that appear to work (same object scanned twice) -- BadStudent ---");
        BadStudent a = new BadStudent("192311001", "Arun"), d = new BadStudent("192311002", "Divya");
        Set<BadStudent> s1 = new HashSet<>();
        s1.add(a); s1.add(d); s1.add(a);
        show("Test 1: add a, d, a (same reference)", 2, s1.size(),
                "same reference => same identity hashCode => same bucket, and HashMap short-circuits on '=='.");

        System.out.println("\n--- TEST 2: scans that expose duplicates -- BadStudent ---");
        Set<BadStudent> s2 = new HashSet<>();
        s2.add(new BadStudent("192311001", "Arun"));
        s2.add(new BadStudent("192311002", "Divya"));
        s2.add(new BadStudent("192311001", "Arun"));
        System.out.println("      identity hashCodes of the two 'Arun' objects: "
                + Integer.toHexString(new BadStudent("192311001", "Arun").hashCode()) + " vs "
                + Integer.toHexString(new BadStudent("192311001", "Arun").hashCode()));
        show("Test 2: add Arun, Divya, new-Arun (equal but distinct objects)", 2 /* what equals() says */, s2.size(),
                "equal objects got different hashCodes (Object.hashCode = identity), so HashSet looked in another bucket and never called equals().");
        System.out.println("      (Prediction made from equals() alone is 2; the contract violation makes the real answer 3.)");

        System.out.println("\n--- TEST 2b: the same scans with the corrected GoodStudent ---");
        Set<GoodStudent> s2b = new HashSet<>();
        s2b.add(new GoodStudent("192311001", "Arun"));
        s2b.add(new GoodStudent("192311002", "Divya"));
        s2b.add(new GoodStudent("192311001", "Arun"));
        show("Test 2b: same scans, GoodStudent", 2, s2b.size(), "equal objects now share a hashCode => same bucket => equals() merges them.");

        System.out.println("\n--- TEST 3 (own): field used in hashCode() changed after insertion -- MutableStudent ---");
        Set<MutableStudent> s3 = new HashSet<>();
        MutableStudent m = new MutableStudent("192311001", "Arun");
        s3.add(m);
        m.regNo = "192399999";                       // mutate the key field
        boolean found = s3.contains(m);
        boolean removed = s3.remove(m);
        System.out.println("      after mutation: contains(m)=" + found + ", remove(m)=" + removed + ", size=" + s3.size());
        s3.add(m);
        show("Test 3: size after re-adding the mutated object (stale copy remains)", 1, s3.size(),
                "the entry sits in the bucket of the OLD hash; lookups use the NEW hash, so it is lost ('ghost' entry) and re-adding creates a second copy.");

        System.out.println("\n--- TEST 4 (own): TreeSet whose compareTo is inconsistent with equals ---");
        TreeSet<GoodStudent> ts = new TreeSet<>(Comparator.comparing(g -> g.name));   // compares NAME only
        ts.add(new GoodStudent("192311001", "Arun"));
        ts.add(new GoodStudent("192311055", "Arun"));   // different student (regNo), same name
        Set<GoodStudent> hs = new HashSet<>(List.of(new GoodStudent("192311001", "Arun"), new GoodStudent("192311055", "Arun")));
        show("Test 4: two different students, same name: TreeSet(by name) size", 2, ts.size(),
                "TreeSet never calls equals(); compare()==0 means 'duplicate', so the second student is silently dropped. HashSet size = " + hs.size() + ".");

        System.out.println("\n--- Contract verification for the corrected class ---");
        GoodStudent x = new GoodStudent("1", "A"), y = new GoodStudent("1", "B");
        System.out.println("x.equals(y)=" + x.equals(y) + " && hashCode equal=" + (x.hashCode() == y.hashCode())
                + "  (equal objects must have equal hashCodes)  -> " + (x.equals(y) && x.hashCode() == y.hashCode() ? "PASS" : "FAIL"));
        Set<Integer> distinctBad = new HashSet<>();
        for (int i = 0; i < 1000; i++) distinctBad.add(new BadStudent("1", "A").hashCode());
        System.out.println("1000 equal BadStudent objects produced " + distinctBad.size() + " distinct hashCodes (contract needs 1)");

        System.out.println("\n--- Alternative structures: n scans of n unique students (ns per scan, mean of 5 runs; allocated MB) ---");
        com.sun.management.ThreadMXBean tmx = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        System.out.printf("%-10s %-22s %14s %14s%n", "n", "structure", "ns/scan", "alloc MB");
        for (int n : new int[]{10_000, 100_000, 1_000_000}) {
            String[] regs = new String[n];
            for (int i = 0; i < n; i++) regs[i] = "19" + (2310000 + i);
            GoodStudent[] studs = new GoodStudent[n];
            for (int i = 0; i < n; i++) studs[i] = new GoodStudent(regs[i], "N" + i);
            for (String kind : new String[]{"HashSet<Student>", "TreeSet<Student>(regNo)", "HashMap<String,Student>"}) {
                long tot = 0; long alloc = 0;
                for (int rep = 0; rep < 7; rep++) {
                    Collection<GoodStudent> hs1 = null; Map<String, GoodStudent> hm = null; TreeSet<GoodStudent> tr = null;
                    long a0 = tmx.getThreadAllocatedBytes(Thread.currentThread().getId());
                    long t0 = System.nanoTime();
                    if (kind.startsWith("HashSet")) { Set<GoodStudent> h = new HashSet<>(); for (GoodStudent g : studs) h.add(g); hs1 = h; }
                    else if (kind.startsWith("TreeSet")) { TreeSet<GoodStudent> t = new TreeSet<>(Comparator.comparing(g -> g.regNo)); for (GoodStudent g : studs) t.add(g); tr = t; }
                    else { Map<String, GoodStudent> h = new HashMap<>(); for (GoodStudent g : studs) h.putIfAbsent(g.regNo, g); hm = h; }
                    long dt = System.nanoTime() - t0;
                    if (rep >= 2) { tot += dt; alloc = tmx.getThreadAllocatedBytes(Thread.currentThread().getId()) - a0; }
                }
                System.out.printf("%-10d %-22s %14.1f %14.1f%n", n, kind, tot / 5.0 / n, alloc / 1048576.0);
                System.out.println("CSV," + n + "," + kind + "," + tot / 5.0 / n + "," + alloc);
            }
        }
    }
}
