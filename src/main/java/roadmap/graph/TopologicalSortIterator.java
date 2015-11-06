package roadmap.graph;

import java.util.*;

class TopologicalSortIterator implements Iterator<RefGraph.Node> {
    private final ArrayDeque<RefGraph.Node> queue = new ArrayDeque<>();
    private RefGraph.Node next;

    TopologicalSortIterator(RefGraph graph) {
        queue.addAll(graph.getRoots());
        for (RefGraph.Node node : graph) {
            for (RefGraph.Node parent : node.getParents()) {
                parent.inDegree++;
            }
        }
        next = findNext();
    }

    TopologicalSortIterator(RefGraph.Node root) {
        this(Collections.singleton(root));
    }

    TopologicalSortIterator(Set<? extends RefGraph.Node> roots) {
        queue.addAll(roots);
        BreadthFirstIterator it = new BreadthFirstIterator(roots);
        while (it.hasNext()) {
            RefGraph.Node node = it.next();
            for (RefGraph.Node parent : node.getParents()) {
                parent.inDegree++;
            }
        }
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
