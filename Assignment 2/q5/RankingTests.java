import java.util.*;

/** Q5 - designed tests, faulty comparators, bounded generics, wildcards and the sort-three-times alternative. */
public class RankingTests {
    static long sink;

    /** Reusable generic component: works for ANY type that is Comparable (natural order) -- bounded type. */
    static class RankedList<T extends Comparable<? super T>> {
        private final List<T> items = new ArrayList<>();
        void add(T t) { items.add(t); }
        /** wildcard: accept a list of any SUBTYPE of T (producer => extends) */
        void addAll(Collection<? extends T> src) { items.addAll(src); }
        List<T> ranking() { List<T> c = new ArrayList<>(items); Collections.sort(c); return c; }
    }

    /** Team as Comparable (the alternative design): only ONE natural order is possible. */
    static class CTeam extends Team implements Comparable<CTeam> {
        CTeam(String n, int p, int g) { super(n, p, g); }
        @Override public int compareTo(CTeam o) {
            int c = Integer.compare(o.points, points);
            if (c != 0) return c;
            c = Integer.compare(o.goalDiff, goalDiff);
            return c != 0 ? c : name.compareTo(o.name);
        }
    }

    /** consumer wildcard: any comparator that can compare T or a supertype of T */
    static <T> void printTop(List<? extends T> list, int k) { System.out.println("  top " + k + ": " + list.subList(0, Math.min(k, list.size()))); }

    public static void main(String[] args) {
        Comparator<Team> good = Comparator.comparingInt((Team t) -> t.points).reversed()
                .thenComparing(Comparator.comparingInt((Team t) -> t.goalDiff).reversed())
                .thenComparing(t -> t.name);

        // ---------------- Data set A: several ties at each level ----------------
        List<Team> A = List.of(new Team("Lions", 12, 5), new Team("Tigers", 12, 5), new Team("Hawks", 12, 5),   // tie on points+GD -> name
                new Team("Wolves", 12, 2), new Team("Bears", 9, 7), new Team("Foxes", 9, 3), new Team("Ravens", 9, 3),
                new Team("Sharks", 4, -1), new Team("Dolphins", 4, -1), new Team("Owls", 0, -9));
        System.out.println("=== Data set A (ties at all three levels) ===");
        System.out.println("expected by hand: Hawks, Lions, Tigers (12,+5, name) > Wolves (12,+2) > Bears (9,+7) > Foxes, Ravens (9,+3) > Dolphins, Sharks (4,-1) > Owls");
        Leaderboard<Team> lb = new Leaderboard<>(good);
        A.forEach(lb::add);
        System.out.println("observed: " + lb.ranking());
        List<Team> shuffled = new ArrayList<>(A);
        boolean allSame = true;
        for (int s = 0; s < 200; s++) { Collections.shuffle(shuffled, new Random(s)); List<Team> c = new ArrayList<>(shuffled); c.sort(good); if (!c.toString().equals(lb.ranking().toString())) allSame = false; }
        System.out.println("order independent of input order over 200 shuffles: " + allSame);

        // ---------------- Data set B: break faulty comparators ----------------
        System.out.println("\n=== Data set B (designed to break faulty comparators) ===");
        // (1) subtraction overflows
        Comparator<Team> subtract = (x, y) -> y.points - x.points;
        List<Team> B1 = new ArrayList<>(List.of(new Team("Big", 1_500_000_000, 0), new Team("Neg", -1_500_000_000, 0)));
        System.out.println("(1) points compared by SUBTRACTION. compare(Big, Neg) = " + subtract.compare(B1.get(0), B1.get(1))
                + " (should be negative: Big has more points). Overflow!");
        B1.sort(subtract);
        System.out.println("    sorted with subtraction : " + B1);
        B1.sort(good);
        System.out.println("    sorted with the real one: " + B1);

        // (2) ignores tie-breakers -> equal items for different teams, ranking depends on input order
        Comparator<Team> pointsOnly = Comparator.comparingInt((Team t) -> t.points).reversed();
        List<Team> in1 = new ArrayList<>(List.of(new Team("Panthers", 9, 4), new Team("Eagles", 9, 6), new Team("Bisons", 9, 4)));
        List<Team> in2 = new ArrayList<>(List.of(new Team("Bisons", 9, 4), new Team("Eagles", 9, 6), new Team("Panthers", 9, 4)));
        in1.sort(pointsOnly); in2.sort(pointsOnly);
        System.out.println("(2) comparator IGNORING tie-breakers: input order 1 -> " + in1 + ", input order 2 -> " + in2 + "  (result depends on input => not a ranking)");
        in1.sort(good); in2.sort(good);
        System.out.println("    correct comparator             : input order 1 -> " + in1 + ", input order 2 -> " + in2);

        // (3) intransitive comparator: "within 2 points counts as equal"
        Comparator<Team> fuzzy = (x, y) -> Math.abs(x.points - y.points) <= 2 ? 0 : Integer.compare(y.points, x.points);
        Team p1 = new Team("A", 10, 0), p2 = new Team("B", 12, 0), p3 = new Team("C", 14, 0);
        System.out.println("(3) 'within 2 points = equal': A~B=" + fuzzy.compare(p1, p2) + ", B~C=" + fuzzy.compare(p2, p3) + ", but A vs C=" + fuzzy.compare(p1, p3)
                + "  -> equality is not transitive, principle violated");
        Random rnd = new Random(5);
        List<Team> big = new ArrayList<>();
        for (int i = 0; i < 2000; i++) big.add(new Team("T" + i, rnd.nextInt(60), rnd.nextInt(20)));
        try { big.sort(fuzzy); System.out.println("    2000 random teams sorted without exception (result still not trustworthy)"); }
        catch (IllegalArgumentException e) { System.out.println("    sort threw: " + e.getMessage()); }

        // ---------------- Comparable alternative, generics, wildcards ----------------
        System.out.println("\n=== Generic reuse ===");
        RankedList<CTeam> rl = new RankedList<>();
        rl.add(new CTeam("Panthers", 9, 4)); rl.addAll(List.of(new CTeam("Eagles", 9, 6), new CTeam("Cobras", 6, 1), new CTeam("Bisons", 9, 4)));
        System.out.println("Comparable<CTeam> + RankedList<T extends Comparable<? super T>>: " + rl.ranking());
        // same Leaderboard reused for Strings (longest first, then alphabetical) - no change to the class
        Leaderboard<String> words = new Leaderboard<>(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()));
        for (String s : List.of("pear", "fig", "apple", "kiwi", "plum")) words.add(s);
        System.out.println("Leaderboard<String> (same class, new comparator): " + words.ranking());
        // wildcard: a Comparator<Object> works for Leaderboard<String> because of  Comparator<? super T>
        Leaderboard<String> byToString = new Leaderboard<String>(Comparator.comparing(Object::toString));
        byToString.add("b"); byToString.add("a");
        System.out.println("Leaderboard<String> with Comparator<Object> (? super T): " + byToString.ranking());
        List<CTeam> subs = rl.ranking();
        System.out.print("List<? extends Team> printTop on List<CTeam>:"); System.out.println();
        RankingTests.<Team>printTop(subs, 2);

