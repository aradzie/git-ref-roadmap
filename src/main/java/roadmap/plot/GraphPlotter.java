package roadmap.plot;

import roadmap.graph.Layout;
import roadmap.ref.Ref;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.util.Iterator;

public class GraphPlotter {
    /** Draws edges. */
    private class EdgePlotter
            implements Layout.VertexVisitor {
        final Graphics2D g;

        EdgePlotter(Graphics2D g) {
            this.g = g;
        }

        @Override public void visit(Layout.Bend bend) {
            draw(bend);
        }

        @Override public void visit(Layout.Node node) {
            draw(node);
        }

        void draw(Layout.Vertex vertex) {
            int x = X(vertex.col());
            int y = Y(vertex.row());
            g.setColor(Color.DARK_GRAY);
            g.setStroke(EDGE_STROKE);
            for (Layout.Vertex outgoing : vertex.getOutgoing()) {
                g.drawLine(x, y, X(outgoing.col()), Y(outgoing.row()));
            }
        }
    }

    /** Draws vertices. */
    private class VertexPlotter
            implements Layout.VertexVisitor {
        final Graphics2D g;

        VertexPlotter(Graphics2D g) {
            this.g = g;
        }

        @Override public void visit(Layout.Bend bend) {
            int x = X(bend.col());
            int y = Y(bend.row());
            g.setColor(new Color(0x333333));
            g.fillOval(x - 5, y - 5, 10, 10);
        }

        @Override public void visit(Layout.Node node) {
            int x = X(node.col());
            int y = Y(node.row());
            drawDisc(node, x, y);
            drawLabel(node, x, y);
        }

        void drawDisc(Layout.Node node, int x, int y) {
            if (node.refs.isEmpty()) {
                g.setColor(C1A);
                g.fillOval(x - 15, y - 15, 30, 30);
                g.setColor(C1B);
                g.drawOval(x - 15, y - 15, 30, 30);
            }
            else {
                int d = 0;
                for (Ref ref : node.refs) {
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

        void drawLabel(Layout.Node node, int x, int y) {
            AffineTransform t = g.getTransform();
            try {
                String s = name(node);
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

        String name(Layout.Node node) {
            if (node.refs.isEmpty()) {
                return node.id.getName().substring(0, 8);
            }
            else {
                StringBuilder s = new StringBuilder();
                Iterator<Ref> it = node.refs.iterator();
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

    private static final double SCALE = 1.0;
    private static final int H_MARGIN = 120;
    private static final int V_MARGIN = 70;
    private static final int H_SPACE = 150;
    private static final int V_SPACE = 80;
    private static final Stroke GRID_STROKE = new BasicStroke(1.0f);
    private static final Stroke EDGE_STROKE = new BasicStroke(2.0f);
    private static final Color C1A = new Color(0x9ED8BA);
    private static final Color C1B = new Color(0x71B090);
    private static final Color C2A = new Color(0xFF9A40);
    private static final Color C2B = new Color(0xFF7800);
    private static final Color C3A = new Color(0x409EFF);
    private static final Color C3B = new Color(0x007DFF);
    private final Layout layout;
    private final double scale;
    private final int hMargin;
    private final int vMargin;
    private final int hSpace;
    private final int vSpace;
    private final int width;
    private final int height;
    private final Dimension size;

    public GraphPlotter(Layout layout) {
        this.layout = layout;
        scale = SCALE;
        hMargin = H_MARGIN;
        vMargin = V_MARGIN;
        hSpace = H_SPACE;
        vSpace = V_SPACE;
        width = (int) Math.ceil(scale * (hMargin * 2 + (layout.getTotalLayers() - 1) * hSpace));
        height = (int) Math.ceil(scale * (vMargin * 2 + (layout.getTotalLanes() - 1) * vSpace));
        size = new Dimension(Math.max(width, 600), Math.max(height, 300));
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public Dimension getSize() {
        return size;
    }

    public void draw(Graphics2D g) {
        antiAliasGraphics(g);

        g.scale(scale, scale);

        g.setColor(Color.LIGHT_GRAY);
        g.setStroke(GRID_STROKE);

        for (int n = 0; n < layout.getTotalLayers(); n++) {
            g.drawLine(X(n), hMargin, X(n), Y(layout.getTotalLanes() - 1));
        }

        for (int m = 0; m < layout.getTotalLanes(); m++) {
            g.drawLine(vMargin, Y(m), X(layout.getTotalLayers() - 1), Y(m));
        }

        int offset = 0;
        for (Layout.Partition partition : layout.getPartitions()) {
            AffineTransform t = g.getTransform();
            try {
                g.translate(0, offset * vSpace);
                partition.visit(new EdgePlotter(g));
                partition.visit(new VertexPlotter(g));
            }
            finally {
                g.setTransform(t);
            }
            offset += partition.getLanes();
        }
    }

    private int X(int col) {
        return hMargin + col * hSpace;
    }

    private int Y(int row) {
        return vMargin + row * vSpace;
    }

    public static void antiAliasGraphics(Graphics2D g) {
        // for antialiasing geometric shapes
        g.addRenderingHints(new RenderingHints(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON));
        // for antialiasing text
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }
}
