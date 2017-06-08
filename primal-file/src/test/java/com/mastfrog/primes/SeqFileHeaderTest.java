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
package com.mastfrog.primes;

import com.mastfrog.primes.SeqFile.Mode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;

/**
 *
 * @author Tim Boudreau
 */
public class SeqFileHeaderTest {

    private SeqFile readFile;
    private SeqFile writeFile;
    private Path path;

    @Before
    public void setup() {
        path = Paths.get(System.getProperty("java.io.tmpdir"), getClass().getSimpleName() + "-" + System.currentTimeMillis());
    }

    @After
    public void teardown() throws Exception {
        try {
            try {
                if (readFile != null && readFile.isOpen()) {
                    readFile.close();
                }
            } finally {
                if (writeFile != null && writeFile.isOpen()) {
                    writeFile.close();
                }
            }
        } finally {
            if (Files.exists(path)) {
                Files.delete(path);
            }
        }
    }

    @Test
    public void testOpenReadWrite() throws Exception {
        SeqFileHeader headerToWrite = new SeqFileHeader(1, 136, 140, 60000);
        writeFile = new SeqFile(path, Mode.WRITE, Optional.of(headerToWrite));
        writeFile.updateCountAndSave(23, 9);
        assertEquals(23, writeFile.header().count());
        assertEquals(9, writeFile.header().maxOffset());
        writeFile.close();
        readFile = new SeqFile(path, Mode.READ, Optional.empty());
        SeqFileHeader read = readFile.header();
        assertNotNull(read);
        assertEquals(headerToWrite, read);
        assertEquals(23, read.count());
        assertEquals(9, read.maxOffset());
    }

}
