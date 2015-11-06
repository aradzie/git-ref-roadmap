package roadmap.test;

import org.eclipse.jgit.internal.storage.file.*;
import org.eclipse.jgit.lib.*;
import org.junit.rules.*;

import java.io.*;

public class RepositorySetupRule extends ExternalResource {
    public FileRepository setupBare(RepositorySetup s)
            throws IOException {
        return createBare(s, TemporaryFiles.dir());
    }

    public FileRepository setupWorkTree(RepositorySetup setup)
            throws IOException {
        return createWorkTree(setup, TemporaryFiles.dir());
    }

    @Override protected void after() {
        TemporaryFiles.cleanUp();
    }

    public static FileRepository createBare(RepositorySetup setup, File dir)
            throws IOException {
        FileRepository db = new FileRepositoryBuilder()
                .setGitDir(dir)
                .setBare()
                .build();
        db.create(true);
        setup(setup, db);
        return db;
    }

    public static FileRepository createWorkTree(RepositorySetup setup, File dir)
            throws IOException {
        FileRepository db = new FileRepositoryBuilder()
                .setWorkTree(dir)
                .build();
        db.create(false);
        setup(setup, db);
        return db;
    }

    public static FileRepository openWorkTree(File dir)
            throws IOException {
        return new FileRepositoryBuilder()
                .setWorkTree(dir.getCanonicalFile())
                .setMustExist(true)
                .build();
    }

    private static void setup(RepositorySetup setup, Repository db)
            throws IOException {
        try {
            setup.play(db);
        }
        catch (RuntimeException ex) {
            throw ex;
        }
        catch (IOException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw new IOException("cannot initialize repository", ex);
        }
    }
}
