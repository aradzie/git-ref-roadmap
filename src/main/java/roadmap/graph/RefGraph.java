package roadmap.graph;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import roadmap.ref.Ref;
import roadmap.ref.RefFilter;
import roadmap.ref.RefSet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

/** Ref ancestry graph it only includes ref nodes and merge base nodes. */
public class RefGraph
        implements Iterable<RefGraph.Node> {
    /** Single node from ref graph. */
    public static class Node
            extends ObjectId {
        /** Keeps {@link #getChildren()} in sync with parents. */
        private class ParentHashSet
                extends HashSet<Node> {
            @Override public boolean add(Node node) {
                assert !Node.this.equals(node);
                boolean b = super.add(node);
                if (b) {
                    node.children.add(Node.this);
                }
                return b;
            }

            @Override public boolean remove(Object o) {
                assert !Node.this.equals(o);
                boolean b = super.remove(o);
                if (b) {
                    ((Node) o).children.remove(Node.this);
                }
                return b;
            }

            @Override public void clear() {
                throw new UnsupportedOperationException();
            }

            @Override public Iterator<Node> iterator() {
                final Iterator<Node> it = super.iterator();
                return new Iterator<Node>() {
                    Node last;

                    @Override public boolean hasNext() {
                        return it.hasNext();
                    }

                    @Override public Node next() {
                        return last = it.next();
                    }

                    @Override public void remove() {
                        it.remove();
                        last.children.remove(Node.this);
                    }
                };
            }
        }

        class NodeHashSet
                extends HashSet<Node> {
            @Override public boolean add(Node node) {
                assert !Node.this.equals(node);
                return super.add(node);
            }

            @Override public boolean remove(Object o) {
                assert !Node.this.equals(o);
                return super.remove(o);
            }
        }

        private final int commitTime;
        private final NodeHashSet parents = new NodeHashSet();
        private final NodeHashSet children = new NodeHashSet();
        private Object tag;
        int inDegree, index;

        Node(AnyObjectId id) {
            super(id);
            commitTime = 0;
        }

        Node(Node that) {
            super(that);
            commitTime = that.getCommitTime();
        }

        Node(RevCommit commit) {
            super(commit);
            commitTime = commit.getCommitTime();
        }

        public int getCommitTime() {
            return commitTime;
        }

        /** @return Live set of parent nodes. */
        public Set<Node> getParents() {
            return parents;
        }

        /** @return Unmodifiable set of nodes for which this node is a parent. */
        public Set<Node> getChildren() {
            return Collections.unmodifiableSet(children);
        }

        void link(Node parent) {
            parents.add(parent);
        }

        void unlink(Node parent) {
            parents.remove(parent);
        }

        boolean isConsistent() {
            for (Node parent : parents) {
                if (!parent.children.contains(this)) {
                    return false;
                }
            }
            for (Node child : children) {
                if (!child.parents.contains(this)) {
                    return false;
                }
            }
            return true;
        }

        @SuppressWarnings("unchecked") <T> T tag(T aux) {
            T val = (T) tag;
            tag = aux;
            return val;
        }

        @SuppressWarnings("unchecked") <T> T tag() {
            return (T) tag;
        }
    }

    /** Ref set this graph was built for. */
    private final RefSet refs;
    /** All graph nodes. */
    final RefNodeSet nodes;
    /** Root nodes, i.e. nodes without children. */
    final RefNodeSet roots;
    /** Set of differences between refs. */
    private final Set<RefDiff> refDiffs;

    /** Create empty graph. */
    private RefGraph() {
        this(RefSet.EMPTY, new RefNodeSet(), new HashSet<RefDiff>());
    }

    RefGraph(RefSet refs, RefNodeSet roots, Set<RefDiff> refDiffs) {
        this.refs = refs;
        this.roots = roots;
        nodes = RefNodeSet.fill(this.roots);
        this.refDiffs = Collections.unmodifiableSet(refDiffs);
        fix();
    }

    /** @return Unmodifiable set of refs this graph was built for. */
    public RefSet getRefs() {
        return refs;
    }

    /** @return Unmodifiable set of graph root nodes. */
    public Set<Node> getRoots() {
        return Collections.unmodifiableSet(roots);
    }

    /** @return Unmodifiable set of all graph nodes, including roots, all refs and merge bases. */
    public Set<Node> getNodes() {
        return Collections.unmodifiableSet(nodes);
    }

    /** @return Unmodifiable set of ref differences. */
    public Set<RefDiff> getRefDiffs() {
        return refDiffs;
    }

    /**
     * @param id Commit id of a node.
     * @return Value indicating whether graph contains node for the specified commit id.
     */
    public boolean contains(AnyObjectId id) {
        return nodes.contains(id);
    }

    /**
     * Get node with the specified id.
     *
     * @param id Node id.
     * @return Node with the specified id.
     * @throws IllegalArgumentException If with the specified id not found in the graph.
     */
    public Node node(AnyObjectId id) {
        try {
            return nodes.get(id);
        }
        catch (NoSuchElementException ex) {
            throw new IllegalArgumentException("id: " + id.getName());
        }
    }

    /** @return Unmodifiable iterator with no particular node ordering. */
    @Override public Iterator<Node> iterator() {
        return Collections.unmodifiableSet(nodes).iterator();
    }

    /**
     * Find merge bases for the specified nodes.
     *
     * @param a          First node.
     * @param b          Second node.
     * @param mergeBases Set to fill in with merge base nodes.
     */
    public void findMergeBases(Node a, Node b, Collection<Node> mergeBases) {
        HashSet<Node> set = new HashSet<>(2);
        set.add(a);
        set.add(b);
        findMergeBases(set, mergeBases);
    }

    /**
     * Find merge bases for the specified nodes.
     *
     * @param heads      Nodes whose merge bases to find.
     * @param mergeBases Set to fill in with merge base nodes.
     */
    public void findMergeBases(Set<Node> heads, Collection<Node> mergeBases) {
        if (heads.size() == 1) {
            // Merging a head with itself.
            mergeBases.addAll(heads);
        }
        else {
            // Merging several different heads.
            HeadSet.Builder hsb = new HeadSet.Builder(heads);
            // Mark roots.
            for (Node node : heads) {
                HeadSet myHeads = new HeadSet(hsb);
                myHeads.add(hsb, node);
                node.tag(myHeads);
            }
            Iterator<Node> it = new TopologicalSortIterator(this);
            Node last = null;
            while (it.hasNext()) {
                if (last != null) {
                    last.tag(null); // Do not leave garbage behind us.
                }
                Node node = it.next();
                // What are our heads?
                HeadSet myHeads = node.tag();
                if (myHeads != null) {
                    // Copy heads onto parents.
                    for (Node parent : node.getParents()) {
                        HeadSet parentHeads = parent.tag();
                        if (parentHeads == null) {
                            // Simply pass my heads onto parent.
                            parent.tag(myHeads);
                        }
                        else {
                            if (myHeads != parentHeads) {
                                if (HeadSet.isMergeBase(myHeads, parentHeads)) {
                                    mergeBases.add(parent);
                                }
                                // Merge with parent heads.
                                HeadSet tmp = new HeadSet(hsb);
                                tmp.addAll(myHeads);
                                tmp.addAll(parentHeads);
                                parent.tag(tmp);
                            }
                        }
                    }
                }
                last = node;
            }
            if (last != null) {
                last.tag(null); // Do not leave garbage behind us.
            }
        }
    }

    /**
     * Make deep clone that does not include tags.
     *
     * @return Deep copy of this graph without tags.
     */
    public RefGraph copy(RefFilter filter) {
        return new Rewriter.Simplifier(this, filter).simplify();
    }

    /**
     * Make deep clone that is exact copy of this graph
     * but does not share nodes with this graph.
     *
     * @return Deep copy of this graph.
     */
    public RefGraph copy() {
        return new RefGraph(refs, copy(roots), refDiffs);
    }

    void fix() {
        for (Node node : nodes) {
            node.children.clear();
        }
        for (Node node : nodes) {
            for (Node parent : node.parents) {
                parent.children.add(node);
            }
        }
    }

    boolean isConsistent() {
        for (Node node : nodes) {
            for (Node parent : node.parents) {
                if (!nodes.contains(parent)) {
                    return false;
                }
            }
            for (Node child : node.children) {
                if (!nodes.contains(child)) {
                    return false;
                }
            }
            if (!node.isConsistent()) {
                return false;
            }
        }
        return true;
    }

    private static RefNodeSet copy(Set<Node> roots) {
        // Roots in new graph.
        RefNodeSet newRoots = new RefNodeSet();
        // Mapping of nodes from old to new graph.
        HashMap<Node, Node> map = new HashMap<>();
        // Queue for graph traversal.
        ArrayDeque<Node> queue = new ArrayDeque<>();
        // Seed traversal with roots.
        for (Node node : roots) {
            Node clone = new Node(node);
            newRoots.add(clone);
            map.put(node, clone);
            queue.add(node);
        }
        // Traverse old graph creating and linking nodes for new graph.
        Node node;
        while ((node = queue.poll()) != null) {
            Node clone = map.get(node);
            for (Node parent : node.getParents()) {
                Node c = map.get(parent);
                if (c == null) {
                    map.put(parent, c = new Node(parent));
                    queue.add(parent);
                }
                clone.link(c);
            }
        }
        return newRoots;
    }

    public void dump(PrintWriter w)
            throws IOException {
        w.printf("head diffs: %d\n", refDiffs.size());
        for (RefDiff diff : refDiffs) {
            String na = names(diff.getA());
            String nb = names(diff.getB());
            w.append(diff.getA().getName(), 0, 8);
            w.append(na);
            w.append("/");
            w.append(String.valueOf(diff.getCommitsA()));
            w.append(" <- ");
            w.append(diff.getMergeBase().getName(), 0, 8);
            w.append(" -> ");
            w.append(diff.getB().getName(), 0, 8);
            w.append(nb);
            w.append("/");
            w.append(String.valueOf(diff.getCommitsB()));
            w.append("\n");
        }
        w.flush();
    }

    private String names(AnyObjectId id) {
        StringBuilder s = new StringBuilder();
        Set<Ref> refs = this.refs.byId(id);
        int index = 0;
        s.append("{");
        for (Ref ref : refs) {
            if (index++ > 0) {
                s.append(", ");
            }
            s.append(ref.getName());
        }
        s.append("}");
        return s.toString();
    }
}
