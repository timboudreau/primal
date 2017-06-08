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

import com.github.jinahya.bit.io.BitOutput;
import com.github.jinahya.bit.io.DefaultBitOutput;
import com.mastfrog.util.Exceptions;
import java.io.IOException;
import java.util.function.LongConsumer;

/**
 * Reads a sequence of numbers in order from a SeqFile starting at the
 * beginning, as bit sequences in lengths specified by the header.
 *
 * @author Tim Boudreau
 */
public class NumberSequenceWriter implements AutoCloseable, LongConsumer {

    private final SeqFile file;
    private long lastValue;
    private long count;
    private final BitOutput out;
    private final FileChannelByteOutput byteOut;

    public NumberSequenceWriter(SeqFile file) {
        this(file, 16384);
    }

    public NumberSequenceWriter(SeqFile file, int bufferSize) {
        this.file = file;
        out = new DefaultBitOutput<>(byteOut = new FileChannelByteOutput(
                file.channel(), bufferSize));
    }

    public long written() {
        return count;
    }
    
    public int maxOffset() {
        return maxOffset;
    }

    private int maxOffset = 0;
    public void write(long prime) throws IOException {
        if (prime == -1) {
            return;
        }
        SeqFileHeader header = file.header();
        if (count % header.offsetEntriesPerFullEntry() == 0) {
            out.writeLong(true, header.bitsPerFullEntry(), prime);
        } else {
            int offset = (int) (prime - lastValue);
            if (offset == 1) {
                offset = 0;
            } else {
                offset /= 2;
            }
            maxOffset = Math.max(maxOffset, offset);
            out.writeChar(header.bitsPerOffsetEntry(), (char) offset);
        }
        count++;
        lastValue = prime;
    }

    volatile boolean closed;

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            out.align(1);
            file.updateCountAndSave(count, maxOffset);
            try {
                byteOut.close();
            } catch (Exception ex) {
                if (ex instanceof IOException) {
                    throw ((IOException) ex);
                } else {
                    throw new IOException(ex);
                }
            }
        }
    }

    @Override
    public void accept(long value) {
        try {
            write(value);
        } catch (IOException ex) {
            Exceptions.chuck(ex);
        }
    }
}
