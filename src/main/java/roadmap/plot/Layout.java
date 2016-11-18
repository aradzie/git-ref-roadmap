package roadmap.plot;

import org.eclipse.jgit.lib.ObjectId;
import roadmap.graph.Graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Set;

public class Layout {
    public interface VertexVisitor {
        void visit(Bend bend);

        void visit(Node node);
    }

    /** Graph edge crossing reduction facility. */
    private class LayerStack {
        /** Specialized array list to hold vertexes from a single layer. */
        class Layer
                extends ArrayList<Vertex> {
            /** Initial arrangement of vertexes on a layer. */
            void init() {
                Collections.sort(this, VERTEX_BY_WEIGHT_REVERSE);
                updateIndexes();
            }

            /** Rearrange vertexes after crossing reduction step. */
            void rearrange() {
                Collections.sort(this, VERTEX_BY_LANE);
                updateIndexes();
            }

            /** Update vertex indexes after proper sorting is applied to them. */
            void updateIndexes() {
                int index = 0;
                for (Vertex vertex : this) {
                    vertex.lane = index++;
                }
            }

            /** Beautify graph by placing root nodes on top, if possible. */
            void rootsOnTop() {
                // In each vertex sort outgoing edges for faster comparison.
                for (Vertex vertex : this) {
                    Collections.sort(vertex.outgoing, VERTEX_BY_LANE);
                }
                // Run insertion sort to rearrange vertexes.
                for (int n = 1; n < size(); n++) {
                    Vertex v1 = get(n);
                    if (!v1.incoming.isEmpty()) {
                        int m = n;
                        for (; m > 0; m--) {
                            Vertex v2 = get(m - 1);
                            Vertex v3 = get(m);
                            if (v2.incoming.isEmpty()
                                    && v2.outgoing.equals(v3.outgoing)) {
                                set(m, v2);
                            }
                            else {
                                break;
                            }
                        }
                        set(m, v1);
                    }
                }
                updateIndexes();
            }

            /** Beautify graph by placing each node not lower than its sole parent. */
            void straightLines() {
                for (int m = 0; m < size(); m++) {
                    Vertex vertex = get(m);
                    if (vertex.outgoing.size() == 1) {
                        Vertex neighbor = vertex.outgoing.get(0);
                        if (vertex.lane < neighbor.lane) {
                            vertex.lane += neighbor.lane - vertex.lane;
                            for (int n = m + 1; n < size(); n++) {
                                Vertex v1 = get(n - 1);
                                Vertex v2 = get(n);
                                if (v1.lane < v2.lane) {
                                    break;
                                }
                                v2.lane = v1.lane + 1;
                            }
                        }
                    }
                }
            }
        }

        // TODO calculate sweeps from the number of nodes in the graph?
        static final int MAX_SWEEPS = 15;
        final Layer[] layers;
        int lanes;

        LayerStack(int height) {
            layers = new Layer[height];
            for (int n = 0; n < height; n++) {
                layers[n] = new Layer();
            }
        }

        void add(Vertex v) {
            layers[v.layer].add(v);
        }

        void reduceCrossings() {
            for (Layer layer : layers) {
                layer.init();
            }
            for (int n = 0; n < MAX_SWEEPS; n++) {
                // Left-to-right
                for (int l = 0; l < layers.length - 1; l++) {
                    crossingReductionStep(l + 1, l);
                }
                // Right-to-left
                for (int l = layers.length - 1; l > 0; l--) {
                    crossingReductionStep(l - 1, l);
                }
            }
        }

        void crossingReductionStep(int layerIndex, int otherLayerIndex) {
            Layer layer = layers[layerIndex];
            Layer otherLayer = layers[otherLayerIndex];
            for (Vertex vertex : layer) {
                ArrayList<Vertex> edges = otherLayerIndex < layerIndex
                        ? vertex.incoming : vertex.outgoing;
                vertex.lane = barycenter(otherLayer, edges);
            }
            layer.rearrange();
        }

        int barycenter(Layer otherLayer, ArrayList<Vertex> edges) {
            int bc = 0;
            int n = 0;
            for (Vertex edge : edges) {
                for (Vertex other : otherLayer) {
                    if (edge == other) {
                        bc += other.lane;
                        n++;
                    }
                }
            }
            if (n == 0) {
                return 0;
            }
            return (int) Math.round((double) bc / n);
        }

