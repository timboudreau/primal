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

import com.mastfrog.util.preconditions.Checks;
import java.io.IOException;
import static java.lang.Math.floor;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.Arrays;

/**
 *
 * @author Tim Boudreau
 */
public class SeqFileHeader {

    private static final int HEADER_LENGTH = 20;
    private static final int SUPPORTED_VERSION = 1;
    private static final byte[] MAGIC = new byte[]{23, 42, 23};
    private final int offsetEntriesPerFullEntry;
    private final int bitsPerOffsetEntry;
    private final int bitsPerFullEntry;
    private final int version;
    private final int headerLength = HEADER_LENGTH;
    private final long fileSize;
    private long count;
    private int maxOffset;

    public SeqFileHeader(int bitsPerOffsetEntry, int bitsPerFullEntry, int offsetEntriesPerFullEntry, long fileSize, long count, int maxOffset) {
        Checks.nonNegative("bytesPerEntry", bitsPerOffsetEntry);
        Checks.nonNegative("frameHeaderLength", bitsPerFullEntry);
        Checks.nonNegative("entriesPerFrame", offsetEntriesPerFullEntry);
        this.offsetEntriesPerFullEntry = offsetEntriesPerFullEntry;
        this.bitsPerOffsetEntry = bitsPerOffsetEntry;
        this.bitsPerFullEntry = bitsPerFullEntry;
        this.version = SUPPORTED_VERSION;
        this.fileSize = fileSize;
        this.count = count;
        this.maxOffset = maxOffset;
    }

    public SeqFileHeader(int bitsPerOffsetEntry, int bitsPerFullEntry, int offsetEntriesPerFullEntry) {
        this(SUPPORTED_VERSION, bitsPerOffsetEntry, bitsPerFullEntry, offsetEntriesPerFullEntry);
    }

    SeqFileHeader(int version, int bitsPerOffsetEntry, int bitsPerFullEntry, int offsetEntriesPerFullEntry) {
        if (offsetEntriesPerFullEntry > 65535) {
            throw new IllegalArgumentException("entriesPerFrame must be <= 65535");
        }
        Checks.nonNegative("version", version);
        Checks.nonNegative("bytesPerEntry", bitsPerOffsetEntry);
        Checks.nonNegative("frameHeaderLength", bitsPerFullEntry);
        Checks.nonNegative("entriesPerFrame", offsetEntriesPerFullEntry);
        this.offsetEntriesPerFullEntry = offsetEntriesPerFullEntry;
        this.bitsPerOffsetEntry = bitsPerOffsetEntry;
        this.bitsPerFullEntry = bitsPerFullEntry;
        this.version = version;
        this.fileSize = 0;
    }

    public SeqFileHeader(SeekableByteChannel channel) throws IOException {
        long size = channel.size();
        if (size < headerLength) {
            throw new IOException("File not long enough even for header block "
                    + "- need headerLength bytes but length is " + channel.size());
        }
        long initialPos = channel.position();
        final ByteBuffer buf = ByteBuffer.allocate(HEADER_LENGTH);
        // read the version
        channel.read(buf);
        buf.flip();

        byte[] magic = new byte[3];
        buf.get(magic);
        if (!Arrays.equals(magic, MAGIC)) {
            throw new IOException("Magic sequence should be 23,42,23 but is "
                    + magic[0] + "," + magic[1] + "," + magic[2]
                    + " (at " + buf.position() + ")");
        }

        version = buf.get() & 0xFF;
        if (version != SUPPORTED_VERSION) {
            throw new IOException("Unsupported version " + version + " (at "
                    + (buf.position() + initialPos) + ")");
        }

        // read the entry length
        bitsPerOffsetEntry = buf.get() & 0xFF;
        if (bitsPerOffsetEntry == 0) {
            throw new IOException("Bits per offset entry may not be zero (at "
                    + (buf.position() + initialPos) + "): " + bitsPerOffsetEntry);
        }

        // read the frame header length
        bitsPerFullEntry = buf.get() & 0xFF;
        if (bitsPerFullEntry == 0) {
            throw new IOException("Bits per full entry may not be zero length (at "
                    + (buf.position() + initialPos) + "): " + bitsPerFullEntry);
        }
        // read the number of entries between frames
        offsetEntriesPerFullEntry = buf.getShort() & 0xFFFF;
        if (offsetEntriesPerFullEntry == 0) {
            throw new IOException("May not have zero entries per frame (at "
                    + (buf.position() + initialPos) + "): " + offsetEntriesPerFullEntry);
        }

        count = buf.getLong();
        if (count < 0) {
            throw new IOException("Negative entry count (at "
                    + (buf.position() + initialPos) + "): " + count);
        }

        maxOffset = buf.getInt();
        if (maxOffset < 0) {
            throw new IOException("Negative max offset (at "
                    + (buf.position() + initialPos) + "): " + maxOffset);
        }
        fileSize = size;
    }

