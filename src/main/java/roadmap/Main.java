package roadmap;

import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import roadmap.graph.CommitList;
import roadmap.graph.Graph;
import roadmap.plot.Layout;
import roadmap.plot.Plotter;
import roadmap.ref.Ref;
import roadmap.ref.RefFilter;
import roadmap.ref.RefSet;
import roadmap.ui.GraphPanel;
import roadmap.util.CliApp;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

public class Main
        extends CliApp {
    public static void main(String[] args)
            throws Exception {
        exec(args, new Main());
    }

    private Repository repository;
    private ObjectReader objectReader;
    @Option(
            name = "--tags",
            usage = "Include tags"
    )
    private boolean tags;
    @Option(
            name = "--remotes",
            usage = "Include remote branches"
    )
    private boolean remotes;
    @Option(
            name = "-o",
            aliases = {"--output"},
            usage = "Save image to PNG file",
            metaVar = "OUTPUT"
    )
    private File out;

    @Override protected void describe(PrintWriter out)
            throws Exception {
        out.println("Display roadmap graph for a Git repository.");
    }

    @Override protected void run(CmdLineParser parser)
            throws Exception {
        File dir = new File(".").getAbsoluteFile();

        try (Repository repository = new FileRepositoryBuilder()
                .setWorkTree(dir.getCanonicalFile())
                .setMustExist(true)
                .build()) {
            try (ObjectReader objectReader = repository.newObjectReader()) {
                this.repository = repository;
                this.objectReader = objectReader;
                run();
            }
        }
    }

    private void run()
            throws IOException {
        RefSet refSet = RefSet.from(repository, getRefFilter());
        CommitList commitList = new CommitList(objectReader, refSet);
        Graph graph = commitList.getGraph();
        Layout layout = new Layout(graph);
        Plotter plotter = new Plotter.Builder().build(layout);
        if (out != null) {
            saveImage(plotter, out);
        }
        else {
            showGui(new GraphPanel(plotter));
        }
    }

    private RefFilter getRefFilter() {
        return new RefFilter() {
            @Override public boolean accept(Ref ref) {
                return ref.isLocal()
                        || remotes && ref.isRemote()
                        || tags && ref.isTag();
            }
        };
    }

    private static void saveImage(Plotter plotter, File file)
            throws IOException {
        BufferedImage image = new BufferedImage(
                plotter.getWidth(),
                plotter.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        plotter.draw((Graphics2D) image.getGraphics());
        ImageIO.write(image, "png", file);
    }

    private static void showGui(final GraphPanel panel) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                JFrame frame = new JFrame("Ref Graph");
                frame.add(new JScrollPane(panel));
                frame.pack();
                frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
                frame.setVisible(true);
            }
        });
    }
}
