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
package com.mastfrog.primal.sieve;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import org.apache.lucene.util.OpenBitSet;

/**
 * A prime sieve, implemented over a bit set using Lucene's (long-indexed)
 * OpenBitSet. Has fairly substantial memory requirements, BUT you can sieve
 * arbitrarily large primes in finite memory by running it repeatedly, and then
 * feeding the results of previous runs into it to prepare the bit set for
 * subsequent primes.
 *
 * @author Tim Boudreau
 */
class SieveImpl {

    private final OpenBitSet set;
    private final long maxValue;

    private final LongConsumer consumer;
    private final LongSupplier preceding;
    private final long startValue;
    private long lastValue;
    private long total;

    /**
     * Sieve for primes, starting from 2.
     *
     * @param maxValue A value above which no primes should be computed and
     * sieving should terminate
     * @param consumer A consumer that will be passed prime numbers as they are
     * sieved
     */
    SieveImpl(long maxValue, LongConsumer consumer, long total) {
        this.total = total;
        this.maxValue = maxValue + 1;
        set = new OpenBitSet(maxValue);
        set.flip(2, maxValue);
//        int bitsForX = bitsRequired(maxValue);
//        writer = new NumberSequenceWriter(new SeqFile(path, Mode.OVERWRITE,
//                Optional.of(new SeqFileHeader(11, bitsForX, FRAME_LENGTH))), 16384);
        this.consumer = consumer;
        this.preceding = null;
        this.startValue = 0;
    }

    /**
     * Sieve more primes based on the output of a previous run, requiring only
     * memory to store from the last value provided by the passed LongSupplier
     * to the passed maximum value.
     * <p>
     * The passed startValue <b>must</b> the same number as the last
     * non-negative number output by the passed LongSupplier (it should pass -1
     * to indicate it is done), and that supplier must provide <i>every single
     * prime number from zero up to the last</i> or you will get incorrect
     * results. While it would be convenient to simply get the start value from
     * the LongSupplier, it is not so fast to read a few billion primes from a
     * file <i>twice</i> just to provide the convenience of one less constructor
     * argument.
     * <p>
     * @param startValue The last prime number the passed LongSupplier will
     * provide
     * @param preceding A LongSupplier which will provide every prime number up
     * to the starting point. Returning a value of -1 indicates the last value
     * has been sent.
     * @param consumer Gets passed prime numbers as they are sieved
     * @param maxValue A value above which no primes should be computed and
     * sieving should terminate
     */
    SieveImpl(long startValue, LongSupplier preceding, LongConsumer consumer, long maxValue, long total) {
        this.total = total;
        this.preceding = preceding;
        this.consumer = consumer;
        this.maxValue = maxValue + 1;
        this.startValue = startValue;
        long bitCount = (maxValue - startValue) + 1;
        set = new OpenBitSet(bitCount);
        set.flip(1, bitCount);
    }

    private long doPrep() {
        long i = -1;
        long curr;
        long prev = -1;
        long index = 0;
        boolean first = true;
        while ((curr = preceding.getAsLong()) != -1) {
            if (first && curr != 2) {
                throw new IllegalStateException("First prime is 2, but was passed " + curr + " first; all"
                        + " primes up to the starting point must be passed here or sieving will "
                        + "produce wrong results.");
            }
            first = false;
            for (long ll : new long[]{2, 3, 5, 7}) {
                if (curr > ll && (curr % ll) == 0) {
                    throw new IllegalArgumentException("Preceding primes contains non-prime value: "
                            + curr + " at " + index + " previous value was " + prev);
                }
            }

            if (curr >= maxValue) {
                throw new IllegalArgumentException("Max value " + maxValue + " is less than "
                        + curr
                        + "which was provided by the preceding primes supplier "
                        + "at index" + index + " - would produce "
                        + "no primes.");
            }
            if (prev > curr) {
                throw new IllegalStateException(preceding + " produced out-of-order numbers "
                        + prev + " followed by " + curr);
            }
            i = curr;
            sievePrep(i);
            prev = curr;
            index++;
        }
        if (i == -1) {
            throw new IllegalStateException("Supplier produced no values");
        }
        if (i != startValue) {
            throw new IllegalArgumentException("Start value is " + startValue + " but it must be the "
                    + "last output value from the preceding sequence, which was " + i);
        }
        return i;
    }

    interface LongInOutFunction {

        long process(long in);
    }

    private final LongInOutFunction innerFromZero = new LongInOutFunction() {
        @Override
        public long process(long in) {
            return sieveInner(in);
        }
    };

    private final LongInOutFunction innerWithSupplier = new LongInOutFunction() {
        @Override
        public long process(long in) {
            return sieveInnerWithStartOffset(in);
        }
    };

    private void sievePrep(final long i) {
        long max = maxValue;
        long div = startValue / i;
        long nearestMultipleBelow = div == 0 ? i * 2 : (div + 1) * i;
        for (long j = nearestMultipleBelow; j < max; j += i) {
            if (j >= startValue) {
                clear(j);
            }
        }
    }

    long count;
    public long sieve() {
        long i = 2;
        LongInOutFunction inner;
        if (preceding != null) {
            inner = innerWithSupplier;
            i = doPrep();
        } else {
            inner = innerFromZero;
        }
        do {
            i = inner.process(i);
            if (total != -1L) {
                if (++count == total) {
                    break;
                }
            }
        } while (i > 0);
        consumer.accept(-1);
        return lastValue;
    }

    private long sieveInnerWithStartOffset(long i) {
        if (i != startValue) {
            consumer.accept(i);
            lastValue = i;
        }
        long doubleI = i * 2;
        long max = maxValue;
        for (long j = doubleI; j < max; j += i) {
            clear(j);
        }
        long lastI = i;
        i = nextSetBit(i + 1);
        if (i < lastI) {
            return -1;
        }
        return i;
    }

    private void clear(long i) {
        set.fastClear(i - startValue);
    }

    private long nextSetBit(long i) {
        long bitPosition = i - startValue;
        long result = set.nextSetBit(bitPosition) + startValue;
        return result;
    }

    private long sieveInner(long i) {
        consumer.accept(i);
        lastValue = i;
        long doubleI = i * 2;
        long max = maxValue - i;
        for (long j = doubleI; j < max; j += doubleI) {
            set.fastClear(j);
            set.fastClear(j + i);
        }
        i = set.nextSetBit(i + 1);
        return i;
    }

    public String toString() {
        return set.toString();
    }
}
