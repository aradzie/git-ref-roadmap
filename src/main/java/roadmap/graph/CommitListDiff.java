package roadmap.graph;

import org.eclipse.jgit.lib.ObjectId;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;

/**
 * Finds added and removed commits between to lists, starting from the specified
 * objects.
 *
 * <p>Consider the following scenario: a push operation was performed, and some
 * new commits were added to a repository, along with few old commits were removed
 * from it (using the <em>force</em> option, e.g. <code>git push --force origin
 * master</code>).</p> Now we have two commit lists, the old and the new one. Now
 * we want to know what commits have been added in branch <em>master</em>, and what
 * commits have been removed.
 */
public class CommitListDiff {
    /**
     * Iterates over all (grand-)parents of a commits using topological
     * sorting. It relies on the fact that topological commit ordering is
     * already established in commit indexes, so the iterating algorithm
     * is a little bit simpler and faster.
     */
    private static class IteratorImpl
            implements Iterator<Commit> {
        static final Comparator<Commit> CMP = new Comparator<Commit>() {
            @Override public int compare(Commit o1, Commit o2) {
                return o1.getIndex() - o2.getIndex();
            }
        };
        final PriorityQueue<Commit> queue = new PriorityQueue<>(8, CMP);
        final HashSet<Commit> seen = new HashSet<>();
        Commit next;

        IteratorImpl(Commit start) {
            queue.add(start);
            seen.add(start);
            findNext();
        }

        void findNext() {
            next = queue.poll();
            if (next != null) {
                seen.remove(next);
                int count = next.getParentCount();
                for (int n = 0; n < count; n++) {
                    Commit parent = next.getParent(n);
                    if (seen.add(parent)) {
                        queue.add(parent);
                    }
                }
            }
        }

        @Override public boolean hasNext() {
            return next != null;
        }

        @Override public Commit next() {
            if (next == null) {
                throw new NoSuchElementException();
            }
            Commit tmp = next;
            findNext();
            return tmp;
        }

        @Override public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private CommitList la, lb;
    private ObjectId ia, ib;

    public CommitListDiff from(CommitList list, ObjectId id) {
        la = list;
        ia = id;
        return this;
    }

    public CommitListDiff to(CommitList list, ObjectId id) {
        lb = list;
        ib = id;
        return this;
    }

    public void diff(CommitDiff.Sink sink) {
        if (la == null || ia == null
                || lb == null || ib == null) {
            throw new IllegalStateException();
        }
        IteratorImpl ita = new IteratorImpl(la.map(ia));
        while (ita.hasNext()) {
            Commit commit = ita.next();
            if (lb.indexOf(commit) != -1) {
                break;
            }
            else {
                sink.removed(commit);
            }
        }
        IteratorImpl itb = new IteratorImpl(lb.map(ib));
        while (itb.hasNext()) {
            Commit commit = itb.next();
            if (la.indexOf(commit) != -1) {
                break;
            }
            else {
                sink.added(commit);
            }
        }
    }
}
