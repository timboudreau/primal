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

import com.mastfrog.primes.SeqFile;
import com.mastfrog.primes.SeqFile.Mode;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import static com.mastfrog.sieve.file.Main.exit;
import static com.mastfrog.sieve.file.SieveMain.HELP;
import static com.mastfrog.sieve.file.SieveMain.INFILE;
import static com.mastfrog.sieve.file.SieveMain.formatHelpLine;
import com.mastfrog.util.preconditions.ConfigurationError;
import com.mastfrog.util.strings.Strings;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.search.Bias;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Tim Boudreau
 */
public class GetMain {

    static final String FIRST = "first";
    static final String LAST = "end";
    static final String OFFSET = "offset";
    static final String GREATER_THAN = "greaterthan";
    static final String LESS_THAN = "lessthan";
    static final String NEAREST = "closestto";
    static final String SURPRISE_ME = "surpriseme";
    private static final Map<Character, String> SHORT_COMMANDS = CollectionUtils.<Character, String>map('i').to(INFILE)
            .map('e').to(LAST)
            .map('f').to(FIRST)
            .map('o').to(OFFSET)
            .map('g').to(GREATER_THAN)
            .map('l').to(LESS_THAN)
            .map('n').to(NEAREST)
            .map('s').to(SURPRISE_ME)
            .map('h').to(HELP)
            .map('i').to(INFILE)
            .build();
    static final Set<String> mutuallyExclusive = new HashSet<>(Arrays.asList(FIRST, LAST, OFFSET, GREATER_THAN, LESS_THAN, NEAREST, SURPRISE_ME));

    private static final Map<String, String> HELP_COMMANDS = CollectionUtils.<String, String>map(HELP).to("Print this help.")
            .map(INFILE).to("The input file of prime number")
            .map(FIRST).to("Print the first entry in this file")
            .map(LAST).to("Print the last entry in this file")
            .map(OFFSET).to("Print the nth entry in the file")
            .map(LESS_THAN).to("Print the first entry less than n")
            .map(NEAREST).to("Print the prime value closest to n")
            .map(SURPRISE_ME).to("Print a random prime from this file")
            .buildLinkedHashMap();

    private static void printHelpAndExit() {
        Map<String, Character> cmdForKey = CollectionUtils.reverse(SHORT_COMMANDS);
        StringBuilder sb = new StringBuilder("Usage:\njava -jar sieve.jar get --infile [file] --first--greaterthan [n] --lessthan [n] --closestto [n] --offset [n] --surpriseme   \n\n");
        HELP_COMMANDS.entrySet().forEach((e) -> {
            sb.append("--").append(e.getKey()).append(" -").append(cmdForKey.get(e.getKey())).append('\t').append(formatHelpLine(e.getValue())).append('\n');
//            System.out.println(e.getKey() + "\t" + e.getValue());
        });
        sb.append("\nReads single numbers from a file output by the sieve command.");
        System.err.println(sb.toString());
        System.exit(1);
    }

    public static void main(String[] args) throws IOException {
        try {
            Settings s = new SettingsBuilder().parseCommandLineArguments(SHORT_COMMANDS, args).build();
            if (s.getBoolean(HELP, false)) {
                printHelpAndExit();
            }
            Set<String> found = new HashSet<String>();
            String command = "";
            Set<String> keys = s.allKeys();
            Set<String> unknowns = new HashSet<>(keys);
            unknowns.removeAll(keys);
            if (!unknowns.isEmpty()) {
                System.err.println("Unknown option(s) '" + Strings.join(", ", unknowns));
                System.exit(5);
            }
            for (String cmd : mutuallyExclusive) {
                if (keys.contains(cmd)) {
                    command = cmd;
                    found.add("--" + cmd);
                }
            }
            if (found.size() > 1) {
                exit(2, "Options " + Strings.join(", ", found) + " are mutually exclusive - use only one of them.");
            }
            if (found.isEmpty() && !keys.isEmpty()) {
                exit(3, "Unknown option " + command);
            }

            if (keys.isEmpty() || s.getBoolean(HELP, false)) {
                printHelpAndExit();
            }

            String infile = s.getString(INFILE);
            if (infile == null) {
                exit(4, "--infile not specified - no input file.");
            }
            Path pth = Paths.get(infile);
            if (!Files.exists(pth)) {
                System.err.println("File does not exist: " + pth);
            }
            try (SeqFile file = new SeqFile(pth, Mode.READ)) {
//            NumberSequenceReader reader = new NumberSequenceReader(file);
                long val;
                switch (command) {
                    case FIRST:
                        val = file.get(0);
                        break;
                    case LAST:
                        val = file.get(file.header().count() - 1);
                        break;
                    case OFFSET:
                        long offset = s.getLong(OFFSET);
                        val = file.get(offset);
                        break;
                    case LESS_THAN:
                        long lt = s.getLong(LESS_THAN);
                        val = file.search(lt, Bias.BACKWARD);
                        if (val != -1) {
                            val = file.get(val);
                        }
                        break;
                    case GREATER_THAN:
                        long gt = s.getLong(GREATER_THAN);
                        val = file.search(gt, Bias.FORWARD);
                        if (val != -1) {
                            val = file.get(val);
                        }
                        break;
                    case NEAREST:
                        long nr = s.getLong(NEAREST);
                        val = file.search(nr, Bias.NEAREST);
                        if (val != -1) {
                            val = file.get(val);
                        }
                        break;
                    case SURPRISE_ME:
                        SecureRandom rmd = new SecureRandom();
                        long randomIndex = Math.abs(rmd.nextLong() % file.header().count());
                        val = file.get(randomIndex);
                        break;
                    default:
                        throw new AssertionError(command);
                }
                System.out.println(val + "\n");
            }
        } catch (ConfigurationError | NumberFormatException err) {
            String msg;
            if (err instanceof NumberFormatException) {
                msg = "Not a number - " + err.getMessage().toLowerCase();
            } else {
                msg = err.getMessage() + "";
            }
            if (Main.unitTest) {
                throw err;
            }
            exit(10, msg);
        }
    }
}
