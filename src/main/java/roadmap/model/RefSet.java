package roadmap.model;

import org.eclipse.jgit.errors.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.*;
import org.eclipse.jgit.transport.*;
import roadmap.*;

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
public class RefSet implements Iterable<roadmap.model.Ref> {
    /** Assists in building refs from repository. */
    public static final class Builder {
        private final RevWalk revWalk;
        private final Map<String, org.eclipse.jgit.lib.Ref> refs;

        private Builder(RevWalk revWalk, Map<String, org.eclipse.jgit.lib.Ref> refs) {
            this.revWalk = revWalk;
            this.refs = refs;
        }

        public RefSet build()
                throws IOException {
            HashMap<String, roadmap.model.Ref> map = new HashMap<>(refs.size());
            roadmap.model.Ref defaultBranch = init(map);
            return new RefSet(map.values(), defaultBranch);
        }

        private roadmap.model.Ref init(Map<String, roadmap.model.Ref> map)
                throws IOException {
            for (org.eclipse.jgit.lib.Ref gitRef : refs.values()) {
                if (gitRef.isSymbolic()) {
                    // Should only happen for HEAD in local repositories.
                    continue;
                }
                if (roadmap.model.Ref.isBranch(gitRef.getName()) || roadmap.model.Ref.isTag(gitRef.getName())) {
                    try {
                        roadmap.model.Ref ref = makeRef(gitRef);
                        if (ref != null) {
                            map.put(ref.getName(), ref);
                        }
                    }
                    catch (MissingObjectException ex) {
                        // Ref references missing object.
                    }
                }
            }
            return findDefaultBranch(map);
        }

        private roadmap.model.Ref makeRef(org.eclipse.jgit.lib.Ref gitRef)
                throws IOException {
            RevObject revObject = revWalk.parseAny(gitRef.getObjectId());
            if (revObject instanceof RevCommit) {
                return new roadmap.model.Ref(gitRef.getName(), revObject);
            }
            if (revObject instanceof RevTag) {
                RevTag revTag = (RevTag) revObject;
                revObject = revTag.getObject();
                while (revObject instanceof RevTag) {
                    revObject = revWalk.parseAny(((RevTag) revObject).getObject());
                }
                if (revObject instanceof RevCommit) {
                    return new AnnotatedTag(gitRef.getName(), revObject, revTag,
                            revTag.getTaggerIdent(), new Message(revTag.getFullMessage()));
                }
            }
            return null;
        }

        private roadmap.model.Ref findDefaultBranch(Map<String, roadmap.model.Ref> map) {
            org.eclipse.jgit.lib.Ref gitRef = refs.get(HEAD);
            if (gitRef != null) {
                String name = gitRef.getLeaf().getName();
                roadmap.model.Ref ref = map.get(name);
                if (ref != null) {
                    return ref;
                }
                if (name.equals(EMPTY_MASTER.getName())) {
                    return EMPTY_MASTER;
                }
                else {
                    return new roadmap.model.Ref(name, ObjectId.zeroId());
                }
            }
            return EMPTY_MASTER;
        }
    }

    /** Helper collection that maps names to refs. */
    private static class RefMap extends HashMap<String, roadmap.model.Ref> {
        RefMap() {}

        RefMap(Collection<roadmap.model.Ref> refs) {
            for (roadmap.model.Ref ref : refs) {
                add(ref);
            }
        }

        void add(roadmap.model.Ref ref) {
            put(ref.getName(), ref);
        }

        RefMap branches() {
            RefMap map = new RefMap();
            for (roadmap.model.Ref ref : values()) {
                if (ref.isBranch()) {
                    map.add(ref);
                }
            }
            return map;
        }

        RefMap tags() {
            RefMap map = new RefMap();
            for (roadmap.model.Ref ref : values()) {
                if (ref.isTag()) {
                    map.add(ref);
                }
            }
            return map;
        }
    }

    /** Helper collection that groups set of refs by the same id. */
    private static class ReverseRefMap extends HashMap<AnyObjectId, Set<roadmap.model.Ref>> {
        ReverseRefMap() {}

        ReverseRefMap(Collection<roadmap.model.Ref> refs) {
            HashMap<AnyObjectId, Set<roadmap.model.Ref>> map = new HashMap<>();
            for (roadmap.model.Ref ref : refs) {
                Set<roadmap.model.Ref> set = map.get(ref.getId());
                if (set == null) {
                    set = singleton(ref);
                }
                else {
                    if (set.size() == 1) {
                        set = new RefSetImpl(set);
                    }
                    set.add(ref);
                }
                map.put(ref.getId(), set);
            }
            for (Map.Entry<AnyObjectId, Set<roadmap.model.Ref>> entry : map.entrySet()) {
                put(entry.getKey(), unmodifiableSet(entry.getValue()));
            }
        }

