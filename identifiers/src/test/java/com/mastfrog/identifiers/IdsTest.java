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

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

/**
 *
 * @author Tim Boudreau
 */
public class IdsTest {

    Ids ids;

    @Before
    public void setup() {
        ids = new Ids(new IdsConfigImpl());
    }
    
    @Test
    public void testPrimeRing() {
        PrimeRing ring = new PrimeRing(new long[] {3, 5, 7, 11, 13});
        ring.pairs(new Predicate<long[]>(){
            @Override
            public boolean test(long[] t) {
                System.out.println(t[0] + " * " + t[1] + " = " + (t[0] * t[1]));
                return false;
            }
        });
    }

    @Test(timeout = 1000)
    public void testEncodeDecode() {
        for (int i = 0; i < 100; i++) {
            long id = ids.nextId();
            System.out.println("ID " + id + "\n");
            System.out.println(id);
            long index = assertMatch(id);
//            assertEquals(ids.lastIndexValue(), index);
            assertNonMatch(id + 1);
            assertNonMatch(id * 2);
            System.out.println("INDEX " + index + "\n\n");
        }
    }

    private long assertMatch(long id) {
        long val = ids.match(id);
        assertNotEquals(-1L, val);
        return val;
    }

    private void assertNonMatch(long id) {
        assertEquals(-1L, ids.match(id));
    }

    static class IdsConfigImpl implements IdsConfig {

        private final PrimeRing ring = new PrimeRing(new long[]{1117, 7, 15923, 995237, 50773});
        private final AtomicLong indices = new AtomicLong(2);

        @Override
        public PrimeRing primes() {
            return ring;
        }

        @Override
        public long xorValue() {
            return Long.MAX_VALUE / 3;
        }

        @Override
        public long nextIndexValue() {
            return indices.getAndIncrement();
        }

        @Override
        public int rotateBy() {
            return 23;
        }
    }

}
