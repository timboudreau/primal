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

import com.mastfrog.util.strings.Strings;
import com.mastfrog.util.collections.CollectionUtils;
import java.util.Map;

/**
 *
 * @author Tim Boudreau
 */
public class Main {

    static boolean unitTest;

    static void exit(int code, String... messages) {
        if (unitTest) {
            throw new Error("Exit " + code + " - " + Strings.join(',', messages));
        }
        for (String s : messages) {
            System.err.println(s);
        }
        System.exit(code);
    }

    private static final String[] COMMANDS = new String[]{"sieve", "get", "read", "info"};

    private static final Map<String, String> help = CollectionUtils.<String, String>map("sieve").to("Generate files or output of prime numbers by sieving primes.")
            .map("get").to("Get specific values from a sieve output file")
            //            .map("filter").to("Optimize a sieve file to use no more bits than necessary, optionally changing the ratio of offset-entries to full entries")
            .map("read").to("Read and filter values from a sieve file to the standard output or to a new file")
            .map("info").to("Print sequence file metadata")
            .build();

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printHelpAndExit();
        }
        String cmd = args[0];
        String[] remainingArgs = new String[args.length - 1];
        System.arraycopy(args, 1, remainingArgs, 0, remainingArgs.length);
        switch (cmd.toLowerCase()) {
            case "sieve":
                SieveMain.main(remainingArgs);
                break;
            case "get":
                GetMain.main(remainingArgs);
                break;
            case "read":
                ReadMain.main(remainingArgs);
                break;
            case "info":
                InfoMain.main(remainingArgs);
                break;
            default:
                System.err.println("Unknown command '" + cmd + "'");
                System.exit(1);
                return;
        }
    }

    private static void printHelpAndExit() {
        StringBuilder sb = new StringBuilder();
        sb.append("Usage: java -jar primes.jar [COMMAND] -x --whatever\n\nPass a command and --help to see command-specific options.\n\n"
                + "Commands:\n");
        for (Map.Entry<String, String> e : help.entrySet()) {
            sb.append("\t").append(e.getKey()).append("\t").append('\n');
        }
        exit(1, sb.toString());
    }
}
