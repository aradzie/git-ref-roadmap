package roadmap.graph;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

class BreadthFirstIterator
        implements Iterator<RefGraph.Node> {
    private final HashSet<RefGraph.Node> seen = new HashSet<>();
    private final ArrayDeque<RefGraph.Node> queue = new ArrayDeque<>();
    private RefGraph.Node next;

    BreadthFirstIterator(RefGraph.Node root) {
        this(Collections.singleton(root));
    }

    BreadthFirstIterator(Set<? extends RefGraph.Node> roots) {
        seen.addAll(roots);
        queue.addAll(roots);
        next = findNext();
    }

    @Override public boolean hasNext() {
        return next != null;
    }

    @Override public RefGraph.Node next() {
        RefGraph.Node n = next;
        if (n == null) {
            throw new NoSuchElementException();
        }
        next = findNext();
        return n;
    }

    private RefGraph.Node findNext() {
        RefGraph.Node node = queue.poll();
        if (node != null) {
            for (RefGraph.Node parent : node.getParents()) {
                if (seen.add(parent)) {
                    queue.add(parent);
                }
            }
        }
        return node;
    }

    @Override public void remove() {
        throw new UnsupportedOperationException();
    }
}
