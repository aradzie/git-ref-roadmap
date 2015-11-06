package roadmap.graph;

import org.junit.*;
import roadmap.util.*;

import java.util.*;

import static org.junit.Assert.*;

public class RefNodeSetTest {
    @Test public void set()
            throws Exception {
        RefGraph.Node a = new RefGraph.Node(Util.newId());
        RefGraph.Node b = new RefGraph.Node(Util.newId());
        RefGraph.Node c = new RefGraph.Node(Util.newId());

        RefNodeSet set = new RefNodeSet();
        assertEquals(0, set.size());
        assertTrue(set.isEmpty());
        assertFalse(set.iterator().hasNext());

        assertTrue(set.add(a));
        assertFalse(set.add(a));
        assertTrue(set.add(b));
        assertFalse(set.add(b));
        assertTrue(set.add(c));
        assertFalse(set.add(c));

        assertEquals(3, set.size());
        assertFalse(set.isEmpty());
        assertTrue(set.contains(a));
        assertTrue(set.contains(b));
        assertTrue(set.contains(c));
        assertFalse(set.contains(new RefGraph.Node(Util.newId())));

        Iterator<RefGraph.Node> it = set.iterator();
        while (it.hasNext()) {
            RefGraph.Node next = it.next();
            assertTrue(a.equals(next)
                    || b.equals(next)
                    || c.equals(next));
            it.remove();
        }

        assertEquals(0, set.size());
        assertTrue(set.isEmpty());
        assertFalse(set.iterator().hasNext());
        assertFalse(set.contains(a));
        assertFalse(set.contains(b));
        assertFalse(set.contains(c));

        assertTrue(set.add(a));
        assertFalse(set.add(a));
        assertTrue(set.add(b));
        assertFalse(set.add(b));
        assertTrue(set.add(c));
        assertFalse(set.add(c));

        assertEquals(3, set.size());
        assertFalse(set.isEmpty());
        assertTrue(set.contains(a));
        assertTrue(set.contains(b));
        assertTrue(set.contains(c));
        assertFalse(set.contains(new RefGraph.Node(Util.newId())));

        assertTrue(set.remove(c));
        assertFalse(set.remove(c));
        assertEquals(2, set.size());
        assertFalse(set.contains(c));

        assertTrue(set.remove(b));
        assertFalse(set.remove(b));
        assertEquals(1, set.size());
        assertFalse(set.contains(b));

        assertTrue(set.remove(a));
        assertFalse(set.remove(a));
        assertEquals(0, set.size());
        assertFalse(set.contains(a));

        assertTrue(set.add(a));
        assertFalse(set.add(a));
        assertTrue(set.add(b));
        assertFalse(set.add(b));
        assertTrue(set.add(c));
        assertFalse(set.add(c));

        set.clear();

        assertEquals(0, set.size());
        assertTrue(set.isEmpty());
        assertFalse(set.iterator().hasNext());
        assertFalse(set.contains(a));
        assertFalse(set.contains(b));
        assertFalse(set.contains(c));

        Set<RefGraph.Node> ref = new HashSet<>();
        for (int n = 0; n < 1000; n++) {
            RefGraph.Node node = new RefGraph.Node(Util.newId());
            assertTrue(ref.add(node));
            assertFalse(ref.add(node));
            assertTrue(set.add(node));
            assertFalse(set.add(node));
        }

        assertEquals(ref, set);

        assertTrue(set.removeAll(ref));

        assertTrue(set.isEmpty());

        assertTrue(set.addAll(ref));

        assertEquals(ref, set);

        it = set.iterator();
        while (it.hasNext()) {
            it.next();
            it.remove();
        }

        assertTrue(set.isEmpty());
    }
}
