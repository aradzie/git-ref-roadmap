package roadmap;

import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.storage.file.*;
import org.kohsuke.args4j.*;
import roadmap.graph.*;
import roadmap.plot.*;
import roadmap.ref.Ref;
import roadmap.ref.*;
import roadmap.util.*;

import javax.swing.*;
import java.io.*;
import java.text.*;
import java.util.*;

import static java.text.DateFormat.*;

public class RoadMap
        extends CliApp {
    private static class Row {
        static final Comparator<Row> COMPARATOR = new Comparator<Row>() {
            @Override public int compare(Row o1, Row o2) {
                return reverse(sort(o1, o2));
            }

            int sort(Row o1, Row o2) {
                // Least amount of commits in the master
                if (o2.nb > o1.nb) {
                    return 1;
                }
                if (o2.nb < o1.nb) {
                    return -1;
                }
                // Least amount of commits in the other branch
                if (o2.na > o1.na) {
                    return 1;
                }
                if (o2.na < o1.na) {
                    return -1;
                }
                return 0;
            }
        };
        static final DateFormat FORMAT = getDateTimeInstance(SHORT, SHORT);
        final Ref a;
        final int na;
        final Ref b;
        final int nb;
        final CommitDetails commit;

        Row(Ref a, int na, Ref b, int nb, CommitDetails commit) {
            this.a = a;
            this.b = b;
            this.na = na;
            this.nb = nb;
            this.commit = commit;
        }

        @Override public String toString() {
            StringBuilder s = new StringBuilder();
            toString(s);
            return s.toString();
        }

        void toString(StringBuilder s) {
            formatDiff(s);
            s.append("\n\t");
            formatCommit(s);
            s.append("\n");
        }

        void formatDiff(StringBuilder s) {
            alignLeft(s, a.getName(), 35);
            s.append(" ");
            alignRight(s, String.valueOf(na), 6);
            s.append(" / ");
            alignLeft(s, String.valueOf(nb), 6);
            s.append(" ");
            align(s, b.getName(), 35);
        }

        void formatCommit(StringBuilder s) {
            s.append("Updated on ");
            s.append(FORMAT.format(commit.getAuthor().getWhen()));
            s.append(" by ");
            s.append(commit.getAuthor().getName());
            s.append(" <");
            s.append(commit.getAuthor().getEmailAddress());
            s.append(">: ");
            String message = commit.getMessage();
            int n = message.indexOf("\n");
            if (n != -1) {
                message = message.substring(0, n);
            }
            align(s, message, 60);
        }
    }

    public static void main(String[] args)
            throws Exception {
        exec(args, new RoadMap());
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
        if (dir == null) {
            dir = new File(".").getAbsoluteFile();
        }

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
        refSet = RefSet.from(repository);
        commitList = new CommitList(objectReader, refSet);
        refGraph = commitList.getRefGraph();

        List<Row> rows = table(refGraph.getRefDiffs());

        StringBuilder s = new StringBuilder();
        for (Row row : rows) {
            row.formatDiff(s);
            s.append("\n\t");
            row.formatCommit(s);
            s.append("\n");
        }
        System.out.print(s);

        plot();
    }

    private List<Row> table(Set<RefDiff> diffs)
            throws IOException {
        ArrayList<Row> rows = new ArrayList<>();
        Ref master = refSet.defaultBranch();
        for (RefDiff diff : diffs) {
            if (ObjectId.equals(master.getId(), diff.getB())) {
                CommitDetails commit = commitList.loadDetails(objectReader, diff.getA());
                Set<Ref> set = refSet.byId(diff.getA());
                for (Ref ref : set) {
                    rows.add(new Row(ref, diff.getCommitsA(), master, diff.getCommitsB(), commit));
                }
            }
        }
        rows.sort(Row.COMPARATOR);
        return rows;
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

    static void alignLeft(StringBuilder s, String v, int w) {
        int l = v.length();
        if (l < w) {
            s.append(v);
            for (int n = l; n < w; n++) {
                s.append(" ");
            }
        }
        else {
            s.append("...");
            s.append(v.substring(l - w + 3));
        }
    }

    static void alignRight(StringBuilder s, String v, int w) {
        int l = v.length();
        if (l < w) {
            for (int n = l; n < w; n++) {
                s.append(" ");
            }
            s.append(v);
        }
        else {
            s.append("...");
            s.append(v.substring(l - w + 3));
        }
    }

    static void align(StringBuilder s, String v, int w) {
        int l = v.length();
        if (l < w) {
            s.append(v);
        }
        else {
            s.append("...");
            s.append(v.substring(l - w + 3));
        }
    }

    static int reverse(int s) {
        if (s > 0) {
            return -1;
        }
        if (s < 0) {
            return 1;
        }
        return 0;
    }
}
