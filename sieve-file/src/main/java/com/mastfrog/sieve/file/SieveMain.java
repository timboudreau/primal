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
package com.mastfrog.sieve.file;

import com.mastfrog.primal.sieve.Sieve;
import com.mastfrog.primes.MultiSeqFileReader;
import com.mastfrog.primes.NumberSequenceWriter;
import com.mastfrog.primes.SeqFile;
import com.mastfrog.primes.SeqFile.Mode;
import com.mastfrog.primes.SeqFileHeader;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import static com.mastfrog.sieve.file.Main.exit;
import com.mastfrog.util.preconditions.Exceptions;
import com.mastfrog.util.strings.Strings;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.collections.IntSet;
import com.mastfrog.util.time.TimeUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/**
 *
 * @author Tim Boudreau
 */
public class SieveMain {

    static final NumberFormat NUMBERS = new DecimalFormat("###,###,###,###,###,###,###,##0");
    static final NumberFormat DECIMALS = new DecimalFormat("###,###,###,###,###,###,###,##0.0##");

    static final String OVERWRITE = "overwrite";
    static final String OUTFILE = "outfile";
    static final String MAX_VALUE = "max";
    static final String INFILE = "infile";
    static final String BITS_PER_OFFSET_ENTRY = "offsetbits";
    static final String BITS_PER_FULL_ENTRY = "bits";
    static final String ENTRIES_PER_FULL_ENTRY = "entries";
    static final String BATCH_SIZE = "batchsize";
    static final String STATS = "stats";
    static final String LOG = "log";
    static final String PROGRESS = "progress";
    static final String VERBOSE = "verbose";
    static final String NATIVE = "native";
    static final String HELP = "help";
    static final String TOTAL = "total";

    static final long BATCH_ABOVE = 10_000_000_000L;
    static int tempFileCount = 1;
    private static final NumberFormat SEVEN_DIGITS = new DecimalFormat("0000000");
    static final String TEMP_FILE_NAME_BASE = Long.toString(System.currentTimeMillis(), 36);

    static final Map<Character, String> SHORT_COMMANDS = CollectionUtils.<Character, String>map('o').to(OUTFILE)
            .map('w').to(OVERWRITE)
            .map('m').to(MAX_VALUE)
            .map('b').to(BITS_PER_FULL_ENTRY)
            .map('t').to(BITS_PER_OFFSET_ENTRY)
            .map('e').to(ENTRIES_PER_FULL_ENTRY)
            .map('z').to(BATCH_SIZE)
            .map('i').to(INFILE)
            .map('n').to(NATIVE)
            .map('l').to(LOG)
            .map('s').to(STATS)
            .map('p').to(PROGRESS)
            .map('v').to(VERBOSE)
            .map('h').to(HELP)
            .map('t').to(TOTAL)
            .map('n').to(NATIVE)
            .build();

    static final Map<String, String> HELP_COMMANDS = CollectionUtils.<String, String>map(HELP).to("Print this help.")
            .map(OUTFILE).to("The output file (if unspecified, primes.seq in the current directory is used)")
            .map(INFILE).to("The input file, if continuing a sequence")
            .map(MAX_VALUE).to("Compute primes up to this value")
            .map(OVERWRITE).to("Overwrite the output file if it exists")
            .map(BITS_PER_FULL_ENTRY).to("The number of bits to use for each full entry (must fit the biggest prime you want to compute) - if not set, the bits for the maximum value are used.")
            .map(BITS_PER_OFFSET_ENTRY).to("The number of bits to use for each offset entry (must fit the biggest gap between primes).")
            .map(ENTRIES_PER_FULL_ENTRY).to("The number of offset entries per full entry.")
            .map(BATCH_SIZE).to("The value above which sieving should be done in batches to avoid memory limits (default " + BATCH_ABOVE + ")")
            .map(NATIVE).to("Use the unix utility primesieve instead of the java sieve (must be on $PATH)")
            .map(LOG).to("Log output as comma-separated values to the standard output.")
            .map(STATS).to("Log statistics about generation progress.")
            .map(PROGRESS).to("Log progress periodically.")
            .map(TOTAL).to("Maximum number of primes to sieve (may be less if max value is reached)")
            .map(VERBOSE).to("Equivalent to --log --stats --verbose.")
            .buildLinkedHashMap();

