package roadmap.graph;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;
import roadmap.util.Util;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HeadSetTest {
    @Test public void testSet()
            throws Exception {
        ObjectId a = Util.newId();
        ObjectId b = Util.newId();
        ObjectId c = Util.newId();

        HeadSet.Builder hsb = new HeadSet.Builder(new HashSet<AnyObjectId>(Arrays.asList(a, b, c)));

        HeadSet x = new HeadSet(hsb);
        x.add(hsb, a);
        HeadSet y = new HeadSet(hsb);
        y.add(hsb, b);
        HeadSet z = new HeadSet(hsb);
        z.add(hsb, c);

        assertFalse(x.containsAll(y));
        assertFalse(y.containsAll(x));
        assertFalse(x.containsAny(y));
        assertFalse(y.containsAny(x));

        x.add(hsb, b);

        assertTrue(x.containsAll(y));
        assertFalse(y.containsAll(x));
        assertTrue(x.containsAny(y));
        assertTrue(y.containsAny(x));
    }
}
