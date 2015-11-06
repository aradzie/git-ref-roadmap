package roadmap.graph;

import org.eclipse.jgit.junit.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.*;
import org.junit.*;
import roadmap.test.*;

import java.util.*;

import static org.eclipse.jgit.lib.Constants.*;
import static org.junit.Assert.*;

public class CommitDiffTest {
    /**
     * Creates the following commit graph:
     *
     * <pre>
     *  B,b      D,d  F,f
     * o--------o----o
     *           \  / \
     *            \/   \
     *            /\    \
     *  HEAD,a   /  \    \
     * o--------o----o----o----o----o
     *           C,c  E,e  G,g  H,h  I,i
     * </pre>
     */
    class Setup implements RepositoryRule.Setup {
        RevCommit a, b, c, d, e, f, g, h, i;

        @Override public void play(Repository repository)
                throws Exception {
            TestRepository<Repository> util = new TestRepository<>(repository);
            i = util.commit().message("I").create();
            h = util.commit().message("H").parent(i).create();
            g = util.commit().message("G").parent(h).create();
            f = util.commit().message("F").parent(g).create();
            e = util.commit().message("E").parent(g).create();
            d = util.commit().message("D").parent(e).parent(f).create();
            c = util.commit().message("C").parent(e).parent(f).create();
            b = util.commit().message("B").parent(d).create();
            a = util.commit().message("A").parent(c).create();

            util.update(HEAD, a);
            util.update(R_HEADS + "G", g);
            util.update(R_HEADS + "F", f);
            util.update(R_HEADS + "E", e);
            util.update(R_HEADS + "D", d);
            util.update(R_HEADS + "C", c);
            util.update(R_HEADS + "B", b);
        }
    }

    class Sink implements CommitDiff.Sink {
        final HashSet<ObjectId> added = new HashSet<>();
        final HashSet<ObjectId> removed = new HashSet<>();

        @Override public void added(ObjectId id) {
            added.add(id);
        }

        @Override public void removed(ObjectId id) {
            removed.add(id);
        }
    }

    @Rule public final RepositorySetupRule setupRule = new RepositorySetupRule();

    @Test public void empty()
            throws Exception {
        Repository db = setupRule.setupBare(new RepositoryRule.Setup.Empty());
        CommitDiff diff = new CommitDiff(new RevWalk(db));
        Sink sink = new Sink();
        diff.diff(ObjectId.zeroId(), ObjectId.zeroId(), sink);
        assertTrue(sink.added.isEmpty());
        assertTrue(sink.removed.isEmpty());
    }

    @Test public void oneSided()
            throws Exception {
        Setup s = new Setup();
        Repository db = setupRule.setupBare(s);
        CommitDiff diff = new CommitDiff(new RevWalk(db));
        if (true) {
            Sink sink = new Sink();
            diff.diff(s.a, ObjectId.zeroId(), sink);
            assertTrue(sink.added.isEmpty());
            assertEquals(7, sink.removed.size());
            assertTrue(sink.removed.contains(s.a));
            assertTrue(sink.removed.contains(s.c));
            assertTrue(sink.removed.contains(s.e));
            assertTrue(sink.removed.contains(s.f));
            assertTrue(sink.removed.contains(s.g));
            assertTrue(sink.removed.contains(s.h));
            assertTrue(sink.removed.contains(s.i));
        }
        if (true) {
            Sink sink = new Sink();
            diff.diff(ObjectId.zeroId(), s.a, sink);
            assertEquals(7, sink.added.size());
            assertTrue(sink.added.contains(s.a));
            assertTrue(sink.added.contains(s.c));
            assertTrue(sink.added.contains(s.e));
            assertTrue(sink.added.contains(s.f));
            assertTrue(sink.added.contains(s.g));
            assertTrue(sink.added.contains(s.h));
            assertTrue(sink.added.contains(s.i));
            assertTrue(sink.removed.isEmpty());
        }
    }

    @Test public void equalSided()
            throws Exception {
        Setup s = new Setup();
        Repository db = setupRule.setupBare(s);
        CommitDiff diff = new CommitDiff(new RevWalk(db));
        Sink sink = new Sink();
        diff.diff(s.a, s.a, sink);
        assertTrue(sink.added.isEmpty());
        assertTrue(sink.removed.isEmpty());
    }

    @Test public void dualSided()
            throws Exception {
        Setup s = new Setup();
        Repository db = setupRule.setupBare(s);
        if (true) {
            CommitDiff diff = new CommitDiff(new RevWalk(db));
            Sink sink = new Sink();
            diff.diff(s.e, s.f, sink);
            assertEquals(1, sink.added.size());
            assertTrue(sink.added.contains(s.f));
            assertEquals(1, sink.removed.size());
            assertTrue(sink.removed.contains(s.e));
        }
        if (true) {
            CommitDiff diff = new CommitDiff(new RevWalk(db));
            Sink sink = new Sink();
            diff.diff(s.f, s.e, sink);
            assertEquals(1, sink.added.size());
            assertTrue(sink.added.contains(s.e));
            assertEquals(1, sink.removed.size());
            assertTrue(sink.removed.contains(s.f));
        }
    }

