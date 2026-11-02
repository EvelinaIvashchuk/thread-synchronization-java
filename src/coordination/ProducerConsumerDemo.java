package coordination;

import java.util.concurrent.atomic.AtomicLongArray;

/**
 * [КРОК 4] Виробник–споживач з обмеженим буфером: два виробники додають числа 1..N,
 * два споживачі забирають їх і накопичують суму. Перевірка: сума = 2·N(N+1)/2.
 *
 * Аргументи: [режим] [N] [місткість]
 *   java coordination.ProducerConsumerDemo               – правильний буфер (while + notifyAll)
 *   java coordination.ProducerConsumerDemo if            – помилка 1: if замість while
 *   java coordination.ProducerConsumerDemo notify 10000 1 – помилка 2: notify() замість notifyAll()
 *                                                            (з місткістю 1 зависання майже гарантоване)
 *   java coordination.ProducerConsumerDemo nosync        – помилка 3: put без synchronized
 */
public class ProducerConsumerDemo {

    public static void main(String[] args) throws InterruptedException {
        String mode = args.length > 0 ? args[0] : "ok";
        int n = args.length > 1 ? Integer.parseInt(args[1]) : 10_000;
        int capacity = args.length > 2 ? Integer.parseInt(args[2]) : 5;

        Buffer<Integer> buffer = switch (mode) {
            case "if" -> new IfBuffer<>(capacity);
            case "notify" -> new NotifyBuffer<>(capacity);
            case "nosync" -> new NoSyncBuffer<>(capacity);
            default -> new BoundedBuffer<>(capacity);
        };
        System.out.printf("Режим %s: 2 виробники по %d чисел, 2 споживачі, місткість буфера %d%n",
                mode, n, capacity);

        // своя комірка для кожного споживача; атомарний масив, щоб прочитати проміжну суму навіть після зависання
        AtomicLongArray sums = new AtomicLongArray(2);
        Thread[] threads = {
                new Thread(() -> produce(buffer, n), "P1"),
                new Thread(() -> produce(buffer, n), "P2"),
                new Thread(() -> consume(buffer, n, sums, 0), "C1"),
                new Thread(() -> consume(buffer, n, sums, 1), "C2"),
        };
        for (Thread t : threads) {
            t.setDaemon(true);                                   // щоб зависання не тримало JVM
            t.start();
        }

        long deadline = System.currentTimeMillis() + 5000;      // чекаємо не більше 5 с
        for (Thread t : threads) t.join(Math.max(1, deadline - System.currentTimeMillis()));

        StringBuilder alive = new StringBuilder();
        for (Thread t : threads) {
            if (t.isAlive()) alive.append(' ').append(t.getName()).append('=').append(t.getState());
        }
        long expected = (long) n * (n + 1);                      // 2 · N(N+1)/2
        if (alive.length() > 0) {
            System.out.println("ЗАВИСАННЯ через 5 с, живі потоки:" + alive);
        }
        long sum = sums.get(0) + sums.get(1);
        System.out.printf("Сума = %d, очікувано %d – %s%n", sum, expected,
                sum == expected && alive.length() == 0 ? "OK" : "ПОМИЛКА");
    }

    static void produce(Buffer<Integer> buffer, int n) {
        try {
            for (int i = 1; i <= n; i++) buffer.put(i);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            System.out.printf("%s: %s%n", Thread.currentThread().getName(), e);
        }
    }

    /** Кожен споживач забирає рівно n елементів (усього 2n – стільки, скільки покладуть виробники). */
    static void consume(Buffer<Integer> buffer, int n, AtomicLongArray sums, int idx) {
        try {
            for (int i = 0; i < n; i++) sums.addAndGet(idx, buffer.take());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            System.out.printf("%s: %s%n", Thread.currentThread().getName(), e);
        }
    }
}
