package roadmap.graph;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

class BreadthFirstIterator
        implements Iterator<Graph.Node> {
    private final HashSet<Graph.Node> seen = new HashSet<>();
    private final ArrayDeque<Graph.Node> queue = new ArrayDeque<>();
    private Graph.Node next;

    BreadthFirstIterator(Graph.Node root) {
        this(Collections.singleton(root));
    }

    BreadthFirstIterator(Set<? extends Graph.Node> roots) {
        seen.addAll(roots);
        queue.addAll(roots);
        next = findNext();
    }

    @Override public boolean hasNext() {
        return next != null;
    }

    @Override public Graph.Node next() {
        Graph.Node n = next;
        if (n == null) {
            throw new NoSuchElementException();
        }
        next = findNext();
        return n;
    }

    private Graph.Node findNext() {
        Graph.Node node = queue.poll();
        if (node != null) {
            for (Graph.Node parent : node.getParents()) {
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
