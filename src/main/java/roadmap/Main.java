package roadmap;

import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import roadmap.graph.CommitList;
import roadmap.graph.RefGraph;
import roadmap.plot.PlotPanel;
import roadmap.ref.Ref;
import roadmap.ref.RefFilter;
import roadmap.ref.RefSet;
import roadmap.util.CliApp;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
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
    private RefSet refSet;
    private CommitList commitList;
    private RefGraph refGraph;
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
        refSet = RefSet.from(repository, new RefFilter() {
            @Override public boolean accept(Ref ref) {
                return ref.isLocal()
                        || remotes && ref.isRemote()
                        || tags && ref.isTag();
            }
        });
        commitList = new CommitList(objectReader, refSet);
        refGraph = commitList.getRefGraph();

        plot();
    }

    private void plot() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                createAndShowGui();
            }
        });
    }

    private void createAndShowGui() {
        JFrame f = new JFrame("Ref Graph");
        f.add(new JScrollPane(new PlotPanel(refGraph)));
        f.pack();
        f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        f.setVisible(true);
    }
}
