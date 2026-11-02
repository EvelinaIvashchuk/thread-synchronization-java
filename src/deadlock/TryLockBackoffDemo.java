package deadlock;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;

/**
 * [КРОК 3, усунення 2] tryLock із випадковим відступом: якщо другий замок захопити не вдалося,
 * потік звільняє перший і повторює спробу після випадкової паузи.
 * Порушується умова Коффмана «утримання та очікування».
 */
public class TryLockBackoffDemo {

    static class SafeConflict {
        private final String name;
        private final ReentrantLock lock = new ReentrantLock();

        SafeConflict(String name) {
            this.name = name;
        }

        /** Повертає кількість спроб, яка знадобилася. */
        int bow(SafeConflict other) throws InterruptedException {
            int attempts = 0;
            while (true) {
                if (lock.tryLock()) {
                    try {
                        Thread.sleep(10);                         // та сама затримка, що й у DeadlockDemo
                        if (other.lock.tryLock()) {
                            try {
                                System.out.printf("%s: %s вклонився мені (спроб: %d)%n",
                                        name, other.name, attempts + 1);
                                return attempts + 1;              // обидва замки отримано
                            } finally {
                                other.lock.unlock();
                            }
                        }
                    } finally {
                        lock.unlock();                            // не вдалося – відступаємо
                    }
                }
                attempts++;
                Thread.sleep(ThreadLocalRandom.current().nextInt(1, 20));   // випадкова пауза
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        SafeConflict alice = new SafeConflict("Аліса");
        SafeConflict bob = new SafeConflict("Богдан");
        int rounds = 5;
        int[] total = new int[2];                                 // своя комірка для кожного потоку

        Thread t1 = new Thread(() -> total[0] = repeat(alice, bob, rounds), "T1");
        Thread t2 = new Thread(() -> total[1] = repeat(bob, alice, rounds), "T2");
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        System.out.printf("Обидва потоки виконали по %d поклонів; спроб усього: T1 = %d, T2 = %d%n",
                rounds, total[0], total[1]);
    }

    static int repeat(SafeConflict me, SafeConflict other, int rounds) {
        int attempts = 0;
        try {
            for (int i = 0; i < rounds; i++) attempts += me.bow(other);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return attempts;
    }
}
