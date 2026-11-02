package locks;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * [КРОК 2, спосіб Г] Атомарна змінна AtomicInteger.
 *
 * Аргументи:
 *   java locks.AtomicDemo          – c.incrementAndGet(): одна атомарна операція, очікувано 10
 *   java locks.AtomicDemo getset   – int a = c.get(); sleep(); c.set(a + 1): атомарний клас,
 *                                    але послідовність із трьох кроків не атомарна – оновлення втрачаються
 */
public class AtomicDemo {

    public static void main(String[] args) throws InterruptedException {
        boolean getSet = args.length > 0 && args[0].equals("getset");
        AtomicInteger c = new AtomicInteger();

        Runnable task = () -> {
            try {
                for (int i = 0; i < 5; i++) {
                    if (getSet) {
                        int a = c.get();                 // атомарне читання…
                        Thread.sleep(150);               // …вікно для гонки…
                        c.set(a + 1);                    // …атомарний запис, але між ними чужі зміни губляться
                    } else {
                        c.incrementAndGet();             // читання, зміна і запис – одна CAS-операція
                    }
                    System.out.printf("%s: c = %d%n", Thread.currentThread().getName(), c.get());
                }
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

        System.out.printf("%s: очікувано 10, отримано %d%n",
                getSet ? "get() + set()" : "incrementAndGet()", c.get());
    }
}