    @Test public void unequalSided()
            throws Exception {
        Setup s = new Setup();
        Repository db = setupRule.setupBare(s);
        if (true) {
            CommitDiff diff = new CommitDiff(new RevWalk(db));
            Sink sink = new Sink();
            diff.diff(s.c, s.b, sink);
            assertEquals(2, sink.added.size());
            assertTrue(sink.added.contains(s.b));
            assertTrue(sink.added.contains(s.d));
            assertEquals(1, sink.removed.size());
            assertTrue(sink.removed.contains(s.c));
        }
        if (true) {
            CommitDiff diff = new CommitDiff(new RevWalk(db));
            Sink sink = new Sink();
            diff.diff(s.b, s.c, sink);
            assertEquals(1, sink.added.size());
            assertTrue(sink.added.contains(s.c));
            assertEquals(2, sink.removed.size());
            assertTrue(sink.removed.contains(s.b));
            assertTrue(sink.removed.contains(s.d));
        }
    }

    @Test public void twisted()
            throws Exception {
        Setup s = new Setup();
        Repository db = setupRule.setupBare(s);
        if (true) {
            CommitDiff diff = new CommitDiff(new RevWalk(db));
            Sink sink = new Sink();
            diff.diff(s.a, s.b, sink);
            assertEquals(2, sink.added.size());
            assertTrue(sink.added.contains(s.b));
            assertTrue(sink.added.contains(s.d));
            assertEquals(2, sink.removed.size());
            assertTrue(sink.removed.contains(s.a));
            assertTrue(sink.removed.contains(s.c));
        }
        if (true) {
            CommitDiff diff = new CommitDiff(new RevWalk(db));
            Sink sink = new Sink();
            diff.diff(s.b, s.a, sink);
            assertEquals(2, sink.added.size());
            assertTrue(sink.added.contains(s.a));
            assertTrue(sink.added.contains(s.c));
            assertEquals(2, sink.removed.size());
            assertTrue(sink.removed.contains(s.b));
            assertTrue(sink.removed.contains(s.d));
        }
    }

    @Test public void moreTwisted()
            throws Exception {
        Setup s = new Setup();
        Repository db = setupRule.setupBare(s);
        if (true) {
            CommitDiff diff = new CommitDiff(new RevWalk(db));
            Sink sink = new Sink();
            diff.diff(s.a, s.d, sink);
            assertEquals(1, sink.added.size());
            assertTrue(sink.added.contains(s.d));
            assertEquals(2, sink.removed.size());
            assertTrue(sink.removed.contains(s.a));
            assertTrue(sink.removed.contains(s.c));
        }
        if (true) {
            CommitDiff diff = new CommitDiff(new RevWalk(db));
            Sink sink = new Sink();
            diff.diff(s.d, s.a, sink);
            assertEquals(2, sink.added.size());
            assertTrue(sink.added.contains(s.a));
            assertTrue(sink.added.contains(s.c));
            assertEquals(1, sink.removed.size());
            assertTrue(sink.removed.contains(s.d));
        }
    }

    @Test public void evenMoreTwisted()
            throws Exception {
        /**
         * Creates the following commit graph:
         *
         * <pre>
         *            c    e
         *           o----o
         *          /      \
         *         /        \
         *      a o          o f
         *         \        /
         *          \ d    /
         *           o----+
         *          /
         *         /
         *      b o
         * </pre>
         */
        class Setup implements RepositoryRule.Setup {
            RevCommit a, b, c, d, e, f;

            @Override public void play(Repository repository)
                    throws Exception {
                TestRepository<Repository> util = new TestRepository<>(repository);
                f = util.commit().message("F").create();
                e = util.commit().message("E").parent(f).create();
                c = util.commit().message("C").parent(e).create();
                d = util.commit().message("D").parent(f).create();
                b = util.commit().message("B").parent(d).create();
                a = util.commit().message("A").parent(c).parent(d).create();

                util.update(HEAD, a);
                util.update(R_HEADS + "F", f);
                util.update(R_HEADS + "E", e);
                util.update(R_HEADS + "D", d);
                util.update(R_HEADS + "C", c);
                util.update(R_HEADS + "B", b);
            }
        }

        Setup s = new Setup();
        Repository db = setupRule.setupBare(s);
        if (true) {
            CommitDiff diff = new CommitDiff(new RevWalk(db));
            Sink sink = new Sink();
            diff.diff(s.a, s.b, sink);
            assertEquals(1, sink.added.size());
            assertTrue(sink.added.contains(s.b));
            assertEquals(3, sink.removed.size());
            assertTrue(sink.removed.contains(s.a));
            assertTrue(sink.removed.contains(s.c));
            assertTrue(sink.removed.contains(s.e));
        }
        if (true) {
            CommitDiff diff = new CommitDiff(new RevWalk(db));
            Sink sink = new Sink();
            diff.diff(s.b, s.a, sink);
            assertEquals(3, sink.added.size());
            assertTrue(sink.added.contains(s.a));
            assertTrue(sink.added.contains(s.c));
            assertTrue(sink.added.contains(s.e));
            assertEquals(1, sink.removed.size());
            assertTrue(sink.removed.contains(s.b));
        }
    }
}
