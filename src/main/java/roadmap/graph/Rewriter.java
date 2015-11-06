package roadmap.graph;

import roadmap.ref.*;

import java.util.*;

/** Rewrite graph by eliminating uninteresting nodes. */
abstract class Rewriter {
    protected final RefGraph graph;

    Rewriter(RefGraph graph) {
        this.graph = graph;
    }

    protected boolean interesting(RefGraph.Node node) {
        return true;
    }

    /**
     * Iterate through graph nodes, if an uninteresting node is encountered,
     * replace it with interesting children of this node.
     *
     * @return New root nodes of the rewritten graph.
     */
    RefNodeSet rewrite() {
        // Interesting roots.
        RefNodeSet in = new RefNodeSet();
        // Uninteresting roots.
        RefNodeSet un = new RefNodeSet();
        // Split roots to interesting and uninteresting ones.
        for (RefGraph.Node root : graph.getRoots()) {
            if (interesting(root)) {
                in.add(root);
            }
            else {
                un.add(root);
            }
        }
        // Start traversal from interesting roots only.
        ArrayDeque<RefGraph.Node> queue = new ArrayDeque<>(in);
        RefNodeSet seen = new RefNodeSet(queue);
        RefGraph.Node node;
        while ((node = queue.poll()) != null) {
            // New parents for this node.
            RefNodeSet parents = new RefNodeSet();
            // Iterate over the existing parents.
            Iterator<RefGraph.Node> it = node.getParents().iterator();
            while (it.hasNext()) {
                RefGraph.Node parent = it.next();
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
                    RefNodeSet replaces = new RefNodeSet();
                    replace(parent, replaces);
                    parents.addAll(replaces);
                    for (RefGraph.Node replace : replaces) {
                        if (seen.add(replace)) {
                            queue.add(replace);
                        }
                    }
                }
            }
            // Update parents out of iteration cycle to avoid concurrent modification.
            for (RefGraph.Node parent : parents) {
                node.link(parent);
            }
        }
        // Replace each uninteresting root with its interesting children.
        for (RefGraph.Node root : un) {
            RefNodeSet replaces = new RefNodeSet();
            replace(root, replaces);
            for (RefGraph.Node replace : replaces) {
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

    private void replace(RefGraph.Node root, RefNodeSet replaces) {
        // Initiate search in the sub-graph starting from the
        // specified child. Stop search as soon as interesting
        // parents are found.
        ArrayDeque<RefGraph.Node> queue = new ArrayDeque<>();
        queue.add(root);
        RefNodeSet seen = new RefNodeSet(queue);
        RefGraph.Node node;
        while ((node = queue.poll()) != null) {
            if (queue.isEmpty()) {
                seen.clear();
            }
            for (RefGraph.Node parent : node.getParents()) {
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
    static final class Simplifier extends Rewriter {
        private final RefNodeSet heads = new RefNodeSet();
        private final RefNodeSet mergeBases = new RefNodeSet();
        private final RefFilter filter;

        Simplifier(RefGraph graph, RefFilter filter) {
            // The current implementation is not particularly efficient
            // as it makes two copies of the specified graph.
            // The first copy is needed to clone every node of the graph.
            // The second copy is needed to correctly fill in the internal set
            // of all graph nodes.
            super(graph.copy());
            this.filter = filter;
        }

        RefGraph simplify() {
            RefNodeSet roots = rewrite();
            RefGraph result = new RefGraph(graph.getRefs(), roots, graph.getRefDiffs());
            result.fix();
            new Beautifier(result).beautify();
            return result;
        }

        @Override protected boolean interesting(RefGraph.Node node) {
            // Filter out tags.
            return heads.contains(node) || mergeBases.contains(node);
        }

        @Override RefNodeSet rewrite() {
            findHeads(heads);
            graph.findMergeBases(heads, mergeBases);
            return super.rewrite();
        }

        /** Find those nodes that have branch refs pointing at them. */
        private void findHeads(RefNodeSet heads) {
            for (RefGraph.Node node : graph) {
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
