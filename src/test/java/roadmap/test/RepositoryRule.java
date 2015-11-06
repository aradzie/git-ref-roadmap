package roadmap.test;

import org.eclipse.jgit.lib.*;
import org.junit.rules.*;
import org.junit.runner.*;
import org.junit.runners.model.*;

import java.lang.reflect.*;

/** For integration tests that require repositories. */
public class RepositoryRule implements TestRule {
    /** Prepare repository for testing, populate it with test data. */
    public interface Setup {
        class Empty implements Setup {
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

    private Setup setup;

    public RepositoryRule() {}

    @Override public Statement apply(final Statement base,
                                     final Description description) {
        return new Statement() {
            @Override public void evaluate()
                    throws Throwable {
                RequireRepository annotation =
                        description.getAnnotation(RequireRepository.class);
                if (annotation != null) {
                    // Test wants repository.
                    run(base, description, annotation);
                }
                else {
                    // An ordinary test.
                    base.evaluate();
                }
            }

            @Override public String toString() {
                return "repository rule";
            }
        };
    }

    private void run(Statement base, Description description,
                     RequireRepository annotation)
            throws Throwable {
        // Create setup instance now, fail test fast if cannot create.
        setSetup(newSetup(annotation));

        // Use setup instance to populate repository with test data.
        init(getSetup());

        // Execute tests.
        base.evaluate();
    }

    public void init(Setup setup)
            throws Exception {
        //
    }

    public Setup newSetup(RequireRepository annotation)
            throws Exception {
        Class<Setup> setupClass = (Class<Setup>) annotation.value();
        Constructor<Setup> constructor = setupClass.getConstructor();
        return constructor.newInstance();
    }

    public <T extends Setup> T getSetup() {
        return (T) setup;
    }

    public void setSetup(Setup setup) {
        this.setup = setup;
    }
}
