package variant;

import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

/**
 * [КРОК 5] Варіант 5: лічильник відвідувань веб-сторінок.
 * Потоки реєструють відвідування випадкових URL; інваріант: сума всіх лічильників дорівнює
 * кількості подій, а лічильник кожного URL – кількості його відвідувань.
 *
 * Аргументи:
 *   java variant.WebVisitsDemo          – наївна і потокобезпечні версії, перевірка інваріанта
 *   java variant.WebVisitsDemo bench    – вимірювання часу для 2, 4, 8 потоків
 */
public class WebVisitsDemo {

    static final int URLS = 20;                                  // 20 «популярних» сторінок
    static final String[] URL = new String[URLS];
    static {
        for (int i = 0; i < URLS; i++) URL[i] = "/page/" + i;
    }
    static final long SEED = 42;

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("bench")) {
            bench();
            return;
        }
        int threads = 4, events = 1_000_000;
        int[][] plan = plan(threads, events);
        long[] expected = expected(plan);
        System.out.printf("%d потоки, %d подій, %d URL%n", threads, events, URLS);

        check("Наївна (HashMap без синхронізації)", NaiveCounter::new, plan, expected);
        check("synchronized HashMap", SyncMapCounter::new, plan, expected);
        check("HashMap + ReentrantLock", LockMapCounter::new, plan, expected);
        check("ConcurrentHashMap + AtomicLong", AtomicLongCounter::new, plan, expected);
        check("ConcurrentHashMap + LongAdder", LongAdderCounter::new, plan, expected);
    }

    /**
     * Наперед згенеровані номери URL для кожного потоку: свій Random(SEED + t),
     * тож послідовність відома і можна порахувати точну кількість відвідувань кожного URL.
     */
    static int[][] plan(int threads, int events) {
        int[][] plan = new int[threads][events / threads];
        for (int t = 0; t < threads; t++) {
            Random rnd = new Random(SEED + t);
            for (int i = 0; i < plan[t].length; i++) plan[t][i] = rnd.nextInt(URLS);
        }
        return plan;
    }

    /** Еталон: послідовний підрахунок відвідувань кожного URL. */
    static long[] expected(int[][] plan) {
        long[] exp = new long[URLS];
        for (int[] p : plan) for (int u : p) exp[u]++;
        return exp;
    }

    /** Запускає потоки за планом і повертає час у мс. */
    static double run(VisitCounter counter, int[][] plan) throws InterruptedException {
        Thread[] th = new Thread[plan.length];
        for (int t = 0; t < plan.length; t++) {
            int[] mine = plan[t];
            th[t] = new Thread(() -> {
                for (int u : mine) counter.visit(URL[u]);
            }, "visitor-" + t);
        }
        long t0 = System.nanoTime();
        for (Thread t : th) t.start();
        for (Thread t : th) t.join();
        return (System.nanoTime() - t0) / 1e6;
    }

    /** Перевірка інваріанта: сума і кожен лічильник окремо. */
    static boolean invariant(VisitCounter counter, long[] expected, StringBuilder report) {
        Map<String, Long> snap = counter.snapshot();
        long sum = 0, total = Arrays.stream(expected).sum();
        int wrong = 0;
        for (int u = 0; u < URLS; u++) {
            long got = snap.getOrDefault(URL[u], 0L);
            sum += got;
            if (got != expected[u]) wrong++;
        }
        report.append(String.format("сума = %d з %d (втрачено %d), неправильних URL: %d з %d",
                sum, total, total - sum, wrong, URLS));
        return sum == total && wrong == 0;
    }

    static void check(String name, Supplier<VisitCounter> factory, int[][] plan, long[] expected)
            throws InterruptedException {
        VisitCounter counter = factory.get();
        StringBuilder report = new StringBuilder();
        boolean ok;
        try {
            run(counter, plan);
            ok = invariant(counter, expected, report);
        } catch (RuntimeException e) {
            ok = false;
            report.append(e);
        }
        System.out.printf("%-36s %s – %s%n", name, ok ? "OK" : "ІНВАРІАНТ ПОРУШЕНО", report);
    }

    /** Час однієї реалізації: 2 прогрівальні запуски, потім медіана з 3 (з перевіркою інваріанта). */
    static double median(Supplier<VisitCounter> factory, int[][] plan, long[] expected) throws Exception {
        for (int w = 0; w < 2; w++) run(factory.get(), plan);
        double[] times = new double[3];
        for (int k = 0; k < 3; k++) {
            VisitCounter c = factory.get();
            times[k] = run(c, plan);
            if (!invariant(c, expected, new StringBuilder())) throw new IllegalStateException("Інваріант порушено!");
        }
        Arrays.sort(times);
        return times[1];
    }

    static void bench() throws Exception {
        int events = 10_000_000;
        String[] names = {"synchronized HashMap", "HashMap + ReentrantLock",
                "CHM + AtomicLong", "CHM + LongAdder"};
        @SuppressWarnings("unchecked")
        Supplier<VisitCounter>[] factories = new Supplier[]{
                SyncMapCounter::new, LockMapCounter::new, AtomicLongCounter::new, LongAdderCounter::new};

        System.out.printf("Лічильник відвідувань: %d подій, %d URL, медіана з 3 після 2 прогрівів%n", events, URLS);
        System.out.printf("%-24s %10s %10s %10s%n", "Реалізація", "p = 2", "p = 4", "p = 8");
        int[] ps = {2, 4, 8};
        int[][][] plans = new int[ps.length][][];
        long[][] exps = new long[ps.length][];
        for (int i = 0; i < ps.length; i++) {
            plans[i] = plan(ps[i], events);
            exps[i] = expected(plans[i]);
        }
        for (int k = 0; k < names.length; k++) {
            StringBuilder row = new StringBuilder(String.format("%-24s", names[k]));
            for (int i = 0; i < ps.length; i++) {
                row.append(String.format(" %7.0f мс", median(factories[k], plans[i], exps[i])));
            }
            System.out.println(row + "   інваріант OK");
        }
    }
}
