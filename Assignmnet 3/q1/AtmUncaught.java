import java.util.Scanner;

/** Lets an exception escape main() so the JVM prints the full stack trace (Student B: explain each line). */
public class AtmUncaught {
    public static void main(String[] args) {
        System.out.println("Calling withdraw() with a null card-file name ...");
        Atm.withdraw(new Scanner("1001 500"), null);      // NPE is not caught by any catch block in Atm
        System.out.println("never reached");
    }
}