        /** Beautify graph by attempting to makes lines straighter. */
        void beautify() {
            for (int l = layers.length; l > 0; l--) {
                Layer layer = layers[l - 1];
                layer.rootsOnTop();
                layer.straightLines();
            }
            // Count number of lanes occupied by this partition.
            for (Layer layer : layers) {
                lanes = Math.max(lanes, layer.get(layer.size() - 1).lane + 1);
            }
        }
    }

    /** Groups subgraph nodes. */
    public class Partition
            implements Iterable<Vertex> {
        private final HashSet<Node> roots = new HashSet<>();
        private final ArrayList<Vertex> points = new ArrayList<>();
        private final Vertex tail;
        private int layers;
        private int lanes;

        private Partition(Vertex tail) {
            this.tail = tail;
        }

        @Override public Iterator<Vertex> iterator() {
            return points.iterator();
        }

        public void visit(VertexVisitor visitor) {
            for (Vertex vertex : points) {
                vertex.accept(visitor);
            }
        }

        public Set<Node> getRoots() {
            return Collections.unmodifiableSet(roots);
        }

        public List<Vertex> getPoints() {
            return Collections.unmodifiableList(points);
        }

        public Vertex getTail() {
            return tail;
        }

        public int getLayers() {
            return layers;
        }

        public int getLanes() {
            return lanes;
        }

        private void add(Vertex vertex) {
            points.add(vertex);
            vertex.partition = this;
        }

        private void layoutNodes() {
            layers = splitIntoLayers();
            insertBends();
            LayerStack stack = new LayerStack(layers);
            interlink(stack);
            stack.reduceCrossings();
            stack.beautify();
            Collections.sort(points, VERTEX_BY_POSITION);

            lanes = stack.lanes;
            totalLayers = Math.max(totalLayers, layers);
            totalLanes += lanes;
        }

        private int splitIntoLayers() {
            int layers = 0;
            ArrayList<Vertex> vertexes = new ArrayList<>();
            // Layout parents behind children.
            Iterator<Vertex> fwIt = new TopologicalSortIterator(roots);
            while (fwIt.hasNext()) {
                Vertex vertex = fwIt.next();
                int nextLayer = vertex.layer + 1;
                layers = Math.max(layers, nextLayer);
                for (Vertex child : vertex.outgoing) {
                    if (child.layer < nextLayer) {
                        child.layer = nextLayer;
                    }
                }
                vertexes.add(vertex);
            }
            // Move parents closer to children.
            ListIterator<Vertex> rwIt = vertexes.listIterator(vertexes.size());
            while (rwIt.hasPrevious()) {
                Vertex vertex = rwIt.previous();
                int l = Integer.MAX_VALUE;
                for (Vertex child : vertex.outgoing) {
                    l = Math.min(l, child.layer);
                }
                if (l != Integer.MAX_VALUE) {
                    vertex.layer = l - 1;
                }
            }
            // For every vertex count number of reachable child nodes.
            for (Vertex vertex : vertexes) {
                if (vertex.set == null) {
                    vertex.set = new HashSet<>();
                }
                vertex.weight = vertex.set.size();
                vertex.set.add(vertex);
                for (Vertex outgoing : vertex.outgoing) {
                    if (outgoing.set == null) {
                        outgoing.set = new HashSet<>();
                    }
                    outgoing.set.addAll(vertex.set);
                }
            }
            for (Vertex vertex : vertexes) {
                vertex.set = null;
            }
            return layers;
        }

        private void insertBends() {
            class Rewriter {
                HashSet<Vertex> vertexes = new HashSet<>();
                Vertex base;

                Rewriter(Vertex base) {
                    this.base = base;
                }

                void add(Vertex vertex) {
                    vertexes.add(vertex);
                }

                /**
                 * Insert bends between vertexes that are too far away
                 * from each other. More precisely, insert N-1 bends where
                 * N is the difference of layers between two nodes, where N > 1.
                 */
                void rewriteSimple() {
                    for (Vertex vertex : vertexes) {
                        Vertex last = null;
                        for (int l = base.layer + 1; l < vertex.layer; l++) {
                            Vertex bend = new Bend(l);
                            if (last != null) {
                                last.outgoing.add(bend);
                            }
                            else {
                                base.outgoing.add(bend);
                            }
                            last = bend;
                        }
                        // noinspection ConstantConditions
                        last.outgoing.add(vertex);
                    }
                }

                /**
                 * Insert bends between vertexes that are too far away
                 * from each other. More precisely, insert N-1 bends where
                 * N is the difference of layers between two nodes, where N > 1.
                 * Minimize number of bends by merging bends that are on the same level.
                 */
                void rewrite() {
                    while (true) {
                        Iterator<Vertex> it = vertexes.iterator();
                        while (it.hasNext()) {
                            Vertex vertex = it.next();
                            if (vertex.layer == base.layer + 1) {
                                base.outgoing.add(vertex);
                                it.remove();
                            }
                        }
                        if (vertexes.isEmpty()) {
                            break;
                        }
                        int d = Integer.MAX_VALUE;
                        for (Vertex vertex : vertexes) {
                            d = Math.min(d, vertex.layer - base.layer);
                        }
                        while (d > 1) {
                            Vertex bend = new Bend(base.layer + 1);
                            base.outgoing.add(bend);
                            base = bend;
                            d--;
                        }
                    }
                }
            }

            Iterator<Vertex> it = new BreadthFirstIterator(roots);
            while (it.hasNext()) {
                Vertex vertex = it.next();
                Rewriter r = null;
                Iterator<Vertex> childIt = vertex.outgoing.iterator();
                while (childIt.hasNext()) {
                    Vertex child = childIt.next();
                    if (child.layer - vertex.layer > 1) {
                        childIt.remove();
                        if (r == null) {
                            r = new Rewriter(vertex);
                        }
                        r.add(child);
                    }
                }
                if (r != null) {
                    r.rewrite();
                }
            }
        }

        private void interlink(LayerStack stack) {
            Iterator<Vertex> it = new BreadthFirstIterator(roots);
            while (it.hasNext()) {
                Vertex vertex = it.next();
                for (Vertex child : vertex.outgoing) {
                    child.incoming.add(vertex);
                }
                stack.add(vertex);
                add(vertex);
            }
        }
    }

