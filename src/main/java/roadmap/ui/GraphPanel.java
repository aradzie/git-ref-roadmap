package roadmap.ui;

import roadmap.plot.Plotter;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

/** Swing component to draw graph of commits. */
public class GraphPanel
        extends JPanel {
    private final Plotter plotter;

    public GraphPanel(Plotter plotter) {
        this.plotter = plotter;
    }

    @Override public Dimension getPreferredSize() {
        int minWidth = Math.max(plotter.getMinWidth(), 600);
        int minHeight = Math.max(plotter.getMinHeight(), 300);
        return new Dimension(minWidth, minHeight);
    }

    @Override public void paintComponent(Graphics g) {
        draw((Graphics2D) g);
    }

    private void draw(Graphics2D g) {
        int width = getWidth();
        int height = getHeight();

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        plotter.draw(g, width, height);
    }
}
