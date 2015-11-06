package roadmap.graph;

import org.eclipse.jgit.junit.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.*;
import org.junit.*;
import roadmap.model.*;
import roadmap.test.*;

import java.io.*;
import java.util.*;

import static org.eclipse.jgit.lib.Constants.*;
import static org.junit.Assert.*;

public class RefGraphTest {
    @Rule public final RepositorySetupRule setupRule = new RepositorySetupRule();

    @Test public void emptyGraph()
            throws Exception {
        class Setup extends RepositoryRule.Setup.Empty {
            //
        }
        Setup s = new Setup();
        Repository db = setupRule.setupBare(s);

        RefSet refs = RefSet.builder(db).build();
        RefGraph graph = graph(db.newObjectReader(), refs);
        dump(graph);
        assertTrue(graph.getRoots().isEmpty());
        assertTrue(graph.getRefDiffs().isEmpty());

        graph = graph.copyWithoutTags();
        assertTrue(graph.getRoots().isEmpty());
        assertTrue(graph.getRefDiffs().isEmpty());
    }

    @Test public void mergeBases1()
            throws Exception {
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
        class Setup implements RepositoryRule.Setup {
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
                util.update(R_HEADS + "F", f);
                util.update(R_HEADS + "E", e);
                util.update(R_HEADS + "D", d);
                util.update(R_HEADS + "C", c);
                util.update(R_HEADS + "B", b);
            }
        }
        Setup s = new Setup();
        Repository db = setupRule.setupBare(s);