        // ---------------- Alternative: three stable sorts vs one comparator chain ----------------
        System.out.println("\n=== One comparator chain vs three stable sorts (ms, mean of 7 runs, 3 warm-up) ===");
        System.out.printf("%-10s %16s %18s %18s%n", "n", "chain (1 sort)", "3 stable sorts", "Comparable<Team>");
        for (int n : new int[]{10_000, 100_000, 1_000_000}) {
            Random r = new Random(3);
            List<Team> data = new ArrayList<>(); List<CTeam> cdata = new ArrayList<>();
            for (int i = 0; i < n; i++) { int p = r.nextInt(100), g = r.nextInt(60) - 30; String nm = "T" + r.nextInt(n); data.add(new Team(nm, p, g)); cdata.add(new CTeam(nm, p, g)); }
            double chain = timeSort(() -> { List<Team> c = new ArrayList<>(data); c.sort(good); return c; });
            double three = timeSort(() -> {
                List<Team> c = new ArrayList<>(data);
                c.sort(Comparator.comparing((Team t) -> t.name));
                c.sort(Comparator.comparingInt((Team t) -> t.goalDiff).reversed());
                c.sort(Comparator.comparingInt((Team t) -> t.points).reversed());
                return c; });
            double comp = timeSort(() -> { List<CTeam> c = new ArrayList<>(cdata); Collections.sort(c); return c; });
            // equivalence of results
            List<Team> a = new ArrayList<>(data); a.sort(good);
            List<Team> b = new ArrayList<>(data);
            b.sort(Comparator.comparing((Team t) -> t.name)); b.sort(Comparator.comparingInt((Team t) -> t.goalDiff).reversed()); b.sort(Comparator.comparingInt((Team t) -> t.points).reversed());
            boolean same = true; for (int i = 0; i < n; i++) if (a.get(i).points != b.get(i).points || a.get(i).goalDiff != b.get(i).goalDiff || !a.get(i).name.equals(b.get(i).name)) { same = false; break; }
            System.out.printf("%-10d %16.1f %18.1f %18.1f   same result chain==3sorts: %s%n", n, chain, three, comp, same);
            System.out.println("CSV," + n + "," + chain + "," + three + "," + comp);
        }
        System.out.println("(sink=" + sink + ")");
    }

    static double timeSort(java.util.function.Supplier<List<?>> s) {
        for (int i = 0; i < 3; i++) sink += s.get().size();
        long t = 0;
        for (int i = 0; i < 7; i++) { long t0 = System.nanoTime(); sink += s.get().size(); t += System.nanoTime() - t0; }
        return t / 7.0 / 1e6;
    }
}
