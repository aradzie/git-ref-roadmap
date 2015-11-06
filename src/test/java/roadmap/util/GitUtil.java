package roadmap.util;

import org.eclipse.jgit.lib.*;
import roadmap.model.*;

import java.util.*;

public class GitUtil {
    private static final Random random = new Random();

    /** @return New random object id. */
    public static Id newId() {
        byte[] buf = new byte[20];
        random.nextBytes(buf);
        return Id.id(ObjectId.fromRaw(buf));
    }
}
