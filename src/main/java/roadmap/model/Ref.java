package roadmap.model;

import org.eclipse.jgit.lib.*;
import roadmap.*;

import java.util.*;

import static org.eclipse.jgit.lib.Constants.*;

/**
 * Represents branch or unannotated tag, maps branch or tag name to an object id.
 *
 * <p>Here's the basic bits of refs science. Reference binds arbitrary human
 * readable name to an object in the repository. Ref name is chosen by a user to
 * identify branches and tags, as well as other things in a repository. Refs
 * point to commits, which is most likely, but also can point to trees, blobs
 * and tags. However, head refs that are used for branch names always point to
 * commits only.</p>
 *
 * <p>Ref name may be local or remote branch name, tag name, or some non-standard
 * name.</p>
 *
 * <p>Ref name examples:</p>
 * <ul>
 * <li><em>refs/heads/master</em> -- identifies branch <em>master</em>
 * in local repository.</li>
 * <li><em>refs/heads/feature1</em> -- identifies branch <em>feature1</em>
 * in local repository.</li>
 * <li><em>refs/heads/feature2</em> -- identifies branch <em>feature2</em>
 * in local repository.</li>
 * <li><em>refs/remotes/origin/master</em> -- identifies branch <em>master</em>
 * in remote repository <em>origin</em>.</li>
 * <li><em>refs/remotes/sync/featureA</em> -- identifies branch <em>featureA</em>
 * in remote repository <em>sync</em></li>
 * <li><em>refs/remotes/sync/featureB</em> -- identifies branch <em>featureB</em>
 * in remote repository <em>sync</em></li>
 * <li><em>refs/tag/r-0.1</em> -- identifies tag <em>r-0.1</em>.</li>
 * <li><em>refs/tag/r-0.2</em> -- identifies tag <em>r-0.`1</em>.</li>
 * <li><em>refs/notes/commits</em> -- identifies something non-standard.</li>
 * </ul>
 *
 * <p>Ref may be symbolic, in this case it does not have its own object id, it merely
 * redirects to another ref, which in turn may be symbolic too. Therefore a symbolic
 * ref must be recursively resolved until a non-symbolic ref is found. An example of
 * symbolic ref is <em>HEAD</em> which points to the current branch in a repository.
 * For example <em>HEAD</em> can actually redirect to <em>refs/heads/master</em> which
 * in turn points to a commit.</p>
 *
 * <p>This class however does not support symbolic refs. We are only interested in
 * resolved, non-symbolic refs.</p>
 *
 * <p>Those refs that identify branches have names that always start with
 * <em>refs/heads/</em> for local branches and <em>refs/remotes/{remote}/</em> for remote
 * branches. These refs always point to commits. Beware that every remote branch name also
 * includes remote repository name that this branch was fetched from.</p>
 *
 * <p>Tags are a little bit more interesting. Ref names for tags always start with
 * <em>tags/</em>. Tag refs can refer to commits, but unlike branches, can also refer to
 * trees, blobs and annotated tags. Annotated tags are represented by a special object
 * type in repository. Annotated tags can refer to other annotated tags, and so on.
 * Peeling operation is the process of discovering object referred by a tag by recursively
 * walking tag chain until a non-tag object is found.</p>
 */
public class Ref implements Comparable<Ref>, HasName, HasIdKey {
    /**
     * Symmetric pair of different refs.
     * The order of refs in the pair does not matter, so that pairs {A,B}
     * and {B,A} considered equal.
     */
    public static final class Pair {
        private final Ref a, b;

        public Pair(Ref a, Ref b) {
            this.a = Objects.requireNonNull(a);
            this.b = Objects.requireNonNull(b);
        }

        public Ref getA() {
            return a;
        }

        public Ref getB() {
            return b;
        }

        public boolean matchAny(Ref ref) {
            return a.equals(ref) || b.equals(ref);
        }

        @Override public boolean equals(Object o) {
            if (this == o) { return true; }
            if (!(o instanceof Pair)) { return false; }
            Pair that = (Pair) o;
            return a.equals(that.a) && b.equals(that.b)
                    || a.equals(that.b) && b.equals(that.a);
        }

        @Override public int hashCode() {
            return a.hashCode() ^ b.hashCode();
        }

        @Override public String toString() {
            return a + " <-> " + b;
        }
    }

    private final String name;
    private final ObjectId id;
    private final boolean forced;

    public Ref(String name, ObjectId id) {
        this.name = Objects.requireNonNull(name);
        this.id = Objects.requireNonNull(id).copy();
        forced = false;
    }

    public Ref(Ref that, boolean forced) {
        name = that.getName();
        id = that.getId();
        this.forced = forced;
    }

    /** @return Ref name. */
    @Override public final String getName() {
        return name;
    }

    /**
     * @return Returns referenced object id, if branch, or peeled
     *         object id, if tag.
     */
    @Override public final ObjectId getId() {
        return id;
    }

    /**
     * @return {@code true} if ref was non-fast-forward pushed,
     *         {@code false} if ref was fast-forward pushed.
     */
    public final boolean isForced() {
        return forced;
    }

    /** @return Return copy of the current ref with forced flag set. */
    public Ref asForced() {
        return new Ref(this, true);
    }

    /** @return {@code true} if branch, {@code false} otherwise. */
    public boolean isBranch() {
        return isBranch(name);
    }

    /** @return {@code true} if tag, {@code false} otherwise. */
    public boolean isTag() {
        return isTag(name);
    }

    /** @return {@code true} if annotated tag, {@code false} otherwise. */
    public boolean isAnnotatedTag() {
        return false;
    }

    @Override public int compareTo(Ref o) {
        return getName().compareTo(o.getName());
    }

    @Override public boolean equals(Object o) {
        if (this == o) { return true; }
        if (!Objects.equals(getClass(), o.getClass())) { return false; }
        Ref that = (Ref) o;
        if (!Objects.equals(getName(), that.getName())) { return false; }
        if (!Objects.equals(getId(), that.getId())) { return false; }
        return true;
    }

    @Override public int hashCode() {
        return Objects.hash(getName(), getId());
    }

    @Override public String toString() {
        return getName() + ":" + getId().getName();
    }

    public static boolean isBranch(String name) {
        return name.startsWith(R_HEADS);
    }

    public static boolean isTag(String name) {
        return name.startsWith(R_TAGS);
    }
}
