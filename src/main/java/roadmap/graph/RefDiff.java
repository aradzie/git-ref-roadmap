package roadmap.graph;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;

/** Difference in number of commits between two heads. */
public final class RefDiff implements Comparable<RefDiff> {
    /** Receives calculated ref diffs. */
    public interface Sink {
        void add(AnyObjectId mergeBase,
                 AnyObjectId a, int commitsA,
                 AnyObjectId b, int commitsB);
    }

    private final ObjectId mergeBase;
    private final ObjectId a;
    private final int commitsA;
    private final ObjectId b;
    private final int commitsB;

    public RefDiff(AnyObjectId mergeBase,
                   AnyObjectId a, int commitsA,
                   AnyObjectId b, int commitsB) {
        this.mergeBase = mergeBase.copy();
        this.a = a.copy();
        this.commitsA = commitsA;
        this.b = b.copy();
        this.commitsB = commitsB;
    }

    /** @return Merge base id, which may or may not be equal to either A or B. */
    public ObjectId getMergeBase() {
        return mergeBase;
    }

    /** @return First head id. */
    public ObjectId getA() {
        return a;
    }

    /** @return Number of new commits in branch A since merge base. */
    public int getCommitsA() {
        return commitsA;
    }

    /** @return Second head id. */
    public ObjectId getB() {
        return b;
    }

    /** @return Number of new commits in branch B since merge base. */
    public int getCommitsB() {
        return commitsB;
    }

    /**
     * @return Value indicating whether the merge base commit
     *         is equal to one of other commits.
     */
    public boolean isOneWay() {
        return ObjectId.equals(mergeBase, a)
                || ObjectId.equals(mergeBase, b);
    }

    /** @return New head diff with commits A and B exchanged. */
    public RefDiff flip() {
        return new RefDiff(mergeBase, b, commitsB, a, commitsA);
    }

    @Override public int compareTo(RefDiff that) {
        int r = a.compareTo(that.a);
        if (r == 0) {
            r = b.compareTo(that.b);
            if (r == 0) {
                r = mergeBase.compareTo(that.mergeBase);
            }
        }
        return r;
    }

    @Override public boolean equals(Object object) {
        if (this == object) { return true; }
        if (!(object instanceof RefDiff)) { return false; }
        RefDiff that = (RefDiff) object;
        if (!ObjectId.equals(mergeBase, that.mergeBase)) { return false; }
        return ObjectId.equals(a, that.a) && ObjectId.equals(b, that.b)
                || ObjectId.equals(a, that.b) && ObjectId.equals(b, that.a);
    }

    @Override public int hashCode() {
        return 31 * mergeBase.hashCode() + (a.hashCode() ^ b.hashCode());
    }

    @Override public String toString() {
        return "RefDiff(" +
                "mb=" + mergeBase.getName() +
                "; a=" + a.getName() + "/" + commitsA +
                "; b=" + b.getName() + "/" + commitsB + ")";
    }
}
