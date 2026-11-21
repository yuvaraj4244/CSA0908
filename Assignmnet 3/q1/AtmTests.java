import java.util.*;

/** Q1 Student B - test inputs, each intended to trigger a different path. */
public class AtmTests {
    public static void main(String[] args) {
        String ok = "cards/accounts.txt";
        Object[][] tests = {
            {"T1 valid withdrawal           ", "1001 500",   ok},
            {"T2 letters in account number  ", "abcd 500",   ok},
            {"T3 letters in amount          ", "1001 five",  ok},
            {"T4 negative amount            ", "1001 -200",  ok},
            {"T5 more than balance          ", "1002 500",   ok},
            {"T6 card-reader file missing   ", "1001 500",   "cards/NO_SUCH_FILE.txt"},
            {"T7 unknown account            ", "9999 500",   ok},
            {"T8 corrupt card file          ", "1001 500",   "cards/corrupt.txt"},
            {"T9 no input at all (EOF)      ", "",           ok},
            {"T10 amount not multiple of 100", "1001 150",   ok},
        };
        System.out.printf("%-32s %-14s %-40s %s%n", "Test", "input", "catch block that handled it", "finally ran?");
        for (Object[] t : tests) {
            int before = Atm.finallyRuns;
            System.out.println("-- " + t[0] + " input='" + t[1] + "'");
            Atm.withdraw(new Scanner((String) t[1]), (String) t[2]);
            boolean fin = Atm.finallyRuns == before + 1;
            System.out.printf("RESULT %-32s %-14s %-40s %s%n", t[0], "'" + t[1] + "'", Atm.path, fin ? "YES" : "NO");
        }
        // T11 an exception that no catch block handles: NullPointerException (null card-reader file name)
        System.out.println("-- T11 unhandled bug: null card file name");
        int before = Atm.finallyRuns;
        try { Atm.withdraw(new Scanner("1001 500"), null); }
        catch (NullPointerException e) {
            System.out.println("RESULT T11 propagated out of withdraw(): " + e.getClass().getSimpleName() + "; finally ran? " + (Atm.finallyRuns == before + 1 ? "YES" : "NO"));
        }
    }
}
