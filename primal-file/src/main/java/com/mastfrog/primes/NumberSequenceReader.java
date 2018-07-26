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

import com.github.jinahya.bit.io.BitInput;
import com.github.jinahya.bit.io.DefaultBitInput;
import com.mastfrog.util.preconditions.Exceptions;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads a sequence of numbers in order from a SeqFile starting at the
 * beginning.
 *
 * @author Tim Boudreau
 */
public final class NumberSequenceReader implements LongSupplier {

    private final SeqFile file;
    private long lastValue;
    private long count;
    private final BitInput in;
    long cumulativeBitsRead;

    public NumberSequenceReader(SeqFile file) {
        this.file = file;
        cumulativeBitsRead = file.header().headerLength() * 8;
        try {
            file.channel().position(file.header().headerLength());
        } catch (IOException ex) {
            Exceptions.chuck(ex);
        }
        in = new DefaultBitInput<>(new FileChannelByteInput(file.channel()));
    }

    public NumberSequenceReader(SeqFile file, BitInput input, long cumulativeBits, long count, long lastValue) {
        this.file = file;
        this.in = input;
        this.cumulativeBitsRead = cumulativeBits;
        this.count = count;
        this.lastValue = lastValue;
    }

    public String toString() {
        try {
            return "Reader at " + count + " last value " + lastValue + " in " + file.header() + " bits read " + cumulativeBitsRead
                    + " channel position " + file.channel().position();
        } catch (IOException ex) {
            Logger.getLogger(NumberSequenceReader.class.getName()).log(Level.SEVERE, null, ex);
            return "Reader at " + count + " last value " + lastValue + " in " + file.header() + " bits read " + cumulativeBitsRead
                    + " channel position unreadable";
        }
    }

    long pos() throws IOException { // for tests
        return file.channel().position();
    }

    boolean debug;

    public long next() throws IOException {
        if (count >= file.header().count()) {
            return -1;
        }
        boolean isOffsetEntry = false;
        try {
            isOffsetEntry = count % file.header().offsetEntriesPerFullEntry() == 0;
            if (isOffsetEntry) {
                long readValue = in.readLong(true, file.header().bitsPerFullEntry());
                cumulativeBitsRead += file.header().bitsPerFullEntry();
                lastValue = file.decodeFull(readValue);
//                if (readValue == 0) {
//                    lastValue = 2;
//                } else {
//                    lastValue = (readValue * 2) + 1;
//                }
                if (debug) {
                    System.out.println("READ FULL ENTRY AT " + count + " for  " + lastValue + " as " + readValue);
                }
            } else {
                int readOffset = in.readInt(true, file.header().bitsPerOffsetEntry());
                int offset = file.decodeOffset(readOffset);
                if (debug) {
                    System.out.println("Read gap entry " + count + " for " + (lastValue + offset) + " realGap " + offset + " read as " + readOffset);
                }

                lastValue += offset;
                cumulativeBitsRead += file.header().bitsPerOffsetEntry();
            }
            count++;
//            assert lastValue % 2 != 0 || lastValue == 2 : " value in " + this.file + " is divisible by 2: " + lastValue;
//            assert lastValue % 3 != 0 || lastValue == 3 : " value in " + this.file + " is divisible by 3: " + lastValue;;
            return lastValue;
        } catch (BufferUnderflowException ex) {
            throw new IOException("Ran out of data reading element " + count
                    + (isOffsetEntry ? " (offset entry) " : "(full entry)")
                    + " in file of " + file.channel().size() + " after reading "
                    + cumulativeBitsRead + "(approx " + (cumulativeBitsRead / 8) + " bytes)",
                    ex);
        }
    }

    long last() {
        return lastValue;
    }

    public long count() {
        return count;
    }

    @Override
    public long getAsLong() {
        try {
            return next();
        } catch (BufferUnderflowException e1) {
            return -1;
        } catch (IOException ex) {
            return Exceptions.chuck(ex);
        }
    }
}
