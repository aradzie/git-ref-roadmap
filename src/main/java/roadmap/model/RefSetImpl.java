package roadmap.model;

import roadmap.Primes;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

/** Optimized set of refs. */
class RefSetImpl extends AbstractSet<Ref> {
    /** Immutable empty instance. */
    public static final RefSetImpl EMPTY = new RefSetImpl() {
        @Override public boolean add(Ref ref) {
            throw new UnsupportedOperationException();
        }

        @Override public boolean addAll(Collection<? extends Ref> c) {
            throw new UnsupportedOperationException();
        }

        @Override public void clear() {}
    };

    public static RefSetImpl safe(RefSetImpl refs) {
        if (refs == null || refs.isEmpty()) {
            return EMPTY;
        }
        return refs;
    }

    /** Use linear scanning of table if set size is below this threshold. */
    private static final int SMALL = 3;
    /** Hash table data. */
    private Ref[] data;
    /** Number of added elements. */
    private int size;
    /** Hash code of this set for fast comparison to other sets. */
    private int hashCode;

    RefSetImpl() {
        data = new Ref[SMALL];
    }

    RefSetImpl(Collection<Ref> c) {
        if (c.size() < SMALL) {
            data = new Ref[SMALL];
        }
        else {
            data = new Ref[c.size() * 2];
        }
        addAll(c);
    }

    private void put(int index, Ref ref) {
        data[index] = ref;
        size++;
        hashCode = hashCode ^ ref.hashCode();
    }

    @Override public boolean add(Ref ref) {
        // Probably would fit in a small array with linear scanning.
        if (data.length == SMALL) {
            // Test if already exist in small array.
            for (int i = 0; i < data.length; i++) {
                if (eq(data[i], ref)) {
                    return false;
                }
            }
            // Small array must have free slots.
            if (size < data.length) {
                // Find free slot to add element.
                for (int i = 0; i < data.length; i++) {
                    if (data[i] == null) {
                        put(i, ref);
                        return true;
                    }
                }
            }
            // Must add new element to a full small array.
            rehash(7);
        }
        int p = hc(ref) % data.length;
        while (data[p] != null) {
            if (eq(data[p], ref)) {
                return false;
            }
            if (++p == data.length) {
                p = 0;
            }
        }
        put(p, ref);
        if (size > data.length * 0.7) {
            rehash(Primes.nextPrime(size * 2));
        }
        return true;
    }

    public final boolean contains(Ref ref) {
        if (data.length == SMALL) {
            // Linear scanning.
            for (int i = 0; i < data.length; i++) {
                if (eq(data[i], ref)) {
                    return true;
                }
            }
        }
        else {
            // Hash table lookup.
            int p = hc(ref) % data.length;
            while (data[p] != null) {
                if (eq(data[p], ref)) {
                    return true;
                }
                if (++p == data.length) {
                    p = 0;
                }
            }
        }
        return false;
    }

    @Override public final boolean contains(Object o) {
        return o instanceof Ref && contains((Ref) o);
    }

    public final boolean containsAll(RefSetImpl that) {
        if (this == that) {
            return true;
        }
        for (Ref ref : that) {
            if (!contains(ref)) {
                return false;
            }
        }
        return true;
    }

    @Override public final boolean containsAll(Collection<?> c) {
        if (c instanceof RefSetImpl) {
            return containsAll((RefSetImpl) c);
        }
        return super.containsAll(c);
    }

    @Override public final boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override public final int size() {
        return size;
    }

    @Override public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override public final Iterator<Ref> iterator() {
        return new IteratorImpl();
    }

    public final boolean equals(RefSetImpl that) {
        return this == that
                || size == that.size
                && hashCode == that.hashCode
                && containsAll(that);
    }

    @Override public final boolean equals(Object o) {
        if (o instanceof RefSetImpl) {
            return equals((RefSetImpl) o);
        }
        return super.equals(o);
    }

    @Override public final int hashCode() {
        return hashCode;
    }

    private void rehash(int size) {
        Ref[] data = new Ref[size];
        for (Ref ref : this.data) {
            if (ref != null) {
                int p = hc(ref) % data.length;
                while (data[p] != null) {
                    if (++p == data.length) {
                        p = 0;
                    }
                }
                data[p] = ref;
            }
        }
        this.data = data;
    }

    static int hc(Ref r) {
        return r.hashCode() >>> 1;
    }

    static boolean eq(Ref a, Ref b) {
        // We assume all refs always come from the same set.
        return a == b;
    }

    final class IteratorImpl implements Iterator<Ref> {
        int index;

        IteratorImpl() {
            findNext();
        }

        void findNext() {
            while (index < data.length) {
                if (data[index] != null) {
                    break;
                }
                index++;
            }
        }

        @Override public boolean hasNext() {
            return index < data.length;
        }

        @Override public Ref next() {
            if (index < data.length) {
                Ref r = data[index++];
                findNext();
                return r;
            }
            throw new NoSuchElementException();
        }

        @Override public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
