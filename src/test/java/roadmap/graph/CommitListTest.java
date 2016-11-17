package roadmap.graph;

import org.eclipse.jgit.junit.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.*;
import org.junit.*;
import roadmap.ref.Ref;
import roadmap.ref.*;
import roadmap.test.*;

import java.util.*;

import static org.eclipse.jgit.lib.Constants.*;
import static org.junit.Assert.*;

public class CommitListTest {
    @Rule public final RepositorySetupRule setup = new RepositorySetupRule();

    @Test public void initialize()
            throws Exception {
        final int commits = 100;

        class Setup
                implements RepositorySetup {
            @Override public void play(Repository repository)
                    throws Exception {
                TestRepository<Repository> util = new TestRepository<>(repository);
                RevCommit commit = null;
                for (int n = 0; n < commits; n++) {
                    String message = String.format("commit %05d", n + 1);
                    TestRepository<Repository>.CommitBuilder cb = util.commit().message(message);
                    if (commit == null) {
                        commit = cb.create();
                    }
                    else {
                        commit = cb.parent(commit).create();
                    }
                }
                util.update(HEAD, commit);
            }
        }

        Setup setup = new Setup();
        Repository db = this.setup.setupBare(setup);

        RefSet refs = RefSet.from(db);
        CommitList list = new CommitList(db.newObjectReader(), refs);

        assertEquals(commits, list.size());

        HashSet<Commit> set = new HashSet<>();
        for (Commit commit : list) {
            assertNotNull(commit);
            set.add(commit);
        }
        assertEquals(commits, set.size());

        assertEquals(0, list.get(0).getChildCount());
        assertEquals(1, list.get(0).getParentCount());
        assertEquals(1, list.get(1).getChildCount());
        assertEquals(1, list.get(1).getParentCount());
        assertEquals(list.get(1), list.get(0).getParent(0));
        assertEquals(list.get(0), list.get(1).getChild(0));
        assertEquals(0, list.get(commits - 1).getParentCount());

        for (Commit commit : list) {
            assertEquals(commit.getIndex(), list.indexOf(commit));

            CommitDetails commitDetails = list.loadDetails(db.newObjectReader(), commit);
            assertTrue(ObjectId.equals(commit, commitDetails));
            assertNotNull(commitDetails.getMessage());
            assertNotNull(commitDetails.getAuthor());
            assertNotNull(commitDetails.getCommitter());
        }

        assertEquals(-1, list.indexOf(ObjectId.zeroId()));

        HashSet<Ref> x = new HashSet<>();
        for (Commit commit : list) {
            list.getRefs(commit, x);
            assertEquals(1, x.size());
            x.clear();
        }
    }

    /**
     * Creates the following commit graph:
     *
     * <pre>
     *  B,b    C,c
     * o------o
     *         \
     * HEAD,a   \ F,f (initial)
     * o---------o
     *          /
     *  D,d    / E,e
     * o------o
     * </pre>
     */
    public static class Setup
            implements RepositorySetup {
        RevCommit a, b, c, d, e, f;

        @Override public void play(Repository repository)
                throws Exception {
            TestRepository<Repository> util = new TestRepository<>(repository);
            f = util.commit().message("F").create();
            e = util.commit().message("E").parent(f).create();
            d = util.commit().message("D").parent(e).create();
            c = util.commit().message("C").parent(f).create();
            b = util.commit().message("B").parent(c).create();
            a = util.commit().message("A").parent(f).create();

            util.update(HEAD, a);
            util.update(R_HEADS + "A", a);
            util.update(R_HEADS + "B", b);
            util.update(R_HEADS + "C", c);
            util.update(R_HEADS + "D", d);
            util.update(R_HEADS + "E", e);
            util.update(R_HEADS + "F", f);
        }
    }

