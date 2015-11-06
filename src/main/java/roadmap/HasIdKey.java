package roadmap;

import org.eclipse.jgit.lib.*;

public interface HasIdKey {
    /** @return Object identifier in a repository. */
    ObjectId getId();
}
