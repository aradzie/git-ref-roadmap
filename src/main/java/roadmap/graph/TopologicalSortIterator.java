package roadmap.graph;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

class TopologicalSortIterator
        implements Iterator<Graph.Node> {
    private final ArrayDeque<Graph.Node> queue = new ArrayDeque<>();
    private Graph.Node next;

    TopologicalSortIterator(Graph graph) {
        queue.addAll(graph.getRoots());
        for (Graph.Node node : graph) {
            for (Graph.Node parent : node.getParents()) {
                parent.inDegree++;
            }
        }
        next = findNext();
    }

    TopologicalSortIterator(Graph.Node root) {
        this(Collections.singleton(root));
    }

    TopologicalSortIterator(Set<? extends Graph.Node> roots) {
        queue.addAll(roots);
        BreadthFirstIterator it = new BreadthFirstIterator(roots);
        while (it.hasNext()) {
            Graph.Node node = it.next();
            for (Graph.Node parent : node.getParents()) {
                parent.inDegree++;
            }
        }
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
                if (--parent.inDegree == 0) {
                    queue.add(parent);
                }
            }
            return node;
        }
        return null;
    }

    @Override public void remove() {
        throw new UnsupportedOperationException();
    }
}
