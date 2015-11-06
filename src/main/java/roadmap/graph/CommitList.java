package roadmap.graph;

import org.eclipse.jgit.errors.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.*;
import org.eclipse.jgit.util.*;
import roadmap.ref.*;

import java.io.*;
import java.nio.charset.*;
import java.util.*;

/**
 * Immutable list of all repository commits sorted in topological order.
 *
 * <p>The list strives for compact representation, the commits of this list
 * only store bare minimum attributes, such as commit id, parents and children.
 * If one wants to obtain commit message, author or committer, he/she needs
 * consult the method {@link #loadDetails(ObjectReader, AnyObjectId) loadDetails}
 * to get these attributes.</p>
 */
public class CommitList implements Iterable<Commit> {
    /** Collection of head sets collected from all children of a commit. */
    private static class HeadSetCollection extends ArrayList<HeadSet> {
        final Commit commit;

        HeadSetCollection(Commit c) {
            commit = c;
            int childCount = commit.getChildCount();
            for (int n = 0; n < childCount; n++) {
                addImpl(commit.getChild(n).getHeads());
            }
        }

        private void addImpl(HeadSet heads) {
            // Several children might carry the same nodes,
            // so we only collect unique nodes like set would do.
            for (HeadSet tmp : this) {
                if (tmp == heads) {
                    return;
                }
            }
            add(heads);
        }

        RefGraph.Node makeNode() {
            RefGraph.Node node = new RefGraph.Node(commit);
            RefNodeSet children = new RefNodeSet();
            for (HeadSet heads : this) {
                children.addAll(heads.getNodes());
            }
            for (RefGraph.Node child : children) {
                child.link(node);
            }
            commit.getHeads().setNodes(Collections.singleton(node));
            return node;
        }

        void mergeNodes() {
            RefNodeSet nodes = new RefNodeSet();
            for (HeadSet heads : this) {
                nodes.addAll(heads.getNodes());
            }
            commit.getHeads().setNodes(nodes);
        }
    }

    /** Specialized rev commit class. */
    private static class RevCommit extends org.eclipse.jgit.revwalk.RevCommit {
        private Commit commit;
        private Object child;

        RevCommit(AnyObjectId id) {
            super(id);
        }

        @Override public void reset() {
            super.reset();
            commit = null;
        }

        void addChild(RevCommit child) {
            if (this.child == null) {
                this.child = child;
            }
            else {
                if (this.child instanceof RevCommit) {
                    this.child = new RevCommit[]{(RevCommit) this.child, child};
                }
                else {
                    RevCommit[] a = (RevCommit[]) this.child;
                    RevCommit[] b = new RevCommit[a.length + 1];
                    for (int i = 0; i < a.length; i++) {
                        b[i] = a[i];
                    }
                    b[a.length] = child;
                    this.child = b;
                }
            }
        }

        public int getChildCount() {
            if (child == null) {
                return 0;
            }
            if (child instanceof RevCommit) {
                return 1;
            }
            return ((RevCommit[]) child).length;
        }

        public RevCommit getChild(int index) {
            if (child instanceof RevCommit) {
                return (RevCommit) child;
            }
            return ((RevCommit[]) child)[index];
        }

        Commit getCommit() {
            if (commit == null) {
                commit = new Commit(this, getTree());
            }
            return commit;
        }
    }

    /** Specialized rev walk class. */
    private static class RevWalk extends org.eclipse.jgit.revwalk.RevWalk {
        RevWalk(ObjectReader reader)
                throws IOException {
            super(reader);
        }

        @Override protected org.eclipse.jgit.revwalk.RevCommit createCommit(AnyObjectId id) {
            return new RevCommit(id);
        }

        @Override public RevCommit parseCommit(AnyObjectId id)
                throws MissingObjectException, IncorrectObjectTypeException, IOException {
            return (RevCommit) super.parseCommit(id);
        }

        @Override public RevCommit lookupCommit(AnyObjectId id) {
            return (RevCommit) super.lookupCommit(id);
        }
    }

