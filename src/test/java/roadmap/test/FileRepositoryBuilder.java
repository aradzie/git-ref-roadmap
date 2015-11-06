package roadmap.test;

import org.eclipse.jgit.errors.*;
import org.eclipse.jgit.internal.storage.file.*;
import org.eclipse.jgit.lib.*;

import java.io.*;

public class FileRepositoryBuilder extends
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
