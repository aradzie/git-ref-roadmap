package roadmap.graph;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public class CommitDiff {
    public interface Sink {
        void added(ObjectId id);

        void removed(ObjectId id);
    }

    private final RevWalk walk;

    public CommitDiff(RevWalk w) {
        walk = w;
    }

    /**
     * Find out the difference in commits between two root commits, that is, find
     * commits added and removed.
     *
     * <p>Let two sets, set {@code x} of all commits reachable from the root {@code a},
     * and set {@code y} of all commits reachable from the root {@code b}, including.</p>
     * <ul>
     * <li>If {@code a} is zero and {@code b} is not, then {@code y} is the set of added
     * commits.</li>
     * <li>If {@code b} is zero and {@code a} is not, then {@code x} is the set of removed
     * commits.</li>
     * <li>If both {@code a} and {@code b} point to the same commit, then nothing
     * is added or removed.</li>
     * <li>Otherwise algorithm works as follow: The added commits is the difference
     * {@code y - x}, and removed commits is the difference {@code x - y}.</li>
     * </ul>
     * <p>The algorithm thrives to build as small sets as possible, excluding any common
     * commits between two. It does so by iterating two graphs in parallel, terminating
     * as soon as overlap is found. So, if tho graphs are very large, but share majority
     * of commits, then only small subsets of unique commits will be kept.</p>
     *
     * @param a    First root commit.
     * @param b    Second root commit.
     * @param sink Result handler.
     */
    public void diff(ObjectId a, ObjectId b, Sink sink)
            throws IOException {
        HashSet<RevCommit> sa = new HashSet<>();
        HashSet<RevCommit> sb = new HashSet<>();
        ArrayDeque<RevCommit> qa = new ArrayDeque<>();
        ArrayDeque<RevCommit> qb = new ArrayDeque<>();
        RevCommit ca;
        RevCommit cb;
        // Seed iteration on side A.
        if (a != null && !ObjectId.zeroId().equals(a)) {
            ca = walk.parseCommit(a);
            sa.add(ca);
            qa.add(ca);
        }
        // Seed iteration on side B.
        if (b != null && !ObjectId.zeroId().equals(b)) {
            cb = walk.parseCommit(b);
            sb.add(cb);
            qb.add(cb);
        }
        while (true) {
            // Iterate two sides in parallel.
            ca = qa.poll();
            if (ca != null) {
                walk.parseBody(ca);
                int count = ca.getParentCount();
                for (int n = 0; n < count; n++) {
                    RevCommit parent = ca.getParent(n);
                    if (sa.add(parent)) {
                        qa.add(parent);
                    }
                }
            }
            cb = qb.poll();
            if (cb != null) {
                walk.parseBody(cb);
                int count = cb.getParentCount();
                for (int n = 0; n < count; n++) {
                    RevCommit parent = cb.getParent(n);
                    if (sb.add(parent)) {
                        qb.add(parent);
                    }
                }
            }
            // Check if both sides are done iterating.
            if (ca == null && cb == null) {
                break;
            }
            if (sb.containsAll(qa) && sa.containsAll(qb)) {
                // Side A is going to iterate commits that have already been
                // seen on side B, and vice versa, side B is going to iterate
                // commits that have already been seed on side A.
                for (RevCommit commit : qa) {
                    prune(commit, sb);
                    sa.remove(commit);
                }
                for (RevCommit commit : qb) {
                    prune(commit, sa);
                    sb.remove(commit);
                }
                break;
            }
        }
        // Generate result for removed commits.
        for (RevCommit commit : sa) {
            if (!sb.contains(commit)) {
                sink.removed(commit);
            }
        }
        // Generate result for added commits.
        for (RevCommit commit : sb) {
            if (!sa.contains(commit)) {
                sink.added(commit);
            }
        }
    }

    /**
     * Remove all (grand-)parents of the specified commit in the specified seen
     * set and queue on the other side of iteration.
     *
     * @param commit The commit whose (grand-)parents to prune from the other side.
     * @param seen   The other side seen set to prune.
     */
    private void prune(RevCommit commit, Set<RevCommit> seen)
            throws IOException {
        ArrayDeque<RevCommit> queue = new ArrayDeque<>();
        queue.add(commit);
        while ((commit = queue.poll()) != null) {
            if (seen.remove(commit)) {
                walk.parseBody(commit);
                int count = commit.getParentCount();
                for (int n = 0; n < count; n++) {
                    queue.add(commit.getParent(n));
                }
            }
        }
    }
}
