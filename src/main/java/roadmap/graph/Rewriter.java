package roadmap.graph;

import roadmap.ref.Ref;
import roadmap.ref.RefFilter;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Set;

/** Rewrite graph by eliminating uninteresting nodes. */
abstract class Rewriter {
    protected final Graph graph;

    Rewriter(Graph graph) {
        this.graph = graph;
    }

    protected boolean interesting(Graph.Node node) {
        return true;
    }

    /**
     * Iterate through graph nodes, if an uninteresting node is encountered,
     * replace it with interesting children of this node.
     *
     * @return New root nodes of the rewritten graph.
     */
    NodeSet rewrite() {
        // Interesting roots.
        NodeSet in = new NodeSet();
        // Uninteresting roots.
        NodeSet un = new NodeSet();
        // Split roots to interesting and uninteresting ones.
        for (Graph.Node root : graph.getRoots()) {
            if (interesting(root)) {
                in.add(root);
            }
            else {
                un.add(root);
            }
        }
        // Start traversal from interesting roots only.
        ArrayDeque<Graph.Node> queue = new ArrayDeque<>(in);
        NodeSet seen = new NodeSet(queue);
        Graph.Node node;
        while ((node = queue.poll()) != null) {
            // New parents for this node.
            NodeSet parents = new NodeSet();
            // Iterate over the existing parents.
            Iterator<Graph.Node> it = node.getParents().iterator();
            while (it.hasNext()) {
                Graph.Node parent = it.next();
                it.remove();
                if (interesting(parent)) {
                    // Keep this parent.
                    parents.add(parent);
                    if (seen.add(parent)) {
                        queue.add(parent);
                    }
                }
                else {
                    // Find parent's interesting parents.
                    NodeSet replaces = new NodeSet();
                    replace(parent, replaces);
                    parents.addAll(replaces);
                    for (Graph.Node replace : replaces) {
                        if (seen.add(replace)) {
                            queue.add(replace);
                        }
                    }
                }
            }
            // Update parents out of iteration cycle to avoid concurrent modification.
            for (Graph.Node parent : parents) {
                node.link(parent);
            }
        }
        // Replace each uninteresting root with its interesting children.
        for (Graph.Node root : un) {
            NodeSet replaces = new NodeSet();
            replace(root, replaces);
            for (Graph.Node replace : replaces) {
                // Set interesting children as roots, but only if not seen previously,
                // otherwise they are reachable from other interesting roots.
                // By definition reachable nodes cannot be roots.
                if (seen.add(replace)) {
                    in.add(replace);
                }
            }
        }
        return in;
    }

    private void replace(Graph.Node root, NodeSet replaces) {
        // Initiate search in the sub-graph starting from the
        // specified child. Stop search as soon as interesting
        // parents are found.
        ArrayDeque<Graph.Node> queue = new ArrayDeque<>();
        queue.add(root);
        NodeSet seen = new NodeSet(queue);
        Graph.Node node;
        while ((node = queue.poll()) != null) {
            if (queue.isEmpty()) {
                seen.clear();
            }
            for (Graph.Node parent : node.getParents()) {
                if (interesting(parent)) {
                    replaces.add(parent);
                }
                else {
                    // Keep this parent.
                    if (seen.add(parent)) {
                        queue.add(parent);
                    }
                }
            }
        }
    }

    /** Remove nodes with tags only except those that are merge bases. */
    static final class Simplifier
            extends Rewriter {
        private final NodeSet heads = new NodeSet();
        private final NodeSet mergeBases = new NodeSet();
        private final RefFilter filter;

        Simplifier(Graph graph, RefFilter filter) {
            // The current implementation is not particularly efficient
            // as it makes two copies of the specified graph.
            // The first copy is needed to clone every node of the graph.
            // The second copy is needed to correctly fill in the internal set
            // of all graph nodes.
            super(graph.copy());
            this.filter = filter;
        }

        Graph simplify() {
            NodeSet roots = rewrite();
            Graph result = new Graph(graph.getRefs(), roots, graph.getRefDiffs());
            result.fix();
            new Beautifier(result).beautify();
            return result;
        }

        @Override protected boolean interesting(Graph.Node node) {
            // Filter out tags.
            return heads.contains(node) || mergeBases.contains(node);
        }

        @Override NodeSet rewrite() {
            findHeads(heads);
            graph.findMergeBases(heads, mergeBases);
            return super.rewrite();
        }

        /** Find those nodes that have branch refs pointing at them. */
        private void findHeads(NodeSet heads) {
            for (Graph.Node node : graph) {
                if (acceptAny(graph.getRefs().byId(node))) {
                    heads.add(node);
                }
            }
        }

        private boolean acceptAny(Set<Ref> refs) {
            for (Ref ref : refs) {
                if (filter.accept(ref)) {
                    return true;
                }
            }
            return false;
        }
    }
}
