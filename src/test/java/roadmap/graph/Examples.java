package roadmap.graph;

import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import roadmap.test.RepositorySetup;

import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.Constants.R_TAGS;

class Examples {
    /**
     * Creates the following commit graph:
     *
     * <pre>
     * X,a    c     e
     * o-----o-----o
     *        \     \
     * Y,b     \ d   \ f     g
     * o--------o-----o-----o
     * </pre>
     */
    static class E1
            implements RepositorySetup {
        RevCommit a, b, c, d, e, f, g;

        @Override public void play(Repository repository)
                throws Exception {
            TestRepository<Repository> util = new TestRepository<>(repository);
            g = util.commit().message("G").create();
            f = util.commit().message("F").parent(g).create();
            e = util.commit().message("E").parent(f).create();
            d = util.commit().message("D").parent(f).create();
            c = util.commit().message("C").parent(d).parent(e).create();
            b = util.commit().message("B").parent(d).create();
            a = util.commit().message("A").parent(c).create();

            util.update(HEAD, a);
            util.update(R_HEADS + "X", a);
            util.update(R_HEADS + "Y", b);
        }
    }

    /**
     * Creates the following commit graph:
     *
     * <pre>
     * X,a    c  e
     * o-----o--o----+
     *        \/      \
     * Y,b   / \       \
     * o----o---o-------o
     *       d   f       g
     * </pre>
     */
    static class E2
            implements RepositorySetup {
        RevCommit a, b, c, d, e, f, g;

        @Override public void play(Repository repository)
                throws Exception {
            TestRepository<Repository> util = new TestRepository<>(repository);
            g = util.commit().message("G").create();
            f = util.commit().message("F").parent(g).create();
            e = util.commit().message("E").parent(g).create();
            d = util.commit().message("D").parent(f).parent(e).create();
            c = util.commit().message("C").parent(e).parent(f).create();
            b = util.commit().message("B").parent(d).create();
            a = util.commit().message("A").parent(c).create();

            util.update(HEAD, a);
            util.update(R_HEADS + "X", a);
            util.update(R_HEADS + "Y", b);
        }
    }

    /**
     * Creates the following commit graph:
     *
     * <pre>
     * T,a    c,X
     * o-----o
     *        \
     * HEAD,b  \ d     e
     * o--------o-----o
     * </pre>
     */
    static class E3
            implements RepositorySetup {
        RevCommit a;
        RevCommit b;
        RevCommit c;
        RevCommit d;
        RevCommit e;

        @Override public void play(Repository repository)
                throws Exception {
            TestRepository<Repository> util = new TestRepository<>(repository);
            e = util.commit().message("E").create();
            d = util.commit().message("D").parent(e).create();
            c = util.commit().message("C").parent(d).create();
            b = util.commit().message("B").parent(d).create();
            a = util.commit().message("A").parent(c).create();

            util.update(HEAD, b);
            util.update(R_TAGS + "T", a);
            util.update(R_TAGS + "X", c);
        }
    }
}
