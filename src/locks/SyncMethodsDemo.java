package locks;

/** Спосіб А: усі методи synchronized – монітор this захищає читання, зміну і запис. */
class SyncCounter {
    private int c = 0;

    public synchronized void increment() throws InterruptedException {
        int a = c;
        Thread.sleep(150);         // sleep() усередині synchronized НЕ звільняє монітор
        a++;
        c = a;
    }

    public synchronized void decrement() throws InterruptedException {
        int a = c;
        Thread.sleep(100);
        a--;
        c = a;
    }

    public synchronized int value() {
        return c;
    }
}

/**
 * [КРОК 2, спосіб А] Синхронізовані методи.
 *
 * Аргументи:
 *   java locks.SyncMethodsDemo       – два потоки по 5 increment() (очікувано 10)
 *   java locks.SyncMethodsDemo dec   – T1 increment(), T2 decrement() (очікувано 0)
 */
public class SyncMethodsDemo {

    public static void main(String[] args) throws InterruptedException {
        boolean incDec = args.length > 0 && args[0].equals("dec");
        SyncCounter counter = new SyncCounter();

        Thread t1 = new Thread(loop(counter, true), "T1");
        Thread t2 = new Thread(loop(counter, !incDec), "T2");
        long t0 = System.nanoTime();
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        System.out.printf("Очікувано %d, отримано %d; час %.0f мс%n",
                incDec ? 0 : 10, counter.value(), (System.nanoTime() - t0) / 1e6);
    }

    static Runnable loop(SyncCounter counter, boolean inc) {
        return () -> {
            try {
                for (int i = 0; i < 5; i++) {
                    if (inc) counter.increment();
                    else counter.decrement();
                    System.out.printf("%s: c = %d%n", Thread.currentThread().getName(), counter.value());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }
}
