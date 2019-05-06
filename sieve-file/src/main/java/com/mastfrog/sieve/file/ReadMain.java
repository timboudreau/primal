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

import com.mastfrog.primes.MultiSeqFileReader;
import com.mastfrog.primes.NumberSequenceWriter;
import com.mastfrog.primes.SeqFile;
import com.mastfrog.primes.SeqFile.Mode;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import static com.mastfrog.sieve.file.GetMain.FIRST;
import static com.mastfrog.sieve.file.GetMain.GREATER_THAN;
import static com.mastfrog.sieve.file.GetMain.LESS_THAN;
import static com.mastfrog.sieve.file.GetMain.OFFSET;
import static com.mastfrog.sieve.file.Main.exit;
import static com.mastfrog.sieve.file.SieveMain.HELP;
import static com.mastfrog.sieve.file.SieveMain.INFILE;
import static com.mastfrog.sieve.file.SieveMain.LOG;
import static com.mastfrog.sieve.file.SieveMain.OUTFILE;
import static com.mastfrog.sieve.file.SieveMain.formatHelpLine;
import com.mastfrog.util.preconditions.ConfigurationError;
import com.mastfrog.util.strings.Strings;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.collections.IntSet;
import com.mastfrog.util.search.Bias;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.LongConsumer;
import java.util.function.LongPredicate;

/**
 *
 * @author Tim Boudreau
 */
public class ReadMain {

    static final String RANDOM = "random";
    static final String LIMIT = "limit";
    static final String END_OFFSET = "endoffset";
    static final String GAPS = "gaps";
    static final String DISTINCT_GAPS = "distinctgaps";
    static final String DIFFERENTIAL_GAPS = "differentialgaps";
    static final String DISTINCT_DIFFERENTIAL_GAPS = "distinctdifferentialgaps";
    private static final Map<Character, String> SHORT_COMMANDS = CollectionUtils.<Character, String>map('i').to(INFILE)
            .map('o').to(OUTFILE)
            .map('l').to(LIMIT)
            .map('r').to(RANDOM)
            .map('s').to(OFFSET)
            .map('e').to(END_OFFSET)
            .map('g').to(GREATER_THAN)
            .map('n').to(LESS_THAN)
            .map('h').to(HELP)
            .map('i').to(INFILE)
            .map('p').to(GAPS)
            .map('d').to(DISTINCT_GAPS)
            .map('q').to(DIFFERENTIAL_GAPS)
            .map('y').to(DISTINCT_DIFFERENTIAL_GAPS)
            .build();

    private static final Map<String, String> HELP_COMMANDS = CollectionUtils.<String, String>map(HELP).to("Print this help.")
            .map(INFILE).to("The input file of prime numbers.  May be a comma-delimited list of files, but they must be in ascending order of values.")
            .map(FIRST).to("Print the first entry in this file")
            .map(OUTFILE).to("File to output to")
            .map(OFFSET).to("Start from the nth entry in the file")
            .map(LESS_THAN).to("Output values must be less than n")
            .map(GREATER_THAN).to("Output values must be greater than n")
            .map(RANDOM).to("Randomly choose some of the values with a 1 in n probability")
            .map(END_OFFSET).to("Output values whose offset in the file is less than n")
            .map(LIMIT).to("Output no more than n values")
            .map(GAPS).to("Output prime gaps rather than values")
            .map(DISTINCT_GAPS).to("Output only distinct gap values")
            .map(DIFFERENTIAL_GAPS).to("Output difference between subsequent gaps")
            .map(DISTINCT_DIFFERENTIAL_GAPS).to("Output distict values of differences between subequent primes")
            .buildLinkedHashMap();

    private static void printHelpAndExit() {
        Map<String, Character> cmdForKey = CollectionUtils.reverse(SHORT_COMMANDS);
        StringBuilder sb = new StringBuilder("Usage:\njava -jar sieve.jar read --infile [file] --first--greaterthan [n] --lessthan [n] --closestto [n] --offset [n] --surpriseme   \n\n");
        HELP_COMMANDS.entrySet().forEach((e) -> {
            sb.append("--").append(e.getKey()).append(" -").append(cmdForKey.get(e.getKey())).append('\t').append(formatHelpLine(e.getValue())).append('\n');
        });
        sb.append("\nReads and filters values from a prime sequence file.");
        exit(1, sb.toString());
    }

    private static final class BasePredicate implements LongPredicate {

        @Override
        public boolean test(long value) {
            return true;
        }

    }

    private static final class LimitPredicate implements LongPredicate {

        private final long limit;
        private long count = 0;

        public LimitPredicate(long limit) {
            this.limit = limit;
        }

        boolean isDone() {
            return count >= limit;
        }

        @Override
        public boolean test(long value) {
            return count++ < limit;
        }
    }

    private static final class Random implements LongPredicate {

        private final SecureRandom rnd = new SecureRandom();
        private final int n;

