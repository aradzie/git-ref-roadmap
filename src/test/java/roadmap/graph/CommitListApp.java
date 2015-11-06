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

public class CommitListApp extends CliApp {
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
