package roadmap.util;

import org.eclipse.jgit.lib.ObjectId;

import java.util.Random;

public class Util {
    private static final Random random = new Random();

    /** @return New random object id. */
    public static ObjectId newId() {
        byte[] buf = new byte[20];
        random.nextBytes(buf);
        return ObjectId.fromRaw(buf);
    }
}
