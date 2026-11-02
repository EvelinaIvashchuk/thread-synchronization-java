package race;

/** Лічильник без синхронізації: операція «читання – зміна – запис» не атомарна. */
class Counter {
    private int c = 0;

    public void increment() throws InterruptedException {
        int a = c;                 // (1) читання спільного значення в локальну змінну
        Thread.sleep(150);         // (2) штучна затримка: вікно для гонки
        a++;                       // (3) зміна локальної копії
        c = a;                     // (4) запис назад – може затерти чужий результат
    }

    public void decrement() throws InterruptedException {
        int a = c;
        Thread.sleep(100);         // інша затримка, щоб перетини були менш регулярними
        a--;
        c = a;
    }

    public int value() {
        return c;
    }
}

/**
 * [КРОК 1] Демонстрація втрачених оновлень: два потоки одночасно змінюють один лічильник.
 *
 * Аргументи:
 *   java race.RaceDemo        – обидва потоки по 5 разів викликають increment() (очікувано 10)
 *   java race.RaceDemo dec    – T1 викликає increment(), T2 – decrement() (очікувано 0)
 */
public class RaceDemo {

    public static void main(String[] args) throws InterruptedException {
        boolean incDec = args.length > 0 && args[0].equals("dec");
        Counter counter = new Counter();

        Thread t1 = new Thread(loop(counter, true), "T1");
        Thread t2 = new Thread(loop(counter, !incDec), "T2");
        t1.start();
        t2.start();                                    // старт у сусідніх інструкціях
        t1.join();
        t2.join();

        System.out.printf("Очікувано %d, отримано %d%n", incDec ? 0 : 10, counter.value());
    }

    /** П'ять викликів increment() або decrement() з виводом поточного значення. */
    static Runnable loop(Counter counter, boolean inc) {
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
