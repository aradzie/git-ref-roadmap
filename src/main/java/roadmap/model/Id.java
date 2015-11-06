package roadmap.model;

import org.eclipse.jgit.lib.*;

public final class Id extends ObjectId implements HasIdKey {
    public static Id id(AnyObjectId id) {
        if (id instanceof Id) {
            return (Id) id;
        }
        return new Id(id);
    }

    private Id() {
        this(ObjectId.zeroId());
    }

    public Id(AnyObjectId id) {
        super(id);
    }

    @Override public ObjectId getId() {
        return this;
    }
}