        public Random(int n) {
            this.n = n;
        }

        @Override
        public boolean test(long value) {
            return n == 0 ? true : rnd.nextInt(n) == 1;
        }
    }

    private static final class GreaterThan implements LongPredicate {

        private final long against;

        public GreaterThan(long against) {
            this.against = against;
        }

        @Override
        public boolean test(long value) {
            return value > against;
        }
    }

    private static final class LessThan implements LongPredicate {

        private final long against;

        public LessThan(long against) {
            this.against = against;
        }

        boolean done;

        @Override
        public boolean test(long value) {
            boolean result = value < against;
            done = !result;
            return result;
        }
    }

    public static void main(String... args) throws IOException {
        try {
            Settings s = new SettingsBuilder().parseCommandLineArguments(SHORT_COMMANDS, args).build();
            if (s.getBoolean(HELP, false)) {
                printHelpAndExit();
            }
            Set<String> keys = s.allKeys();
            Set<String> unknowns = new HashSet<>(keys);
            unknowns.removeAll(keys);
            if (!unknowns.isEmpty()) {
                exit(5, "Unknown option(s) '" + Strings.join(", ", unknowns));
            }

            if (keys.isEmpty() || s.getBoolean(HELP, false)) {
                printHelpAndExit();
            }
            String outfile = s.getString(OUTFILE);
            Path outPath = outfile != null ? Paths.get(outfile) : null;
            if (outPath != null && !Files.exists(outPath.getParent())) {
                exit(7, "Parent folder of " + outPath + " does not exist.");
            }
            if (keys.contains(OFFSET) && (keys.contains(GREATER_THAN))) {
                exit(1, "Cannot specify both an offset and a greater-than value.");
            }
            if (keys.contains(END_OFFSET) && (keys.contains(LESS_THAN))) {
                exit(1, "Cannot specify both an end offset and a less-than value.");
            }

            String infile = s.getString(INFILE);
            if (infile == null) {
                exit(4, "--infile not specified - no input file.");
            }
            boolean outputGaps = s.getBoolean(GAPS, false);
            boolean outputDistinctGaps = s.getBoolean(DISTINCT_GAPS, false);
            boolean outputDifferentialGaps = s.getBoolean(DIFFERENTIAL_GAPS, false);
            boolean outputDistinctDifferentialGaps = s.getBoolean(DISTINCT_DIFFERENTIAL_GAPS, false);
            int ct = 0;
            for (boolean b : new boolean[]{outputGaps, outputDistinctGaps, outputDifferentialGaps, outputDistinctDifferentialGaps}) {
                if (b) {
                    ct++;
                }
            }
            if (ct > 1) {
                exit(5, "Use --gaps or --distinctgaps or --differentialgaps - cannot do more than one at the same time.\n");
            }
            LimitPredicate limit = new LimitPredicate(s.getLong(LIMIT, Long.MAX_VALUE));
            LessThan indexLessThan;
            LessThan valueLessThan;
            LongPredicate indexPredicate = new BasePredicate()
                    .and(new GreaterThan(s.getLong(OFFSET, -1)))
                    .and(indexLessThan = new LessThan(s.getLong(END_OFFSET, Long.MAX_VALUE)));

            LongPredicate valuePredicate = new BasePredicate()
                    .and(new GreaterThan(s.getLong(GREATER_THAN, Long.MIN_VALUE)))
                    .and(valueLessThan = new LessThan(s.getLong(LESS_THAN, Long.MAX_VALUE)))
                    .and(new Random(s.getInt(RANDOM, 0)))
                    .and(limit);

            try (MultiSeqFileReader file = MultiSeqFileReader.fromCommaDelimited(infile)) {
                long offset = s.getLong(OFFSET, 0);
                if (offset != 0) {
                    file.seek(offset);
                } else if (keys.contains(GREATER_THAN)) {
                    offset = file.search(s.getLong(GREATER_THAN), Bias.FORWARD);
                }
                long ix = offset;
                LongConsumer cons = null;
                SeqFile sfile = null;
                NumberSequenceWriter writer = null;
                if (outPath != null) {
                    sfile = new SeqFile(outPath, Mode.OVERWRITE, file.sizeOptimizedHeaderForNewFile());
                    if (s.getBoolean(LOG, false)) {
                        LongConsumer console = outputGaps
                                ? new ConsoleGapsConsumer()
                                : outputDistinctGaps
                                        ? new ConsoleDistinctGapsConsumer()
                                        : outputDifferentialGaps ? new ConsoleDifferentialGapsConsumer()
                                                : outputDistinctDifferentialGaps ? new ConsoleDistinctDifferentialGapsConsumer()
                                                        : new ConsoleLongConsumer();
                        cons = console.andThen(writer = new NumberSequenceWriter(sfile));
                    } else {
                        cons = writer = new NumberSequenceWriter(sfile);
                    }
                } else {
                    cons = outputGaps ? new ConsoleGapsConsumer()
                            : outputDistinctGaps
                                    ? new ConsoleDistinctGapsConsumer()
                                    : outputDifferentialGaps
                                            ? new ConsoleDifferentialGapsConsumer()
                                            : outputDistinctDifferentialGaps ? new ConsoleDistinctDifferentialGapsConsumer()
                                                    : new ConsoleLongConsumer();
                }
                try {
                    while (file.hasNext()) {
                        long val = file.next();
                        if (indexPredicate.test(ix++) && valuePredicate.test(val)) {
                            cons.accept(val);
                        }
                        if (limit.isDone() || indexLessThan.done || valueLessThan.done) {
                            break;
                        }
                    }
                } finally {
                    if (writer != null) {
                        writer.close();
                    }
                    if (sfile != null) {
                        sfile.close();
                    }
                }
            }
        } catch (ConfigurationError | NumberFormatException err) {
            String msg;
            if (err instanceof NumberFormatException) {
                msg = "Not a number - " + err.getMessage().toLowerCase();
            } else {
                msg = err.getMessage();
            }
            if (Main.unitTest) {
                throw err;
            } else {
                exit(1, msg);
            }
        }
    }

