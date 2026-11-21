import java.io.*;
import java.util.*;

/** Broader catch BEFORE a narrower one: this file is meant NOT to compile (Student B demonstration). */
public class BadOrder {
    static void run(Scanner in) {
        try {
            in.nextLong();
            new FileReader("x.txt").close();
        } catch (IOException e) {                // broad (superclass) first ...
            System.out.println("io");
        } catch (FileNotFoundException e) {      // ... narrow (subclass) afterwards  -> unreachable
            System.out.println("not found");
        } catch (NoSuchElementException e) {     // parent first ...
            System.out.println("nse");
        } catch (InputMismatchException e) {     // ... child afterwards -> unreachable
            System.out.println("mismatch");
        }
    }
    public static void main(String[] a) { run(new Scanner("1")); }
}
