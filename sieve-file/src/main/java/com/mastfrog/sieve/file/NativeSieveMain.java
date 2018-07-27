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

import com.mastfrog.primes.SeqFile;
import com.mastfrog.primes.SeqFileHeader;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import static com.mastfrog.sieve.file.Main.exit;
import static com.mastfrog.sieve.file.SieveMain.BATCH_ABOVE;
import static com.mastfrog.sieve.file.SieveMain.BATCH_SIZE;
import static com.mastfrog.sieve.file.SieveMain.BITS_PER_FULL_ENTRY;
import static com.mastfrog.sieve.file.SieveMain.BITS_PER_OFFSET_ENTRY;
import static com.mastfrog.sieve.file.SieveMain.ENTRIES_PER_FULL_ENTRY;
import static com.mastfrog.sieve.file.SieveMain.INFILE;
import static com.mastfrog.sieve.file.SieveMain.LOG;
import static com.mastfrog.sieve.file.SieveMain.MAX_VALUE;
import static com.mastfrog.sieve.file.SieveMain.OUTFILE;
import static com.mastfrog.sieve.file.SieveMain.OVERWRITE;
import static com.mastfrog.sieve.file.SieveMain.SHORT_COMMANDS;
import static com.mastfrog.sieve.file.SieveMain.STATS;
import static com.mastfrog.sieve.file.SieveMain.TOTAL;
import com.mastfrog.util.preconditions.Exceptions;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.LongConsumer;

/**
 *
 * @author Tim Boudreau
 */
public class NativeSieveMain {