    /** Mutable structure that assists in making immutable commit list. */
    private static class CommitListBuilder {
        private static final int DIR_SIZE = 32;
        private static final float LOAD_FACTOR = 1.5f;
        // We allocate memory in small segments instead of one large
        // continuous array to reduce pressure on memory and garbage collector.
        // To lookup an element first find corresponding segment
        // in the top level directory, then navigate in that segment.
        private Commit[][] list;
        private Commit[][] hashTable;
        private int size;

        private CommitListBuilder() {
            list = new Commit[DIR_SIZE][];
        }

        void add(Commit commit) {
            int dirIdx = size >> BLOCK_BITS;
            int blkIdx = size & BLOCK_MASK;
            if (dirIdx == list.length) {
                list = Arrays.copyOf(list, list.length + DIR_SIZE);
            }
            if (list[dirIdx] == null) {
                list[dirIdx] = new Commit[BLOCK_SIZE];
            }
            list[dirIdx][blkIdx] = commit;
            size++;
            hashTable = null;
        }

        Commit[][] getList() {
            return list;
        }

        Commit[][] getHashTable() {
            if (hashTable == null) {
                int dirSize = (int) (size * LOAD_FACTOR / BLOCK_SIZE) + 1;
                hashTable = new Commit[dirSize][];
                for (int i = 0; i < hashTable.length; i++) {
                    hashTable[i] = new Commit[BLOCK_SIZE];
                }
                for (int n = 0; n < size; n++) {
                    Commit commit = list[n >> BLOCK_BITS][n & BLOCK_MASK];
                    int dirIdx = (commit.hashCode() >>> BLOCK_BITS) % hashTable.length;
                    Commit[] block = hashTable[dirIdx];
                    int blkIdx = commit.hashCode() & BLOCK_MASK;
                    while (block[blkIdx] != null) {
                        blkIdx = ++blkIdx & BLOCK_MASK;
                    }
                    block[blkIdx] = commit;
                }
            }
            return hashTable;
        }

        int getSize() {
            return size;
        }
    }

    /** Commit predicate. */
    public interface CommitMatcher {
        /** An instance that matches all commits. */
        CommitMatcher ANY = new CommitMatcher() {
            @Override public boolean matches(Commit commit) {
                return true;
            }
        };

        boolean matches(Commit commit);
    }

    /** Assists in building iterator over this commit list. */
    public class IteratorBuilder {
        /** Configurable iterator implementation. */
        private class IteratorImpl implements Iterator<Commit> {
            private int index;

            IteratorImpl() {
                index = findNext(0);
            }

            int findNext(int index) {
                while (index < size()) {
                    Commit commit = get(index);
                    HeadSet heads = commit.getHeads();
                    // First make cheap check....
                    if (heads.containsAny(since) && !heads.containsAny(until)) {
                        // ... then potentially slow check.
                        if (matcher.matches(commit)) {
                            break;
                        }
                    }
                    index++;
                }
                return index;
            }

            @Override public boolean hasNext() {
                return index < size();
            }

            @Override public Commit next() {
                if (index == size()) {
                    throw new NoSuchElementException();
                }
                Commit commit = get(index);
                index = findNext(index + 1);
                return commit;
            }

            @Override public void remove() {
                throw new UnsupportedOperationException();
            }
        }

        private final HeadSet since = new HeadSet(hsb);
        private final HeadSet until = new HeadSet(hsb);
        private CommitMatcher matcher = CommitMatcher.ANY;

        /**
         * Include commits reachable from the specified ref.
         *
         * <p>This method may be called multiple times to add more
         * refs to the builder.</p>
         *
         * @param ref A ref that includes commits reachable from it.
         * @return This instance for fluent interface.
         */
        public IteratorBuilder since(roadmap.ref.Ref... ref) {
            for (roadmap.ref.Ref r : ref) {
                validateRef(r);
                since.add(hsb, r.getId());
            }
            return this;
        }