    private static class ConsoleLongConsumer implements LongConsumer {

        boolean first = true;
        long chars = 0;

        @Override
        public void accept(long value) {
            if (!first) {
                System.out.print(',');
            } else {
                first = false;
            }
            String s = Long.toString(value);
            if (chars + s.length() > 70) {
                System.out.print('\n');
                System.out.flush();
                chars = s.length() + 1;
            } else {
                chars += s.length() + 1;
            }
            System.out.print(s);
        }
    }

    private static class ConsoleGapsConsumer implements LongConsumer {

        boolean first = true;
        long chars = 0;
        long last = 0;

        @Override
        public void accept(long value) {
            if (last == 0) {
                last = value;
                return;
            }
            if (!first) {
                System.out.print(',');
            } else {
                first = false;
            }
            String s = Long.toString(value - last);
            if (chars + s.length() > 70) {
                System.out.print('\n');
                System.out.flush();
                chars = s.length() + 1;
            } else {
                chars += s.length() + 1;
            }
            System.out.print(s);
            last = value;
        }
    }

    private static class ConsoleDifferentialGapsConsumer implements LongConsumer {

        boolean first = true;
        long chars = 0;
        long last = 0;
        int lastGap = 0;

        {
            System.err.println("created a diff gaps consumer");
        }

        @Override
        public void accept(long value) {
            if (last == 0) {
                last = value;
                return;
            }
            int gap = (int) (value - last);
            if (lastGap == 0) {
                lastGap = gap;
                last = value;
                return;
            }
            if (!first) {
                System.out.print(',');
            } else {
                first = false;
            }
            int diff = lastGap - gap;
            String s = Integer.toString(diff);
            if (chars + s.length() > 70) {
                System.out.print('\n');
                System.out.flush();
                chars = s.length() + 1;
            } else {
                chars += s.length() + 1;
            }
            System.out.print(s);
            last = value;
            lastGap = gap;
        }
    }

    private static class ConsoleDistinctDifferentialGapsConsumer implements LongConsumer {

        boolean first = true;
        long chars = 0;
        long last = 0;
        int lastGap = 0;
        final Set<Integer> gaps = new HashSet<>(4096);

        @Override
        public void accept(long value) {
            if (last == 0) {
                last = value;
                return;
            }
            int gap = (int) (value - last);
            if (lastGap == 0) {
                lastGap = gap;
                last = value;
                return;
            }
            int diff = gap - lastGap;
            
            if (gaps.contains(diff)) {
                lastGap = gap;
                last = value;
                return;
            }
            gaps.add(diff);
            if (!first) {
                System.out.print(',');
            } else {
                first = false;
            }
            String s = Integer.toString(diff);
            if (chars + s.length() > 70) {
                System.out.print('\n');
                System.out.flush();
                chars = s.length() + 1;
            } else {
                chars += s.length() + 1;
            }
            System.out.print(s);
            last = value;
            lastGap = gap;
        }
    }

    private static class ConsoleDistinctGapsConsumer implements LongConsumer {

        boolean first = true;
        long chars = 0;
        long last = 0;
        final IntSet gaps = IntSet.create();

        @Override
        public void accept(long value) {
            if (last == 0) {
                last = value;
                return;
            }
            int gap = (int) (value - last);
            if (gaps.contains(gap)) {
                last = value;
                return;
            }
            gaps.add(gap);
            if (!first) {
                System.out.print(',');
            }
            first = false;
            String s = Integer.toString(gap);
            if (chars + s.length() > 70) {
                System.out.print('\n');
                System.out.flush();
                chars = s.length() + 1;
            } else {
                chars += s.length() + 1;
            }
            System.out.print(s);
            last = value;
        }
    }
}