    static String formatHelpLine(String s) {
        StringBuilder sb = new StringBuilder("\t");
        StringTokenizer tok = new StringTokenizer(s);
        int chars = 0;
        while (tok.hasMoreTokens()) {
            String t = tok.nextToken();
            if (chars > 40) {
                sb.append("\n\t\t\t");
                chars = 0;
            }
            sb.append(t);
            sb.append(' ');
            chars += t.length() + 1;
        }
        return sb.append('\n').toString();
    }

    static void printHelpAndExit() {
        Map<String, Character> cmdForKey = CollectionUtils.reverse(SHORT_COMMANDS);
        StringBuilder sb = new StringBuilder("Usage:\njava -jar sieve.jar --outfile/-o [file] --max/-m [number] \\\n\t --bits/-b [numbits] --overwrite/-w --entries [num]\\\n\t --offsetbits/-s [num] --infile [file] --log\n\n");
        HELP_COMMANDS.entrySet().forEach((e) -> {
            sb.append("--").append(e.getKey()).append(" -").append(cmdForKey.get(e.getKey())).append('\t').append(formatHelpLine(e.getValue())).append('\n');
//            System.out.println(e.getKey() + "\t" + e.getValue());
        });
        sb.append("\nOutputs compressed files containing a sequence of prime numbers.  The resulting files contain"
                + "a 16 byte header, followed by a data segment consisting of a starting value (using the number of bits"
                + "specified by the --bits argument, or computed from the passed maximum value if not specified) and "
                + "then offset values using a much smaller number of bits (the first five billion primes can be fit in "
                + "37 bits for full values and 9 bits for offsets - the largest known prime gap will fit in 11 bits).\n\n"
                + "It is possible to create invalid files if you set the number of bits wrong.\n\nSieving primes uses a "
                + "lot of memory - the larger the maximum prime, the more memory will be used.  The process speeds up as "
                + "more primes are factored.\n\nBatch mode is used for very large maximum values - at 137 billion primes "
                + "it is impossible to create the necessary data structures, and without a 10x gigabyte heap it is possible "
                + "to run out of memory much sooner than that.  Batching allows us to quickly sequence some primes, then "
                + "throw away the existing memory structures and sequence another subsequent batch, at the price of having "
                + "to replay the output of previous batches into the next.  But this feature makes it possible to sequence "
                + "primes up to Long.MAX_VALUE.");
        exit(1, sb.toString());
    }