        /**
         * Include commits reachable from the specified refs.
         *
         * <p>This method may be called multiple times to add more
         * refs to the builder.</p>
         *
         * @param refs A collection of refs that include commits reachable from them.
         * @return This instance for fluent interface.
         */
        public IteratorBuilder since(Collection<roadmap.ref.Ref> refs) {
            for (roadmap.ref.Ref r : refs) {
                validateRef(r);
                since.add(hsb, r.getId());
            }
            return this;
        }

        /**
         * Exclude commits reachable from the specified ref.
         *
         * <p>This method may be called multiple times to add more
         * refs to the builder.</p>
         *
         * @param ref A ref that excludes commits reachable from it.
         * @return This instance for fluent interface.
         */
        public IteratorBuilder until(roadmap.ref.Ref... ref) {
            for (roadmap.ref.Ref r : ref) {
                validateRef(r);
                until.add(hsb, r.getId());
            }
            return this;
        }

        /**
         * Exclude commits reachable from the specified refs.
         *
         * <p>This method may be called multiple times to add more
         * refs to the builder.</p>
         *
         * @param refs A collection of refs that exclude commits reachable from them.
         * @return This instance for fluent interface.
         */
        public IteratorBuilder until(Collection<roadmap.ref.Ref> refs) {
            for (roadmap.ref.Ref r : refs) {
                validateRef(r);
                until.add(hsb, r.getId());
            }
            return this;
        }

        public IteratorBuilder matcher(CommitMatcher matcher) {
            this.matcher = matcher;
            return this;
        }

        private void validateRef(roadmap.ref.Ref r) {
            if (!refs.all().contains(r)) {
                throw new IllegalStateException("unknown ref");
            }
        }

        public Iterable<Commit> make() {
            return new Iterable<Commit>() {
                @Override public Iterator<Commit> iterator() {
                    return new IteratorImpl();
                }
            };
        }
    }

    /** Prevents overflowing with ref diff pairs when there are too many refs. */
    private class RefDiffSink extends HashSet<RefDiff> implements RefDiff.Sink {
        /**
         * Empirical study suggests that the application and web UI handles
         * repositories of order of ~100 refs quite well. This amounts to
         * roughly 100^2 = 10000 ref diff pairs. So we set upper threshold
         * roughly the same.
         */
        static final int THRESHOLD = 150;
        /** Only include ref pair if any one of its commits is interesting. */
        final HashSet<ObjectId> interesting = new HashSet<>();

        RefDiffSink(RefSet refs) {
            // All heads from a repository.
            HashSet<Commit> heads = new HashSet<>();
            for (roadmap.ref.Ref ref : refs.all()) {
                heads.add(map(ref.getId()));
            }
            add(heads);
        }

        void add(HashSet<Commit> refs) {
            ArrayList<Commit> list = new ArrayList<>(refs);
            Collections.sort(list, CMP);
            for (Commit commit : list) {
                if (interesting.size() < THRESHOLD) {
                    interesting.add(commit);
                }
                else {
                    break;
                }
            }
        }

        @Override public void add(AnyObjectId mergeBase,
                                  AnyObjectId a, int commitsA,
                                  AnyObjectId b, int commitsB) {
            if (interesting.contains(a) && interesting.contains(b)) {
                add(new RefDiff(mergeBase, a, commitsA, b, commitsB));
            }
        }
    }

    /** Sorts commits in order from most to least recent. */
    static final Comparator<Commit> CMP = new Comparator<Commit>() {
        @Override public int compare(Commit o1, Commit o2) {
            return o1.getIndex() - o2.getIndex();
        }
    };
    private static final int BLOCK_BITS = 10;
    private static final int BLOCK_SIZE = 1 << BLOCK_BITS;
    private static final int BLOCK_MASK = BLOCK_SIZE - 1;
    private final RefSet refs;
    private final Commit[][] list;
    private final Commit[][] hashTable;
    private final int size;
    private final HeadSet.Builder hsb;
    private final RefGraph refGraph;

