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

import com.github.jinahya.bit.io.DefaultBitInput;
import com.mastfrog.primes.SeqFileHeader.EntryPosition;
import static com.mastfrog.primes.SeqFileHeader.bitsRequired;
import com.mastfrog.util.collections.IntSet;
import com.mastfrog.util.preconditions.Checks;
import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.util.collections.Longerator;
import com.mastfrog.util.search.Bias;
import com.mastfrog.util.search.BinarySearch;
import com.mastfrog.util.strings.Strings;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * A file containing a sequence of numbers, where, to save space, numbers are
 * stored packed as bits that are not necessarily multiples of 8, where an
 * initial value is stored in full, and then some number of offsets are stored
 * using a smaller number of bits, followed by another full value, and so forth.
 * <p>
 * Presumes that the data is an <i>ascending</i> sequence of numbers; negative
 * numbers are not supported.
 * <p>
 * Used mainly to store lists of prime numbers, but any sorted list of numbers
 * will work.
 *
 * @author Tim Boudreau
 */
public class SeqFile implements AutoCloseable, Iterable<Long> {

    private final FileChannel channel;
    private final SeqFileHeader header;
    private final Mode mode;

    /**
     * Create a new file for the passed path in READ mode.
     *
     * @param path
     * @throws IOException
     */
    public SeqFile(Path path) throws IOException {
        this(path, Mode.READ);
    }

    public SeqFile(Path path, Mode mode) throws IOException {
        this(path, mode, Optional.empty());
    }

    public SeqFile(Path path, Mode mode, SeqFileHeader hdr) throws IOException {
        this(path, mode, Optional.of(hdr));
    }

