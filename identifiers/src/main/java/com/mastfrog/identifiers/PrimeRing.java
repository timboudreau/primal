/*
 * The MIT License
 *
 * Copyright 2017 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mastfrog.identifiers;

import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.collections.Longerator;
import com.mastfrog.util.thread.AtomicRoundRobin;
import java.util.Iterator;
import java.util.function.Predicate;

/**
 *
 * @author Tim Boudreau
 */
public final class PrimeRing implements Iterable<Long> {

    private final long[] primes;
    private final AtomicRoundRobin index1;
    private final AtomicRoundRobin index2;
    private final long bitmask;

    public PrimeRing(long[] primes) {
        this.primes = primes;
        index1 = new AtomicRoundRobin(primes.length);
        index2 = new AtomicRoundRobin(primes.length);
        index2.get();
        long bm = 0;
        for (int i = 0; i < primes.length; i++) {
            for (int j = 0; j < primes.length; j++) {
                bm |= primes[i] * primes[j];
            }
        }
        bitmask = bm;
    }

    @SuppressWarnings("empty-statement")
    public long nextPrimeMultiple() {
        int one = index1.next();
        int two;
        while ((two = index2.next()) == one);
        return primes[one] * primes[two];
    }

    public boolean pairs(Predicate<long[]> func) {
        final int total = primes.length;
        long[] testVals = new long[2];
        for (int i = 0; i < total; i++) {
            for (int j = 0; j < total; j++) {
                if (i == j) {
                    continue;
                }
                testVals[0] = primes[i];
                testVals[1] = primes[j];
                if (func.test(testVals)) {
                    return true;
                }
            }
        }
        return false;
    }

    public long match(long number) {
        // Flip the bits in the bitmask, which has all bits set that
        // are set in any of our prime multiples, zeroing out all of them.
        // If any are still set, we have a non-match.
        if ((number & ~bitmask) != 0) {
            return -1;
        }
        int matchCount = 0;
        long val1 = 0;
        for (int i = 0; i < primes.length; i++) {
            if (number % primes[i] == 0) {
                val1 = primes[i];
                matchCount++;
                if (matchCount > 1) {
                    return val1 * primes[i];
                }
            }
        }
        return -1;
    }

    @Override
    public Iterator<Long> iterator() {
        return CollectionUtils.toIterator(primes);
    }

    public Longerator longerator() {
        return CollectionUtils.toLongerator(primes);
    }
}
