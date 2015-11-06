package roadmap.plot;

import org.eclipse.jgit.lib.*;
import roadmap.graph.*;
import roadmap.ref.Ref;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.*;

/** Swing component to draw graph of commits. */
public class RefGraphPlotPanel extends JPanel {
    /** Graph edge crossing reduction facility. */
    private class LayerStack {
        /** Specialized array list to hold vertexes from a single layer. */
        class Layer extends ArrayList<Vertex> {
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

    /** Groups subgraph nodes, provides subgraph metadata. */
    private class Partition implements Iterable<Vertex> {
        final HashSet<Node> roots = new HashSet<>();
        final ArrayList<Vertex> points = new ArrayList<>();
        final Vertex tail;
        int layers;
        int lanes;

        Partition(Vertex tail) {
            this.tail = tail;
        }

        @Override public Iterator<Vertex> iterator() {
            return points.iterator();
        }

        void add(Vertex vertex) {
            points.add(vertex);
            vertex.partition = this;
        }

        void layoutNodes() {
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

        int splitIntoLayers() {
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

        void insertBends() {
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

        void interlink(LayerStack stack) {
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
    private class Vertex {
        /** Incoming vertexes. */
        final ArrayList<Vertex> incoming = new ArrayList<>();
        /** Outgoing vertexes. */
        final ArrayList<Vertex> outgoing = new ArrayList<>();
        /** The subgraph this node belongs to. */
        Partition partition;
        /** Number of incoming edges. */
        int inDegree;
        /** X offset. */
        int layer;
        /** Y offset. */
        int lane;
        /** Temporary field. */
        HashSet<Vertex> set;
        /** Temporary field. */
        int weight;

        int x() {
            return xx(totalLayers - layer - 1);
        }

        int y() {
            return yy(partition.lanes - lane - 1);
        }

        void drawEdges(Graphics2D g) {}

        void drawVertex(Graphics2D g) {}
    }

    /**
     * Synthetic vertex generated for long edges
     * spanning multiple levels.
     */
    private class Bend extends Vertex {
        Bend(int layer) {
            this.layer = layer;
        }

        @Override void drawEdges(Graphics2D g) {
            int x = x();
            int y = y();
            g.setColor(Color.DARK_GRAY);
            g.setStroke(edgeStroke);
            for (Vertex vertex : outgoing) {
                g.drawLine(x, y, vertex.x(), vertex.y());
            }
        }

        @Override void drawVertex(Graphics2D g) {
            int x = x();
            int y = y();
            g.setColor(new Color(0x333333));
            g.fillOval(x - 5, y - 5, 10, 10);
        }
    }

    /** Vertex for commit. */
    private class Node extends Vertex {
        final ObjectId id;
        final ArrayList<Ref> refs;

        Node(ObjectId id) {
            this.id = id;
            refs = new ArrayList<>(graph.getRefs().byId(id));
        }

        @Override void drawEdges(Graphics2D g) {
            int x = x();
            int y = y();
            g.setColor(Color.DARK_GRAY);
            g.setStroke(edgeStroke);
            for (Vertex vertex : outgoing) {
                g.drawLine(x, y, vertex.x(), vertex.y());
            }
        }

        @Override void drawVertex(Graphics2D g) {
            int x = x();
            int y = y();
            drawDisc(g, x, y);
            drawLabel(g, x, y);
        }

        void drawDisc(Graphics2D g, int x, int y) {
            if (refs.isEmpty()) {
                g.setColor(C1A);
                g.fillOval(x - 15, y - 15, 30, 30);
                g.setColor(C1B);
                g.drawOval(x - 15, y - 15, 30, 30);
            }
            else {
                int d = 0;
                for (Ref ref : refs) {
                    if (ref.isTag()) {
                        g.setColor(C2A);
                        g.fillOval(x - 15 + d, y - 15 + d, 30, 30);
                        g.setColor(C2B);
                        g.drawOval(x - 15 + d, y - 15 + d, 30, 30);
                    }
                    else {
                        g.setColor(C3A);
                        g.fillOval(x - 15 + d, y - 15 + d, 30, 30);
                        g.setColor(C3B);
                        g.drawOval(x - 15 + d, y - 15 + d, 30, 30);
                    }
                    d = d - 3;
                }
            }
        }

        void drawLabel(Graphics2D g, int x, int y) {
            AffineTransform t = g.getTransform();
            try {
                String s = name();
                Rectangle bounds = g.getFontMetrics().getStringBounds(s, g).getBounds();
                g.translate(x + 15, y + bounds.height);
                g.rotate(Math.toRadians(30));
                g.setColor(Color.BLACK);
                g.drawString(s, 0, 0);
            }
            finally {
                g.setTransform(t);
            }
        }

        String name() {
            if (refs.isEmpty()) {
                return id.getName().substring(0, 8);
            }
            else {
                StringBuilder s = new StringBuilder();
                Iterator<Ref> it = refs.iterator();
                while (true) {
                    Ref ref = it.next();
                    s.append(ref.getSuffix());
                    if (it.hasNext()) {
                        s.append(", ");
                    }
                    else {
                        break;
                    }
                }
                return s.toString();
            }
        }
    }

    /**
     * Iterates over graph vertex nodes with no particular ordering,
     * just doing breadth-first search.
     */
    private static class BreadthFirstIterator implements Iterator<Vertex> {
        private final HashSet<Vertex> seen = new HashSet<>();
        private final ArrayDeque<Vertex> queue = new ArrayDeque<>();
        private Vertex next;

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
            else {
                this.next = findNext();
            }
            return next;
        }

        private Vertex findNext() {
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
    private static class TopologicalSortIterator implements Iterator<Vertex> {
        private final ArrayDeque<Vertex> queue = new ArrayDeque<>();
        private Vertex next;

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
            else {
                this.next = findNext();
            }
            return next;
        }

        private Vertex findNext() {
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
    private static final double SCALE = 1;
    private static final int H_MARGIN = 120;
    private static final int V_MARGIN = 70;
    private static final int H_SPACE = 150;
    private static final int V_SPACE = 80;
    private static final Color C1A = new Color(0x9ED8BA);
    private static final Color C1B = new Color(0x71B090);
    private static final Color C2A = new Color(0xFF9A40);
    private static final Color C2B = new Color(0xFF7800);
    private static final Color C3A = new Color(0x409EFF);
    private static final Color C3B = new Color(0x007DFF);
    private static final Stroke gridStroke = new BasicStroke(1.0f);
    private static final Stroke edgeStroke = new BasicStroke(2.0f);
    private final RefGraph graph;
    private final ArrayList<Partition> partitions = new ArrayList<>();
    private int totalLayers, totalLanes;

    public RefGraphPlotPanel(RefGraph graph) {
        this.graph = graph;
        setOpaque(true);
        setBackground(Color.WHITE);
        mapNodes();
    }

    private void mapNodes() {
        HashMap<RefGraph.Node, Node> mapping = new HashMap<>();
        for (RefGraph.Node node : graph) {
            mapping.put(node, new Node(node));
        }
        for (RefGraph.Node node : graph) {
            for (RefGraph.Node parent : node.getParents()) {
                mapping.get(node).outgoing.add(mapping.get(parent));
            }
        }
        HashSet<Vertex> roots = new HashSet<>();
        for (RefGraph.Node node : graph.getRoots()) {
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
        ArrayDeque<Vertex> q = new ArrayDeque<>();
        if (root.partition == null) {
            root.partition = partition;
            q.add(root);
            while ((node = q.poll()) != null) {
                for (Vertex outgoing : node.outgoing) {
                    if (outgoing.partition == null) {
                        outgoing.partition = partition;
                        q.push(outgoing);
                    }
                }
            }
        }
        partition.roots.add((Node) root);
    }

    @Override public Dimension getPreferredSize() {
        return new Dimension(
                (int) (SCALE * (H_MARGIN * 2 + (totalLayers - 1) * H_SPACE)),
                (int) (SCALE * (V_MARGIN * 2 + (totalLanes - 1) * V_SPACE)));
    }

    @Override public void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;

        antiAliasGraphics(g2d);
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, getWidth(), getHeight());
        g2d.scale(SCALE, SCALE);

        g2d.setColor(Color.LIGHT_GRAY);
        g2d.setStroke(gridStroke);

        // Vertical lines
        for (int n = 0; n < totalLayers; n++) {
            g2d.drawLine(xx(n), H_MARGIN, xx(n), yy(totalLanes - 1));
        }

        // Horizontal lines
        for (int m = 0; m < totalLanes; m++) {
            g2d.drawLine(V_MARGIN, yy(m), xx(totalLayers - 1), yy(m));
        }

        int offset = 0;
        for (Partition partition : partitions) {
            AffineTransform t = g2d.getTransform();
            try {
                g2d.translate(0, offset * V_SPACE);
                for (Vertex vertex : partition) {
                    vertex.drawEdges(g2d);
                }
                for (Vertex vertex : partition) {
                    vertex.drawVertex(g2d);
                }
            }
            finally {
                g2d.setTransform(t);
            }
            offset += partition.lanes;
        }
    }

    private static void antiAliasGraphics(Graphics2D g) {
        // for antialiasing geometric shapes
        g.addRenderingHints(new RenderingHints(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON));
        // for antialiasing text
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private static int xx(int i) {
        return H_MARGIN + i * H_SPACE;
    }

    private static int yy(int j) {
        return V_MARGIN + j * V_SPACE;
    }
}