    public SeqFile(Path path, Mode mode, Optional<SeqFileHeader> header) throws IOException {
        boolean exists = Files.exists(path);
        this.mode = mode;
        switch (mode) {
            case READ:
            case APPEND:
                if (header.isPresent()) {
                    throw new IOException("Should not pass a header when mode is " + mode);
                }
                break;
            default:
                if (!header.isPresent()) {
                    throw new IOException("Must pass a header when mode is " + mode);
                }
        }
        mode.check(path);
        if (mode == Mode.APPEND && exists) {
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
                this.header = new SeqFileHeader(channel);
            }
            channel = FileChannel.open(path, mode.options());
        } else if (mode.isRead()) {
            channel = FileChannel.open(path, mode.options());
            this.header = new SeqFileHeader(channel);
        } else {
            channel = FileChannel.open(path, mode.options());
            if (mode != Mode.APPEND) {
                this.header = header.get();
                this.header.write(channel);
                channel.force(true);
            } else {
                throw new IllegalStateException();
            }
        }
    }

    public interface Filter {

        boolean accept(long val);

        default void reset() {
        }

        default Filter and(Filter b) {
            return new MergeFilter(this, b);
        }
    }

    private static class MergeFilter implements Filter {

        private final Filter a;
        private final Filter b;

        public MergeFilter(Filter a, Filter b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public boolean accept(long val) {
            return a.accept(val) && b.accept(val);
        }

        @Override
        public void reset() {
            a.reset();
            b.reset();
        }
    }

    /**
     * Filter this file to a new one containing a random assortment
     *
     * @param probabilityOneIn
     * @param path
     * @param overwrite
     * @return
     */
    public SeqFileHeader randomFilter(final int probabilityOneIn, Path path, boolean overwrite) throws Exception {
        final long now = System.currentTimeMillis();
        Filter flt = new Filter() {

            Random rnd = new Random(now);

            @Override
            public boolean accept(long val) {
                return rnd.nextInt(probabilityOneIn) == 1;
            }

            @Override
            public void reset() {
                // so we match the same elements on the second pass
                rnd = new Random(now);
            }
        };
        return filter(flt, path, overwrite, 300);
    }

    /**
     * Filter a number sequence file, producing a new file with only the matched
     * elements. Note that the passed filter will be called twice for every
     * number in the original file - once to compute gaps and figure out the
     * number of bits needed in the new file, and a second time to record the
     * results. The filter's reset() method is called between, for the case of
     * stateful filters.
     *
     * @param filter A filter
     * @param path The path to the new file to write
     * @param overwrite If true, open in overwrite mode, if not, an exception
     * will be thrown if the path exists.
     * @param offsetEntries The number of entries which are stored as offsets
     * from a preceding entry. A higher number results in smaller files but
     * slower seek times. A few hundred is usually reasonable.
     * @return The header for the new file
     * @throws Exception If something goes wrong
     */
    public SeqFileHeader filter(Filter filter, Path path, boolean overwrite, long offsetEntries) throws IOException {
        Checks.notNull("filter", filter);
        Checks.notNull("path", path);
        Checks.nonNegative("offsetEntries", offsetEntries);
        if (!overwrite && Files.exists(path)) {
            throw new IOException("Overwrite flag is false, but " + path + " exists.");
        }
        Longerator longs = this.longerator();
        long maxGap = 0;
        boolean hasAny = false;
        long last = 0;
        long max = 0;
        while (longs.hasNext()) {
            long val = longs.next();
            boolean accept = filter.accept(val);
            if (accept) {
                if (hasAny) {
                    long gap = val - last;
                    maxGap = Math.max(last, gap);
                }
                hasAny = true;
                max = Math.max(max, val);
            }
            hasAny |= accept;
        }
        if (!hasAny) {
            throw new IOException("Filter accepted nothing");
        }
        if (maxGap == 0) {
            throw new IOException("Filter accepted only one element");
        }
        int gapBitsRequired = bitsRequired(maxGap);
        int valueBitsRequired = bitsRequired(max);
        filter.reset();
        SeqFileHeader header = new SeqFileHeader(gapBitsRequired, valueBitsRequired, 300);
        try (SeqFile file = new SeqFile(path, overwrite ? Mode.OVERWRITE : Mode.WRITE, header)) {
            try (NumberSequenceWriter writer = new NumberSequenceWriter(file)) {
                longs = this.longerator();
                while (longs.hasNext()) {
                    long val = longs.next();
                    if (filter.accept(val)) {
                        writer.write(val);
                    }
                }
            }
        }
        return header;
    }

    @Override
    public Iterator<Long> iterator() {
        final Longerator longs = longerator();
        return new Iterator<Long>() {
            @Override
            public boolean hasNext() {
                return longs.hasNext();
            }

            @Override
            public Long next() {
                return longs.next();
            }
        };
    }

    private long _get(long val) {
        try {
            return get(val);
        } catch (IOException ex) {
            return Exceptions.chuck(ex);
        }
    }

    public long nearestTo(long value, Bias bias) throws IOException {
        long index = search(value, bias);
        if (index >= 0) {
            return get(index);
        }
        return -1;
    }

    public long search(long value) {
        return search(value, Bias.NONE);
    }

    public long search(long value, Bias bias) {
        BinarySearch<Long> search = BinarySearch.binarySearch(header.count(), this::_get);
        return search.search(value, bias);
    }

    long estimatedCount() throws IOException {
        return header.estimatedCount(channel.size());
    }

    public long get(long index) throws IOException {
        long oldPosition = channel.position();
        try {
            return getNoReposition(index);
        } finally {
            channel.position(oldPosition);
        }
    }

    long encodeFull(long prime) {
        return prime == 1L ? 1L : (prime - 1L) / 2L;
    }

    int encodeOffset(int offset) {
        switch (offset) {
            case 2:
                offset = 1;
                break;
            case 1:
                offset = 0;
                break;
            default:
                offset /= 2;
        }
        return offset;
    }

    int decodeOffset(int offset) {
        switch (offset) {
            case 0:
                offset = 1;
                break;
            case 1:
                offset = 2;
                break;
            default:
                offset *= 2;
        }
        return offset;
    }

    long decodeFull(long result) {
        if (result == 0) {
            result = 2;
        } else {
            result = (result * 2) + 1;
        }
        return result;
    }

    NumberSequenceReader seek(long index) throws IOException {
        if (index == 0) {
            return new NumberSequenceReader(this);
        }
        EntryPosition pos = header.positionOf(index - 1);
        FileChannelByteInput in = new FileChannelByteInput(channel, 512);
        DefaultBitInput<FileChannelByteInput> bits = new DefaultBitInput<>(in);
        long cumulative = 8 * (pos.framePosition - header.headerLength());
        channel.position(pos.framePosition);
        if (pos.skipBitsToFrameTop != 0) {
            int bitsRemaining = pos.skipBitsToFrameTop;
            while (bitsRemaining > 0) {
                int toRead = Math.min(bitsRemaining, 63);
                cumulative += toRead;
                bits.readLong(true, toRead);
                bitsRemaining -= toRead;
            }
        }
        long readFull = bits.readLong(true, header.bitsPerFullEntry());
        long result = decodeFull(readFull);
        cumulative += header.bitsPerFullEntry();
        for (int i = 0; i < pos.offsetIntoFrame; i++) {
            cumulative += header.bitsPerOffsetEntry();
            int readOffset = bits.readInt(true, header.bitsPerOffsetEntry());
            int offset = decodeOffset(readOffset);
            result += offset;
        }
        return new NumberSequenceReader(this, bits, cumulative, index - 1, result);
    }

    long getNoReposition(long index) throws IOException {
        EntryPosition pos = header.positionOf(index);
        FileChannelByteInput in = new FileChannelByteInput(channel, 512);
        DefaultBitInput<FileChannelByteInput> bits = new DefaultBitInput<>(in);
        channel.position(pos.framePosition);
        if (pos.skipBitsToFrameTop != 0) {
            int bitsRemaining = pos.skipBitsToFrameTop;
            while (bitsRemaining > 0) {
                int toRead = Math.min(bitsRemaining, 63);
                bits.readLong(false, toRead);
                bitsRemaining -= toRead;
            }
        }
        long result = decodeFull(bits.readLong(true, header.bitsPerFullEntry()));
        for (int i = 0; i < pos.offsetIntoFrame; i++) {
            int readOffset = bits.readInt(true, header.bitsPerOffsetEntry());
            int offset = decodeOffset(readOffset);
            result += offset;
        }
        return result;
    }

    public SeqFile top() throws IOException {
        channel.position(header.headerLength());
        return this;
    }

    public Longerator longeratorFromValue(long value, Bias bias) throws IOException {
        long offset = search(value, bias);
        return longerator(offset);
    }

    public Longerator longerator(final long startOffset) throws IOException {
        Checks.nonNegative("startOffset", startOffset);
        final NumberSequenceReader reader = seek(startOffset);
        return new Longerator() {
            long ix = startOffset;

            @Override
            public long next() {
                ix++;
                try {
                    return reader.next();
                } catch (IOException ex) {
                    return Exceptions.chuck(ex);
                }
            }

            @Override
            public boolean hasNext() {
                return ix < header().count();
            }
        };
    }

    public long first() throws IOException {
        return get(0);
    }

    public long last() throws IOException {
        return get(header.count() - 1);
    }

    public Longerator longerator() {
        return new Longerator() {
            long ix = 0;
            private final NumberSequenceReader reader = new NumberSequenceReader(SeqFile.this);
            long lastPosition;

            {
                try {
                    lastPosition = channel.position();
                } catch (IOException ex) {
                    Exceptions.chuck(ex);
                }
            }

            @Override
            public long next() {
                ix++;
                try {
                    if (channel.position() != lastPosition) {
                        throw new ConcurrentModificationException("File channel position was moved between "
                                + "calls to next() - expected " + lastPosition + " but was " + channel.position());
                    }
                    long result = reader.next();
                    lastPosition = channel.position();
                    return result;
                } catch (IOException ex) {
                    return Exceptions.chuck(ex);
                }
            }

            @Override
            public boolean hasNext() {
                return ix < header().count();
            }

        };
    }

    public void updateCountAndSave(long count, int maxOffset) throws IOException {
        header.updateCountAndSave(count, maxOffset, channel);
    }

    public SeqFileHeader header() {
        return header;
    }

    public FileChannel channel() {
        return channel;
    }

    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public void close() throws IOException {
        if (channel.isOpen()) {
            flush();
            channel.close();
        }
    }

    void flush() throws IOException {
        if (this.mode.isWrite() && this.mode.isSync()) {
            channel.force(true);
        }
    }

    public enum Mode {
        READ,
        WRITE,
        APPEND,
        WRITE_SYNC,
        APPEND_SYNC,
        OVERWRITE,
        OVERWRITE_SYNC;

        boolean isSync() {
            switch (this) {
                case WRITE_SYNC:
                case APPEND_SYNC:
                case OVERWRITE_SYNC:
                    return true;
                default:
                    return false;
            }
        }

        boolean isWrite() {
            return !isRead();
        }

        void check(Path path) throws IOException {
            if (Files.exists(path) && Files.isDirectory(path)) {
                throw new IOException("Is a folder, not a file: " + path);
            }
            switch (this) {
                case READ:
                    if (!Files.exists(path)) {
                        throw new IOException("Input file does not exist: " + path);
                    }
                    break;
            }
        }

        Set<StandardOpenOption> options() {
            switch (this) {
                case WRITE:
                    return EnumSet.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                case WRITE_SYNC:
                    return EnumSet.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
                case OVERWRITE:
                    return EnumSet.of(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                case OVERWRITE_SYNC:
                    return EnumSet.of(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
                case APPEND:
                    return EnumSet.of(StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
                case APPEND_SYNC:
                    return EnumSet.of(StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
                case READ:
                    return EnumSet.of(StandardOpenOption.READ);
                default:
                    throw new AssertionError(this);
            }
        }

        boolean isRead() {
            return this == READ;
        }
    }

    public static void main(String[] args) throws IOException, Exception {
        long minValue = 0;
        String path = args.length == 0 ? "/home/tim/primes.seq" : args[0];
        SeqFile orig = new SeqFile(Paths.get(path), Mode.READ, Optional.empty());
        int maxGap = 0;
        long last = 0;
        if (orig.header.count() == 0) {
            orig.header.updateCount(orig.estimatedCount());
        }
        Longerator longs = orig.longerator();
        IntSet gaps = new IntSet();
        while (longs.hasNext()) {
            long val = longs.next();
            if (val < minValue) {
                continue;
            }
            int gap = (int) (val - last);
            gaps.add(gap);
            last = val;
            maxGap = Math.max(gap, maxGap);
        }
        List<Integer> gapsSorted = new ArrayList<>(gaps);
        Collections.sort(gapsSorted);
        System.out.println(gapsSorted.size() + " distinct gaps: " + Strings.join(",", gapsSorted));
        System.out.println("Max gap is " + maxGap);
        int intervalBits = bitsRequired(maxGap);
        int valBits = bitsRequired(last);
        System.out.println("Bits needed for intervals: " + intervalBits);
        System.out.println("Bits needed for values: " + valBits);
        if (intervalBits < orig.header.bitsPerOffsetEntry() || valBits < orig.header.bitsPerFullEntry()) {
            Path opt = Paths.get("/home/tim/primes-optimized.seq");
            System.out.println("Write optimized: " + opt);
            SeqFileHeader hdr = new SeqFileHeader(intervalBits, valBits, orig.header.offsetEntriesPerFullEntry() * 2);
            try (SeqFile newFile = new SeqFile(opt, Mode.OVERWRITE, Optional.of(hdr))) {
                longs = orig.longerator();
                try (NumberSequenceWriter w = new NumberSequenceWriter(newFile)) {
                    while (longs.hasNext()) {
                        long val = longs.next();
                        if (val < minValue) {
                            continue;
                        }
                        w.write(val);
                    }
                    System.out.println("Wrote " + w.written() + " primes");
                }
            }
        }
    }
}
