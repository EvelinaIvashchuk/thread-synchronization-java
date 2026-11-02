package locks;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/** Спосіб В: явний замок ReentrantLock зі спробою захоплення з таймаутом. */
class LockCounter {
    private final Lock lock = new ReentrantLock();
    private final long timeoutMs;
    private int c = 0;
    final AtomicInteger skipped = new AtomicInteger();          // скільки операцій пропущено

    LockCounter(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public void increment() throws InterruptedException {
        if (lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS)) {   // чекаємо не більше timeoutMs
            try {
                int a = c;
                Thread.sleep(150);
                c = a + 1;
            } finally {
                lock.unlock();                                  // звільнення гарантоване
            }
        } else {
            skipped.incrementAndGet();
            System.out.printf("%s: замок зайнятий понад %d мс, операцію пропущено%n",
                    Thread.currentThread().getName(), timeoutMs);
        }
    }

    public int value() {
        lock.lock();
        try {
            return c;
        } finally {
            lock.unlock();
        }
    }
}

/**
 * [КРОК 2, спосіб В] ReentrantLock з tryLock(таймаут) у блоці try-finally.
 *
 * Аргументи:
 *   java locks.TryLockDemo        – таймаут 1000 мс: усі 10 операцій виконуються
 *   java locks.TryLockDemo 100    – таймаут 100 мс: частина операцій пропускається, але програма не зависає
 */
public class TryLockDemo {

    public static void main(String[] args) throws InterruptedException {
        long timeout = args.length > 0 ? Long.parseLong(args[0]) : 1000;
        LockCounter counter = new LockCounter(timeout);

        Runnable task = () -> {
            try {
                for (int i = 0; i < 5; i++) counter.increment();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        Thread t1 = new Thread(task, "T1");
        Thread t2 = new Thread(task, "T2");
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        int skipped = counter.skipped.get();
        System.out.printf("Таймаут %d мс: виконано %d, пропущено %d, c = %d (%s)%n",
                timeout, 10 - skipped, skipped, counter.value(),
                counter.value() == 10 - skipped ? "збігається з кількістю виконаних" : "ПОМИЛКА");
    }
}
