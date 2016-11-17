package roadmap.graph;

import org.eclipse.jgit.internal.storage.file.*;
import org.eclipse.jgit.lib.*;
import org.kohsuke.args4j.*;
import roadmap.ref.Ref;
import roadmap.ref.*;
import roadmap.test.*;
import roadmap.util.*;

import java.io.*;
import java.util.*;

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
        RefGraph graph = list.getRefGraph();

        for (Ref a : refs) {
            if (!a.isBranch()) {
                continue;
            }
            for (Ref b : refs) {
                if (!b.isBranch()) {
                    continue;
                }
                TreeSet<RefGraph.Node> our = new TreeSet<>();
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
                    for (RefGraph.Node node : our) {
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