    public static void main(String[] args) throws IOException {
        Settings settings = new SettingsBuilder("sieve-file").parseCommandLineArguments(SHORT_COMMANDS, args).build();
        long max = settings.getLong(MAX_VALUE, 1_000_000L);
        if (max == 0) {
            max = Long.MAX_VALUE;
        }
        String outfile = settings.getString(OUTFILE);
        boolean overwrite = settings.getBoolean(OVERWRITE, false);
        String infile = settings.getString(INFILE);
        if (infile != null) {
            System.err.println("infile not supported with native sieve");
            System.exit(1);
        }
        boolean log = settings.getBoolean(LOG, false);
        boolean stats = settings.getBoolean(STATS, false);

        if (outfile == null && !log) {
            System.err.println("No output file specified - will log to standard output");
            log = true;
        }
        int bitsPerFull = settings.getInt(BITS_PER_FULL_ENTRY, 0);
        int bitsPerOffset = settings.getInt(BITS_PER_OFFSET_ENTRY, 16);
        int entriesPerFull = settings.getInt(ENTRIES_PER_FULL_ENTRY, 300);

        long total = settings.getLong(TOTAL, -1);

        long batchThreshold = settings.getLong(BATCH_SIZE, BATCH_ABOVE);
        if (batchThreshold != BATCH_ABOVE) {
            System.err.println("Batch-above not supported with native sieve");
            System.exit(1);
        }

        SieveMain.PrimeWriter writer = null;
        if (outfile != null) {
            Path path = Paths.get(outfile);
            if (path.getParent() != null && !Files.exists(path.getParent())) {
                exit(2, "Parent folder of " + path + " does not exist.");
            }
            if (path.getParent() != null && !Files.isDirectory(path.getParent())) {
                exit(3, "Parent folder of " + path + " is not a directory.");
            }
            if (Files.exists(path) && !overwrite) {
                exit(4, path + " exists and --overwrite not specified - will not clobber it.");
            }

            if (bitsPerFull == 0) {
                bitsPerFull = SeqFileHeader.bitsRequired(max);
            }

            writer = new SieveMain.PrimeWriter(path, bitsPerFull, bitsPerOffset, entriesPerFull, overwrite ? SeqFile.Mode.OVERWRITE : SeqFile.Mode.WRITE);
            final SieveMain.PrimeWriter pw = writer;
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        System.err.println("Close file via shutdown hook");
                        pw.close();
                    } catch (Exception ex) {
                        Exceptions.chuck(ex);
                    }
                }
            }));

            LongConsumer consumer = writer == null ? new SieveMain.NoopWriter() : writer;
            if (log) {
                consumer = consumer.andThen(new SieveMain.LogWriter());
            }
            if (stats) {
                consumer = consumer.andThen(new SieveMain.StatsWriter(max, total));
            }
            sieveNative(max, consumer, total);
        }
    }

    static void sieveNative(long max, LongConsumer consumer, long total) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("primesieve", "-t8", "--no-status", Long.toString(0), Long.toString(max), "-p");
        Process proc = pb.start();
        try (InputStream in = proc.getInputStream()) {
            parse(proc, in, consumer, 2048, total);
        }
    }

    static String csFromBytes(byte[] b, int offset, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            if (b[i + offset] == '\n') {
                sb.append("\\n");
            } else {
                sb.append((char) b[i + offset]);
            }
        }
        return sb.toString();
    }

    static void parse(Process process, InputStream in, LongConsumer cons, int bufferSize, long total) throws IOException {
        byte[] a = new byte[bufferSize];
        byte[] b = new byte[bufferSize];
        byte[] temp = new byte[64];
        int remainderStart = -1;
        int remainderLength = -1;
        int lastStart = 0;
        long primesRead = 0;
        byte[] buffer, other;
        iteration:
        for (int iter = 0;; iter++) {
            if (iter % 2 == 0) {
                buffer = a;
                other = b;
            } else {
                buffer = b;
                other = a;
            }
            int read = in.read(buffer);
            if (read <= 0) {
                return;
            } else if (read > 0) {
                for (int i = 0; i < read; i++) {
                    byte curr = buffer[i];
                    if (i == read - 1 && curr != '\n') {
                        remainderStart = lastStart;
                        remainderLength = read - remainderStart;
                        lastStart = 0;
                        continue;
                    } else if (curr == '\n') {
                        if (remainderStart == -1) {
                            long val = parseLong(buffer, lastStart, i - lastStart);
                            remainderStart = -1;
                            remainderLength = -1;
                            cons.accept(val);
                            primesRead++;
                        } else {
                            System.arraycopy(other, remainderStart, temp, 0, remainderLength);
                            if (i != 0) {
                                System.arraycopy(buffer, lastStart, temp, remainderLength, i);
                            }
                            long val = parseLong(temp, 0, i + remainderLength);
                            remainderStart = -1;
                            remainderLength = -1;
                            cons.accept(val);
                            primesRead++;
                        }
                        if (i == read - 1) {
                            lastStart = 0;
                        } else {
                            lastStart = i + 1;
                        }
                    }
                    if (total != -1 && primesRead == total) {
                        process.destroy();
                        break iteration;
                    }
                }
            }
        }
    }

    public static long parseLong(byte[] bytes, int offset, int length) {
        long result = 0;
        int max = offset + (length - 1);
        long position = 1;
        boolean negative = false;
        for (int i = max; i >= offset; i--) {
            switch (bytes[i]) {
                case '-':
                    if (i == 0) {
                        negative = true;
                        continue;
                    }
                    throw new NumberFormatException("- encountered not at start of '" + csFromBytes(bytes, offset, length) + "'");
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    long prev = result;
                    result += position * (bytes[i] - '0');
                    if (prev > result) {
                        throw new NumberFormatException("Number too large for long: '" + csFromBytes(bytes, offset, length) + "' - "
                                + " " + prev + " + " + (position * (bytes[i] - '0')) + " = " + result);
                    }
                    position *= 10;
                    continue;
                default:
                    throw new NumberFormatException("Illegal character '" + (char) bytes[i] + "' in number '" + csFromBytes(bytes, offset, length) + "'");
            }
        }
        return negative ? -result : result;
    }

}