    static class EntryPosition {

        final long framePosition;
        final int offsetIntoFrame;
        final int skipBitsToFrameTop;

        EntryPosition(long framePosition, int offsetIntoFrame, int skipBitsToFrameTop) {
            this.framePosition = framePosition;
            this.offsetIntoFrame = offsetIntoFrame;
            this.skipBitsToFrameTop = skipBitsToFrameTop;
        }

        @Override
        public String toString() {
            return "EntryPosition{" + "framePosition=" + framePosition + ", offsetIntoFrame=" + offsetIntoFrame + ", skipBitsToFrameTop=" + skipBitsToFrameTop + '}';
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 97 * hash + (int) (this.framePosition ^ (this.framePosition >>> 32));
            hash = 97 * hash + this.offsetIntoFrame;
            hash = 97 * hash + this.skipBitsToFrameTop;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final EntryPosition other = (EntryPosition) obj;
            if (this.framePosition != other.framePosition) {
                return false;
            }
            if (this.offsetIntoFrame != other.offsetIntoFrame) {
                return false;
            }
            if (this.skipBitsToFrameTop != other.skipBitsToFrameTop) {
                return false;
            }
            return true;
        }
    }

    public long estimatedCount(long fileSize) {
        long dataBytes = fileSize - headerLength;
        long dataBits = dataBytes * 8;
        long bitsPerFrame = bitsPerFullEntry + (bitsPerOffsetEntry * (offsetEntriesPerFullEntry - 1));

        long fullFrameCount = dataBits / bitsPerFrame;

        long entryCount = offsetEntriesPerFullEntry * fullFrameCount;

        long remainderBits = dataBits - (fullFrameCount * bitsPerFrame);

        if (remainderBits > 0) {
            if (remainderBits > bitsPerFullEntry) {
                entryCount++;
                remainderBits -= bitsPerFullEntry;
            }
            if (remainderBits > 0) {
                entryCount += remainderBits / bitsPerOffsetEntry;
            }
        }
        return entryCount;
    }

    EntryPosition positionOf(long index) {
        long bitsPerFrame = bitsPerFullEntry + (bitsPerOffsetEntry * (offsetEntriesPerFullEntry - 1));
        long entriesPerFrame = offsetEntriesPerFullEntry;
        long nearestFrame = index / entriesPerFrame;
        long bitOffsetOfNearestFrame = bitsPerFrame * nearestFrame;
        long bytePositionOfNearestFrame = headerLength + (bitOffsetOfNearestFrame / 8);
        int skipBits = (int) (bitOffsetOfNearestFrame % 8);
        int offsetIntoFrame = (int) (index - (nearestFrame * entriesPerFrame));
        return new EntryPosition(bytePositionOfNearestFrame, offsetIntoFrame, skipBits);
    }

    public SeqFileHeader updateCount(long newCount) {
        Checks.nonNegative("newCount", newCount);
        this.count = newCount;
        return this;
    }

