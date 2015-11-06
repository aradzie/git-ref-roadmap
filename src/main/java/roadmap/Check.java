package roadmap;

import org.eclipse.jgit.lib.*;

import java.util.*;

/** Assertions for parameters and return values. */
public class Check {
    public @interface Nullable {
    }

    /**
     * Assert parameter is not null.
     *
     * @param v   A value.
     * @param <T> Value type.
     * @return The same value.
     * @throws NullPointerException If value is {@code null}.
     */
    public static <T> T notNull(T v) {
        if (v == null) {
            throw new NullPointerException();
        }
        return v;
    }

    /**
     * Assert parameter is not null.
     *
     * @param v A value.
     * @return The same value.
     * @throws NullPointerException If value is {@code null}.
     */
    public static String notNull(String v) {
        if (v == null) {
            throw new NullPointerException();
        }
        return v;
    }

    /**
     * Assert parameter is not null.
     *
     * @param v A value.
     * @return The same value.
     * @throws NullPointerException If value is {@code null}.
     */
    public static Date notNull(Date v) {
        if (v == null) {
            throw new NullPointerException();
        }
        return new Date(v.getTime()); // Get rid of OpenJPA proxies.
    }

    /**
     * Assert parameter is not null.
     *
     * @param v An object id.
     * @return Copy of object id.
     * @throws NullPointerException If value is {@code null}.
     */
    public static ObjectId notNull(AnyObjectId v) {
        if (v == null) {
            throw new NullPointerException();
        }
        return v.copy(); // Make instance GWT serializable.
    }

    /**
     * Assert parameter is not null or empty string.
     *
     * @param v A string value.
     * @return The same value.
     * @throws NullPointerException  If value is {@code null}.
     * @throws IllegalStateException If value is empty.
     */
    public static String notEmpty(String v) {
        if (v == null) {
            throw new NullPointerException();
        }
        if (v.isEmpty()) {
            throw new IllegalStateException();
        }
        return v;
    }

    /** Assert parameter &gt;= 0. */
    public static int notNegative(int v) {
        if (v < 0) {
            throw new IllegalArgumentException();
        }
        return v;
    }

    /** Assert parameter &gt; 0. */
    public static int positive(int v) {
        if (v < 1) {
            throw new IllegalArgumentException();
        }
        return v;
    }

    /** Assert parameter &lt;= 0. */
    public static int notPositive(int v) {
        if (v > 0) {
            throw new IllegalArgumentException();
        }
        return v;
    }

    /** Assert parameter &lt; 0. */
    public static int negative(int v) {
        if (v > -1) {
            throw new IllegalArgumentException();
        }
        return v;
    }

    public static int range(int v, int min, int max) {
        if (v < min || v > max) {
            throw new IllegalArgumentException();
        }
        return v;
    }

    public static Date date(Date d) {
        if (d == null) {
            return null;
        }
        return new Date(d.getTime()); // Get rid of OpenJPA proxies.
    }
}