    public CommitList(ObjectReader reader, RefSet r)
            throws IOException {
        refs = r;

        Set<ObjectId> tips = refs.roots();

        RevWalk revWalk = new RevWalk(reader);
        revWalk.setRetainBody(false);
        revWalk.sort(RevSort.TOPO);
        for (AnyObjectId id : tips) {
            revWalk.markStart(revWalk.parseCommit(id));
        }

        CommitListBuilder b = new CommitListBuilder();

        try {
            // Weave the graph of commits, store commits in the list
            // sorted in topological order.
            for (org.eclipse.jgit.revwalk.RevCommit tmp : revWalk) {
                RevCommit rc = (RevCommit) tmp;

                // Setup children of this rev commit.
                for (int n = 0; n < rc.getParentCount(); n++) {
                    ((RevCommit) rc.getParent(n)).addChild(rc);
                }

                // Create commit from rev object.
                Commit commit = rc.getCommit();
                commit.setIndex(b.getSize());
                for (int n = 0; n < rc.getParentCount(); n++) {
                    commit.addParent(((RevCommit) rc.getParent(n)).getCommit());
                }
                for (int n = 0; n < rc.getChildCount(); n++) {
                    commit.addChild(rc.getChild(n).getCommit());
                }
                commit.setRefs(refs.byId(commit));
                b.add(commit);
            }
        }
        finally {
            revWalk.dispose();
        }

        list = b.getList();
        hashTable = b.getHashTable();
        size = b.getSize();
        hsb = new HeadSet.Builder(tips);
        HashSet<ObjectId> mergeBases = new HashSet<>();
        RefDiffSink diffs = new RefDiffSink(refs);
        init(mergeBases, diffs);
        refGraph = buildGraph(mergeBases, diffs);
    }

    private CommitList(RefSet refs, CommitListBuilder b) {
        this.refs = refs;
        list = b.getList();
        hashTable = b.getHashTable();
        size = b.getSize();
        hsb = new HeadSet.Builder(refs.roots());
        HashSet<ObjectId> mergeBases = new HashSet<>();
        RefDiffSink diffs = new RefDiffSink(refs);
        init(mergeBases, diffs);
        refGraph = buildGraph(mergeBases, diffs);
    }

    /** @return Make ref graph from the current list of commits and refs. */
    private RefGraph buildGraph(HashSet<ObjectId> mergeBases, Set<RefDiff> diffs) {
        RefNodeSet roots = new RefNodeSet();
        buildGraph(mergeBases, roots);
        RefGraph graph = new RefGraph(refs, roots, diffs);
        new Beautifier(graph).beautify();
        return graph;
    }

    /** For every commit find set of refs this commit is reachable from. */
    private void init(Set<ObjectId> mergeBases, RefDiff.Sink diffs) {
        // For this algorithm to work the list has to be sorted
        // topologically.
        for (Commit commit : this) {
            HeadSetCollection hsc = new HeadSetCollection(commit);

            boolean mergeBase = false;

            HeadSet heads;
            Iterator<HeadSet> it = hsc.iterator();
            if (it.hasNext()) {
                // Commit with single child. Borrow its head set as is.
                heads = it.next();
                if (it.hasNext()) {
                    // Commit with multiple children. Make union of all head sets.
                    heads = new HeadSet(heads);
                    while (it.hasNext()) {
                        HeadSet tmp = it.next();
                        if (HeadSet.isMergeBase(heads, tmp)) {
                            hsb.asMergeBase(diffs, commit, heads, tmp);
                            mergeBase = true;
                        }
                        heads.addAll(tmp);
                    }
                }
            }
            else {
                // Root commit without children.
                heads = new HeadSet(hsb);
            }

            Set<roadmap.ref.Ref> refs = commit.getRefs();
            if (!refs.isEmpty()) {
                heads = heads.addRefs(hsb, refs);
                for (HeadSet tmp : hsc) {
                    hsb.asMergeBase(diffs, commit, tmp);
                }
                mergeBase = true;
            }

            if (mergeBase) {
                mergeBases.add(commit);
            }

            commit.setHeads(heads);

            heads.count(hsb);
        }
    }

