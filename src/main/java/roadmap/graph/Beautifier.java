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
    private static final Comparator<RefGraph.Node> COMPARATOR = new Comparator<RefGraph.Node>() {
        @Override public int compare(RefGraph.Node o1, RefGraph.Node o2) {
            return o2.index - o1.index;
        }
    };
    private final RefGraph graph;

    Beautifier(RefGraph graph) {
        this.graph = graph;
    }

    /** Simplify graph by removing extra edges. */
    void beautify() {
        assert graph.isConsistent();

        Set<RefGraph.Node> nodes = graph.getNodes();
        HeadSet.Builder hsb = new HeadSet.Builder(nodes);

        ArrayList<RefGraph.Node> list = new ArrayList<>(nodes.size());
        TopologicalSortIterator it = new TopologicalSortIterator(graph);
        for (int n = 0; it.hasNext(); n++) {
            RefGraph.Node node = it.next();
            node.index = n;
            list.add(node);
        }

        // For each parent commit...
        for (RefGraph.Node node : list) {
            // ...copy refs from its children.
            Set<RefGraph.Node> children = node.getChildren();
            if (children.isEmpty()) {
                // This is a root node, start new heads branch.
                HeadSet heads = new HeadSet(hsb);
                heads.add(hsb, node);
                node.tag(heads);
            }
            else if (children.size() == 1) {
                // Borrow heads from the single child.
                HeadSet heads = new HeadSet(hsb);
                for (RefGraph.Node child : children) {
                    heads.addAll(child.tag());
                }
                heads.add(hsb, node);
                node.tag(heads);
            }
            else {
                // Combine heads from multiple children.
                HeadSet heads = new HeadSet(hsb);
                ArrayList<RefGraph.Node> l = new ArrayList<>(children);
                Collections.sort(l, COMPARATOR);
                for (RefGraph.Node child : l) {
                    HeadSet childHeads = child.tag();
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
        for (RefGraph.Node node : list) {
            node.tag(null);
        }
    }
}
