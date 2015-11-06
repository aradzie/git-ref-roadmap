package roadmap.test;

import org.junit.rules.ExternalResource;

/** Manages temporary files for tests, cleans up everything on test completion. */
public class FileUtilRule extends ExternalResource {
    private final FileUtil fileUtil = FileUtil.instance();

    /** @return File util instance. */
    public FileUtil util() {
        return fileUtil;
    }

    @Override protected void after() {
        fileUtil.cleanUpAll();
    }
}
