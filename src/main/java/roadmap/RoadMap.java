package roadmap;

import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.storage.file.*;
import org.kohsuke.args4j.*;
import roadmap.graph.*;
import roadmap.plot.*;
import roadmap.ref.*;
import roadmap.util.*;

import javax.swing.*;
import java.io.*;

public class RoadMap extends CliApp {
    public static void main(String[] args)
            throws Exception {
        exec(args, new RoadMap());
    }

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
    @Argument(
            index = 0,
            metaVar = "DIR",
            usage = "Git repository dir"
    )
    private File dir;

    @Override protected void describe(PrintWriter out)
            throws Exception {
        out.println("Display ref roadmap graph for a Git repository.");
    }

    @Override protected void run(CmdLineParser parser)
            throws Exception {
        RefGraph graph = graph();
        graph.dump(new PrintWriter(System.out));
        plot(graph);
    }

    private RefGraph graph()
            throws IOException {
        if (dir == null) {
            dir = new File(".").getAbsoluteFile();
        }
        try (Repository db = new FileRepositoryBuilder()
                .setWorkTree(dir.getCanonicalFile())
                .setMustExist(true)
                .build()) {
            return new CommitList(db.newObjectReader(), RefSet.from(db)).getRefGraph();
        }
    }

    private void plot(final RefGraph graph) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                createAndShowGui(graph);
            }
        });
    }

    private void createAndShowGui(RefGraph graph) {
        JFrame f = new JFrame("Ref Graph");
        f.add(new JScrollPane(new RefGraphPlotPanel(graph)));
        f.pack();
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setVisible(true);
    }
}
