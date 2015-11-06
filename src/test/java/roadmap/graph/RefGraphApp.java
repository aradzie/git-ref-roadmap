package roadmap.graph;

import org.kohsuke.args4j.*;
import roadmap.model.*;
import roadmap.test.*;
import roadmap.test.RepositorySetupRule.*;
import roadmap.util.*;

import javax.swing.*;
import java.io.*;

public class RefGraphApp extends CliApp {
    public static void main(String[] args)
            throws Exception {
        exec(args, new RefGraphApp());
    }

    @Argument(
            index = 0,
            required = true,
            metaVar = "DIR",
            usage = "Git repository dir"
    )
    private File dir;

    @Override protected void describe(PrintWriter out)
            throws Exception {
        out.println("Display ref graph for a repository.");
    }

    @Override protected void run(CmdLineParser parser)
            throws Exception {
        RefGraph graph = graph();
        graph.dump(new PrintWriter(System.out));
        plot(graph);
    }

    private RefGraph graph()
            throws IOException {
        try (FileRepository db =
                     RepositorySetupRule.openWorkTree(dir)) {
            return new CommitList(db.newObjectReader(),
                    RefSet.builder(db).build()).getRefGraph().copy();
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
