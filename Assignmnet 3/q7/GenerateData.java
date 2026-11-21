import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** usage: java GenerateData <dir> <N>   - creates N-file data set with injected corrupt and missing files, and expected_summary.txt */
public class GenerateData {
    static boolean isMissing(int i) { return i % 50 == 0; }
    static boolean isCorrupt(int i) { return i % 20 == 7; }
    static int score(int i, int q) { return (i * 7 + q * 3) % 11; }      // 0..10, deterministic

    public static void main(String[] a) throws Exception {
        Path dir = Paths.get(a[0]); int n = Integer.parseInt(a[1]);
        Files.createDirectories(dir);
        List<String> ids = new ArrayList<>(); long ok = 0, corrupt = 0, missing = 0, sum = 0, passed = 0;
        for (int i = 1; i <= n; i++) {
            String id = String.format("S%05d", i); ids.add(id);
            if (isMissing(i)) { missing++; continue; }                    // listed in the manifest, no file on disk
            StringBuilder sb = new StringBuilder("STUDENT:" + id + "\n"); int total = 0;
            for (int q = 1; q <= 10; q++) { int s = score(i, q); total += s; sb.append("Q").append(q).append(',').append(s).append('\n'); }
            if (isCorrupt(i)) {
                corrupt++;
                switch ((i / 20) % 6) {
                    case 0 -> Files.writeString(dir.resolve(id + ".txt"), sb.toString().replace("STUDENT:", "STUDNT:"));                 // bad header
                    case 1 -> Files.writeString(dir.resolve(id + ".txt"), sb.toString().replace("Q3,", "Q3,abc").replaceFirst("abc\\d+", "abc"));  // non-numeric
                    case 2 -> Files.writeString(dir.resolve(id + ".txt"), sb.toString().replaceFirst("Q5,\\d+", "Q5,11"));                // out of range
                    case 3 -> Files.writeString(dir.resolve(id + ".txt"), sb.toString().lines().limit(7).reduce("", (x, y) -> x + y + "\n")); // truncated
                    case 4 -> Files.write(dir.resolve(id + ".txt"), new byte[0]);                                                      // empty
                    default -> Files.write(dir.resolve(id + ".txt"), new byte[]{'S', 'T', (byte) 0xC3, (byte) 0x28, (byte) 0xFF, '\n'}); // invalid UTF-8
                }
            } else { ok++; sum += total; if (total >= 40) passed++; Files.writeString(dir.resolve(id + ".txt"), sb.toString(), StandardCharsets.UTF_8); }
        }
        Files.write(dir.resolve("manifest.txt"), ids);
        Files.writeString(dir.resolve("expected_summary.txt"), "ok=" + ok + "\ncorrupt=" + corrupt + "\nmissing=" + missing + "\nsum=" + sum + "\npassed=" + passed + "\n");
        System.out.println("generated " + n + " ids: ok=" + ok + " corrupt=" + corrupt + " missing=" + missing + " sum=" + sum + " passed=" + passed);
    }
}
