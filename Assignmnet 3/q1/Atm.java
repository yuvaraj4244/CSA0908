import java.io.*;
import java.util.*;

// ---- user-defined exceptions for the ATM ----
class InsufficientFundsException extends Exception {          // checked: the caller must decide what to tell the customer
    InsufficientFundsException(String m) { super(m); }
}
class AccountNotFoundException extends Exception {            // checked
    AccountNotFoundException(String m) { super(m); }
}
class CardDataException extends Exception {                   // checked: card file unreadable/corrupt (wraps the low-level cause)
    CardDataException(String m, Throwable cause) { super(m, cause); }
}
class InvalidAmountException extends IllegalArgumentException { // unchecked: caller supplied a bad argument (negative / not a multiple of 100)
    InvalidAmountException(String m) { super(m); }
}

/** Q1 - ATM withdrawal using try / multi-catch / finally. */
public class Atm {
    static int finallyRuns = 0;       // how many times the finally block executed
    static String path = "";          // which catch block handled the last call (for the test table)

    static Map<Long, Long> load(BufferedReader r) throws IOException, CardDataException {
        Map<Long, Long> bal = new HashMap<>();
        String line;
        while ((line = r.readLine()) != null) {
            try {
                String[] p = line.split(",");
                bal.put(Long.parseLong(p[0].trim()), Long.parseLong(p[1].trim()));
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                throw new CardDataException("Corrupt record: '" + line + "'", e);   // chaining: keep the cause
            }
        }
        return bal;
    }

    /** returns the cash dispensed, or 0 if the withdrawal failed */
    static int withdraw(Scanner in, String cardFile) {
        BufferedReader reader = null;
        path = "(none - success)";
        try {
            long acct = in.nextLong();                      // InputMismatchException if letters / NoSuchElementException if empty
            int amount = Integer.parseInt(in.next());       // NumberFormatException if letters
            if (amount <= 0 || amount % 100 != 0)
                throw new InvalidAmountException("Amount must be a positive multiple of 100 but was " + amount);
            reader = new BufferedReader(new FileReader(cardFile));   // FileNotFoundException if the card-reader file is missing
            Map<Long, Long> bal = load(reader);
            Long balance = bal.get(acct);
            if (balance == null) throw new AccountNotFoundException("No account " + acct);
            if (amount > balance) throw new InsufficientFundsException("Balance " + balance + " < requested " + amount);
            System.out.println("   DISPENSED " + amount + " (new balance " + (balance - amount) + ")");
            return amount;
        } catch (InsufficientFundsException e) {                       // 1 specific business failures first
            path = "catch(InsufficientFundsException)"; System.out.println("   Declined: " + e.getMessage());
        } catch (AccountNotFoundException | CardDataException e) {     // 2 multi-catch: two unrelated checked types, same handling
            path = "catch(AccountNotFound | CardData)"; System.out.println("   Card problem: " + e.getMessage());
        } catch (InvalidAmountException e) {                           // 3 subclass of IllegalArgumentException (unchecked)
            path = "catch(InvalidAmountException)"; System.out.println("   Invalid amount: " + e.getMessage());
        } catch (InputMismatchException | NumberFormatException e) {   // 4 multi-catch: unrelated unchecked types
            path = "catch(InputMismatch | NumberFormat)"; System.out.println("   Please type digits only (" + e.getClass().getSimpleName() + ")");
        } catch (NoSuchElementException e) {                           // 5 PARENT of InputMismatchException => must come after it
            path = "catch(NoSuchElementException)"; System.out.println("   No input received");
        } catch (FileNotFoundException e) {                            // 6 subclass of IOException => before IOException
            path = "catch(FileNotFoundException)"; System.out.println("   Card reader unavailable: " + e.getMessage());
        } catch (IOException e) {                                      // 7 any other I/O failure
            path = "catch(IOException)"; System.out.println("   I/O error: " + e.getMessage());
        } finally {                                                    // runs on success, on every catch above, and on UNCAUGHT exceptions
            finallyRuns++;
            if (reader != null) { try { reader.close(); } catch (IOException ignore) { } }
            System.out.println("   [finally] card ejected, reader closed");
        }
        return 0;
    }
}
