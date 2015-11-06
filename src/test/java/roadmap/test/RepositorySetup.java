package roadmap.test;

import org.eclipse.jgit.lib.*;

/** Prepare repository for testing, populate it with test data. */
public interface RepositorySetup {
    class Empty implements RepositorySetup {
        @Override public void play(Repository repository)
                throws Exception {}
    }

    /**
     * Populate repository with test data.
     *
     * @param repository An empty repository to update.
     * @throws Exception Any exception.
     */
    void play(Repository repository)
            throws Exception;
}