    /** Abstract graph vertex. */
    public abstract class Vertex {
        /** Incoming vertexes. */
        protected final ArrayList<Vertex> incoming = new ArrayList<>(3);
        /** Outgoing vertexes. */
        protected final ArrayList<Vertex> outgoing = new ArrayList<>(3);
        /** The subgraph this node belongs to. */
        private Partition partition;
        /** X offset. */
        protected int layer;
        /** Y offset. */
        protected int lane;
        /** Number of incoming edges. */
        private int inDegree;
        /** Temporary field. */
        private HashSet<Vertex> set;
        /** Temporary field. */
        private int weight;

        public List<Vertex> getIncoming() {
            return Collections.unmodifiableList(incoming);
        }

        public List<Vertex> getOutgoing() {
            return Collections.unmodifiableList(outgoing);
        }

        public Partition getPartition() {
            return partition;
        }

        public int getLayer() {
            return layer;
        }

        public int getLane() {
            return lane;
        }

        public int col() {
            return totalLayers - layer - 1;
        }

        public int row() {
            return partition.lanes - lane - 1;
        }

        public abstract void accept(VertexVisitor visitor);
    }

    /** Synthetic vertex generated for long edges spanning multiple levels. */
    public class Bend
            extends Vertex {
        private Bend(int layer) {
            this.layer = layer;
        }

        @Override public void accept(VertexVisitor visitor) {
            visitor.visit(this);
        }
    }

    /** Vertex for a commit. */
    public class Node
            extends Vertex {
        public final ObjectId id;
        public final Set<roadmap.ref.Ref> refs;

        private Node(ObjectId id) {
            this.id = id;
            refs = graph.getRefs().byId(id);
        }

        @Override public void accept(VertexVisitor visitor) {
            visitor.visit(this);
        }
    }

