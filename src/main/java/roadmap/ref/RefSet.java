package roadmap.ref;

import org.eclipse.jgit.errors.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.*;

import java.io.*;
import java.util.*;

import static java.util.Collections.*;
import static org.eclipse.jgit.lib.Constants.*;

/**
 * A collection of refs from a repository.
 *
 * <p>We are only interested in refs that point to commits.
 * Refs pointing to trees and blobs will be excluded from the set.</p>
 */
public final class RefSet implements Iterable<Ref> {
    public static RefSet from(Repository db)
            throws IOException {
        return build(new RevWalk(db), db.getAllRefs());
    }

    public static final Ref EMPTY_MASTER = new Ref(R_HEADS + MASTER, ObjectId.zeroId());
    /** Empty refs collection. */
    public static final RefSet EMPTY = new RefSet();
    /** All refs by name. */
    private final Map<String, Ref> byName;
    /** All refs grouped by object id. */
    private final Map<AnyObjectId, Set<Ref>> byId;
    /** The default branch. */
    private final Ref defaultBranch;

    /** Create empty ref set instance. */
    private RefSet() {
        byName = emptyMap();
        byId = emptyMap();
        defaultBranch = EMPTY_MASTER;
    }

    private RefSet(Collection<Ref> refs, Ref defaultBranch) {
        HashMap<String, Ref> byName = new HashMap<>();
        HashMap<AnyObjectId, Set<Ref>> byId = new HashMap<>();

        for (Ref ref : refs) {
            byName.put(ref.getName(), ref);

            Set<Ref> set = byId.get(ref.getId());
            if (set == null) {
                set = singleton(ref);
            }
            else {
                if (set.size() == 1) {
                    set = new HashSet<>(set);
                }
                set.add(ref);
            }
            byId.put(ref.getId(), set);
        }

        for (Map.Entry<AnyObjectId, Set<Ref>> entry : byId.entrySet()) {
            byId.put(entry.getKey(), unmodifiableSet(entry.getValue()));
        }

        this.byName = unmodifiableMap(byName);
        this.byId = unmodifiableMap(byId);
        this.defaultBranch = defaultBranch;
    }

    /**
     * The default branch ref which was discovered from the symbolic ref
     * <em>HEAD</em>. If the ref set is empty (when repository is empty),
     * or repository specifies unknown default branch name, then the returned
     * ref object does not belong to this set and has zero object id.
     *
     * @return The default branch ref, which may not belong to this set.
     */
    public Ref defaultBranch() {
        return defaultBranch;
    }

    /** @return All refs with branches and tags. */
    public Collection<Ref> all() {
        return byName.values();
    }

    /**
     * Get any ref by name.
     *
     * @param name Ref name.
     * @return Ref with that name.
     * @throws RefNotFoundException If ref with that name does not exist.
     */
    public Ref byName(String name) {
        Ref ref = byName.get(Objects.requireNonNull(name));
        if (ref == null) {
            throw new RefNotFoundException(name);
        }
        return ref;
    }

    /**
     * Get set of refs that point to the specified id.
     *
     * @param id Commit id.
     * @return Unmodifiable set of refs for the specified id, or empty set if not found.
     */
    public Set<Ref> byId(AnyObjectId id) {
        Set<Ref> set = byId.get(Objects.requireNonNull(id));
        if (set == null) {
            set = emptySet();
        }
        return set;
    }

    /** @return Set of all root commit ids. */
    public Set<ObjectId> roots() {
        HashSet<ObjectId> tips = new HashSet<>(byName.size());
        for (Ref ref : byName.values()) {
            tips.add(ref.getId());
        }
        return unmodifiableSet(tips);
    }

    /**
     * Select refs with the specified names.
     *
     * @param names Wanted ref names. May include unknown names.
     * @return Refs with the specified names.
     */
    public Collection<Ref> selectAll(Collection<String> names) {
        return select(names, byName);
    }

    private static Collection<Ref> select(Collection<String> names, Map<String, Ref> refs) {
        if (refs.isEmpty()) {
            return emptySet();
        }
        ArrayList<Ref> res = new ArrayList<>();
        for (String name : names) {
            Ref ref = refs.get(name);
            if (ref != null) {
                res.add(ref);
            }
        }
        return unmodifiableList(res);
    }

    @Override public Iterator<Ref> iterator() {
        return byName.values().iterator();
    }

    @Override public boolean equals(Object o) {
        if (this == o) { return true; }
        if (!(o instanceof RefSet)) { return false; }
        RefSet that = (RefSet) o;
        return byName.equals(that.byName) && Objects.equals(defaultBranch, that.defaultBranch);
    }

    @Override public int hashCode() {
        return 31 * byName.hashCode() + Objects.hashCode(defaultBranch);
    }

    static RefSet build(RevWalk revWalk, Map<String, org.eclipse.jgit.lib.Ref> refs)
            throws IOException {
        HashMap<String, Ref> map = new HashMap<>(refs.size());
        Ref defaultBranch = init(revWalk, refs, map);
        return new RefSet(map.values(), defaultBranch);
    }

    private static Ref init(RevWalk revWalk, Map<String, org.eclipse.jgit.lib.Ref> refs, Map<String, Ref> map)
            throws IOException {
        for (org.eclipse.jgit.lib.Ref gitRef : refs.values()) {
            String name = gitRef.getName();
            ObjectId id = gitRef.getObjectId();
            if (Ref.isBranch(name) || Ref.isTag(name)) {
                try {
                    Ref ref = makeRef(revWalk, name, id);
                    if (ref != null) {
                        map.put(ref.getName(), ref);
                    }
                }
                catch (MissingObjectException ex) {
                    // Ref references missing object.
                }
            }
        }
        org.eclipse.jgit.lib.Ref gitRef = refs.get(HEAD);
        if (gitRef != null) {
            String name = gitRef.getLeaf().getName();
            Ref ref = map.get(name);
            if (ref != null) {
                return ref;
            }
            return new Ref(name, ObjectId.zeroId());
        }
        return EMPTY_MASTER;
    }

    private static Ref makeRef(RevWalk revWalk, String name, ObjectId id)
            throws IOException {
        RevObject revObject = revWalk.parseAny(id);
        if (revObject instanceof RevCommit) {
            return new Ref(name, revObject);
        }
        if (revObject instanceof RevTag) {
            RevTag revTag = (RevTag) revObject;
            revObject = revTag.getObject();
            while (revObject instanceof RevTag) {
                revObject = revWalk.parseAny(((RevTag) revObject).getObject());
            }
            if (revObject instanceof RevCommit) {
                return new Ref(name, revObject);
            }
        }
        return null;
    }
}
