package deadlock;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

/**
 * [КРОК 3] Моделювання взаємного блокування: два об'єкти взаємно «вклоняються»,
 * і кожен синхронізований метод викликає синхронізований метод партнера.
 *
 * Аргументи:
 *   java deadlock.DeadlockDemo           – сторожовий потік знаходить deadlock через ThreadMXBean і завершує програму
 *   java deadlock.DeadlockDemo nowatch   – без сторожа: програма зависає, deadlock фіксується через jps + jstack
 */
public class DeadlockDemo {

    static class Conflict {
        private final String name;

        Conflict(String name) {
            this.name = name;
        }

        String getName() {
            return name;
        }

        synchronized void bow(Conflict other) {                   // утримує монітор this
            System.out.printf("%s: %s вклонився мені%n", name, other.getName());
            sleep(10);                                            // робить deadlock стабільним
            other.bowBack(this);                                  // потрібен монітор other
        }

        synchronized void bowBack(Conflict other) {
            System.out.printf("%s: %s вклонився у відповідь%n", name, other.getName());
        }
    }

    public static void main(String[] args) {
        boolean watch = !(args.length > 0 && args[0].equals("nowatch"));
        if (watch) startWatchdog();

        Conflict alice = new Conflict("Аліса");
        Conflict bob = new Conflict("Богдан");
        new Thread(() -> alice.bow(bob), "T1").start();           // T1: this = alice, other = bob
        new Thread(() -> bob.bow(alice), "T2").start();           // T2: this = bob, other = alice
    }

    /** Сторожовий демон-потік: щосекунди питає JVM, чи є потоки у взаємному блокуванні. */
    static void startWatchdog() {
        Thread watchdog = new Thread(() -> {
            ThreadMXBean mx = ManagementFactory.getThreadMXBean();
            try {
                while (true) {
                    Thread.sleep(1000);
                    long[] ids = mx.findDeadlockedThreads();
                    if (ids != null) {
                        for (ThreadInfo ti : mx.getThreadInfo(ids)) {
                            System.out.printf("DEADLOCK: %s чекає %s, який утримує %s%n",
                                    ti.getThreadName(), ti.getLockName(), ti.getLockOwnerName());
                        }
                        System.exit(1);
                    }
                }
            } catch (InterruptedException ignored) {
            }
        }, "watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
