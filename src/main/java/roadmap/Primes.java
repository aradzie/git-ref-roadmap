package roadmap;

import java.util.*;

/** Generates prime numbers to use in hash tables. */
public class Primes {
    private static final int[] PRIMES;

    static {
        BitSet primes = primes(10000);
        PRIMES = new int[primes.cardinality()];
        int p = 0;
        int l = primes.length();
        for (int n = 0; n < l; n++) {
            if (primes.get(n)) {
                PRIMES[p++] = n + 2;
            }
        }
    }

    /**
     * Find next prime number after the specified one.
     *
     * @param n A number.
     * @return The next prime number after the specified one.
     */
    public static int nextPrime(int n) {
        int i = Arrays.binarySearch(PRIMES, n);
        if (i >= 0) {
            if (i < PRIMES.length - 1) {
                return PRIMES[i + 1];
            }
            else {
                return nextPrimeSlow(n);
            }
        }
        else {
            return PRIMES[-i - 1];
        }
    }

    private static int nextPrimeSlow(int i) {
        // Round to the next odd number.
        i |= 1;
        // Probe all odd numbers.
        while (true) {
            // Test if the current odd number is prime by finding divider.
            int j;
            for (j = 3; j * j <= i; j += 2) {
                if (i % j == 0) {
                    break;
                }
            }
            // No divider found, this is prime number.
            if (j * j > i) {
                return i;
            }
            i += 2;
        }
    }

    /**
     * To find all the prime numbers less than or equal
     * to a given integer <em>n</em> by Eratosthenes' method:
     *
     * <ol>
     * <li>Create a list of consecutive integers from two
     * to <em>n</em>: (2, 3, 4, ..., <em>n</em>).</li>
     * <li>Initially, let <em>p</em> equal 2, the first prime number.</li>
     * <li>Strike from the list all multiples of <em>p</em> less than
     * or equal to <em>n</em>: (2<em>p</em>, 3<em>p</em>, 4<em>p</em>, ...)</li>
     * <li>Find the first number remaining on the list after <em>p</em>
     * (this number is the next prime); replace <em>p</em> with this number.</li>
     * <li>Repeat steps 3 and 4 until <em>p<sup>2</sup></em> is greater than <em>n</em>.
     * All the remaining numbers in the list are prime.</li>
     * <ol>
     *
     * @param n Find all prime numbers up to this number.
     * @return Bit set where only bits set are those for prime numbers,
     *         starting from 2 (0th bit - 2, 1st bit - 3, etc).
     */
    private static BitSet primes(int n) {
        BitSet primes = new BitSet(n - 2);
        primes.set(0, n - 2);
        int p = 2;
        while (p * p <= n) {
            for (int x = p + p; x < n; x = x + p) {
                primes.clear(x - 2);
            }
            for (int x = p + 1; x < n; x++) {
                if (primes.get(x - 2)) {
                    p = x;
                    break;
                }
            }
        }
        return primes;
    }
}
