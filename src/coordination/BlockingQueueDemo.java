package coordination;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * [КРОК 4] Та сама задача на ArrayBlockingQueue: put() блокується при заповненій черзі,
 * take() – при порожній. Споживачі завершуються «отруйною пігулкою» POISON.
 *
 * Аргументи: [N] (за замовчуванням 10 000)
 */
public class BlockingQueueDemo {

    static final int POISON = -1;                                 // маркер завершення

    public static void main(String[] args) throws InterruptedException {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 10_000;
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(5);  // та сама місткість
        AtomicLong total = new AtomicLong();

        Runnable producer = () -> {
            try {
                for (int i = 1; i <= n; i++) queue.put(i);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        Runnable consumer = () -> {
            try {
                long sum = 0;
                for (int x = queue.take(); x != POISON; x = queue.take()) sum += x;
                total.addAndGet(sum);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Thread[] producers = { new Thread(producer, "P1"), new Thread(producer, "P2") };
        Thread[] consumers = { new Thread(consumer, "C1"), new Thread(consumer, "C2") };
        for (Thread t : producers) t.start();
        for (Thread t : consumers) t.start();

        for (Thread t : producers) t.join();
        for (Thread t : consumers) queue.put(POISON);             // по одному маркеру на кожного споживача
        for (Thread t : consumers) t.join();

        long expected = (long) n * (n + 1);                       // 2 · N(N+1)/2
        System.out.printf("ArrayBlockingQueue(5): 2 виробники по %d чисел, 2 споживачі%n", n);
        System.out.printf("Сума = %d, очікувано %d – %s%n", total.get(), expected,
                total.get() == expected ? "OK" : "ПОМИЛКА");
    }
}
