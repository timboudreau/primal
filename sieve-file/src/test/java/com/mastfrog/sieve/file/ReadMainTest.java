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

import com.mastfrog.primes.SeqFile;
import com.mastfrog.primes.SeqFileHeader;
import com.mastfrog.util.Strings;
import com.mastfrog.util.collections.Longerator;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Tim Boudreau
 */
public class ReadMainTest {

    static {
        Main.unitTest = true;
    }

    private void assertPrime(long val) {
        for (long prime : SieveMainTest.PRIMES_BELOW_1000) {
            if (val > prime) {
                if (val % prime == 0) {
                    fail("Not prime: " + val);
                }
            }
        }
    }

    @Test
    public void testMergeFiles() throws Throwable {
        String base = Long.toString(System.currentTimeMillis(), 36);
        Path firstThousandFile = Paths.get(System.getProperty("java.io.tmpdir"), getClass().getName() + "-" + base + "-1.seq");
        Path secondThousandFile = Paths.get(System.getProperty("java.io.tmpdir"), getClass().getName() + "-" + base + "-2.seq");
        Path mergedFile = Paths.get(System.getProperty("java.io.tmpdir"), getClass().getName() + "-" + base + "-3.seq");
        try {
            SieveMain.main("-m", "1000", "-o", firstThousandFile.toString(), "-l", "-z", "10000000", "-p", "-s");
            SieveMain.main("-m", "2000", "-o", secondThousandFile.toString(), "-l", "-z", "10000000", "-p", "-s", "-i", firstThousandFile.toString());
            assertTrue(Files.exists(firstThousandFile));
            assertTrue(Files.exists(secondThousandFile));

            ReadMain.main("-i", firstThousandFile.toString());
            ReadMain.main("-i", Strings.join(',', firstThousandFile.toString(), secondThousandFile.toString()), "-o", mergedFile.toString());
            assertTrue(Files.exists(mergedFile));
            try (SeqFile second = new SeqFile(secondThousandFile)) {
                Longerator lon2 = second.longerator();
                while (lon2.hasNext()) {
                    assertPrime(lon2.next());
                }
            }
            try (SeqFile first = new SeqFile(firstThousandFile)) {
                Longerator lon1 = first.longerator();
                try (SeqFile second = new SeqFile(secondThousandFile)) {
                    Longerator lon2 = second.longerator();
                    try (SeqFile merged = new SeqFile(mergedFile)) {
                        assertEquals(first.header().count() + second.header().count(), merged.header().count());
                        Longerator lon = merged.longerator();
                        long ix = 0;
                        long last = 0;
                        while (lon.hasNext()) {
                            long expect = lon1.hasNext() ? lon1.next() : lon2.next();
                            long got = lon.next();
                            System.out.println("Expect " + expect + " got " + lon.next());
                            assertPrime(got);
                            assertNotEquals(last, got);
                            assertEquals("Mismatch at " + ix + " prev was " + last, expect, got);
                            last = got;
                            ix++;
                        }
                    }
                }
            }

        } finally {
            if (Files.exists(firstThousandFile)) {
                Files.delete(firstThousandFile);
            }
            if (Files.exists(secondThousandFile)) {
                Files.delete(secondThousandFile);
            }

        }
    }
    
    public static void main(String[] args) {
        System.out.println(SeqFileHeader.bitsRequired(959390900839L));
    }
}
