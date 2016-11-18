package roadmap.graph;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineParser;
import roadmap.ref.Ref;
import roadmap.ref.RefSet;
import roadmap.test.RepositorySetupRule;
import roadmap.util.CliApp;
import roadmap.util.Git;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Objects;
import java.util.TreeSet;

public class MergeBaseApp
        extends CliApp {
    public static void main(String[] args)
            throws Exception {
        exec(args, new MergeBaseApp());
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
        out.println("Make sure internal algorithm finds the same " +
                "merge bases that native Git does.");
    }

    @Override protected void run(CmdLineParser parser)
            throws Exception {
        try (FileRepository db = RepositorySetupRule.openWorkTree(dir)) {
            run(db);
        }
    }

    private void run(Repository db)
            throws IOException {
        RefSet refs = RefSet.from(db);
        CommitList list = new CommitList(db.newObjectReader(), refs);
        Graph graph = list.getGraph();

        for (Ref a : refs) {
            if (!a.isBranch()) {
                continue;
            }
            for (Ref b : refs) {
                if (!b.isBranch()) {
                    continue;
                }
                TreeSet<Graph.Node> our = new TreeSet<>();
                graph.findMergeBases(
                        graph.node(a.getId()),
                        graph.node(b.getId()), our);

                TreeSet<ObjectId> git = new TreeSet<>(
                        new Git(db.getDirectory())
                                .mergeBase()
                                .all(true)
                                .commit(a.getId().getName())
                                .commit(b.getId().getName())
                                .execute(new Git.IdListResult()));

                if (!Objects.equals(our, git)) {
                    System.out.println("does not match: " + a.getName() + "/" + b.getName());
                    System.out.println("our:");
                    for (Graph.Node node : our) {
                        System.out.println("\t" + node.getName());
                    }
                    System.out.println("git:");
                    for (ObjectId node : git) {
                        System.out.println("\t" + node.name());
                    }
                }
            }
        }
    }
}
