package roadmap.plot;

import roadmap.graph.Layout;
import roadmap.graph.Layout.Partition;
import roadmap.graph.Layout.Vertex;
import roadmap.graph.Layout.VertexVisitor;
import roadmap.graph.RefGraph;
import roadmap.ref.Ref;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.Iterator;

/** Swing component to draw graph of commits. */
public class PlotPanel
        extends JPanel {
    private class DrawEdges
            implements VertexVisitor {
        final Graphics2D g;

        DrawEdges(Graphics2D g) {
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
            g.setStroke(edgeStroke);
            for (Vertex outgoing : vertex.getOutgoing()) {
                g.drawLine(x, y, X(outgoing.col()), Y(outgoing.row()));
            }
        }
    }

    private class DrawVertex
            implements VertexVisitor {
        final Graphics2D g;

        DrawVertex(Graphics2D g) {
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
    private static final Color C1A = new Color(0x9ED8BA);
    private static final Color C1B = new Color(0x71B090);
    private static final Color C2A = new Color(0xFF9A40);
    private static final Color C2B = new Color(0xFF7800);
    private static final Color C3A = new Color(0x409EFF);
    private static final Color C3B = new Color(0x007DFF);
    private static final Stroke gridStroke = new BasicStroke(1.0f);
    private static final Stroke edgeStroke = new BasicStroke(2.0f);
    private final Layout layout;

    public PlotPanel(RefGraph graph) {
        this.layout = new Layout(graph);
    }

    @Override public Dimension getPreferredSize() {
        int w = (int) (SCALE * (H_MARGIN * 2 + (layout.getTotalLayers() - 1) * H_SPACE));
        int h = (int) (SCALE * (V_MARGIN * 2 + (layout.getTotalLanes() - 1) * V_SPACE));
        return new Dimension(Math.max(w, 600), Math.max(h, 300));
    }

    @Override public void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;

        antiAliasGraphics(g2d);

        draw(g2d);
    }

    private void draw(Graphics2D g) {
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, getWidth(), getHeight());

        g.scale(SCALE, SCALE);

        g.setColor(Color.LIGHT_GRAY);
        g.setStroke(gridStroke);

        for (int n = 0; n < layout.getTotalLayers(); n++) {
            g.drawLine(X(n), H_MARGIN, X(n), Y(layout.getTotalLanes() - 1));
        }

        for (int m = 0; m < layout.getTotalLanes(); m++) {
            g.drawLine(V_MARGIN, Y(m), X(layout.getTotalLayers() - 1), Y(m));
        }

        int offset = 0;
        for (Partition partition : layout.getPartitions()) {
            AffineTransform t = g.getTransform();
            try {
                g.translate(0, offset * V_SPACE);
                partition.visit(new DrawEdges(g));
                partition.visit(new DrawVertex(g));
            }
            finally {
                g.setTransform(t);
            }
            offset += partition.getLanes();
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

    private static int X(int col) {
        return H_MARGIN + col * H_SPACE;
    }

    private static int Y(int row) {
        return V_MARGIN + row * V_SPACE;
    }
}
