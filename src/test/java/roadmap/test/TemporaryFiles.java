package roadmap.test;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedList;

public class TemporaryFiles {
    private static final LinkedList<File> files = new LinkedList<>();

    /**
     * Create new unique temporary directory for test files.
     * The directory will be deleted by the method {@link #cleanUp()}.
     *
     * @return New unique temporary directory for test files.
     */
    public static File dir() {
        File parent = new File(System.getProperty("java.io.tmpdir"));
        int n = 0;
        while (true) {
            File file = new File(parent, String.format("git-branch-%03d", n++));
            if (file.exists()) {
                continue;
            }
            if (!file.mkdirs()) {
                throw new IllegalStateException();
            }
            files.add(file);
            return file;
        }
    }

    /** Remove all temporary files created by tests. */
    public static void cleanUp() {
        Iterator<File> it = files.iterator();
        while (it.hasNext()) {
            File file = it.next();
            if (file.exists()) {
                delete(file);
            }
            it.remove();
        }
    }

    private static void delete(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    delete(child);
                }
            }
        }
        if (!file.delete()) {
            file.deleteOnExit();
        }
    }
}
