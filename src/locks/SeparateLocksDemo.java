package locks;

/** Два незалежні лічильники під ОДНИМ монітором this: потоки заважають один одному без потреби. */
class SharedLockCounters {
    private int a = 0, b = 0;

    public synchronized void incA() throws InterruptedException {
        int t = a;
        Thread.sleep(50);
        a = t + 1;
    }

    public synchronized void incB() throws InterruptedException {
        int t = b;
        Thread.sleep(50);
        b = t + 1;
    }

    public synchronized int a() { return a; }
    public synchronized int b() { return b; }
}

/** Ті самі лічильники з ОКРЕМИМИ об'єктами-замками: a і b можна змінювати одночасно. */
class TwoCounters {
    private final Object lockA = new Object();          // окремий замок для a
    private final Object lockB = new Object();          // окремий замок для b
    private int a = 0, b = 0;

    public void incA() throws InterruptedException {
        synchronized (lockA) {
            int t = a;
            Thread.sleep(50);
            a = t + 1;
        }
    }

    public void incB() throws InterruptedException {
        synchronized (lockB) {
            int t = b;
            Thread.sleep(50);
            b = t + 1;
        }
    }

    public int a() { synchronized (lockA) { return a; } }
    public int b() { synchronized (lockB) { return b; } }
}

/**
 * [КРОК 2, спосіб Б] Синхронізовані блоки з окремими замками.
 * Потік A 20 разів збільшує лічильник a, потік B – лічильник b; порівнюємо час
 * для «спільного замка» і «окремих замків».
 */
public class SeparateLocksDemo {

    interface Inc { void run() throws InterruptedException; }

    public static void main(String[] args) throws InterruptedException {
        SharedLockCounters shared = new SharedLockCounters();
        double tShared = twoThreads(shared::incA, shared::incB);
        System.out.printf("Спільний замок this: a = %d, b = %d, час %.0f мс%n", shared.a(), shared.b(), tShared);

        TwoCounters separate = new TwoCounters();
        double tSeparate = twoThreads(separate::incA, separate::incB);
        System.out.printf("Окремі замки:        a = %d, b = %d, час %.0f мс%n", separate.a(), separate.b(), tSeparate);
    }

    /** Два потоки, кожен по 20 разів викликає свою операцію; повертає час у мс. */
    static double twoThreads(Inc incA, Inc incB) throws InterruptedException {
        Thread ta = new Thread(() -> repeat(incA), "A");
        Thread tb = new Thread(() -> repeat(incB), "B");
        long t0 = System.nanoTime();
        ta.start();
        tb.start();
        ta.join();
        tb.join();
        return (System.nanoTime() - t0) / 1e6;
    }

    static void repeat(Inc inc) {
        try {
            for (int i = 0; i < 20; i++) inc.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