        RefSet refs = RefSet.builder(db).build();
        RefGraph graph = graph(db.newObjectReader(), refs);
        dump(graph);
        assertSetsEquals(set(s.a, s.b, s.d), graph.getRoots());
        assertSetsEquals(set(s.a), mergeBases(graph, s.a, s.a));
        assertSetsEquals(set(s.b), mergeBases(graph, s.b, s.b));
        assertSetsEquals(set(s.c), mergeBases(graph, s.c, s.c));
        assertSetsEquals(set(s.f), mergeBases(graph, s.a, s.b, s.d));
        assertSetsEquals(set(s.f), mergeBases(graph, s.a, s.c, s.e));
        assertSetsEquals(set(s.f), mergeBases(graph, s.a, s.b));
        assertSetsEquals(set(s.f), mergeBases(graph, s.a, s.c));
        assertSetsEquals(set(s.f), mergeBases(graph, s.a, s.d));
        assertSetsEquals(set(s.f), mergeBases(graph, s.a, s.e));
        assertSetsEquals(set(s.c), mergeBases(graph, s.b, s.c));
        assertSetsEquals(set(s.e), mergeBases(graph, s.d, s.e));
        assertSetsEquals(set(s.f), mergeBases(graph, s.c, s.f));
        assertSetsEquals(set(s.f), mergeBases(graph, s.e, s.f));
    }

    @Test public void mergeBases2()
            throws Exception {
        /**
         * Creates the following commit graph:
         *
         * <pre>
         *  B,b      D,d  F,f
         * o--------o----o
         *           \  / \
         *            \/   \
         *            /\    \
         * HEAD,a    /  \    \
         * o--------o----o----o
         *           C,c  E,e  G,g
         * </pre>
         */
        class Setup implements RepositoryRule.Setup {
            RevCommit a, b, c, d, e, f, g;

            @Override public void play(Repository repository)
                    throws Exception {
                TestRepository<Repository> util = new TestRepository<>(repository);
                g = util.commit().message("G").create();
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
        Setup s = new Setup();
        Repository db = setupRule.setupBare(s);

        RefSet refs = RefSet.builder(db).build();
        RefGraph graph = graph(db.newObjectReader(), refs);
        dump(graph);
        assertSetsEquals(set(s.a, s.b), graph.getRoots());
        assertEquals(2, graph.node(s.c).getParents().size());
        assertEquals(2, graph.node(s.d).getParents().size());
        assertSetsEquals(set(s.a), mergeBases(graph, s.a, s.a));
        assertSetsEquals(set(s.b), mergeBases(graph, s.b, s.b));
        assertSetsEquals(set(s.c), mergeBases(graph, s.c, s.c));
        assertSetsEquals(set(s.e, s.f), mergeBases(graph, s.a, s.b));
        assertSetsEquals(set(s.e, s.f), mergeBases(graph, s.c, s.d));
        assertSetsEquals(set(s.g), mergeBases(graph, s.e, s.f));
    }

    @Test public void rewrite1()
            throws Exception {
        /**
         * Creates the following commit graph:
         *
         * <pre>
         * T,a    c
         * o-----o
         *        \
         * HEAD,b  \ d     e (initial)
         * o--------o-----o
         * </pre>
         */
        class Setup implements RepositoryRule.Setup {
            RevCommit a, b, c, d, e;

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
            }
        }
        Setup s = new Setup();
        Repository db = setupRule.setupBare(s);

        RefSet refs = RefSet.builder(db).build();
        RefGraph graph = graph(db.newObjectReader(), refs);
        dump(graph);
        assertSetsEquals(set(s.a, s.b), graph.getRoots());
        assertEquals(1, graph.node(s.a).getParents().size());
        assertEquals(1, graph.node(s.b).getParents().size());
        assertEquals(0, graph.node(s.d).getParents().size());
        assertSetsEquals(set(s.d), mergeBases(graph, s.a, s.b));
        assertSetsEquals(set(s.d), mergeBases(graph, s.a, s.d));
        assertSetsEquals(set(s.d), mergeBases(graph, s.b, s.d));
        assertEquals(1, graph.getRefDiffs().size());
        assertTrue(graph.getRefDiffs().contains(new RefDiff(s.d, s.a, 2, s.b, 1)));

        graph = graph.copyWithoutTags();
        assertSetsEquals(set(s.b), graph.getRoots());
        assertEquals(0, graph.node(s.b).getParents().size());
        assertEquals(1, graph.getRefDiffs().size());
        assertTrue(graph.getRefDiffs().contains(new RefDiff(s.d, s.a, 2, s.b, 1)));
    }

    @Test public void rewrite2()
            throws Exception {
        /**
         * Creates the following commit graph:
         *
         * <pre>
         * T,a
         * o-----+
         *        \
         * HEAD,b  \ c,X   d (initial)
         * o--------o-----o
         * </pre>
         */
        class Setup implements RepositoryRule.Setup {
            RevCommit a, b, c, d;

            @Override public void play(Repository repository)
                    throws Exception {
                TestRepository<Repository> util = new TestRepository<>(repository);
                d = util.commit().message("D").create();
                c = util.commit().message("C").parent(d).create();
                b = util.commit().message("B").parent(c).create();
                a = util.commit().message("A").parent(c).create();

                util.update(HEAD, b);
                util.update(R_HEADS + "X", c);
                util.update(R_TAGS + "T", a);
            }
        }
        Setup s = new Setup();
        Repository db = setupRule.setupBare(s);

        RefSet refs = RefSet.builder(db).build();
        RefGraph graph = graph(db.newObjectReader(), refs);
        dump(graph);
        assertSetsEquals(set(s.a, s.b), graph.getRoots());
        assertEquals(1, graph.node(s.a).getParents().size());
        assertEquals(1, graph.node(s.b).getParents().size());
        assertEquals(0, graph.node(s.c).getParents().size());
        assertSetsEquals(set(s.c), mergeBases(graph, s.a, s.b));
        assertSetsEquals(set(s.c), mergeBases(graph, s.a, s.c));
        assertSetsEquals(set(s.c), mergeBases(graph, s.b, s.c));
        assertEquals(3, graph.getRefDiffs().size());
        assertTrue(graph.getRefDiffs().contains(new RefDiff(s.c, s.a, 1, s.b, 1)));
        assertTrue(graph.getRefDiffs().contains(new RefDiff(s.c, s.a, 1, s.c, 0)));
        assertTrue(graph.getRefDiffs().contains(new RefDiff(s.c, s.b, 1, s.c, 0)));

        graph = graph.copyWithoutTags();
        assertSetsEquals(set(s.b), graph.getRoots());
        assertEquals(1, graph.node(s.b).getParents().size());
        assertEquals(0, graph.node(s.c).getParents().size());
    }

    @Test public void rewrite3()
            throws Exception {
        /**
         * Creates the following commit graph:
         *
         * <pre>
         * T,a    c,X
         * o-----o
         *        \
         * HEAD,b  \ d     e (initial)
         * o--------o-----o
         * </pre>
         */
        class Setup implements RepositoryRule.Setup {
            RevCommit a, b, c, d, e;

            @Override public void play(Repository repository)
                    throws Exception {
                TestRepository<Repository> util = new TestRepository<>(repository);
                e = util.commit().message("E").create();
                d = util.commit().message("D").parent(e).create();
                c = util.commit().message("C").parent(d).create();
                b = util.commit().message("B").parent(d).create();
                a = util.commit().message("A").parent(c).create();

                util.update(HEAD, b);
                util.update(R_HEADS + "X", c);
                util.update(R_TAGS + "T", a);
            }
        }
        Setup s = new Setup();
        Repository db = setupRule.setupBare(s);

        RefSet refs = RefSet.builder(db).build();
        RefGraph graph = graph(db.newObjectReader(), refs);
        dump(graph);
        assertSetsEquals(set(s.a, s.b), graph.getRoots());
        assertEquals(1, graph.node(s.a).getParents().size());
        assertEquals(1, graph.node(s.b).getParents().size());
        assertEquals(1, graph.node(s.c).getParents().size());
        assertEquals(0, graph.node(s.d).getParents().size());
        assertSetsEquals(set(s.d), mergeBases(graph, s.a, s.b));
        assertSetsEquals(set(s.d), mergeBases(graph, s.b, s.c));
        assertSetsEquals(set(s.c), mergeBases(graph, s.a, s.c));
        assertSetsEquals(set(s.d), mergeBases(graph, s.c, s.d));
        assertSetsEquals(set(s.d), mergeBases(graph, s.b, s.d));
        assertEquals(3, graph.getRefDiffs().size());
        assertTrue(graph.getRefDiffs().contains(new RefDiff(s.d, s.a, 2, s.b, 1)));
        assertTrue(graph.getRefDiffs().contains(new RefDiff(s.d, s.c, 1, s.b, 1)));
        assertTrue(graph.getRefDiffs().contains(new RefDiff(s.c, s.c, 0, s.a, 1)));

        graph = graph.copyWithoutTags();
        assertSetsEquals(set(s.b, s.c), graph.getRoots());
        assertEquals(1, graph.node(s.b).getParents().size());
        assertEquals(1, graph.node(s.c).getParents().size());
        assertEquals(0, graph.node(s.d).getParents().size());
    }

    @Test public void refDiff1()
            throws Exception {
        /**
         * Creates the following commit graph:
         *
         * <pre>
         * HEAD,a (initial)
         * o
         * </pre>
         */
        class Setup implements RepositoryRule.Setup {
            RevCommit a;

            @Override public void play(Repository repository)
                    throws Exception {
                TestRepository<Repository> util = new TestRepository<>(repository);
                a = util.commit().message("A").create();

                util.update(HEAD, a);
            }
        }
        Setup s = new Setup();
        Repository db = setupRule.setupBare(s);

        RefSet refs = RefSet.builder(db).build();
        RefGraph graph = graph(db.newObjectReader(), refs);
        dump(graph);
        assertSetsEquals(set(s.a), graph.getRoots());
        assertEquals(0, graph.getRefDiffs().size());
    }

    @Test public void refDiff2()
            throws Exception {
        /**
         * Creates the following commit graph:
         *
         * <pre>
         * HEAD,a    X,b     Y,c (initial)
         * o--------o-------o
         * </pre>
         */
        class Setup implements RepositoryRule.Setup {
            RevCommit a, b, c;

            @Override public void play(Repository repository)
                    throws Exception {
                TestRepository<Repository> util = new TestRepository<>(repository);
                c = util.commit().message("C").create();
                b = util.commit().message("B").parent(c).create();
                a = util.commit().message("A").parent(b).create();

                util.update(HEAD, a);
                util.update(R_HEADS + "X", b);
                util.update(R_HEADS + "Y", c);
            }
        }
        Setup s = new Setup();
        Repository db = setupRule.setupBare(s);

        RefSet refs = RefSet.builder(db).build();
        RefGraph graph = graph(db.newObjectReader(), refs);
        dump(graph);
        assertSetsEquals(set(s.a), graph.getRoots());
        assertEquals(1, graph.node(s.a).getParents().size());
        assertEquals(1, graph.node(s.b).getParents().size());
        assertEquals(0, graph.node(s.c).getParents().size());
        assertSetsEquals(set(s.b), mergeBases(graph, s.a, s.b));
        assertSetsEquals(set(s.c), mergeBases(graph, s.a, s.c));
        assertSetsEquals(set(s.c), mergeBases(graph, s.b, s.c));
        assertEquals(3, graph.getRefDiffs().size());
        assertTrue(graph.getRefDiffs().contains(new RefDiff(s.b, s.a, 1, s.b, 0)));
        assertTrue(graph.getRefDiffs().contains(new RefDiff(s.c, s.a, 2, s.c, 0)));
        assertTrue(graph.getRefDiffs().contains(new RefDiff(s.c, s.b, 1, s.c, 0)));
    }

    @Test public void refDiff3()
            throws Exception {
        /**
         * Creates the following commit graph:
         *
         * <pre>
         *  X,b
         * o------+
         *         \
         * HEAD,a   \ Z,d (initial)
         * o---------o
         *          /
         *  Y,c    /
         * o------+
         * </pre>
         */
        class Setup implements RepositoryRule.Setup {
            RevCommit a, b, c, d;

            @Override public void play(Repository repository)
                    throws Exception {
                TestRepository<Repository> util = new TestRepository<>(repository);
                d = util.commit().message("D").create();
                c = util.commit().message("C").parent(d).create();
                b = util.commit().message("B").parent(d).create();
                a = util.commit().message("A").parent(d).create();

                util.update(HEAD, a);
                util.update(R_HEADS + "X", b);
                util.update(R_HEADS + "Y", c);
                util.update(R_HEADS + "Z", d);
            }
        }
        Setup s = new Setup();
        Repository db = setupRule.setupBare(s);

        RefSet refs = RefSet.builder(db).build();
        RefGraph graph = graph(db.newObjectReader(), refs);
        dump(graph);
        assertSetsEquals(set(s.a, s.b, s.c), graph.getRoots());
        assertSetsEquals(set(s.d), mergeBases(graph, s.a, s.b, s.c));
        assertSetsEquals(set(s.d), mergeBases(graph, s.a, s.b));
        assertSetsEquals(set(s.d), mergeBases(graph, s.b, s.c));
        assertSetsEquals(set(s.d), mergeBases(graph, s.a, s.c));
        assertEquals(6, graph.getRefDiffs().size());
        // One sided
        assertTrue(graph.getRefDiffs().contains(new RefDiff(s.d, s.d, 0, s.a, 1)));
        assertTrue(graph.getRefDiffs().contains(new RefDiff(s.d, s.d, 0, s.b, 1)));
        assertTrue(graph.getRefDiffs().contains(new RefDiff(s.d, s.d, 0, s.c, 1)));
        // Two sided
        assertTrue(graph.getRefDiffs().contains(new RefDiff(s.d, s.a, 1, s.b, 1)));
        assertTrue(graph.getRefDiffs().contains(new RefDiff(s.d, s.a, 1, s.c, 1)));
        assertTrue(graph.getRefDiffs().contains(new RefDiff(s.d, s.b, 1, s.c, 1)));
    }

    static RefGraph graph(ObjectReader reader, RefSet refs)
            throws IOException {
        return new CommitList(reader, refs).getRefGraph().copy();
    }

    static Set<AnyObjectId> set(AnyObjectId... id) {
        HashSet<AnyObjectId> s = new HashSet<>(id.length);
        for (AnyObjectId objectId : id) {
            s.add(objectId.copy());
        }
        return s;
    }

    static Set<RefGraph.Node> mergeBases(RefGraph g, AnyObjectId a, AnyObjectId... b) {
        HashSet<RefGraph.Node> heads = new HashSet<>();
        heads.add(g.node(a));
        for (AnyObjectId id : b) {
            heads.add(g.node(id));
        }
        HashSet<RefGraph.Node> mb = new HashSet<>();
        g.findMergeBases(heads, mb);
        return mb;
    }

    static void assertSetsEquals(Set<? extends AnyObjectId> a, Set<? extends AnyObjectId> b) {
        if (!(a instanceof HashSet)) {
            a = new HashSet<>(a);
        }
        if (!(b instanceof HashSet)) {
            b = new HashSet<>(b);
        }
        assertEquals(a, b);
    }

    static void dump(RefGraph graph)
            throws IOException {
        PrintWriter w = new PrintWriter(System.out, true);
        graph.dump(w);
        w.flush();
    }
}
