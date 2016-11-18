package roadmap.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;

// TODO new HeadSet.Builder(nodes) should only include refs nodes, but not merge base nodes?

/**
 * Beautify graph by removing extra edges between nodes.
 *
 * <pre>
 *        B   C
 *       o---o
 *      /     \
 * <---o-------o---<
 *      A       D
 * </pre>
 *
 * <p>In the example above, we are only interested in path A-B-C-D,
 * so we can safely remove shorter path A-D. Although the A-D path
 * introduces new commits (ref A is a merge of B and D), it does not
 * introduce new refs, so we are not interested in this edge.</p>
 *
 * <p>It seems like we implemented the algorithm for
 * <a href="http://en.wikipedia.org/wiki/Transitive_reduction">transitive reduction</a>
 * here.</p>
 */
final class Beautifier {
    private static final Comparator<Graph.Node> COMPARATOR = new Comparator<Graph.Node>() {
        @Override public int compare(Graph.Node o1, Graph.Node o2) {
            return o2.index - o1.index;
        }
    };
    private final Graph graph;

    Beautifier(Graph graph) {
        this.graph = graph;
    }

    /** Simplify graph by removing extra edges. */
    void beautify() {
        assert graph.isConsistent();

        Set<Graph.Node> nodes = graph.getNodes();
        HeadSet.Builder hsb = new HeadSet.Builder(nodes);

        ArrayList<Graph.Node> list = new ArrayList<>(nodes.size());
        TopologicalSortIterator it = new TopologicalSortIterator(graph);
        for (int n = 0; it.hasNext(); n++) {
            Graph.Node node = it.next();
            node.index = n;
            list.add(node);
        }

        // For each parent commit...
        for (Graph.Node node : list) {
            // ...copy refs from its children.
            Set<Graph.Node> children = node.getChildren();
            if (children.isEmpty()) {
                // This is a root node, start new heads branch.
                HeadSet heads = new HeadSet(hsb);
                heads.add(hsb, node);
                node.tag(heads);
            }
            else if (children.size() == 1) {
                // Borrow heads from the single child.
                HeadSet heads = new HeadSet(hsb);
                for (Graph.Node child : children) {
                    heads.addAll(child.<HeadSet>tag());
                }
                heads.add(hsb, node);
                node.tag(heads);
            }
            else {
                // Combine heads from multiple children.
                HeadSet heads = new HeadSet(hsb);
                ArrayList<Graph.Node> l = new ArrayList<>(children);
                Collections.sort(l, COMPARATOR);
                for (Graph.Node child : l) {
                    HeadSet childHeads = child.<HeadSet>tag();
                    if (HeadSet.isMergeBase(heads, childHeads)) {
                        heads.addAll(childHeads);
                    }
                    else if (heads.containsAll(childHeads)) {
                        // Longer path leads to this node, and this node is not a merge base.
                        // This shorter path is not interesting for us, so we remove this edge.
                        child.unlink(node);
                    }
                    else {
                        heads.addAll(childHeads);
                    }
                }
                heads.add(hsb, node);
                node.tag(heads);
            }
        }

        // Do not leave garbage behind.
        for (Graph.Node node : list) {
            node.tag(null);
        }
    }
}
