import java.io.IOException;

/** Same program with UNCHECKED custom exceptions: note what disappears from the signatures. */
class ExamException extends RuntimeException {                         // was: extends Exception
    ExamException(String message) { super(message); }
    ExamException(String message, Throwable cause) { super(message, cause); }
}
class SubmissionStorageException extends ExamException {
    SubmissionStorageException(String message, Throwable cause) { super(message, cause); }
}
class Storage {
    void save(String fileName, long sizeKb) {                          // was: ... throws SubmissionStorageException
        try {
            if (sizeKb > 500) throw new IOException("Disk quota exceeded");
            System.out.println("Saved " + fileName);
        } catch (IOException e) {
            throw new SubmissionStorageException("Could not store " + fileName, e);
        }
    }
}
public class StorageDemoUnchecked {
    public static void main(String[] args) {
        Storage storage = new Storage();
        storage.save("answer01.pdf", 120);
        storage.save("answer02.pdf", 800);   // compiles WITHOUT try/catch; nothing forces the caller to handle it
        System.out.println("never reached");
    }
}
