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

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.LongConsumer;

/**
 *
 * @author Tim Boudreau
 */
public final class AsyncNumberSequenceWriter implements AutoCloseable, LongConsumer {

    private static final int DEFAULT_BATCH_SIZE = 256;
    private final NumberSequenceWriter realWriter;
    private final ExecutorService threadPool;
    private LongBuffer buffer;
    private final LinkedTransferQueue<LongBuffer> xfer = new LinkedTransferQueue<>();
    private final Deliverer deliverer = new Deliverer();
    private boolean createdThreadPool;
    private long count;
    private int batchSize;

    public AsyncNumberSequenceWriter(NumberSequenceWriter realWriter) {
        this(realWriter, Executors.newSingleThreadExecutor());
        createdThreadPool = true;
    }

    public AsyncNumberSequenceWriter(NumberSequenceWriter realWriter, ExecutorService threadPool) {
        this(realWriter, threadPool, DEFAULT_BATCH_SIZE);
    }
    public AsyncNumberSequenceWriter(NumberSequenceWriter realWriter, ExecutorService threadPool, int batchSize) {
        this.realWriter = realWriter;
        this.threadPool = threadPool;
        buffer =  LongBuffer.allocate(batchSize);
        threadPool.submit(deliverer);
        this.batchSize = batchSize;
    }

    @Override
    public synchronized void close() throws Exception {
        try {
            deliverer.shutdown();
        } finally {
            if (createdThreadPool) {
                threadPool.shutdown();
            }
            realWriter.close();
        }
    }
    
    public long written() {
        return count;
    }
    
    public long committed() {
        return realWriter.written();
    }

    @Override
    public synchronized void accept(long value) {
        if (deliverer.shutdown) {
            return;
        }
        buffer.put(value);
        count++;
        if (!buffer.hasRemaining()) {
            LongBuffer old = buffer;
            buffer = LongBuffer.allocate(batchSize);
            xfer.offer(old);
        }
    }

    class Deliverer implements Callable<Void> {

        private final CountDownLatch exit = new CountDownLatch(1);
        private volatile Thread thread;
        volatile boolean shutdown;

        void shutdown() throws InterruptedException {
            shutdown = true;
            if (thread != null) {
                thread.interrupt();
                waitForLoopExit();
                List<LongBuffer> buffers = new ArrayList<>(5);
                xfer.drainTo(buffers);
                for (LongBuffer buf : buffers) {
                    writeBuffer(buf);
                }
                if (buffer.position() > 0) {
                    writeBuffer(buffer);
                }
            }
        }

        void waitForLoopExit() throws InterruptedException {
            exit.await(20, TimeUnit.SECONDS);
        }

        private void writeBuffer(LongBuffer buf) {
            buf.flip();
            while (buf.hasRemaining()) {
                long val = buf.get();
                realWriter.accept(val);
            }
        }

        @Override
        public Void call() throws Exception {
            thread = Thread.currentThread();
            try {
                for (;;) {
                    writeBuffer(xfer.take());
                    if (Thread.interrupted()) {
                        return null;
                    }
                }
            } catch (InterruptedException ex) {
                //do nothing, expected
            } finally {
                exit.countDown();
            }
            return null;
        }

    }

}