    public SeqFileHeader updateMaxOffset(int maxOffset) {
        Checks.nonNegative("maxOffset", maxOffset);
        this.maxOffset = maxOffset;
        return this;
    }

    public int maxOffset() {
        return maxOffset;
    }

    public long count() {
        return count;
    }

    public SeqFileHeader updateCountAndSave(long newCount, int maxOffset, SeekableByteChannel channel) throws IOException {
        long oldCount = this.count;
        int oldMaxOffset = this.maxOffset;
        this.count = newCount;
        this.maxOffset = maxOffset;
        long oldPosition = channel.position();
        try {
            channel.position(0);
            write(channel);
//            channel.force(true);
        } catch (IOException ex) {
            this.maxOffset = oldMaxOffset;
            count = oldCount;
            throw ex;
        } finally {
            channel.position(oldPosition);
        }
        return this;
    }

    public int headerLength() {
        return headerLength;
    }

    public int bitsPerOffsetEntry() {
        return bitsPerOffsetEntry;
    }

    public int offsetEntriesPerFullEntry() {
        return offsetEntriesPerFullEntry;
    }

    public int bitsPerFullEntry() {
        return bitsPerFullEntry;
    }

    public int version() {
        return version;
    }

    public long dataLength() {
        return fileSize - headerLength;
    }

    public void write(SeekableByteChannel ch) throws IOException {
        ByteBuffer b = Boolean.getBoolean("primal.heapbuffers")
                ? ByteBuffer.allocate(HEADER_LENGTH)
                : ByteBuffer.allocateDirect(HEADER_LENGTH);
        b.put(MAGIC);
        b.put((byte) version);
        b.put((byte) bitsPerOffsetEntry);
        b.put((byte) bitsPerFullEntry);
        b.putShort((short) offsetEntriesPerFullEntry);
        b.putLong(count);
        b.putInt(maxOffset);
        b.flip();
        ch.write(b);
//        if (ch instanceof FileChannel) {
//            ((FileChannel) ch).force(true);
//        }
    }

    @Override
    public String toString() {
        return "SeqFileHeader{"
                + "offsetEntriesPerFullEntry=" + offsetEntriesPerFullEntry
                + ", bitsPerOffsetEntry=" + bitsPerOffsetEntry
                + ", bitsPerFullEntry=" + bitsPerFullEntry
                + ", count=" + count + '}';
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 17 * hash + this.offsetEntriesPerFullEntry;
        hash = 17 * hash + this.bitsPerOffsetEntry;
        hash = 17 * hash + this.bitsPerFullEntry;
        hash = 17 * hash + (int) (this.count ^ (this.count >>> 32));
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final SeqFileHeader other = (SeqFileHeader) obj;
        if (this.offsetEntriesPerFullEntry != other.offsetEntriesPerFullEntry) {
            return false;
        }
        if (this.bitsPerOffsetEntry != other.bitsPerOffsetEntry) {
            return false;
        }
        if (this.bitsPerFullEntry != other.bitsPerFullEntry) {
            return false;
        }
        if (this.count != other.count) {
            return false;
        }
        return true;
    }

    /**
     * Get the number of bits required to hold the passed number.
     *
     * @param n A number
     * @return The number of bits needed.
     */
    public static int bitsRequired(long n) {
        if (n == 1) {
            return 1;
        }
        n = (n - 1) / 2;
        return (int) floor(log2(n) + 1);
    }

    /**
     * Get the number of bits required to hold the passed offset (can be halved
     * since all prime gaps are even except 2-3).
     *
     * @param n An offset
     * @return A number of bits
     */
    public static int bitsRequiredForPrimeOffset(long n) {
        return (int) floor(log2(n / 2) + 1);
    }

    private static int log2(long bits) {
        if (bits == 0) {
            return 0;
        }
        return 63 - Long.numberOfLeadingZeros(bits);
    }
}
