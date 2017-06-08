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
package com.mastfrog.sieve.file;

import com.mastfrog.primes.NumberSequenceReader;
import com.mastfrog.primes.SeqFile;
import com.mastfrog.util.collections.Longerator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Tim Boudreau
 */
public class SieveMainTest {

    static {
        Main.unitTest = true;
    }

    @Test
    public void testBatches() throws Exception {
        String base = Long.toString(System.currentTimeMillis(), 36);
        Path batched = Paths.get(System.getProperty("java.io.tmpdir"), getClass().getName() + "-" + base + "-batched.seq");
        try {
            SieveMain.main("-m", "10000", "-o", batched.toString(), "-l", "-z", "1000", "-p", "-s");
            try (SeqFile sf = new SeqFile(batched)) {
//                assertEquals(1112, sf.header().count());
            }
        } finally {
            if (Files.exists(batched)) {
                Files.delete(batched);
            }
        }
    }

    @Test
    public void testTwoFiles() throws Exception {
        String base = Long.toString(System.currentTimeMillis(), 36);
        Path firstThousandFile = Paths.get(System.getProperty("java.io.tmpdir"), getClass().getName() + "-" + base + "-1.seq");
        Path secondThousandFile = Paths.get(System.getProperty("java.io.tmpdir"), getClass().getName() + "-" + base + "-2.seq");
        Path thirdThousandFile = Paths.get(System.getProperty("java.io.tmpdir"), getClass().getName() + "-" + base + "-3.seq");
        try {
            SieveMain.main("-m", "1000", "-o", firstThousandFile.toString(), "-l", "-z", "10000000", "-p", "-s");
            SieveMain.main("-m", "2000", "-o", secondThousandFile.toString(), "-l", "-z", "10000000", "-p", "-s", "-i", firstThousandFile.toString());
            SieveMain.main("-m", "2000", "-o", thirdThousandFile.toString(), "-l", "-z", "10000000", "-p", "-s", "-i", firstThousandFile.toString() + "," + secondThousandFile.toString());
            SeqFile file = new SeqFile(secondThousandFile, SeqFile.Mode.READ);
            assertEquals(135, file.header().count());
            assertEquals(1009, file.first());
            assertEquals(1999, file.last());
        } finally {
            if (Files.exists(firstThousandFile)) {
                Files.delete(firstThousandFile);
            }
            if (Files.exists(secondThousandFile)) {
                Files.delete(secondThousandFile);
            }
        }
    }

    @Test
    public void testOutputFiles() throws Exception {
        Path pth = Paths.get(System.getProperty("java.io.tmpdir"), getClass().getName() + "-" + System.currentTimeMillis() + ".seq");
        try {
            SieveMain.main("-m", "1000", "-o", pth.toString(), "-l", "-z", "10000000", "-p", "-s");
            assertTrue(Files.exists(pth));

            SeqFile file = new SeqFile(pth, SeqFile.Mode.READ);
            Longerator lngs = file.longerator();

            assertEquals("Wrong prime count for file", PRIMES_BELOW_1000.length, file.header().count());
            int ct = 0;
            while (lngs.hasNext()) {
                long val = lngs.next();
                assertEquals(PRIMES_BELOW_1000[ct], val);
                for (long i : new long[]{2, 3, 5, 7, 11, 13, 17, 19, 23}) {
                    if (val > i && val % i == 0) {
                        fail("At index " + ct + ", computed prime '" + val + "' is actually divisible by " + i);
                    }
                }
                ct++;
            }

            long val;
            NumberSequenceReader rdr = new NumberSequenceReader(file);
            while ((val = rdr.getAsLong()) != -1) {
//                System.out.println(val);
            }

        } finally {
            if (Files.exists(pth)) {
                Files.delete(pth);
            }
        }
    }

    static final long[] PRIMES_BELOW_1000 = new long[]{2,
        3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41,
        43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97,
        101, 103, 107, 109, 113, 127, 131, 137, 139, 149, 151, 157,
        163, 167, 173, 179, 181, 191, 193, 197, 199, 211, 223, 227,
        229, 233, 239, 241, 251, 257, 263, 269, 271, 277, 281, 283,
        293, 307, 311, 313, 317, 331, 337, 347, 349, 353, 359, 367,
        373, 379, 383, 389, 397, 401, 409, 419, 421, 431, 433, 439,
        443, 449, 457, 461, 463, 467, 479, 487, 491, 499, 503, 509,
        521, 523, 541, 547, 557, 563, 569, 571, 577, 587, 593, 599,
        601, 607, 613, 617, 619, 631, 641, 643, 647, 653, 659, 661,
        673, 677, 683, 691, 701, 709, 719, 727, 733, 739, 743, 751,
        757, 761, 769, 773, 787, 797, 809, 811, 821, 823, 827, 829,
        839, 853, 857, 859, 863, 877, 881, 883, 887, 907, 911, 919,
        929, 937, 941, 947, 953, 967, 971, 977, 983, 991, 997
    };
}
