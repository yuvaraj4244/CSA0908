import java.io.*;
import java.nio.file.*;
import java.time.LocalTime;
import java.util.*;

// ================= exception hierarchy =================
/** Checked base type: every business-rule failure of the portal. Callers must decide how to react. */
class ExamException extends Exception {
    ExamException(String m) { super(m); }
    ExamException(String m, Throwable cause) { super(m, cause); }
}
class ExamClosedException extends ExamException {            // checked: expected, recoverable (student is simply told)
    ExamClosedException(String m) { super(m); }
}
class NotRegisteredException extends ExamException {         // checked
    NotRegisteredException(String m) { super(m); }
}
class AnswerTooLargeException extends ExamException {        // checked: student can fix it (shrink the file)
    AnswerTooLargeException(String m) { super(m); }
}
class SubmissionStorageException extends ExamException {     // checked: infrastructure failure that wraps the low-level cause
    SubmissionStorageException(String m, Throwable cause) { super(m, cause); }
    SubmissionStorageException(String m) { super(m); }       // ONLY for the "lost cause" demonstration (bad practice)
}
class InvalidRequestException extends IllegalArgumentException { // UNCHECKED: programmer/caller bug (null or blank argument)
    InvalidRequestException(String m) { super(m); }
}

// ================= storage layer =================
class Storage {
    final Path root;
    boolean losesCause = false;                              // switch used by the "lost cause" experiment
    Storage(Path root) { this.root = root; }
    void save(String fileName, long sizeKb) throws SubmissionStorageException {
        try {
            Path target = root.resolve(fileName);
            Files.writeString(target, "answer-bytes:" + sizeKb);   // real I/O: fails if the directory does not exist
            System.out.println("   [storage] saved " + target.getFileName());
        } catch (IOException e) {
            if (losesCause) throw new SubmissionStorageException("Could not store " + fileName);        // BAD: cause dropped
            throw new SubmissionStorageException("Could not store " + fileName, e);                      // GOOD: chained
        }
    }
}

// ================= service layer =================
class ExamService {
    static final long MAX_KB = 500;
    final Map<String, Set<String>> registrations = new HashMap<>();
    final LocalTime closes;
    final Storage storage;
    ExamService(Storage s, LocalTime closes) { this.storage = s; this.closes = closes; }
    void register(String student, String course) { registrations.computeIfAbsent(course, c -> new HashSet<>()).add(student); }

    void submit(String student, String course, String fileName, long sizeKb, LocalTime now) throws ExamException {
        if (student == null || student.isBlank()) throw new InvalidRequestException("student id is required");   // unchecked
        if (now.isAfter(closes)) throw new ExamClosedException("Exam window closed at " + closes + " (submitted at " + now + ")");
        if (!registrations.getOrDefault(course, Set.of()).contains(student))
            throw new NotRegisteredException(student + " is not registered for " + course);
        if (sizeKb > MAX_KB) throw new AnswerTooLargeException(sizeKb + " KB exceeds the " + MAX_KB + " KB limit");
        storage.save(fileName, sizeKb);                       // SubmissionStorageException propagates unchanged through this layer
    }
}

// ================= controller layer =================
class ExamController {
    final ExamService service;
    final List<String> log = new ArrayList<>();
    ExamController(ExamService s) { service = s; }

    /** returns the message shown to the student */
    String submit(String student, String course, String file, long kb, LocalTime now) {
        try {
            service.submit(student, course, file, kb, now);
            return "Submission accepted: " + file;
        } catch (ExamClosedException e) {
            return "Sorry, the exam window is closed.";
        } catch (NotRegisteredException e) {
            return "You are not registered for " + course + ". Contact the exam cell.";
        } catch (AnswerTooLargeException e) {
            return "Your file is too large (limit " + ExamService.MAX_KB + " KB). Please compress it and retry.";
        } catch (SubmissionStorageException e) {
            StringWriter sw = new StringWriter(); e.printStackTrace(new PrintWriter(sw));
            log.add(sw.toString());                           // the FULL chain (with the IOException) goes to the log, not to the student
            return "We could not save your answer. Please try again in a minute.";
        } catch (ExamException e) {                           // safety net for future ExamException subclasses
            log.add("unexpected ExamException: " + e);
            return "Submission failed. Reference logged.";
        } catch (InvalidRequestException e) {                 // unchecked: a bug in the caller
            log.add("bad request: " + e.getMessage());
            return "Invalid request.";
        }
    }
}
