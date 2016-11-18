package roadmap.graph;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import roadmap.ref.Ref;
import roadmap.ref.RefDiff;

import java.util.Arrays;
import java.util.Set;

/** Set of reachable heads implemented as a simple and fast bit set. */
final class HeadSet {
    /**
     * Commit pointed to by refs.
     *
     * <p>We are not interested in what refs exactly, we merely need
     * to know where are merge bases for this commit.</p>
     */
    static class Head
            extends ObjectId {
        /**
         * The number of commits in all parents of a ref. Which
         * means, the number of commits having this ref.
         */
        int commits;

        Head(AnyObjectId src) {
            super(src);
        }
    }

    /** Assists in building sets of heads. */
    static final class Builder {
        /** All commits marked by refs. */
        final Head[] id;

        /**
         * Make new builder.
         *
         * @param id All commits that have refs pointing at them.
         */
        Builder(Set<? extends AnyObjectId> id) {
            this.id = new Head[id.size()];
            int index = 0;
            for (AnyObjectId x : id) {
                this.id[index++] = new Head(x);
            }
            Arrays.sort(this.id);
        }

        /**
         * Copy the other builder.
         *
         * @param that The builder to copy.
         */
        Builder(Builder that) {
            id = new Head[that.id.length];
            for (int i = 0; i < that.id.length; i++) {
                id[i] = new Head(that.id[i]);
            }
        }

        int size() {
            return id.length;
        }

        int indexOf(AnyObjectId id) {
            return Arrays.binarySearch(this.id, id);
        }

        /**
         * Get array of heads as specified by the set.
         *
         * @param s Head set instance.
         * @return Array of heads.
         */
        Head[] select(HeadSet s) {
            Head[] id = new Head[s.size()];
            int n = 0;
            for (int i = 0; i < s.words.length; i++) {
                int w = s.words[i];
                int j = 0;
                while (w != 0) {
                    if ((w & 1) == 1) {
                        id[n++] = this.id[(i << 5) + j];
                    }
                    w >>>= 1;
                    j++;
                }
            }
            return id;
        }

        /**
         * Every referenced commit is merge base between itself
         * and all reachable child heads.
         *
         * <p>Here is an example:</p>
         *
         * <pre>
         * HEAD,a   X,b     Y,c (initial)
         * o-------o-------o
         * </pre>
         *
         * <p>Ref <em>X</em> is the merge base for the following pairs of refs:
         * <em>(HEAD,X)</em>. Ref <em>Y</em> is the merge base for the following
         * pairs of refs: <em>(HEAD,Y)</em>, <em>(X,Y)</em>.</p>
         *
         * @param mb Merge base id.
         * @param a  Set of reachable heads.
         */
        void asMergeBase(RefDiff.Sink diffs, AnyObjectId mb, HeadSet a) {
            Head[] id = select(a.words);
            for (Head x : id) {
                diffs.add(mb, mb, 0, x, x.commits);
            }
        }

        /**
         * Record head diffs between two head set relative
         * to the specified merge base.
         *
         * <p>Here is an example:</p>
         *
         * <pre>
         * X,a
         * o-----+
         *        \
         * HEAD,b  \ c     d (initial)
         * o--------o-----o
         * </pre>
         *
         * <p>Commit <em>c</em> is the merge base for the following pair of refs:
         * <em>(HEAD,X)</em>.</p>
         *
         * @param mb Merge base id.
         * @param a  Set of heads reachable from one side.
         * @param b  Set of heads reachable from another side.
         */
        void asMergeBase(RefDiff.Sink diffs, AnyObjectId mb, HeadSet a, HeadSet b) {
            int l = a.words.length;
            int[] wa = new int[l];
            int[] wb = new int[l];
            for (int i = 0; i < l; i++) {
                int x = a.words[i];
                int y = b.words[i];
                int m = x ^ y;
                wa[i] = x & m;
                wb[i] = y & m;
            }
            Head[] ida = select(wa);
            Head[] idb = select(wb);
            for (Head x : ida) {
                for (Head y : idb) {
                    diffs.add(mb, x, x.commits, y, y.commits);
                }
            }
        }

        /**
         * Get array of heads as specified by the bit set.
         *
         * @param words Bit set words.
         * @return Array of heads.
         */
        Head[] select(int[] words) {
            // Count heads.
            int l = 0;
            for (int word : words) {
                l += Integer.bitCount(word);
            }
            // Fill in array of heads being merged.
            Head[] id = new Head[l];
            l = 0;
            for (int i = 0; i < words.length; i++) {
                int w = words[i];
                int j = 0;
                while (w != 0) {
                    if ((w & 1) == 1) {
                        id[l++] = this.id[(i << 5) + j];
                    }
                    w >>>= 1;
                    j++;
                }
            }
            return id;
        }
    }

    private static final int[] EMPTY = {};
    /** Bit set implemented as array of words. */
    final int[] words;

    HeadSet(Builder builder) {
        if (builder.size() > 0) {
            words = new int[((builder.size() - 1) >>> 5) + 1];
        }
        else {
            words = EMPTY;
        }
    }

    HeadSet(HeadSet that) {
        words = new int[that.words.length];
        for (int i = 0; i < that.words.length; i++) {
            words[i] = that.words[i];
        }
    }

    int size() {
        int size = 0;
        for (int word : words) {
            size += Integer.bitCount(word);
        }
        return size;
    }

    HeadSet addRefs(Builder hsb, Set<Ref> refs) {
        HeadSet tmp = new HeadSet(this);
        for (Ref ref : refs) {
            tmp.add(hsb, ref.getId());
        }
        return tmp;
    }

    void add(Builder builder, AnyObjectId head) {
        int bit = builder.indexOf(head);
        int wordIndex = bit >>> 5;
        int mask = 1 << bit;
        words[wordIndex] |= mask;
    }

    void addAll(HeadSet that) {
        for (int i = 0; i < words.length; i++) {
            words[i] |= that.words[i];
        }
    }

    boolean contains(Builder builder, AnyObjectId head) {
        int bit = builder.indexOf(head);
        int wordIndex = bit >>> 5;
        int mask = 1 << bit;
        return (words[wordIndex] & mask) != 0;
    }

    boolean containsAll(HeadSet that) {
        for (int i = 0; i < words.length; i++) {
            if ((words[i] & that.words[i]) != that.words[i]) {
                return false;
            }
        }
        return true;
    }

    boolean containsAny(HeadSet that) {
        for (int i = 0; i < words.length; i++) {
            if ((words[i] & that.words[i]) != 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * For each head increment the number of commit in it.
     *
     * @param builder Builder instance that created this set.
     */
    void count(Builder builder) {
        for (int i = 0; i < words.length; i++) {
            int w = words[i];
            int j = 0;
            while (w != 0) {
                if ((w & 1) == 1) {
                    builder.id[(i << 5) + j].commits++;
                }
                w >>>= 1;
                j++;
            }
        }
    }

    private Set<Graph.Node> nodes;

    Set<Graph.Node> getNodes() {
        return nodes;
    }

    void setNodes(Set<Graph.Node> nodes) {
        this.nodes = nodes;
    }

    /**
     * Test if we are at merge base commit.
     *
     * <p>It is a merge base commit when the set <em>a - b</em> is not empty
     * and the set <em>b - a</em> is not empty too.</p>
     *
     * @param a First set.
     * @param b Second set.
     * @return Merge base indicator.
     */
    static boolean isMergeBase(HeadSet a, HeadSet b) {
        return !a.containsAll(b) && !b.containsAll(a);
    }
}
