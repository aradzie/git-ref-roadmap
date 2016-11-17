package roadmap.graph;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineParser;
import roadmap.ref.Ref;
import roadmap.ref.RefSet;
import roadmap.test.RepositorySetupRule;
import roadmap.util.CliApp;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;

public class CommitListApp
        extends CliApp {
    public static void main(String[] args)
            throws Exception {
        exec(args, new CommitListApp());
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
        out.println("Get list of all refs in a repository.");
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

        System.out.println("ALL REFS=" + refs.all().size());

        CommitList list = new CommitList(db.newObjectReader(), refs);

        HashSet<Ref> a = new HashSet<>();
        HashSet<Ref> b = new HashSet<>();
        for (Commit commit : list) {
            list.getRefs(commit, a);
            if (a.size() != b.size()) {
                System.out.println("CURRENT REFS=" + a.size());
            }
            HashSet<Ref> t = a;
            a = b;
            b = t;
            a.clear();
        }
    }
}
