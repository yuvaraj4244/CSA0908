import java.util.*;

/** Q2 - ArrayList vs LinkedList vs ArrayDeque vs plain array (manual shifting) for a playlist. */
public class PlaylistBench {
    record Song(int id) {}
    static long sink;
    static final int K = 1000;          // operations per measurement for insert/remove/get
    static final int WARM = 3, REPS = 7;

    interface Op { void run(); }

    /** time in ns/op, mean over REPS; setup is rebuilt for every run and excluded from timing. */
    static double time(java.util.function.Supplier<Op> setup, int opsPerRun) {
        for (int w = 0; w < WARM; w++) setup.get().run();
        long tot = 0;
        for (int r = 0; r < REPS; r++) {
            Op op = setup.get();
            long t0 = System.nanoTime();
            op.run();
            tot += System.nanoTime() - t0;
        }
        return tot / (double) REPS / opsPerRun;
    }

    static List<Song> fill(List<Song> l, Song[] s) { l.addAll(Arrays.asList(s)); return l; }

    public static void main(String[] a) {
        System.out.println("Java " + System.getProperty("java.version") + "   ns per operation, mean of " + REPS + " runs (" + WARM + " warm-up)");
        System.out.printf("%-9s %-10s %12s %12s %12s %12s %12s %12s %12s%n", "size", "structure", "add end", "ins front", "ins middle", "rem front", "rem middle", "get k-th", "iterate/elem");
        for (int n : new int[]{1_000, 10_000, 100_000, 400_000}) {
            Song[] songs = new Song[n];
            for (int i = 0; i < n; i++) songs[i] = new Song(i);
            Random rnd = new Random(42);
            int[] idx = new int[K];
            for (int i = 0; i < K; i++) idx[i] = rnd.nextInt(n);
            Song extra = new Song(-1);

            for (String kind : new String[]{"ArrayList", "LinkedList", "ArrayDeque", "Array[]"}) {
                double addEnd, insF, insM, remF, remM, get, iter;
                boolean isDeque = kind.equals("ArrayDeque"), isArr = kind.equals("Array[]");
                // add at end: build n elements from empty
                addEnd = time(() -> {
                    if (isArr) return () -> { Song[] arr = new Song[n]; for (int i = 0; i < n; i++) arr[i] = songs[i]; sink += arr.length; };
                    Collection<Song> c = kind.equals("ArrayList") ? new ArrayList<>() : kind.equals("LinkedList") ? new LinkedList<>() : new ArrayDeque<>();
                    return () -> { for (int i = 0; i < n; i++) c.add(songs[i]); sink += c.size(); };
                }, n);

                if (isDeque) {
                    insF = time(() -> { ArrayDeque<Song> d = new ArrayDeque<>(Arrays.asList(songs)); return () -> { for (int i = 0; i < K; i++) d.addFirst(extra); sink += d.size(); }; }, K);
                    remF = time(() -> { ArrayDeque<Song> d = new ArrayDeque<>(Arrays.asList(songs)); return () -> { for (int i = 0; i < K; i++) sink += d.pollFirst().id(); }; }, K);
                    insM = remM = get = Double.NaN;
                    iter = time(() -> { ArrayDeque<Song> d = new ArrayDeque<>(Arrays.asList(songs)); return () -> { long s = 0; for (Song x : d) s += x.id(); sink += s; }; }, n);
                } else if (isArr) {
                    insF = time(() -> { Song[] arr = Arrays.copyOf(songs, n + K); int[] size = {n}; return () -> { for (int i = 0; i < K; i++) { for (int j = size[0]; j > 0; j--) arr[j] = arr[j - 1]; arr[0] = extra; size[0]++; } sink += size[0]; }; }, K);
                    insM = time(() -> { Song[] arr = Arrays.copyOf(songs, n + K); int[] size = {n}; return () -> { for (int i = 0; i < K; i++) { int p = size[0] / 2; for (int j = size[0]; j > p; j--) arr[j] = arr[j - 1]; arr[p] = extra; size[0]++; } sink += size[0]; }; }, K);
                    remF = time(() -> { Song[] arr = Arrays.copyOf(songs, n); int[] size = {n}; return () -> { for (int i = 0; i < K; i++) { for (int j = 1; j < size[0]; j++) arr[j - 1] = arr[j]; size[0]--; } sink += size[0]; }; }, K);
                    remM = time(() -> { Song[] arr = Arrays.copyOf(songs, n); int[] size = {n}; return () -> { for (int i = 0; i < K; i++) { int p = size[0] / 2; for (int j = p + 1; j < size[0]; j++) arr[j - 1] = arr[j]; size[0]--; } sink += size[0]; }; }, K);
                    get = time(() -> { return () -> { long s = 0; for (int i = 0; i < K; i++) s += songs[idx[i]].id(); sink += s; }; }, K);
                    iter = time(() -> () -> { long s = 0; for (int i = 0; i < n; i++) s += songs[i].id(); sink += s; }, n);
                } else {
                    java.util.function.Supplier<List<Song>> mk = () -> kind.equals("ArrayList") ? fill(new ArrayList<>(n + K), songs) : fill(new LinkedList<>(), songs);
                    insF = time(() -> { List<Song> l = mk.get(); return () -> { for (int i = 0; i < K; i++) l.add(0, extra); sink += l.size(); }; }, K);
                    insM = time(() -> { List<Song> l = mk.get(); return () -> { for (int i = 0; i < K; i++) l.add(l.size() / 2, extra); sink += l.size(); }; }, K);
                    remF = time(() -> { List<Song> l = mk.get(); return () -> { for (int i = 0; i < K; i++) l.remove(0); sink += l.size(); }; }, K);
                    remM = time(() -> { List<Song> l = mk.get(); return () -> { for (int i = 0; i < K; i++) l.remove(l.size() / 2); sink += l.size(); }; }, K);
                    get = time(() -> { List<Song> l = mk.get(); return () -> { long s = 0; for (int i = 0; i < K; i++) s += l.get(idx[i]).id(); sink += s; }; }, K);
                    iter = time(() -> { List<Song> l = mk.get(); return () -> { long s = 0; for (Song x : l) s += x.id(); sink += s; }; }, n);
                }
                System.out.printf("%-9d %-10s %12s %12s %12s %12s %12s %12s %12s%n", n, kind, f(addEnd), f(insF), f(insM), f(remF), f(remM), f(get), f(iter));
                System.out.println("CSV," + n + "," + kind + "," + addEnd + "," + insF + "," + insM + "," + remF + "," + remM + "," + get + "," + iter);
            }
        }
        System.out.println("(sink=" + sink + ")");
    }

    static String f(double d) { return Double.isNaN(d) ? "n/a" : String.format("%.1f", d); }
}
