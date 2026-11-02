package variant;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;

/** Лічильник відвідувань: відображення URL → кількість відвідувань. */
interface VisitCounter {
    void visit(String url);

    /** Знімок лічильників; викликається лише після завершення всіх потоків. */
    Map<String, Long> snapshot();
}

/** Наївна версія: звичайний HashMap без синхронізації – гонка «читання – зміна – запис». */
class NaiveCounter implements VisitCounter {
    private final Map<String, Long> map = new HashMap<>();

    @Override
    public void visit(String url) {
        map.put(url, map.getOrDefault(url, 0L) + 1);     // три кроки: get, +1, put – не атомарно
    }

    @Override
    public Map<String, Long> snapshot() {
        return new HashMap<>(map);
    }
}

/** synchronized HashMap: один монітор на все відображення. */
class SyncMapCounter implements VisitCounter {
    private final Map<String, Long> map = new HashMap<>();

    @Override
    public synchronized void visit(String url) {
        map.merge(url, 1L, Long::sum);
    }

    @Override
    public synchronized Map<String, Long> snapshot() {
        return new HashMap<>(map);
    }
}

/** HashMap під явним замком ReentrantLock. */
class LockMapCounter implements VisitCounter {
    private final Map<String, Long> map = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    @Override
    public void visit(String url) {
        lock.lock();
        try {
            map.merge(url, 1L, Long::sum);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Map<String, Long> snapshot() {
        lock.lock();
        try {
            return new HashMap<>(map);
        } finally {
            lock.unlock();
        }
    }
}

/** ConcurrentHashMap + AtomicLong: атомарне додавання ключа і CAS-інкремент лічильника. */
class AtomicLongCounter implements VisitCounter {
    private final ConcurrentHashMap<String, AtomicLong> map = new ConcurrentHashMap<>();

    @Override
    public void visit(String url) {
        map.computeIfAbsent(url, k -> new AtomicLong()).incrementAndGet();
    }

    @Override
    public Map<String, Long> snapshot() {
        Map<String, Long> res = new HashMap<>();
        map.forEach((k, v) -> res.put(k, v.get()));
        return res;
    }
}

/** ConcurrentHashMap + LongAdder: лічильник розбитий на комірки, потоки рідше конкурують за одну. */
class LongAdderCounter implements VisitCounter {
    private final ConcurrentHashMap<String, LongAdder> map = new ConcurrentHashMap<>();

    @Override
    public void visit(String url) {
        map.computeIfAbsent(url, k -> new LongAdder()).increment();
    }

    @Override
    public Map<String, Long> snapshot() {
        Map<String, Long> res = new HashMap<>();
        map.forEach((k, v) -> res.put(k, v.sum()));
        return res;
    }
}
