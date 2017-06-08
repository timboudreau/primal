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

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
public class Ids {

    private final IdsConfig config;

    @Inject
    public Ids(IdsConfig config) {
        this.config = config;
    }
    
    long lastIndexValue; //for tests
    
    long lastIndexValue() {
        return lastIndexValue;
    }
    
    private long circularShift(long bits, int by) {
        by = by % 64;
        return (bits >>> by) | (bits << (Long.SIZE - by));
    }
    
    public long nextId() {
        long base = config.primes().nextPrimeMultiple();
//        System.out.println("Base pair " + base);
        long index = lastIndexValue = config.nextIndexValue();
//        base *= index;
//        System.out.println(" Times index " + index + " = " + base);
        long xor = config.xorValue();
        base = base ^ xor;
//        System.out.println(" Xor with " + xor + " = " + base);
        base = circularShift(base, config.rotateBy());
//        System.out.println(" Rotate 21 = " + base);
        return base;
    }
    
    public long match(long id) {
//        System.out.println("Match " + id);
        id = circularShift(id, -config.rotateBy());
//        System.out.println("Rotate -21 = " + id);
        id = id ^ config.xorValue();
//        System.out.println("  un-xor to " + id);
        long divideBy = config.primes().match(id);
//        System.out.println("  divide by " + divideBy);
        if (divideBy == -1) {
//            System.out.println("    bail...no match");
            return -1;
        }
        long index = id / divideBy;
//        System.out.println("  return index " + index);
        return Math.abs(index);
    }
}