    private void buildGraph(Set<ObjectId> mergeBases, Set<RefGraph.Node> roots) {
        // For this algorithm to work the list has to be sorted
        // topologically.
        for (Commit commit : this) {
            HeadSetCollection hsc = new HeadSetCollection(commit);
            if (mergeBases.contains(commit)) {
                RefGraph.Node node = hsc.makeNode();
                if (hsc.isEmpty()) {
                    roots.add(node);
                }
            }
            else {
                if (hsc.size() > 1) {
                    hsc.mergeNodes();
                }
            }
        }
        for (Commit commit : this) {
            commit.getHeads().setNodes(null);
        }
    }

    @Override public Iterator<Commit> iterator() {
        return new Iterator<Commit>() {
            int index;

            @Override public boolean hasNext() {
                return index < size;
            }

            @Override public Commit next() {
                return get(index++);
            }

            @Override public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    /** @return Creates and configures iterator over the list of commits. */
    public IteratorBuilder iteratorBuilder() {
        return new IteratorBuilder();
    }

    /** @return The refs this list was built for. */
    public RefSet getRefs() {
        return refs;
    }

    /** @return A value indicating whether the list is empty. */
    public boolean isEmpty() {
        return size == 0;
    }

    /** @return Number of all commits in the list. */
    public int size() {
        return size;
    }

    /**
     * Get commit by the specified index.
     *
     * @param index A commit index in this list.
     * @return The commit for the specified index.
     */
    public Commit get(int index) {
        if (index < 0 || size <= index) {
            throw new IndexOutOfBoundsException(String.valueOf(index));
        }
        return list[index >> BLOCK_BITS][index & BLOCK_MASK];
    }

    /**
     * Get index of a commit by the specified id.
     *
     * @param id A commit id.
     * @return The commit index in this list,
     * or -1 if id is not found in this list.
     */
    public int indexOf(AnyObjectId id) {
        if (size > 0) {
            int dirIdx = (id.hashCode() >>> BLOCK_BITS) % hashTable.length;
            Commit[] block = hashTable[dirIdx];
            int blkIdx = id.hashCode() & BLOCK_MASK;
            while (block[blkIdx] != null) {
                if (ObjectId.equals(block[blkIdx], id)) {
                    return block[blkIdx].getIndex();
                }
                blkIdx = ++blkIdx & BLOCK_MASK;
            }
        }
        return -1;
    }

    /**
     * Maps commit id to commit object.
     *
     * @param id A commit id.
     * @return A commit object with the specified id.
     */
    public Commit map(AnyObjectId id) {
        int index = indexOf(id);
        if (index == -1) {
            throw new IllegalArgumentException("unknown id: " + id.getName());
        }
        return get(index);
    }

    /**
     * Get commit extended details by loading missing attributes
     * from the database.
     *
     * @param reader An object reader to read missing commit details.
     * @param id     Commit id.
     * @return Extended commit details.
     * @throws IOException If I/O error occurs.
     */
    public CommitDetails loadDetails(ObjectReader reader, AnyObjectId id)
            throws IOException {
        int index = indexOf(id);
        if (index == -1) {
            throw new MissingObjectException(id.copy(), Constants.OBJ_COMMIT);
        }
        ObjectLoader loader = reader.open(id, Constants.OBJ_COMMIT);
        byte[] buffer = loader.getCachedBytes();
        String message = Parser.getMessage(buffer);
        PersonIdent author = Parser.parseAuthor(buffer);
        PersonIdent committer = Parser.parseCommitter(buffer);
        return new CommitDetails(get(index), message, author, committer);
    }

    /** @return Graph of refs. */
    public RefGraph getRefGraph() {
        return refGraph;
    }

    /**
     * Find all refs that the specified commit is reachable from.
     *
     * <p>The specified commit must be taken from this list by some index.
     * If commit origins from some other list, the behaviour of this method
     * is unknown.</p>
     *
     * @param commit Find all refs that contain this commit.
     * @param refs   user specified set of refs to update.
     */
    public void getRefs(Commit commit, Set<roadmap.ref.Ref> refs) {
        HeadSet.Head[] heads = hsb.select(commit.getHeads());
        for (HeadSet.Head head : heads) {
            refs.addAll(this.refs.byId(head));
        }
    }

    /** Result of method {@link CommitList#countGroupByRef(CommitList.CommitMatcher)}. */
    public static class GroupByRefMap {
        private final HashMap<AnyObjectId, HeadSet.Head> total
                = new HashMap<>();
        private final HashMap<AnyObjectId, HeadSet.Head> matched
                = new HashMap<>();

        private GroupByRefMap(HeadSet.Builder total, HeadSet.Builder matched) {
            for (HeadSet.Head id : total.id) {
                this.total.put(id, id);
            }
            for (HeadSet.Head id : matched.id) {
                this.matched.put(id, id);
            }
        }

        /**
         * @param ref A ref.
         * @return Number of all commits having the specified ref.
         */
        public int getTotal(roadmap.ref.Ref ref) {
            HeadSet.Head head = total.get(ref.getId());
            if (head == null) {
                throw new IllegalStateException("unknown ref " + ref);
            }
            return head.commits;
        }

        /**
         * @param ref A ref.
         * @return The number of commits matched the predicate and
         * having the specified ref.
         */
        public int getMatched(roadmap.ref.Ref ref) {
            HeadSet.Head head = matched.get(ref.getId());
            if (head == null) {
                throw new IllegalStateException("unknown ref" + ref);
            }
            return head.commits;
        }
    }

    /**
     * Count commits matching the specified predicate
     * and group them by ref.
     *
     * @param m A predicate to filter commits.
     * @return Count of commits grouped by refs.
     */
    public GroupByRefMap countGroupByRef(CommitMatcher m) {
        HeadSet.Builder total = new HeadSet.Builder(hsb);
        HeadSet.Builder matched = new HeadSet.Builder(hsb);
        for (Commit commit : this) {
            commit.getHeads().count(total);
            if (m.matches(commit)) {
                commit.getHeads().count(matched);
            }
        }
        return new GroupByRefMap(total, matched);
    }

    /** @return Immutable view of the commit list. */
    public List<Commit> adapt() {
        return new AbstractList<Commit>() {
            @Override public int size() {
                return size;
            }

            @Override public Commit get(int index) {
                return CommitList.this.get(index);
            }

            @Override public int indexOf(Object o) {
                if (o instanceof AnyObjectId) {
                    return CommitList.this.indexOf((AnyObjectId) o);
                }
                throw new ClassCastException();
            }

            @Override public int lastIndexOf(Object o) {
                return indexOf(o);
            }

            @Override public boolean contains(Object o) {
                return indexOf(o) != -1;
            }

            @Override public Iterator<Commit> iterator() {
                return CommitList.this.iterator();
            }
        };
    }

    public void dump(PrintWriter w)
            throws IOException {
        w.printf("commits: %d\n", size());
        w.printf("refs: %d\n", refs.all().size());
        refGraph.dump(w);
        w.flush();
    }

    private static class Parser {
        static String getMessage(byte[] bytes) {
            int offset = RawParseUtils.commitMessage(bytes, 0);
            if (offset < 0) {
                return "";
            }
            Charset cs = RawParseUtils.parseEncoding(bytes);
            return RawParseUtils.decode(cs, bytes, offset, bytes.length);
        }

        static PersonIdent parseAuthor(byte[] bytes) {
            int offset = RawParseUtils.author(bytes, 0);
            if (offset < 0) {
                return null;
            }
            return RawParseUtils.parsePersonIdent(bytes, offset);
        }

        static PersonIdent parseCommitter(byte[] bytes) {
            int offset = RawParseUtils.committer(bytes, 0);
            if (offset < 0) {
                return null;
            }
            return RawParseUtils.parsePersonIdent(bytes, offset);
        }
    }
}
