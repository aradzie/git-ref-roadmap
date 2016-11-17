package roadmap.graph;

import org.eclipse.jgit.lib.*;
import roadmap.util.*;

import java.util.*;

/** Set of ref graph nodes which allows mapping of object ids to nodes. */
class RefNodeSet
        extends AbstractSet<RefGraph.Node>
        implements Iterable<RefGraph.Node> {
    private static final RefGraph.Node DELETED = new RefGraph.Node(ObjectId.zeroId());
    private static final int INITIAL_CAPACITY = Primes.nextPrime(32);
    private RefGraph.Node[] data;
    private int size;

    /**
     * Fill in node set from the specified roots.
     *
     * @param roots Graph root nodes.
     * @return Set of all nodes reachable from the roots.
     */
    static RefNodeSet fill(RefNodeSet roots) {
        RefNodeSet nodes = new RefNodeSet();
        ArrayDeque<RefGraph.Node> queue = new ArrayDeque<>(roots);
        RefGraph.Node node;
        while ((node = queue.poll()) != null) {
            if (nodes.add(node)) {
                for (RefGraph.Node parent : node.getParents()) {
                    queue.add(parent);
                }
            }
        }
        return nodes;
    }

    RefNodeSet() {
        clear();
    }

    RefNodeSet(Collection<RefGraph.Node> collection) {
        this();
        addAll(collection);
    }

    @Override public int size() {
        return size;
    }

    @Override public boolean isEmpty() {
        return size == 0;
    }

    @Override public boolean add(RefGraph.Node node) {
        int p = slot(data, node);
        while (data[p] != null && data[p] != DELETED) {
            if (AnyObjectId.equals(data[p], node)) {
                return false;
            }
            p = next(data, p);
        }
        data[p] = node;
        size++;
        if (size > data.length * 0.7) {
            rehash(Primes.nextPrime(size * 2));
        }
        return true;
    }

    @Override public boolean contains(Object o) {
        return o instanceof AnyObjectId && contains((AnyObjectId) o);
    }

    boolean contains(AnyObjectId id) {
        int p = slot(data, id);
        while (data[p] != null) {
            if (AnyObjectId.equals(data[p], id)) {
                return true;
            }
            p = next(data, p);
        }
        return false;
    }

    @Override public boolean remove(Object o) {
        return o instanceof AnyObjectId && remove((AnyObjectId) o);
    }

    boolean remove(AnyObjectId id) {
        int p = slot(data, id);
        while (data[p] != null) {
            if (AnyObjectId.equals(data[p], id)) {
                delete(p, true);
                return true;
            }
            p = next(data, p);
        }
        return false;
    }

    @Override public boolean removeAll(Collection<?> c) {
        boolean modified = false;
        for (Object item : c) {
            modified |= remove(item);
        }
        return modified;
    }

    @Override public void clear() {
        data = new RefGraph.Node[INITIAL_CAPACITY];
        size = 0;
    }

    public RefGraph.Node get(AnyObjectId id) {
        int p = slot(data, id);
        while (data[p] != null) {
            if (AnyObjectId.equals(data[p], id)) {
                return data[p];
            }
            p = next(data, p);
        }
        throw new NoSuchElementException();
    }

    private void delete(int p, boolean rehash) {
        data[p] = DELETED;
        size--;
        if (rehash) {
            if (size == 0) {
                clear();
            }
            else {
                int l = data.length / 2;
                if (size <= l && l >= INITIAL_CAPACITY) {
                    rehash(l);
                }
            }
        }
    }

    private void rehash(int size) {
        RefGraph.Node[] data = new RefGraph.Node[size];
        for (RefGraph.Node node : this.data) {
            if (node != null && node != DELETED) {
                int p = slot(data, node);
                while (data[p] != null) {
                    p = next(data, p);
                }
                data[p] = node;
            }
        }
        this.data = data;
    }

    private static int slot(AnyObjectId[] data, AnyObjectId node) {
        return (node.hashCode() >>> 1) % data.length;
    }

    private static int next(AnyObjectId[] data, int p) {
        if (++p == data.length) {
            p = 0;
        }
        return p;
    }

    @Override public Iterator<RefGraph.Node> iterator() {
        return new IteratorImpl();
    }

    private final class IteratorImpl
            implements Iterator<RefGraph.Node> {
        RefGraph.Node[] data;
        int index;
        RefGraph.Node last;

        IteratorImpl() {
            data = RefNodeSet.this.data;
            index = findNext(index);
        }

        int findNext(int index) {
            while (index < data.length) {
                if (data[index] != null && data[index] != DELETED) {
                    break;
                }
                index++;
            }
            return index;
        }

        @Override public boolean hasNext() {
            return index < data.length;
        }

        @Override public RefGraph.Node next() {
            if (index < data.length) {
                last = data[index];
                index = findNext(index + 1);
                if (index == data.length) {
                    data = new RefGraph.Node[] {};
                }
                return last;
            }
            throw new NoSuchElementException();
        }

        @Override public void remove() {
            RefNodeSet.this.remove((Object) last);
        }
    }
}
