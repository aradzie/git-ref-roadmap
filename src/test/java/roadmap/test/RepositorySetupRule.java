package roadmap.test;

import org.eclipse.jgit.errors.*;
import org.eclipse.jgit.lib.*;
import org.junit.rules.*;

import java.io.*;

public class RepositorySetupRule extends ExternalResource {
    public static class FileRepositoryBuilder extends
            BaseRepositoryBuilder<FileRepositoryBuilder, FileRepository> {
        @Override public FileRepository build()
                throws IOException {
            FileRepository repo = new FileRepository(setup());
            if (isMustExist() && !repo.getObjectDatabase().exists()) {
                throw new RepositoryNotFoundException(getGitDir());
            }
            return repo;
        }
    }

    public static class FileRepository
            extends org.eclipse.jgit.internal.storage.file.FileRepository
            implements AutoCloseable {
        public FileRepository(File gitDir)
                throws IOException {
            super(gitDir);
        }

        public FileRepository(String gitDir)
                throws IOException {
            super(gitDir);
        }

        public FileRepository(BaseRepositoryBuilder options)
                throws IOException {
            super(options);
        }
    }

    private final FileUtil fileUtil = FileUtil.instance();

    public FileRepository setupBare(RepositoryRule.Setup s)
            throws IOException {
        return createBare(s, fileUtil.dir());
    }

    public FileRepository setupWorkTree(RepositoryRule.Setup s)
            throws IOException {
        return createWorkTree(s, fileUtil.dir());
    }

    @Override protected void after() {
        fileUtil.cleanUpAll();
    }

    public static FileRepository createBare(RepositoryRule.Setup setup, File dir)
            throws IOException {
        FileRepository db = new FileRepositoryBuilder()
                .setGitDir(dir)
                .setBare()
                .build();
        db.create(true);
        setup(setup, db);
        return db;
    }

    public static FileRepository createWorkTree(RepositoryRule.Setup setup, File dir)
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

    private static void setup(RepositoryRule.Setup setup, Repository db)
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
