package roadmap.graph;

import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.*;
import roadmap.ref.Ref;

import java.io.*;
import java.util.*;

/**
 * Compact commit representation that only stores bare minimum details,
 * such as commit id, its parent and children.
 */
public class Commit extends ObjectId {
    private final ObjectId treeId;
    private int index;
    private Object child;
    private Object parent;
    private Set<Ref> refs;
    private HeadSet heads;

    protected Commit(RevCommit commit) {
        this(commit, commit.getTree());
    }

    protected Commit(AnyObjectId id, AnyObjectId treeId) {
        super(id);
        this.treeId = treeId.copy();
        refs = Collections.emptySet();
    }

    protected Commit(Commit commit) {
        super(commit);
        treeId = commit.treeId;
        index = commit.index;
        child = commit.child;
        parent = commit.parent;
        refs = commit.refs;
    }

    /** @return Tree id this commit points to. */
    public final ObjectId getTreeId() {
        return treeId;
    }

    void setIndex(int index) {
        this.index = index;
    }

    /** @return Commit index within sorted list. */
    public final int getIndex() {
        return index;
    }

    void setRefs(Set<Ref> refs) {
        this.refs = refs;
    }

    /** @return Unmodifiable set of refs that point to this commit. */
    public Set<Ref> getRefs() {
        return refs;
    }

    public final boolean isRoot() {
        return child == null;
    }

    final void addChild(Commit child) {
        if (this.child == null) {
            this.child = child;
        }
        else {
            if (this.child instanceof Commit) {
                this.child = new Commit[]{(Commit) this.child, child};
            }
            else {
                Commit[] a = (Commit[]) this.child;
                Commit[] b = new Commit[a.length + 1];
                for (int i = 0; i < a.length; i++) {
                    b[i] = a[i];
                }
                b[a.length] = child;
                this.child = b;
            }
        }
    }

    /** @return Number of children of this commit. */
    public final int getChildCount() {
        if (child == null) {
            return 0;
        }
        if (child instanceof Commit) {
            return 1;
        }
        return ((Commit[]) child).length;
    }

    /**
     * @param index Child index.
     * @return A child commit by the specified index.
     */
    public final Commit getChild(int index) {
        if (child instanceof Commit) {
            if (index > 0) {
                throw new ArrayIndexOutOfBoundsException(index);
            }
            return (Commit) child;
        }
        return ((Commit[]) child)[index];
    }

    final void addParent(Commit parent) {
        if (this.parent == null) {
            this.parent = parent;
        }
        else {
            if (this.parent instanceof Commit) {
                this.parent = new Commit[]{(Commit) this.parent, parent};
            }
            else {
                Commit[] a = (Commit[]) this.parent;
                Commit[] b = new Commit[a.length + 1];
                for (int i = 0; i < a.length; i++) {
                    b[i] = a[i];
                }
                b[a.length] = parent;
                this.parent = b;
            }
        }
    }

    /** @return Number of parents. */
    public final int getParentCount() {
        if (parent == null) {
            return 0;
        }
        if (parent instanceof Commit) {
            return 1;
        }
        return ((Commit[]) parent).length;
    }

    /**
     * @param index Parent index.
     * @return A parent commit by the specified index.
     */
    public final Commit getParent(int index) {
        if (parent instanceof Commit) {
            if (index > 0) {
                throw new ArrayIndexOutOfBoundsException(index);
            }
            return (Commit) parent;
        }
        return ((Commit[]) parent)[index];
    }

    HeadSet getHeads() {
        return heads;
    }

    void setHeads(HeadSet heads) {
        this.heads = heads;
    }

    @Override public String toString() {
        return getName();
    }

    public void append(Appendable a)
            throws IOException {
        a.append(getName());
        if (parent instanceof Commit) {
            a.append(" ").append(((Commit) parent).getName());
        }
        else if (parent instanceof Commit[]) {
            Commit[] parents = (Commit[]) parent;
            for (Commit p : parents) {
                a.append(" ").append(p.getName());
            }
        }
    }
}
