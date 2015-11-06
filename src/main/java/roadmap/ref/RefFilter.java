package roadmap.ref;

public interface RefFilter {
    RefFilter NONE = new RefFilter() {
        @Override public boolean accept(Ref ref) {
            return false;
        }
    };

    RefFilter ANY = new RefFilter() {
        @Override public boolean accept(Ref ref) {
            return true;
        }
    };

    RefFilter BRANCHES = new RefFilter() {
        @Override public boolean accept(Ref ref) {
            return ref.isBranch();
        }
    };

    RefFilter LOCALS = new RefFilter() {
        @Override public boolean accept(Ref ref) {
            return ref.isLocal();
        }
    };

    RefFilter REMOTES = new RefFilter() {
        @Override public boolean accept(Ref ref) {
            return ref.isRemote();
        }
    };

    RefFilter TAGS = new RefFilter() {
        @Override public boolean accept(Ref ref) {
            return ref.isTag();
        }
    };

    boolean accept(Ref ref);
}
