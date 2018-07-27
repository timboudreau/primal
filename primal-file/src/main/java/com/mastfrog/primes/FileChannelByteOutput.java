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

import com.github.jinahya.bit.io.AbstractByteOutput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;

/**
 *
 * @author Tim Boudreau
 */
public class FileChannelByteOutput extends AbstractByteOutput<SeekableByteChannel> implements AutoCloseable {

    private final ByteBuffer buf;
    private int written = 0;

    public FileChannelByteOutput(SeekableByteChannel target) {
        this(target, 512, Boolean.getBoolean("primal.heapbuffers"));
    }

    public FileChannelByteOutput(SeekableByteChannel target, int bufferSize) {
        this(target, bufferSize, Boolean.getBoolean("primal.heapbuffers"));
    }

    public FileChannelByteOutput(SeekableByteChannel target, int bufferSize, boolean heap) {
        super(target);
        buf = heap ? ByteBuffer.allocate(bufferSize) : ByteBuffer.allocateDirect(bufferSize);
    }

    @Override
    public synchronized void write(int value) throws IOException {
        buf.put((byte) value);
        written++;
        if (!buf.hasRemaining()) {
            flush();
        }
    }

    public long written() {
        return written;
    }

    private synchronized void flush() throws IOException {
        buf.flip();
        target.write(buf);
        buf.rewind();
        written = 0;
    }

    @Override
    public synchronized void close() throws Exception {
        if (buf.position() > 0) {
            flush();
        }
        if (target instanceof FileChannel) {
            ((FileChannel) target).force(true);
        }
        target.close();
    }

}
