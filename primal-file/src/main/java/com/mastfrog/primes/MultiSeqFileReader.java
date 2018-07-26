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

import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.util.strings.Strings;
import com.mastfrog.util.collections.Longerator;
import com.mastfrog.util.search.Bias;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.LongSupplier;

/**
 *
 *
 * @author Tim Boudreau
 */
public class MultiSeqFileReader implements LongSupplier, Longerator, AutoCloseable {

    private List<Path> paths = new ArrayList<>();
//    private final List<SeqFile> files = new ArrayList<>();
//    private final List<NumberSequenceReader> readers = new ArrayList<>();
    private int cursor = 0;
    private long total;

    public static MultiSeqFileReader fromCommaDelimited(String s) throws IOException {
        return new MultiSeqFileReader(s.split(","));
    }

    public MultiSeqFileReader(String... paths) throws IOException {
        this(toPaths(paths));
    }

    public MultiSeqFileReader(Path... paths) throws IOException {
        this(Arrays.asList(paths));
    }

    public MultiSeqFileReader(List<Path> paths) throws IOException {
        this.paths = new ArrayList<>(paths);
        for (Path p : paths) {
            long lastLast = 0;
            total = 0;
            try (SeqFile file = new SeqFile(p)) {
                long ct = file.header().count();
                if (ct == 0) {
                    throw new IOException(p + " contains zero entries");
                }
                long last = file.last();
                if (last < lastLast) {
                    throw new IOException(p + " is out of order - last value is "
                            + last + " while the preceding file's "
                            + "last element is " + lastLast);
                }
                total += ct;
            }
        }
    }

    MultiSeqFileReader reset() throws IOException {
        cursor = 0;
        if (currFile != null) {
            currFile.close();
            currReader = null;
        }
        return this;
    }

    public long search(long value, Bias bias) throws IOException {
        long last = -1;
        for (int i = 0; i < paths.size(); i++) {
            currFile = new SeqFile(paths.get(i));
            long oldLast = last;
            last = currFile.last();
            if (value > last) {
                if (oldLast == currFile.first()) {
                    this.count += currFile.header().count() - 1;
                } else {
                    this.count += currFile.header().count();
                }
                cursor++;
            } else {
                long offset = currFile.search(value, bias);
                if (offset != -1) {
                    this.count += offset;
                    currReader = currFile.seek(offset);
                    return count;
                }
            }
            currFile.close();
        }
        return count;
    }

    public MultiSeqFileReader seek(long val) throws IOException {
        long ct = 0;
        for (int i = 0; i < paths.size(); i++) {
            try (SeqFile sf = new SeqFile(paths.get(i))) {
                long countForFile = sf.header().count();
                ct += countForFile;
                if (val > ct) {
                    cursor++;
                    count += countForFile;
                }
            }
        }
        return this;
    }

    public int bitsPerOffsetEntry() throws IOException {
        long maxGap = 0;
        for (Path p : paths) {
            try (SeqFile sf = new SeqFile(p)) {
                maxGap = Math.max(maxGap, sf.header().maxOffset());
            }
        }
        return SeqFileHeader.bitsRequiredForPrimeOffset(maxGap);
    }

    public int bitsPerFullEntry() throws IOException {
        return SeqFileHeader.bitsRequired(last());
    }

    public long last() throws IOException {
        try (SeqFile sf = new SeqFile(paths.get(paths.size() - 1))) {
            return sf.last();
        }
    }

    public SeqFileHeader sizeOptimizedHeaderForNewFile() throws IOException {
        return optimalHeaderForNewFile(500);
    }

    public SeqFileHeader optimalHeaderForNewFile(int offsetEntriesPerFullEntry) throws IOException {
        return new SeqFileHeader(bitsPerOffsetEntry(), bitsPerFullEntry(), offsetEntriesPerFullEntry);
    }

    static List<Path> toPaths(String... names) throws IOException {
        List<String> str = Arrays.asList(names);
        if (new HashSet<>(str).size() != str.size()) {
            throw new IOException("List of files contains duplicates: " + Strings.join(", ", str));
        }
        List<Path> pths = new ArrayList<>(str.size());
        for (String s : str) {
            pths.add(toPath(s));
        }
        return pths;
    }

    private static Path toPath(String pth) throws IOException {
        Path result = Paths.get(pth);
        if (!Files.exists(result)) {
            throw new IOException("File does not exist: " + result);
        }
        return result;
    }

    SeqFile currFile;
    NumberSequenceReader currReader;

    NumberSequenceReader reader() throws IOException {
        if (currReader == null && cursor < paths.size()) {
            Path p = paths.get(cursor);
            currFile = new SeqFile(p);
            currReader = new NumberSequenceReader(currFile);
        }

        if (currReader != null && currReader.count() >= currFile.header().count()) {
            currFile.close();
            currReader = null;
            cursor++;
            if (cursor < paths.size()) {
                currFile = new SeqFile(paths.get(cursor));
                currReader = new NumberSequenceReader(currFile);
            } else {
                currReader = null;
            }
        }
        return currReader;
    }

    long count = 0;
    long lastValue;

    public long count() {
        return count;
    }

    @Override
    public long next() {
        try {
            NumberSequenceReader reader = reader();
            if (reader == null) {
                throw new IndexOutOfBoundsException("Done.");
            }
            long result = reader.next();
            if (result == lastValue) {
                reader = reader();
                if (reader == null) {
                    return -1;
                }
                result = reader.next();
            }
            count++;
            return lastValue = result;
        } catch (IOException ex) {
            return Exceptions.chuck(ex);
        }
    }

    @Override
    public boolean hasNext() {
        if (cursor >= paths.size()) {
            return false;
        }
        NumberSequenceReader reader;
        try {
            reader = reader();
            return reader == null ? false : reader.count() < currFile.header().count();
        } catch (IOException ex) {
            return Exceptions.chuck(ex);
        }
    }

    @Override
    public void close() throws IOException {
        if (currFile != null) {
            currFile.close();
        }
    }
}
