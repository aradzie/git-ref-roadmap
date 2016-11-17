package roadmap.test;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.BaseRepositoryBuilder;

import java.io.IOException;

public class FileRepositoryBuilder
        extends
        BaseRepositoryBuilder<FileRepositoryBuilder, FileRepository> {
    @Override public FileRepository build()
            throws IOException {
        FileRepository db = new FileRepository(setup());
        if (isMustExist() && !db.getObjectDatabase().exists()) {
            throw new RepositoryNotFoundException(getGitDir());
        }
        return db;
    }
}