    @Test public void iterateSinceUntil()
            throws Exception {
        Setup s = new Setup();
        Repository db = setup.setupBare(s);

        RefSet refs = RefSet.from(db);
        CommitList list = new CommitList(db.newObjectReader(), refs);

        Iterator<Commit> it;

        it = list.iteratorBuilder()
                 .since(refs.selectAll(Arrays.asList("refs/heads/F")))
                 .make().iterator();
        assertTrue(it.hasNext());
        assertEquals(s.f, it.next());
        assertFalse(it.hasNext());

        it = list.iteratorBuilder()
                 .since(refs.selectAll(Arrays.asList("refs/heads/A")))
                 .make().iterator();
        assertTrue(it.hasNext());
        assertEquals(s.a, it.next());
        assertTrue(it.hasNext());
        assertEquals(s.f, it.next());
        assertFalse(it.hasNext());

        it = list.iteratorBuilder()
                 .since(refs.selectAll(Arrays.asList("refs/heads/A")))
                 .until(refs.selectAll(Arrays.asList("refs/heads/F")))
                 .make().iterator();
        assertTrue(it.hasNext());
        assertEquals(s.a, it.next());
        assertFalse(it.hasNext());

        it = list.iteratorBuilder()
                 .since(refs.selectAll(Arrays.asList("refs/heads/A")))
                 .until(refs.selectAll(Arrays.asList("refs/heads/B", "refs/heads/D")))
                 .make().iterator();
        assertTrue(it.hasNext());
        assertEquals(s.a, it.next());
        assertFalse(it.hasNext());
    }

    @Test public void countGroupByAll()
            throws Exception {
        Setup setup = new Setup();
        Repository db = this.setup.setupBare(setup);

        RefSet refs = RefSet.from(db);
        CommitList list = new CommitList(db.newObjectReader(), refs);

        CommitList.GroupByRefMap map = list.countGroupByRef(CommitList.CommitMatcher.ANY);
        assertEquals(2, map.getTotal(refs.byName("refs/heads/A")));
        assertEquals(2, map.getMatched(refs.byName("refs/heads/A")));
        assertEquals(3, map.getTotal(refs.byName("refs/heads/B")));
        assertEquals(3, map.getMatched(refs.byName("refs/heads/B")));
        assertEquals(2, map.getTotal(refs.byName("refs/heads/C")));
        assertEquals(2, map.getMatched(refs.byName("refs/heads/C")));
        assertEquals(3, map.getTotal(refs.byName("refs/heads/D")));
        assertEquals(3, map.getMatched(refs.byName("refs/heads/D")));
        assertEquals(2, map.getTotal(refs.byName("refs/heads/E")));
        assertEquals(2, map.getMatched(refs.byName("refs/heads/E")));
        assertEquals(1, map.getTotal(refs.byName("refs/heads/F")));
        assertEquals(1, map.getMatched(refs.byName("refs/heads/F")));
    }

    @Test public void countGroupByFilterWithPredicate()
            throws Exception {
        final Setup setup = new Setup();
        Repository db = this.setup.setupBare(setup);

        RefSet refs = RefSet.from(db);
        CommitList list = new CommitList(db.newObjectReader(), refs);

        CommitList.GroupByRefMap map = list.countGroupByRef(new CommitList.CommitMatcher() {
            @Override public boolean matches(Commit commit) {
                return commit.equals(setup.a)
                        || commit.equals(setup.f);
            }
        });
        assertEquals(2, map.getTotal(refs.byName("refs/heads/A")));
        assertEquals(2, map.getMatched(refs.byName("refs/heads/A")));
        assertEquals(3, map.getTotal(refs.byName("refs/heads/B")));
        assertEquals(1, map.getMatched(refs.byName("refs/heads/B")));
        assertEquals(2, map.getTotal(refs.byName("refs/heads/C")));
        assertEquals(1, map.getMatched(refs.byName("refs/heads/C")));
        assertEquals(3, map.getTotal(refs.byName("refs/heads/D")));
        assertEquals(1, map.getMatched(refs.byName("refs/heads/D")));
        assertEquals(2, map.getTotal(refs.byName("refs/heads/E")));
        assertEquals(1, map.getMatched(refs.byName("refs/heads/E")));
        assertEquals(1, map.getTotal(refs.byName("refs/heads/F")));
        assertEquals(1, map.getMatched(refs.byName("refs/heads/F")));
    }
}
