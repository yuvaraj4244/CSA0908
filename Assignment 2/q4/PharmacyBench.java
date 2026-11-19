import java.util.*;

/** Q4 - HashMap vs TreeMap vs LinkedHashMap (vs Hashtable, vs sorted array + binarySearch) for a pharmacy inventory. */
public class PharmacyBench {
    record Medicine(String code, String name, int qty) {}
    static long sink;
    static final int LOOKUPS = 100_000;

    static String code(int i) { return String.format("MED%07d", i); }

    static List<Medicine> data(int n) {            // shuffled => insertion order differs from code order
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) ids.add(i);
        Collections.shuffle(ids, new Random(7));
        List<Medicine> l = new ArrayList<>(n);
        for (int id : ids) l.add(new Medicine(code(id), "Drug" + id, id % 500));
        return l;
    }

    static Map<String, Medicine> build(String kind, List<Medicine> d) {
        Map<String, Medicine> m = switch (kind) {
            case "HashMap" -> new HashMap<>();
            case "TreeMap" -> new TreeMap<>();
            case "LinkedHashMap" -> new LinkedHashMap<>();
            default -> new Hashtable<>();
        };
        for (Medicine x : d) m.put(x.code(), x);
        return m;
    }

    /** report in code order */
    static List<String> codeOrder(Map<String, Medicine> m) {
        List<String> out = new ArrayList<>(m.size());
        Collection<Medicine> vals = (m instanceof TreeMap) ? m.values() : sortedValues(m);
        for (Medicine x : vals) out.add(x.code());
        return out;
    }
    static Collection<Medicine> sortedValues(Map<String, Medicine> m) {   // the "extra work" for a non-sorted map
        List<Medicine> l = new ArrayList<>(m.values());
        l.sort(Comparator.comparing(Medicine::code));
        return l;
    }
    /** report in insertion order */
    static List<String> insertionOrder(Map<String, Medicine> m, List<Medicine> original) {
        List<String> out = new ArrayList<>(m.size());
        if (m instanceof LinkedHashMap) { for (Medicine x : m.values()) out.add(x.code()); }
        else {   // wrong map: only a separately maintained list can give insertion order (extra memory + work)
            for (Medicine x : original) out.add(x.code());
        }
        return out;
    }

    static double ms(Runnable r, int warm, int reps) {
        for (int i = 0; i < warm; i++) r.run();
        long t = 0;
        for (int i = 0; i < reps; i++) { long t0 = System.nanoTime(); r.run(); t += System.nanoTime() - t0; }
        return t / (double) reps / 1e6;
    }

    public static void main(String[] args) {
        System.out.println("Java " + System.getProperty("java.version"));
        System.out.println("100,000 random lookups; reports; mean of runs (3 warm-up, 5 measured). ms unless stated.");
        System.out.printf("%-9s %-14s %10s %14s %12s %12s %10s %10s%n", "n", "structure", "build", "100k lookups", "ns/lookup", "code-order", "insert-ord", "orders OK");
        for (int n : new int[]{1_000, 10_000, 100_000, 1_000_000}) {
            List<Medicine> d = data(n);
            Random r = new Random(1);
            String[] probes = new String[LOOKUPS];
            for (int i = 0; i < LOOKUPS; i++) probes[i] = code(r.nextInt(n));
            int reps = n >= 1_000_000 ? 3 : 5;

            for (String kind : new String[]{"HashMap", "TreeMap", "LinkedHashMap", "Hashtable"}) {
                double build = ms(() -> sink += build(kind, d).size(), 2, reps);
                Map<String, Medicine> m = build(kind, d);
                double look = ms(() -> { long s = 0; for (String p : probes) s += m.get(p).qty(); sink += s; }, 3, 5);
                double rep1 = ms(() -> sink += codeOrder(m).size(), 2, reps);
                double rep2 = ms(() -> sink += insertionOrder(m, d).size(), 2, reps);
                List<String> c = codeOrder(m), ins = insertionOrder(m, d);
                boolean okC = isSorted(c);
                boolean okI = true;
                for (int i = 0; i < n; i++) if (!ins.get(i).equals(d.get(i).code())) { okI = false; break; }
                System.out.printf("%-9d %-14s %10.2f %14.2f %12.1f %12.2f %10.2f %10s%n", n, kind, build, look, look * 1e6 / LOOKUPS, rep1, rep2, (okC ? "code:Y" : "code:N") + "/" + (okI ? "ins:Y" : "ins:N"));
                System.out.println("CSV," + n + "," + kind + "," + build + "," + look + "," + rep1 + "," + rep2);
            }
            // natural-order check without extra work, i.e. raw iteration order of each map
            for (String kind : new String[]{"HashMap", "TreeMap", "LinkedHashMap"}) {
                Map<String, Medicine> m = build(kind, d);
                List<String> raw = new ArrayList<>();
                for (Medicine x : m.values()) raw.add(x.code());
                boolean codeSorted = isSorted(raw), insOrder = true;
                for (int i = 0; i < n; i++) if (!raw.get(i).equals(d.get(i).code())) { insOrder = false; break; }
                if (n == 1_000_000 || n == 1_000) System.out.printf("   raw iteration of %-13s at n=%d: code-sorted=%s, insertion-ordered=%s%n", kind, n, codeSorted, insOrder);
            }
            // sorted array + binarySearch
            Medicine[] arr = d.toArray(new Medicine[0]);
            Comparator<Medicine> byCode = Comparator.comparing(Medicine::code);
            double build = ms(() -> { Medicine[] a = arr.clone(); Arrays.sort(a, byCode); sink += a.length; }, 2, reps);
            Medicine[] sorted = arr.clone(); Arrays.sort(sorted, byCode);
            double look = ms(() -> { long s = 0; for (String p : probes) s += sorted[Arrays.binarySearch(sorted, new Medicine(p, "", 0), byCode)].qty(); sink += s; }, 3, 5);
            double ins = ms(() -> {   // 200 insertions with shifting
                Medicine[] a = Arrays.copyOf(sorted, n + 200); int sz = n;
                for (int i = 0; i < 200; i++) {
                    Medicine x = new Medicine("MED9" + String.format("%06d", i * 7), "new", 1);
                    int p = Arrays.binarySearch(a, 0, sz, x, byCode); if (p < 0) p = -p - 1;
                    System.arraycopy(a, p, a, p + 1, sz - p); a[p] = x; sz++;
                }
                sink += sz; }, 2, reps) / 200 * 1e6;
            System.out.printf("%-9d %-14s %10.2f %14.2f %12.1f %12s %10s   insert=%.0f ns/op (arraycopy shifting)%n", n, "sorted[]+bsearch", build, look, look * 1e6 / LOOKUPS, "0 (already)", "n/a", ins);
            System.out.println("CSV," + n + ",sortedArray," + build + "," + look + ",0,0," + ins);
        }
        System.out.println("(sink=" + sink + ")");
    }

    static boolean isSorted(List<String> l) {
        for (int i = 1; i < l.size(); i++) if (l.get(i - 1).compareTo(l.get(i)) > 0) return false;
        return true;
    }
}
