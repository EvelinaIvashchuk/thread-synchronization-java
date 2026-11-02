package deadlock;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * [КРОК 3, усунення 1] Впорядкування замків: кожен об'єкт має унікальний номер,
 * і монітори завжди захоплюються у порядку зростання номера.
 * Порушується умова Коффмана «циклічне очікування».
 */
public class LockOrderingDemo {

    static final AtomicInteger NEXT_ID = new AtomicInteger();

    static class Conflict {
        private final int id = NEXT_ID.getAndIncrement();         // унікальний номер у конструкторі
        private final String name;

        Conflict(String name) {
            this.name = name;
        }

        void bow(Conflict other) {
            Conflict first = this.id < other.id ? this : other;   // менший id – перший
            Conflict second = (first == this) ? other : this;
            synchronized (first) {
                DeadlockDemo.sleep(10);                           // та сама затримка, що й у DeadlockDemo
                synchronized (second) {
                    System.out.printf("%s: %s вклонився мені%n", name, other.name);
                    System.out.printf("%s: %s вклонився у відповідь%n", other.name, name);
                }
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Conflict alice = new Conflict("Аліса");
        Conflict bob = new Conflict("Богдан");
        int rounds = 5;

        Thread t1 = new Thread(() -> { for (int i = 0; i < rounds; i++) alice.bow(bob); }, "T1");
        Thread t2 = new Thread(() -> { for (int i = 0; i < rounds; i++) bob.bow(alice); }, "T2");
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        System.out.printf("Обидва потоки виконали по %d поклонів – deadlock немає%n", rounds);
    }
}
