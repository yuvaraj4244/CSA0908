import java.util.concurrent.ConcurrentLinkedQueue;

/** Common state for the answer-script scanner stations. */
class Scan {
    static final ConcurrentLinkedQueue<String> finishOrder = new ConcurrentLinkedQueue<>();
    static void work(int scripts, int sleepMs) throws InterruptedException {
        for (int i = 0; i < scripts; i++) {
            Thread.sleep(sleepMs);                                   // paper handling / camera time
            double x = 0; for (int k = 0; k < 20_000; k++) x += Math.sqrt(k);   // image processing
            if (x < 0) System.out.println(x);
        }
    }
}

/** Way 1: extend Thread. */
class StationThread extends Thread {
    private final int scripts, sleepMs; volatile int scanned;
    StationThread(String name, int scripts, int sleepMs) { super(name); this.scripts = scripts; this.sleepMs = sleepMs; }
    @Override public void run() {
        try { Scan.work(scripts, sleepMs); scanned = scripts; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        Scan.finishOrder.add(getName());
    }
    int scanned() { return scanned; }
}

/** Way 2: implement Runnable (the task is separate from the thread that runs it). */
class StationTask implements Runnable {
    final String name; private final int scripts, sleepMs; volatile int scanned;
    StationTask(String name, int scripts, int sleepMs) { this.name = name; this.scripts = scripts; this.sleepMs = sleepMs; }
    @Override public void run() {
        try { Scan.work(scripts, sleepMs); scanned = scripts; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        Scan.finishOrder.add(name);
    }
}
