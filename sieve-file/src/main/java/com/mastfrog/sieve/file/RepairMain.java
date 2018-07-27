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

import com.mastfrog.primes.NumberSequenceReader;
import com.mastfrog.primes.NumberSequenceWriter;
import com.mastfrog.primes.SeqFile;
import com.mastfrog.primes.SeqFile.Mode;
import com.mastfrog.primes.SeqFileHeader;
import com.mastfrog.settings.Settings;
import static com.mastfrog.sieve.file.SieveMain.ENTRIES_PER_FULL_ENTRY;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.NumberFormat;

/**
 *
 * @author Tim Boudreau
 */
public class RepairMain {

    static long LOG_INTERVAL = 200_000_000;

    public static void main(String[] args) throws IOException, Exception {
        Settings settings = Settings.builder()
                .commandLineShortcut('i', "infile")
                .commandLineShortcut('o', "outfile")
                .commandLineShortcut('z', "optimize")
                .commandLineShortcut('h', "help")
                .commandLineShortcut('e', ENTRIES_PER_FULL_ENTRY)
                .parseCommandLineArguments(args).build();
        boolean help = settings.getBoolean("--help", false);
        if (help) {
            System.err.println("Usage: primal repair -i/--infile $file [-o/--outfile repairedFile] [-e/" + ENTRIES_PER_FULL_ENTRY + " n] [-o/--optimize]");
            System.exit(1);
        }

        String infile = settings.getString("infile");
        if (infile == null) {
            System.err.println("primal repair -i/--infile [file] [-z/--optimize] [-o/--outfile] [outfile]");
            System.exit(1);
        }
        Path path = Paths.get(infile);
        if (!Files.exists(path) || Files.isDirectory(path)) {
            System.err.println("Does not exist or is a directory: " + path);
        }
        String outfile = settings.getString("outfile");
        boolean usingTempFile = outfile == null;
        if (usingTempFile) {
            Path tmp = Paths.get(System.getProperty("java.io.tmpdir"));
            outfile = tmp.resolve("sieve-repair-" + System.currentTimeMillis() + ".tmp").toString();
        }
        boolean optimize = settings.getBoolean("optimize", false);
        int offsetEntriesPerFullEntry = settings.getInt(ENTRIES_PER_FULL_ENTRY, -1);
        SeqFileHeader header;
        long estimatedCount;
        try (SeekableByteChannel channel = Files.newByteChannel(path, StandardOpenOption.READ)) {
            header = new SeqFileHeader(channel);
            estimatedCount = header.estimatedCount(channel.size());
        }
        if (offsetEntriesPerFullEntry == -1) {
            offsetEntriesPerFullEntry = header.offsetEntriesPerFullEntry();
        }
        if (header.bitsPerFullEntry() <= 1 || header.bitsPerOffsetEntry() <= 1 || header.offsetEntriesPerFullEntry() <= 0) {
            System.err.println("Header is too broken - cannot determine number of bits per entry.  Sorry.");
            System.exit(2);
        }
        boolean broken = header.count() == 0 || header.maxOffset() == 0;
        double est = estimatedCount;
        if (broken || optimize) {
            long length = Files.size(path);
            header = broken ? new SeqFileHeader(header.bitsPerOffsetEntry(), header.bitsPerFullEntry(), header.offsetEntriesPerFullEntry(), length, Long.MAX_VALUE, 65535)
                    : new SeqFileHeader(header.bitsPerOffsetEntry(), header.bitsPerFullEntry(), header.offsetEntriesPerFullEntry(), length, header.count(), header.maxOffset());
            long count = 0;
            int maxGap = 0;
            long maxValue = -1;
            SeqFile file = new SeqFile(path, Mode.READ, header);
            if (broken) {
                System.err.println("Starting scan of " + path + ". This may take a while.");
                try (NumberSequenceReader reader = new NumberSequenceReader(file, 64 * 1024, true)) {
                    for (;; count++) {
                        long curr;
                        try {
                            curr = reader.next();
                        } catch (IOException | BufferUnderflowException ex) {
                            if (!(ex instanceof IOException) && (ex.getCause() instanceof BufferUnderflowException) ) {
                                ex.printStackTrace(System.err);
                            }
                            count--;
                            System.err.println("Ran off end of file at " + count + " - last entry " + maxValue);
                            break;
                        }
                        if (curr == -1) {
                            System.err.println("Reached probable end of file at " + count + ".");
                            break;
                        }
                        if (curr < maxValue || curr == maxValue || curr <= 1) {
                            System.err.println("Encountered invalid value " + curr + " at " + count + " discarding that and subsequent entries.");
                            break;
                        }
                        if (curr != 2 && curr % 2 == 0) {
                            System.err.println("Encountered even value " + curr + " at " + count + " discarding that and subsequent entries");
                            break;
                        }
                        if (maxValue != -1) {
                            int gap = (int) (curr - maxValue);
                            if (maxValue > 2 && gap == 1) {
                                System.err.println("Encountered a gap of " + gap + " at " + count + ". Assuming the rest of the file is invalid");
                                break;
                            } else if (gap % 2 != 0 && maxValue != 2) {
                                System.err.println("All prime gaps are even numbers, but encountered a recorded gap of " + gap + " at " + count + ". "
                                        + "Discarding remainder of file.");
                                break;
                            }
                            maxGap = Math.max(gap, maxGap);
                        }
                        maxValue = curr;
                        if (count % LOG_INTERVAL == 0 && count != 0) {
                            double c = count;
                            String pct = NumberFormat.getInstance().format((c / est) * 100);
                            System.err.println("...content still good at " + count + " with " + curr + " maxGap " + maxGap + " - " + pct + "% done");
                        }
                    }
                }
                System.err.println("Scan pass done.");
                if (count == 0) {
                    System.err.println("No entries read.  Done.");
                    System.exit(3);
                }
            } else {
                count = header.count();
                maxGap = header.maxOffset();
                maxValue = file.last();
                file.close();
            }
            SeqFileHeader readHeader = new SeqFileHeader(header.bitsPerOffsetEntry(), header.bitsPerFullEntry(), header.offsetEntriesPerFullEntry(), length, count, maxGap);
            int bitsPerOffsetEntry = SeqFileHeader.bitsRequiredForPrimeOffset(maxGap);
            int bitsPerFullEntry = SeqFileHeader.bitsRequired(maxValue);
            if (!broken && bitsPerFullEntry == header.bitsPerFullEntry() && bitsPerOffsetEntry == header.bitsPerOffsetEntry() && header.offsetEntriesPerFullEntry() == offsetEntriesPerFullEntry) {
                System.err.println("File space usage is already optimal.  If you want to make the file smaller (at the price of higher random access times), use -e.");
                System.exit(1);
            }
            System.err.println("Repaired/optimized file will have the following:");
            System.err.println("\tNumber of entries: " + count);
            System.err.println("\tBits per full entry: " + bitsPerFullEntry + " (may be less than the bits needed to hold the highest prime)");
            System.err.println("\tBits per offset entry: " + bitsPerOffsetEntry);
            System.err.println("\tOffset entries per full entry: " + offsetEntriesPerFullEntry);
            SeqFileHeader writeHeader = new SeqFileHeader(bitsPerOffsetEntry, bitsPerFullEntry, offsetEntriesPerFullEntry, 0, 0, 0);
            file = new SeqFile(path, Mode.READ, readHeader);
            Path destPath = Paths.get(outfile);
            try (SeqFile outFile = new SeqFile(destPath, Mode.WRITE, writeHeader)) {
                outFile.channel().position(0);
                writeHeader.write(outFile.channel());
                System.err.println("Starting write to " + destPath);
                try (NumberSequenceReader reader = new NumberSequenceReader(file, 64 * 1024, true)) {
                    try (NumberSequenceWriter writer = new NumberSequenceWriter(outFile, 32768, true)) {
                        for (long i = 0; i < count; i++) {
                            long curr;
                            try {
                                curr = reader.next();
                            } catch (IOException | BufferUnderflowException ex) {
                                if (!(ex instanceof IOException) && (ex.getCause() instanceof BufferUnderflowException) ) {
                                    ex.printStackTrace(System.err);
                                }
                                System.err.println("Ran off end of file at " + count);
                                break;
                            }
                            if (curr == -1) {
                                System.err.println("Got end of input at " + count);
                                break;
                            }
                            writer.write(curr);
                            if (count % LOG_INTERVAL == 0) {
                                double c = count;
                                String pct = NumberFormat.getInstance().format((c / est) * 100);
                                System.err.println("...still writing content at " + count + " with " + curr + " - " + pct + "% done");
                            }
                            if (count % 50_000_000 == 0) {
                                outFile.updateCountAndSave(count, maxGap);
                            }
                        }
                    }
                }
            }
            System.err.println("Write done.");
            if (usingTempFile) {
                System.err.println("Replacing " + path + " with temp file " + destPath);
                Files.move(destPath, path, StandardCopyOption.REPLACE_EXISTING);
            }
            System.err.println("Done.");
        } else {
            System.err.println("File is okay, --optimize not specified.  Nothing to do.");
        }

    }

    static class ScanInfo {

        public final long maxEntry;
        public final int maxGap;
        public final int validEntryCount;

        public ScanInfo(long maxEntry, int maxGap, int validEntryCount) {
            this.maxEntry = maxEntry;
            this.maxGap = maxGap;
            this.validEntryCount = validEntryCount;
        }

    }
}
