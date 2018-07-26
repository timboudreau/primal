/*
 * The MIT License
 *
 * Copyright 2018 Tim Boudreau.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.function.LongConsumer;
import org.junit.Assert;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class NativeSieveMainTest {

    @Test(timeout = 1000)
    public void testReadLongStream() throws Exception {
        testOne(11532L, 23, 52, 2390239, 2837194L, 234, 322);
        testOne(3203920390134L, 232092303, 523902930293902L, 24348484390239L, 2837194L, 234, 322, 3290039011111L);
    }

    @Test(timeout=1000)
    public void sanityCheckParse() {
        long val = NativeSieveMain.parseLong("30557b".getBytes(), 0, 5);
        assertEquals(30557, val);
        val = NativeSieveMain.parseLong("30557".getBytes(), 1, 4);
        assertEquals(557, val);
    }

    @Test(timeout = 1000)
    public void sanityCheck() throws IOException {
        long[] arr = {5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 10005, 10006, 10007};
        InputStream in = new FakeInputStream(5, arr);
        StringBuilder expect = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            expect.append(arr[i]).append('\n');
        }
        byte[] buf = new byte[10];
        int read;
        int iter = 0;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while ((read = in.read(buf)) > 0) {
            System.out.println(iter++ + " read " + read + " bytes: " + new String(buf, 0, read));
            out.write(buf, 0, read);
        }
        assertArrayEquals(out.toByteArray(), expect.toString().getBytes(US_ASCII));
    }

    private void testOne(long... longs) throws Exception {
        int minBatchSize = 0;
        int totalLength = 0;
        for (long l : longs) {
            String s = Long.toString(l);
            minBatchSize = Math.max(s.length() + 1, minBatchSize);
            totalLength += s.length() + 1;
        }
        for (int batchSize = minBatchSize; batchSize < totalLength + 3; batchSize++) {
            testOne(batchSize, longs);
        }
    }

    private void testOne(int batchSize, long... longs) throws Exception {
        FakeInputStream fake = new FakeInputStream(batchSize, longs);
        String debString = new String(fake.bytes, US_ASCII);
        final long[] got = new long[longs.length];
        LongConsumer cons = new LongConsumer() {
            int cursor = 0;

            int strlen = 0;

            @Override
            public void accept(long value) {
                got[cursor++] = value;
                assertEquals("BatchSize " + batchSize + " expecting "
                        + debString.substring(strlen)
                        + " Mismatch at " + (cursor - 1), longs[cursor - 1], got[cursor - 1]);
                strlen += Long.toString(value).length() + 1;
            }
        };
        NativeSieveMain.parse(null, fake, cons, batchSize, longs.length + 2);
        Assert.assertArrayEquals(longs, got);
    }

    static final class FakeInputStream extends InputStream {

        byte[] bytes;
        int cursor = 0;
        private final int batchSize;

        FakeInputStream(int batchSize, long... longs) {
            this.batchSize = batchSize;
            StringBuilder sb = new StringBuilder();
            for (long l : longs) {
                if (sb.length() != 0) {
                    sb.append("\n");
                }
                sb.append(l);
            }
            sb.append('\n');
            String toStream = sb.toString();
            this.bytes = toStream.getBytes(UTF_8);
        }

        @Override
        public synchronized void reset() throws IOException {
            super.reset();
            cursor = 0;
        }

        @Override
        public void close() throws IOException {
            super.close();
            cursor = 0;
        }

        @Override
        public int available() throws IOException {
            return bytes.length;
        }

        public byte[] readAllBytes() throws IOException {
            return bytes;
        }

        @Override
        public int read() throws IOException {
            if (cursor >= bytes.length) {
                return -1;
            }
            return bytes[cursor++];
        }

        @Override
        public int read(byte[] b) throws IOException {
            if (cursor >= bytes.length) {
                return -1;
            }
            int amt = batchSize;
            if (cursor + amt > bytes.length) {
                amt = bytes.length - cursor;
            }
//            System.out.println("return bytes " + cursor + " thru " + (cursor + amt) + " " + csFromBytes(bytes, cursor, amt));
            System.arraycopy(bytes, cursor, b, 0, amt);
            cursor += amt;
            return amt;
        }
    }
}