        @Override public Set<roadmap.model.Ref> get(Object key) {
            Set<roadmap.model.Ref> refs = super.get(key);
            if (refs == null) {
                return emptySet();
            }
            return refs;
        }
    }

    public static Builder builder(Repository repository) {
        return new Builder(new RevWalk(repository), repository.getAllRefs());
    }

    public static Builder builder(Repository repository, RefFilter filter) {
        return new Builder(new RevWalk(repository), filter.filter(repository.getAllRefs()));
    }

    public static final roadmap.model.Ref EMPTY_MASTER =
            new roadmap.model.Ref(R_HEADS + MASTER, ObjectId.zeroId());
    /** Empty refs collection. */
    public static final RefSet EMPTY = new RefSet();
    /** Request to select all refs. */
    public static final Set<String> STAR = singleton("*");
    /** All refs by name. */
    private final Map<String, roadmap.model.Ref> all;
    /** All refs grouped by object id. */
    private final Map<AnyObjectId, Set<roadmap.model.Ref>> allById;
    /** Branches by name. */
    private final Map<String, roadmap.model.Ref> branches;
    /** Branches grouped by object id. */
    private final Map<AnyObjectId, Set<roadmap.model.Ref>> branchesById;
    /** Tags by name. */
    private final Map<String, roadmap.model.Ref> tags;
    /** Tags grouped by object id. */
    private final Map<AnyObjectId, Set<roadmap.model.Ref>> tagsById;
    /** The default branch. */
    private final roadmap.model.Ref defaultBranch;

    /** Create empty ref set instance. */
    private RefSet() {
        all = branches = tags = emptyMap();
        allById = branchesById = tagsById = emptyMap();
        defaultBranch = EMPTY_MASTER;
    }

    /**
     * Create ref set with proper forced status on its refs.
     *
     * @param refs   Original ref set.
     * @param forced Forced ref identifiers.
     */
    private RefSet(RefSet refs, Set<? extends AnyObjectId> forced) {
        RefMap map = new RefMap();
        roadmap.model.Ref defaultBranch = refs.defaultBranch();
        for (roadmap.model.Ref ref : refs.all()) {
            roadmap.model.Ref newRef = ref;
            if (forced.contains(ref.getId())) {
                newRef = ref.asForced();
                if (defaultBranch == ref) {
                    defaultBranch = newRef;
                }
            }
            map.add(newRef);
        }
        all = unmodifiableMap(map);
        branches = unmodifiableMap(map.branches());
        tags = unmodifiableMap(map.tags());
        allById = unmodifiableMap(new ReverseRefMap(all.values()));
        branchesById = unmodifiableMap(new ReverseRefMap(branches.values()));
        tagsById = unmodifiableMap(new ReverseRefMap(tags.values()));
        this.defaultBranch = defaultBranch;
    }

