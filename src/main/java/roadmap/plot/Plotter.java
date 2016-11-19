package roadmap.plot;

import roadmap.ref.Ref;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;

public class Plotter {
    private static final Stroke GRID_STROKE = new BasicStroke(1.0f);
    private static final Color GRID_COLOR = Color.LIGHT_GRAY;
    private static final Stroke EDGE_STROKE = new BasicStroke(2.0f);
    private static final Color EDGE_COLOR = Color.DARK_GRAY;
    private static final Color COMMIT_L_COLOR = new Color(0x9ED8BA);
    private static final Color COMMIT_D_COLOR = new Color(0x71B090);
    private static final Color TAG_L_COLOR = new Color(0xFF9A40);
    private static final Color TAG_D_COLOR = new Color(0xFF7800);
    private static final Color BRANCH_L_COLOR = new Color(0x409EFF);
    private static final Color BRANCH_D_COLOR = new Color(0x007DFF);
    private static final Color LABEL_COLOR = Color.BLACK;
    private static final int H_MARGIN = 120;
    private static final int V_MARGIN = 70;
    private static final int H_SPACE = 150;
    private static final int V_SPACE = 80;
    private static final int RADIUS = 15;
    private final Layout layout;
    private final int gridWidth;
    private final int gridHeight;
    private int hMargin;
    private int vMargin;
    private int hSpace;
    private int vSpace;
    private int radius;
    private double hScale;
    private double vScale;

    public Plotter(Layout layout) {
        this.layout = layout;
        gridWidth = layout.getTotalLayers() - 1;
        gridHeight = layout.getTotalLanes() - 1;
        hMargin = H_MARGIN;
        vMargin = V_MARGIN;
        hSpace = H_SPACE;
        vSpace = V_SPACE;
        radius = RADIUS;
        hScale = 1;
        vScale = 1;
    }

    public int getMinWidth() {
        return (int) Math.ceil(hMargin * 2 + gridWidth * hSpace);
    }

    public int getMinHeight() {
        return (int) Math.ceil(vMargin * 2 + gridHeight * vSpace);
    }

    private int X(int x) {
        return (int) Math.round(x * hScale);
    }

    private int Y(int y) {
        return (int) Math.round(y * vScale);
    }

    private int colX(int col) {
        return X(hMargin + col * hSpace);
    }

    private int rowY(int row) {
        return Y(vMargin + row * vSpace);
    }

    public void draw(final Graphics2D g, int width, int height) {
        double minWidth = getMinWidth();
        double minHeight = getMinHeight();
        hScale = Math.max(width, minWidth) / minWidth;
        vScale = Math.max(height, minHeight) / minHeight;

        antiAliasGraphics(g);

        drawGrid(g);

        int offset = 0;
        for (Layout.Partition partition : layout.getPartitions()) {
            AffineTransform t = g.getTransform();
            try {
                g.translate(X(0), Y(offset * vSpace));
                partition.visit(new Layout.VertexVisitor() {
                    @Override public void visit(Layout.Bend bend) {
                        drawEdge(g, bend);
                    }

                    @Override public void visit(Layout.Node node) {
                        drawEdge(g, node);
                    }
                });
                partition.visit(new Layout.VertexVisitor() {
                    @Override public void visit(Layout.Bend bend) {}

                    @Override public void visit(Layout.Node node) {
                        drawVertex(g, node);
                        drawLabels(g, node);
                    }
                });
            }
            finally {
                g.setTransform(t);
            }
            offset += partition.getLanes();
        }
    }

    private void drawGrid(Graphics2D g) {
        g.setColor(GRID_COLOR);
        g.setStroke(GRID_STROKE);
        for (int n = 0; n <= gridHeight; n++) {
            g.drawLine(colX(0), rowY(n), colX(gridWidth), rowY(n));
        }
        for (int n = 0; n <= gridWidth; n++) {
            g.drawLine(colX(n), rowY(0), colX(n), rowY(gridHeight));
        }
    }

    private void drawEdge(Graphics2D g, Layout.Vertex vertex) {
        int x = colX(vertex.col());
        int y = rowY(vertex.row());
        g.setColor(EDGE_COLOR);
        g.setStroke(EDGE_STROKE);
        for (Layout.Vertex outgoing : vertex.getOutgoing()) {
            g.drawLine(x, y, colX(outgoing.col()), rowY(outgoing.row()));
        }
    }

    private void drawVertex(Graphics2D g, Layout.Node node) {
        int x = colX(node.col());
        int y = rowY(node.row());
        if (node.refs.isEmpty()) {
            g.setColor(COMMIT_L_COLOR);
            g.fillOval(x - radius, y - radius, radius * 2, radius * 2);
            g.setColor(COMMIT_D_COLOR);
            g.drawOval(x - radius, y - radius, radius * 2, radius * 2);
        }
        else {
            int d = 0;
            for (Ref ref : node.refs) {
                if (ref.isTag()) {
                    g.setColor(TAG_L_COLOR);
                    g.fillOval(x - radius + d, y - radius + d, radius * 2, radius * 2);
                    g.setColor(TAG_D_COLOR);
                    g.drawOval(x - radius + d, y - radius + d, radius * 2, radius * 2);
                }
                else {
                    g.setColor(BRANCH_L_COLOR);
                    g.fillOval(x - radius + d, y - radius + d, radius * 2, radius * 2);
                    g.setColor(BRANCH_D_COLOR);
                    g.drawOval(x - radius + d, y - radius + d, radius * 2, radius * 2);
                }
                d = d - 3;
            }
        }
    }

    private void drawLabels(Graphics2D g, Layout.Node node) {
        int x = colX(node.col());
        int y = rowY(node.row());
        FontMetrics fm = g.getFontMetrics();
        String[] labels = nodeLabels(node);
        for (int n = 0; n < labels.length; n++) {
            String label = fitLabel(g, fm, labels[n], X(hSpace - radius * 2));
            Rectangle bounds = fm.getStringBounds(label, g).getBounds();
            g.setColor(LABEL_COLOR);
            g.drawString(label, x + radius, y + radius + (n + 1) * bounds.height);
        }
    }

    private static String[] nodeLabels(Layout.Node node) {
        ArrayList<String> l = new ArrayList<>();
        if (node.refs.isEmpty()) {
            l.add(node.id.getName().substring(0, 8));
        }
        else {
            for (Ref ref : node.refs) {
                l.add(ref.getSuffix());
            }
        }
        return l.toArray(new String[l.size()]);
    }

    private static String fitLabel(Graphics2D g, FontMetrics fm, String label, int width) {
        String s = label;
        int n = 0;
        while (n < label.length()) {
            Rectangle bounds = fm.getStringBounds(s, g).getBounds();
            if (bounds.width <= width) {
                break;
            }
            n++;
            s = "..." + label.substring(n);
        }
        return s;
    }

    private static void antiAliasGraphics(Graphics2D g) {
        // for anti-aliasing geometric shapes
        g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        // for anti-aliasing text
        g.setRenderingHint(
                RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }
}
