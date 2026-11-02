package variant;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntSupplier;

/**
 * [КРОК 5] Спільний стенд: один лічильник, реалізований трьома способами (і без синхронізації
 * для порівняння), 10⁷ інкрементів пулом з p потоків. Затримок sleep() немає.
 * 2 прогрівальні запуски, потім медіана з 3; після кожного запуску перевіряється кінцеве значення.
 */
public class CounterBench {

    static final long OPS = 10_000_000L;

    /** Без синхронізації – найшвидший і неправильний. */
    static class PlainCounter {
        private int c;
        void increment() { c++; }
        int value() { return c; }
    }

    static class SyncCounter {
        private int c;
        synchronized void increment() { c++; }
        synchronized int value() { return c; }
    }

    static class LockCounter {
        private final ReentrantLock lock = new ReentrantLock();
        private int c;

        void increment() {
            lock.lock();
            try {
                c++;
            } finally {
                lock.unlock();
            }
        }

        int value() {
            lock.lock();
            try {
                return c;
            } finally {
                lock.unlock();
            }
        }
    }

    /** Один замір: пул з p потоків, кожен виконує OPS / p операцій. Повертає час у мс. */
    static double bench(int threads, Runnable op) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        long perThread = OPS / threads;
        long t0 = System.nanoTime();
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                for (long i = 0; i < perThread; i++) op.run();
            });
        }
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.MINUTES);
        return (System.nanoTime() - t0) / 1e6;
    }

    interface Fresh {
        Runnable op();
        IntSupplier value();
    }

    /** 2 прогріви + медіана з 3; кожен запуск – новий лічильник. Повертає {час, кінцеве значення}. */
    static double[] median(int threads, java.util.function.Supplier<Fresh> factory) throws InterruptedException {
        for (int w = 0; w < 2; w++) bench(threads, factory.get().op());
        double[] times = new double[3];
        int last = 0;
        for (int k = 0; k < 3; k++) {
            Fresh f = factory.get();
            times[k] = bench(threads, f.op());
            last = f.value().getAsInt();
        }
        Arrays.sort(times);
        return new double[]{times[1], last};
    }

    public static void main(String[] args) throws InterruptedException {
        String[] names = {"без синхронізації", "synchronized", "ReentrantLock", "AtomicInteger"};
        @SuppressWarnings("unchecked")
        java.util.function.Supplier<Fresh>[] factories = new java.util.function.Supplier[]{
                () -> { PlainCounter c = new PlainCounter(); return fresh(c::increment, c::value); },
                () -> { SyncCounter c = new SyncCounter(); return fresh(c::increment, c::value); },
                () -> { LockCounter c = new LockCounter(); return fresh(c::increment, c::value); },
                () -> { AtomicInteger c = new AtomicInteger(); return fresh(c::incrementAndGet, c::get); },
        };
        int[] ps = {1, 2, 4, 8};

        System.out.printf("%d інкрементів, процесорів: %d, медіана з 3 після 2 прогрівів%n",
                OPS, Runtime.getRuntime().availableProcessors());
        System.out.printf("%-18s %9s %9s %9s %9s   %s%n", "Механізм", "p = 1", "p = 2", "p = 4", "p = 8",
                "Кінцеве значення (p = 8)");
        for (int k = 0; k < names.length; k++) {
            StringBuilder row = new StringBuilder(String.format("%-18s", names[k]));
            int value = 0;
            for (int p : ps) {
                double[] r = median(p, factories[k]);
                row.append(String.format(" %6.1f мс", r[0]));
                value = (int) r[1];
            }
            row.append(String.format("   %,d%s", value, value == OPS ? "" : " (ПОМИЛКА)"));
            System.out.println(row);
        }
    }

    static Fresh fresh(Runnable op, IntSupplier value) {
        return new Fresh() {
            public Runnable op() { return op; }
            public IntSupplier value() { return value; }
        };
    }
}