    public static void main(String... args) throws Exception {
        Settings settings = new SettingsBuilder("sieve-file").parseCommandLineArguments(SHORT_COMMANDS, args).build();
        boolean help = settings.getBoolean(HELP, false) || !settings.iterator().hasNext();
        if (help) {
            printHelpAndExit();
        }
        if (settings.getBoolean(NATIVE, false)) {
            NativeSieveMain.main(args);
            return;
        }
        long max = settings.getLong(MAX_VALUE, 1_000_000L);
        if (max == 0) {
            max = Long.MAX_VALUE;
        }
        String outfile = settings.getString(OUTFILE);
        boolean overwrite = settings.getBoolean(OVERWRITE, false);
        String infile = settings.getString(INFILE);
        boolean log = settings.getBoolean(LOG, false);
        boolean stats = settings.getBoolean(STATS, false);
        long total = settings.getLong(TOTAL, -1);

        if (outfile == null && !log) {
            System.err.println("No output file specified - will log to standard output");
            log = true;
        }
        int bitsPerFull = settings.getInt(BITS_PER_FULL_ENTRY, 0);
        int bitsPerOffset = settings.getInt(BITS_PER_OFFSET_ENTRY, 11);
        int entriesPerFull = settings.getInt(ENTRIES_PER_FULL_ENTRY, 300);

        long batchThreshold = settings.getLong(BATCH_SIZE, BATCH_ABOVE);

        PrimeWriter writer = null;
        if (outfile != null) {
            Path path = Paths.get(outfile);
            if (path.getParent() != null && !Files.exists(path.getParent())) {
                exit(2, "Parent folder of " + path + " does not exist.");
            }
            if (path.getParent() != null && !Files.isDirectory(path.getParent())) {
                exit(3,"Parent folder of " + path + " is not a directory.");
            }
            if (Files.exists(path) && !overwrite) {
                exit(4, path + " exists and --overwrite not specified - will not clobber it.");
            }

            if (bitsPerFull == 0) {
                bitsPerFull = SeqFileHeader.bitsRequired(max);
            }

            writer = new PrimeWriter(path, bitsPerFull, bitsPerOffset, entriesPerFull, overwrite ? Mode.OVERWRITE : Mode.WRITE);
            final PrimeWriter pw = writer;
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
        }

        LongConsumer consumer = new NoopWriter();
        if (writer != null) {
            consumer = consumer.andThen(writer);
        }
        if (log) {
            consumer = consumer.andThen(new LogWriter());
        }
        if (stats) {
            consumer = consumer.andThen(new StatsWriter(max, total));
        }

        try {
            if (max == 0 && infile == null) {
                max = 1_000_000;
            }
            if (infile == null) {
                if (max > batchThreshold) {
                    System.err.println("Above batch threshold - will sieve in batches of " + batchThreshold + " to temporary files to"
                            + " avoid running out of memory.");
                    final long maximum = max;
                    long[] batchNumber = new long[1];
                    LongSupplier currentBatch = new LongSupplier() {
                        long current = 0;
                        @Override
                        public long getAsLong() {
                            if (current >= maximum) {
                                return -1;
                            }
                            current += batchThreshold;
                            if (maximum - current < 10000) {
                                // Optimization - make the last batch slightly larger if there's
                                // not much more to do
                                current = maximum;
                            }
                            if (current > maximum) {
                                current= maximum;
                            }
                            batchNumber[0]++;
                            return current;
                        }
                        
                    };
                    List<Path> tempFiles = new ArrayList<>();
                    try {
                        long last = 0;
                        final LongConsumer realConsumer = consumer;
                        long batch;
                        while ((batch = currentBatch.getAsLong()) != -1) {
                            Path pth = tempFile("sieve");
                            System.err.println("Batch " + batchNumber[0] + " -> " + pth + " thru " + batch);
                            SeqFileHeader hdr = new SeqFileHeader(bitsPerOffset, SeqFileHeader.bitsRequired(batch), entriesPerFull);

                            try (SeqFile file = new SeqFile(pth, Mode.WRITE, hdr)) {
                                try (NumberSequenceWriter tempwriter = new NumberSequenceWriter(file)) {
                                    LongConsumer cons = tempwriter.andThen((long value) -> {
                                        // Don't cause the file to close on -1 from a non-final sequence
                                        if (value != -1) {
                                            realConsumer.accept(value);
                                        }
                                    });
                                    if (tempFiles.isEmpty()) {
                                        last = Sieve.sieve(batch, cons, total);
                                    } else {
                                        System.err.println("Batch " + last + " to " + (last + batchThreshold) + " written: " + writer.writer.written() + " - will "
                                                + "replay " + tempFiles.size() + " temporary files");
                                        last = Sieve.sieve(last, new MultiSeqFileReader(tempFiles), cons, last + batchThreshold, total);
                                    }
                                    tempFiles.add(pth);
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        if (Main.unitTest) {
                            throw e;
                        } else {
                            exit(10, e.getMessage() + "");
                        }
                    } finally {
                        for (Path p : tempFiles) {
                            if (Files.exists(p)) {
                                Files.delete(p);
                            }
                        }
                    }
                } else {
                    Sieve.sieve(max, consumer, total);
                }
            } else {
                try (MultiSeqFileReader file = MultiSeqFileReader.fromCommaDelimited(infile)) {
                    Sieve.sieve(file.last(), file, consumer, max, total);
                }
            }
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }


    private static Path tempFile(String action) {
        String tempFileBase = action + "-" + TEMP_FILE_NAME_BASE + "-" + SEVEN_DIGITS.format(tempFileCount++);
        String filename = tempFileBase + SEVEN_DIGITS.format(tempFileCount++) + ".tmp";
        return Paths.get(System.getProperty("java.io.tmpdir"), filename);
    }

    static final class NoopWriter implements LongConsumer {

        @Override
        public void accept(long value) {
            // do nothing
        }
    }

    static final class PrimeWriter implements LongConsumer, AutoCloseable {

        private final SeqFile file;
        private final NumberSequenceWriter writer;
        volatile boolean closed;

        PrimeWriter(Path pth, int bitsPerFullEntry, int bitsPerOffsetEntry, int entryCount, Mode mode) throws IOException {
            SeqFileHeader hdr = new SeqFileHeader(bitsPerOffsetEntry, bitsPerFullEntry, entryCount);
            file = new SeqFile(pth, mode, hdr);
//            writer = new AsyncNumberSequenceWriter(new NumberSequenceWriter(file));
            writer = new NumberSequenceWriter(file);
        }

        @Override
        public void accept(long value) {
            if (value != -1) {
                writer.accept(value);
            }
        }

        @Override
        public void close() throws Exception {
            if (!closed) {
                closed = true;
                try {
                    writer.close();
                } finally {
                    file.close();
                }
            }
        }

    }

    static final class LogWriter implements LongConsumer {

        long count = 0;
        int chars = 0;

        @Override
        public void accept(long value) {
            if (count > 0 && value != -1) {
                System.out.print(',');
            }
            String str = Long.toString(value);
            if ((chars + str.length()) > 80) {
                System.out.print('\n');
                System.out.flush();
                chars = 0;
            }
            chars += str.length() + 1;
            if (value == -1) {
                System.err.println("\n\nDone.");
                return;
            }
            System.out.print(str);
            count++;
        }
    }

    static final class StatsWriter implements LongConsumer {

        static final Duration interval = Duration.ofSeconds(30);
        final ZonedDateTime startTime = ZonedDateTime.now();
        ZonedDateTime nextLog = startTime.plus(interval);
        ZonedDateTime lastLog = startTime;
        long countAtLastLog = 0;
        int maxBits = 0;
        int maxGap = 0;
        final IntSet gaps = IntSet.create(65535);
        long count = 0;
        long prev = 0;
        final long max;
        final long total;

        public StatsWriter(long max, long total) {
            this.max = max;
            this.total = total;
        }

        private void logDetails() {
            System.err.println("Bits required for values: " + maxBits);
            System.err.println("Bits required for gaps: " + SeqFileHeader.bitsRequiredForPrimeOffset(maxGap) + "; biggest gap: " + maxGap);
            System.err.println(gaps.size() + " distinct prime-gaps seen.\n");
        }

        @Override
        public void accept(long value) {
            if (value == -1) {
                System.err.println("Sieved " + NUMBERS.format(count) + " primes in " + TimeUtil.format(Duration.between(startTime, ZonedDateTime.now()), true));
                logDetails();
                List<Integer> allGaps = new ArrayList<>(gaps);
                Collections.sort(allGaps);
                System.err.println(Strings.join(',', allGaps));
                System.err.println("Done at " + TimeUtil.toHttpHeaderFormat(ZonedDateTime.now()) + ".");
            } else {
                count++;
                maxBits = Math.max(maxBits, SeqFileHeader.bitsRequired(value));
                if (prev != 0) {
                    int gap = (int) (value - prev);
                    maxGap = Math.max(gap, maxGap);
                    gaps.add(gap);
                }
                if (count > 0 && ZonedDateTime.now().isAfter(nextLog)) {
                    Duration sinceLast = Duration.between(lastLog, ZonedDateTime.now());
                    Duration sinceStart = Duration.between(startTime, ZonedDateTime.now());
                    float cumulativeMinutes = sinceStart.toMinutes();
                    String throughput = DECIMALS.format(cumulativeMinutes == 0 ? 0 : (float) count / cumulativeMinutes);

                    float percent;
                    if (total != -1) {
                        float ct = count;
                        float tl = total;
                        percent = (ct / tl) * 100;
                    } else {
                        float v = value;
                        float m = max;
                        percent = (v / m) * 100;
                    }

                    System.err.println("Sieved " + NUMBERS.format(count - countAtLastLog) + " primes in last " + TimeUtil.format(sinceLast, false)
                            + " total " + NUMBERS.format(count) + ";  most recent: " + value + "\nElapsed time: " + TimeUtil.format(sinceStart, false)
                            + "\nThroughput: " + throughput + " primes / minute\nPercent done: " + DECIMALS.format(percent) + "%");
                    lastLog = nextLog;
                    nextLog = ZonedDateTime.now().plus(interval);
                    countAtLastLog = count;
                    logDetails();
                }
                prev = value;
            }
        }

    }

}