    /**
     * Iterates over graph vertex nodes with no particular ordering,
     * just doing breadth-first search.
     */
    private static class BreadthFirstIterator
            implements Iterator<Vertex> {
        final HashSet<Vertex> seen = new HashSet<>();
        final ArrayDeque<Vertex> queue = new ArrayDeque<>();
        Vertex next;

        BreadthFirstIterator(HashSet<? extends Vertex> roots) {
            seen.addAll(roots);
            queue.addAll(roots);
            next = findNext();
        }

        @Override public boolean hasNext() {
            return next != null;
        }

        @Override public Vertex next() {
            Vertex next = this.next;
            if (next == null) {
                throw new NoSuchElementException();
            }
            this.next = findNext();
            return next;
        }

        Vertex findNext() {
            Vertex node = queue.poll();
            if (node != null) {
                for (Vertex child : node.outgoing) {
                    if (seen.add(child)) {
                        queue.add(child);
                    }
                }
            }
            return node;
        }

        @Override public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Iterates over graph vertex nodes ordering them topologically,
     * so that no parents may come before children.
     */
    private static class TopologicalSortIterator
            implements Iterator<Vertex> {
        final ArrayDeque<Vertex> queue = new ArrayDeque<>();
        Vertex next;

        TopologicalSortIterator(HashSet<? extends Vertex> nodes) {
            queue.addAll(nodes);
            Iterator<Vertex> it = new BreadthFirstIterator(nodes);
            while (it.hasNext()) {
                Vertex node = it.next();
                for (Vertex child : node.outgoing) {
                    child.inDegree++;
                }
            }
            next = findNext();
        }

        @Override public boolean hasNext() {
            return next != null;
        }

        @Override public Vertex next() {
            Vertex next = this.next;
            if (next == null) {
                throw new NoSuchElementException();
            }
            this.next = findNext();
            return next;
        }

        Vertex findNext() {
            Vertex node = queue.poll();
            if (node != null) {
                for (Vertex child : node.outgoing) {
                    if (--child.inDegree == 0) {
                        queue.add(child);
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

    private static final Comparator<Vertex> VERTEX_BY_WEIGHT_REVERSE =
            new Comparator<Vertex>() {
                @Override public int compare(Vertex a, Vertex b) {
                    return b.weight - a.weight;
                }
            };
    private static final Comparator<Vertex> VERTEX_BY_LANE =
            new Comparator<Vertex>() {
                @Override public int compare(Vertex a, Vertex b) {
                    return a.lane - b.lane;
                }
            };
    private static final Comparator<Vertex> VERTEX_BY_POSITION =
            new Comparator<Vertex>() {
                @Override public int compare(Vertex a, Vertex b) {
                    int r = a.layer - b.layer;
                    if (r == 0) {
                        r = a.lane - b.lane;
                    }
                    return r;
                }
            };
    private static final Comparator<Partition> PARTITION_BY_SIZE =
            new Comparator<Partition>() {
                @Override public int compare(Partition a, Partition b) {
                    return a.points.size() - b.points.size();
                }
            };
    private final Graph graph;
    private final ArrayList<Partition> partitions = new ArrayList<>();
    private int totalLayers, totalLanes;

    public Layout(Graph graph) {
        this.graph = graph;
        HashMap<Graph.Node, Node> mapping = new HashMap<>();
        for (Graph.Node node : graph) {
            mapping.put(node, new Node(node));
        }
        for (Graph.Node node : graph) {
            for (Graph.Node parent : node.getParents()) {
                mapping.get(node).outgoing.add(mapping.get(parent));
            }
        }
        HashSet<Vertex> roots = new HashSet<>();
        for (Graph.Node node : graph.getRoots()) {
            roots.add(mapping.get(node));
        }
        for (Vertex root : roots) {
            partition(root);
        }
        for (Partition partition : partitions) {
            partition.layoutNodes();
        }
        Collections.sort(partitions, PARTITION_BY_SIZE);
    }

    private void partition(Vertex root) {
        // Find partition for this root node.
        Vertex node = root;
        Partition partition;
        while (true) {
            if (node.partition != null) {
                // Already partitioned node.
                partition = node.partition;
                break;
            }
            Iterator<Vertex> it = node.outgoing.iterator();
            if (it.hasNext()) {
                node = it.next();
            }
            else {
                // No more parents, this is the last node, make new partition.
                partitions.add(partition = new Partition(node));
                break;
            }
        }
        // Copy partition onto all root children nodes.
        ArrayDeque<Vertex> queue = new ArrayDeque<>();
        if (root.partition == null) {
            root.partition = partition;
            queue.add(root);
            while ((node = queue.poll()) != null) {
                for (Vertex outgoing : node.outgoing) {
                    if (outgoing.partition == null) {
                        outgoing.partition = partition;
                        queue.push(outgoing);
                    }
                }
            }
        }
        partition.roots.add((Node) root);
    }

    public Graph getGraph() {
        return graph;
    }

    public List<Partition> getPartitions() {
        return Collections.unmodifiableList(partitions);
    }

    public int getTotalLayers() {
        return totalLayers;
    }

    public int getTotalLanes() {
        return totalLanes;
    }
}
