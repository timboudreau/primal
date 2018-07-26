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

import com.github.jinahya.bit.io.AbstractByteInput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 *
 * @author Tim Boudreau
 */
public class FileChannelByteInput extends AbstractByteInput<FileChannel> {
    private ByteBuffer buf;
    public FileChannelByteInput(FileChannel source) {
        this(source, 16384);
    }

    public FileChannelByteInput(FileChannel source, int bufferSize) {
        super(source);
        this.buf = ByteBuffer.allocateDirect(bufferSize);
    }

    private boolean done;
    private boolean first = true;

    public int read() throws IOException {
        if (done) {
            return -1;
        }
        if (first || !buf.hasRemaining()) {
            first = false;
            buf.rewind();
            int read = source.read(buf);
            done = read == -1;
            buf.flip();
        }
        return buf.get();
    }
}
