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
        return plotter.getSize();
    }

    @Override public void paintComponent(Graphics g) {
        draw((Graphics2D) g);
    }

    private void draw(Graphics2D g) {
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, getWidth(), getHeight());

        plotter.draw(g);
    }
}
