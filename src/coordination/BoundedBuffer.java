package coordination;

/** Спільний інтерфейс правильного буфера і буферів із навмисними помилками. */
interface Buffer<T> {
    void put(T x) throws InterruptedException;
    T take() throws InterruptedException;
}

/** [КРОК 4] Обмежений кільцевий буфер із захищеними блоками wait()/notifyAll(). */
class BoundedBuffer<T> implements Buffer<T> {
    protected final Object[] items;
    protected int head = 0, tail = 0, count = 0;

    BoundedBuffer(int capacity) {
        items = new Object[capacity];
    }

    @Override
    public synchronized void put(T x) throws InterruptedException {
        while (count == items.length) wait();          // буфер повний – чекаємо
        items[tail] = x;
        tail = (tail + 1) % items.length;
        count++;
        notifyAll();                                   // розбудити споживачів
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized T take() throws InterruptedException {
        while (count == 0) wait();                     // буфер порожній – чекаємо
        T x = (T) items[head];
        items[head] = null;
        head = (head + 1) % items.length;
        count--;
        notifyAll();                                   // розбудити виробників
        return x;
    }
}

/** Помилка 1: if замість while – після пробудження умова не перевіряється повторно. */
class IfBuffer<T> extends BoundedBuffer<T> {
    IfBuffer(int capacity) {
        super(capacity);
    }

    @Override
    public synchronized void put(T x) throws InterruptedException {
        if (count == items.length) wait();             // ПОМИЛКА: if
        items[tail] = x;
        tail = (tail + 1) % items.length;
        count++;
        notifyAll();
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized T take() throws InterruptedException {
        if (count == 0) wait();                        // ПОМИЛКА: if
        T x = (T) items[head];
        items[head] = null;
        head = (head + 1) % items.length;
        count--;
        notifyAll();
        return x;
    }
}

/** Помилка 2: notify() замість notifyAll() – може розбудити «не того» (виробник виробника). */
class NotifyBuffer<T> extends BoundedBuffer<T> {
    NotifyBuffer(int capacity) {
        super(capacity);
    }

    @Override
    public synchronized void put(T x) throws InterruptedException {
        while (count == items.length) wait();
        items[tail] = x;
        tail = (tail + 1) % items.length;
        count++;
        notify();                                      // ПОМИЛКА: notify
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized T take() throws InterruptedException {
        while (count == 0) wait();
        T x = (T) items[head];
        items[head] = null;
        head = (head + 1) % items.length;
        count--;
        notify();                                      // ПОМИЛКА: notify
        return x;
    }
}

/** Помилка 3: put без synchronized – wait()/notifyAll() викликаються без монітора. */
class NoSyncBuffer<T> extends BoundedBuffer<T> {
    NoSyncBuffer(int capacity) {
        super(capacity);
    }

    @Override
    public void put(T x) throws InterruptedException {         // ПОМИЛКА: немає synchronized
        while (count == items.length) wait();
        items[tail] = x;
        tail = (tail + 1) % items.length;
        count++;
        notifyAll();                                   // IllegalMonitorStateException
    }
}