    private RefSet(Collection<roadmap.model.Ref> refs, roadmap.model.Ref defaultBranch) {
        RefMap map = new RefMap(refs);
        all = unmodifiableMap(map);
        branches = unmodifiableMap(map.branches());
        tags = unmodifiableMap(map.tags());
        allById = unmodifiableMap(new ReverseRefMap(all.values()));
        branchesById = unmodifiableMap(new ReverseRefMap(branches.values()));
        tagsById = unmodifiableMap(new ReverseRefMap(tags.values()));
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
    public roadmap.model.Ref defaultBranch() {
        return defaultBranch;
    }

    /** @return All refs with branches and tags. */
    public Collection<roadmap.model.Ref> all() {
        return all.values();
    }

    /** @return Branch refs only. */
    public Collection<roadmap.model.Ref> branches() {
        return branches.values();
    }

    /** @return Tag refs only. */
    public Collection<roadmap.model.Ref> tags() {
        return tags.values();
    }

    /**
     * Get any ref by name.
     *
     * @param name Ref name.
     * @return Ref with that name.
     * @throws RefNotFoundException If ref with that name does not exist.
     */
    public roadmap.model.Ref any(String name) {
        roadmap.model.Ref ref = all.get(Objects.requireNonNull(name));
        if (ref == null) {
            throw new RefNotFoundException(name);
        }
        return ref;
    }

    /**
     * A map that groups all refs by the same id.
     *
     * @return Unmodifiable map of all refs grouped by id.
     */
    public Map<AnyObjectId, Set<roadmap.model.Ref>> allById() {
        return allById;
    }

    /**
     * Get set of branches and tags that point to the specified id.
     *
     * @param id Commit id.
     * @return Unmodifiable set of branches and tags for the specified id,
     *         or empty set if not found.
     */
    public Set<roadmap.model.Ref> allById(AnyObjectId id) {
        return allById.get(Objects.requireNonNull(id));
    }

    /**
     * Get branch by name.
     *
     * @param name Branch name.
     * @return Branch with that name.
     * @throws RefNotFoundException If branch with that name does not exist.
     */
    public roadmap.model.Ref branch(String name) {
        roadmap.model.Ref ref = branches.get(Objects.requireNonNull(name));
        if (ref == null) {
            throw new RefNotFoundException(name);
        }
        return ref;
    }

    /**
     * Get set of branches that point to the specified id.
     *
     * @param id Commit id.
     * @return Unmodifiable set of branches for the specified id,
     *         or empty set if not found.
     */
    public Set<roadmap.model.Ref> branchesById(AnyObjectId id) {
        return branchesById.get(Objects.requireNonNull(id));
    }

    /**
     * Get tag by name.
     *
     * @param name Tag name.
     * @return Tag with that name.
     * @throws RefNotFoundException If tag with that name does not exist.
     */
    public roadmap.model.Ref tag(String name) {
        roadmap.model.Ref ref = tags.get(Objects.requireNonNull(name));
        if (ref == null) {
            throw new RefNotFoundException(name);
        }
        return ref;
    }

    /**
     * Get set of tags that point to the specified id.
     *
     * @param id Commit id.
     * @return Unmodifiable set of tags for the specified id,
     *         or empty set if not found.
     */
    public Set<roadmap.model.Ref> tagsById(AnyObjectId id) {
        return tagsById.get(Objects.requireNonNull(id));
    }

    /** @return Set of all commit ids reachable from all branches and tags. */
    public Set<ObjectId> getAllTips() {
        HashSet<ObjectId> tips = new HashSet<>(all.size());
        for (roadmap.model.Ref ref : all.values()) {
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
    public Collection<roadmap.model.Ref> selectAll(Collection<String> names) {
        return select(names, all);
    }

    /**
     * Select branches with the specified names.
     *
     * @param names Wanted branch names. May include unknown names.
     * @return Branch refs with the specified names.
     */
    public Collection<roadmap.model.Ref> selectBranches(Collection<String> names) {
        return select(names, branches);
    }

    /**
     * Select tags with the specified names.
     *
     * @param names Wanted tag names. May include unknown names.
     * @return Tag refs with the specified names.
     */
    public Collection<roadmap.model.Ref> selectTags(Collection<String> names) {
        return select(names, tags);
    }

    private static Collection<roadmap.model.Ref> select(Collection<String> names, Map<String, roadmap.model.Ref> refs) {
        if (refs.isEmpty()) {
            return emptySet();
        }
        ArrayList<roadmap.model.Ref> res = new ArrayList<>();
        for (String name : names) {
            roadmap.model.Ref ref = refs.get(name);
            if (ref != null) {
                res.add(ref);
            }
        }
        return unmodifiableList(res);
    }

//    public RefSet findForced(RemoteOperation.Push push) {
//        return findForced(push.forced());
//    }
//
//    public RefSet findForced(Set<? extends AnyObjectId> forced) {
//        if (forced.isEmpty()) {
//            return this;
//        }
//        return new RefSet(this, forced);
//    }

    @Override public Iterator<roadmap.model.Ref> iterator() {
        return all.values().iterator();
    }

    @Override public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof RefSet)) {
            return false;
        }
        RefSet that = (RefSet) object;
        return all.equals(that.all)
                && Objects.equals(defaultBranch, that.defaultBranch);
    }

    @Override public int hashCode() {
        return 31 * all.hashCode() + Objects.hashCode(defaultBranch);
    }

    /** @return Ref specs for mirroring a remote repository. */
    public static List<RefSpec> getFetchRefSpecs() {
        ArrayList<RefSpec> refSpecs = new ArrayList<>();
        refSpecs.add(new RefSpec("+refs/heads/*:refs/heads/*"));
        refSpecs.add(new RefSpec("+refs/tags/*:refs/tags/*"));
        refSpecs.add(new RefSpec("+refs/notes/*:refs/notes/*"));
        return unmodifiableList(refSpecs);
    }

    /**
     * Test if this is an application specific ref name and should
     * not be visible outside of the world.
     */
    public static boolean isCustom(String name) {
        return name.startsWith("refs/meta/");
    }

    /** @return Ref for the specified cached merge result tree. */
    public static String customTreeRef(String name) {
        return "refs/meta/trees/" + name;
    }
}
