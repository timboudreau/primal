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

import com.mastfrog.util.preconditions.Checks;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/**
 * A prime sieve, implemented over a bit set using Lucene's (long-indexed)
 * OpenBitSet. Has fairly substantial memory requirements, BUT you can sieve
 * arbitrarily large primes in finite memory by running it repeatedly, and then
 * feeding the results of previous runs into it to prepare the bit set for
 * subsequent primes.
 *
 * @author Tim Boudreau
 */
public class Sieve {

    /**
     * Sieve for primes, starting from 2.
     *
     * @param maxValue A value above which no primes should be computed and
     * sieving should terminate
     * @param consumer A consumer that will be passed prime numbers as they are
     * sieved
     * @param total The maximum number of primes to generate (may be less if
     * max value is reached)
     *
     */
    public static long sieve(long maxValue, LongConsumer consumer, long total) {
        Checks.notNull("consumer", consumer);
        Checks.nonNegative("maxValue", maxValue);
        return new SieveImpl(maxValue, consumer, total).sieve();
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
     * @return The last prime generated
     */
    public static long sieve(long startValue, LongSupplier preceding, LongConsumer consumer, long maxValue, long total) {
        Checks.notNull("preceding", preceding);
        Checks.notNull("consumer", consumer);
        Checks.nonNegative("startValue", startValue);
        Checks.nonNegative("maxValue", maxValue);
        if (maxValue <= startValue) {
            throw new IllegalArgumentException("Start value must be < max value - " + startValue + " > " + maxValue);
        }
        return new SieveImpl(startValue, preceding, consumer, maxValue, total).sieve();
    }

}
