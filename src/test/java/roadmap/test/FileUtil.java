package roadmap.test;

import java.io.*;
import java.util.*;

/** Manages temporary files for tests. */
public class FileUtil {
    private static final FileUtil INSTANCE = new FileUtil();

    /** @return Singleton instance. */
    public static FileUtil instance() {
        return INSTANCE;
    }

    private final HashSet<File> files = new HashSet<>();

    /**
     * Create new unique temporary directory for test files.
     * The directory will be deleted by method {@link #cleanUpAll()}.
     *
     * @return New unique temporary directory for test files.
     */
    public File dir() {
        File parent = new File(System.getProperty("java.io.tmpdir"));
        int n = 0;
        while (true) {
            String name = String.format("commitq-%03d", n++);
            File file = new File(parent, name);
            if (file.exists()) {
                continue;
            }
            if (!file.mkdirs()) {
                throw new IllegalStateException("Cannot create temporary directory: "
                        + file.getAbsolutePath());
            }
            files.add(file);
            return file;
        }
    }

    /**
     * Create new unique file in temporary directory.
     * The file will be deleted by method {@link #cleanUpAll()}.
     *
     * @param name File suffix.
     * @return New unique temporary file for tests.
     */
    public File file(String name) {
        return new File(dir(), name);
    }

    /** Remove all temporary files created by tests. */
    public void cleanUpAll() {
        Iterator<File> it = files.iterator();
        while (it.hasNext()) {
            File file = it.next();
            if (delete(file)) {
                it.remove();
            }
        }
    }

    private static boolean delete(File file) {
        if (file.exists()) {
            if (deleteQuietly(file)) {
                return true;
            }
            // This might fail on Windows if a memory-mapped file was closed.
            // Then JVM will take some time to unmap that file before it will
            // be able to delete it.
            for (int n = 1; n <= 10; n++) {
                System.gc();
                Thread.yield();
                try {
                    Thread.sleep(3 * n);
                }
                catch (InterruptedException ex) {}
                if (deleteQuietly(file)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    static boolean deleteQuietly(File file) {
        // TODO implement me!
        return true;
    }
}
