package roadmap.plot;

import roadmap.graph.Layout;
import roadmap.graph.RefGraph;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

/** Swing component to draw graph of commits. */
public class GraphPanel
        extends JPanel {
    private final GraphPlotter graphPlotter;

    public GraphPanel(RefGraph graph) {
        graphPlotter = new GraphPlotter(new Layout(graph));
    }

    @Override public Dimension getPreferredSize() {
        return graphPlotter.getSize();
    }

    @Override public void paintComponent(Graphics g) {
        draw((Graphics2D) g);
    }

    private void draw(Graphics2D g) {
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, getWidth(), getHeight());

        graphPlotter.draw(g);
    }
}
