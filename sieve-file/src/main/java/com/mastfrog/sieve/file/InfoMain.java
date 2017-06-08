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
import com.mastfrog.primes.SeqFileHeader;
import static com.mastfrog.sieve.file.Main.exit;
import static com.mastfrog.sieve.file.SieveMain.NUMBERS;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 *
 * @author Tim Boudreau
 */
public class InfoMain {

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            exit(1, "No file specified");
        }
        Path pth = Paths.get(args[0]);
        try (SeqFile file = new SeqFile(pth, Mode.READ, Optional.empty())) {
            long first = file.first();
            long last = file.last();
            SeqFileHeader h = file.header();
            System.out.println(pth.getFileName() + ":");
            System.out.println("\tFormat version:\t\t" + h.version());
            System.out.println("\tHeader length:\t\t" + h.headerLength());
            System.out.println("\tData section length:\t" + NUMBERS.format(h.dataLength()) + " bytes");
            System.out.println("\tFile size:\t\t" + NUMBERS.format(Files.size(pth)) + " bytes");
            System.out.println("\tEntries:\t\t" + NUMBERS.format(h.count()));
            System.out.println("\tBits per full value:\t" + h.bitsPerFullEntry());
            System.out.println("\tBits per offset value:\t" + h.bitsPerOffsetEntry());
            System.out.println("\tOffset values per full value:\t" + NUMBERS.format(h.offsetEntriesPerFullEntry()));
            System.out.println("\tMax gap between values:\t" + (h.maxOffset() * 2));
            System.out.println("\tActual bits required for gaps:\t" + (SeqFileHeader.bitsRequiredForPrimeOffset(h.maxOffset())));
            System.out.println("\tActual bits required for full values:\t" + SeqFileHeader.bitsRequired(last));
            System.out.println("");
            System.out.println("\tFirst value: \t" + NUMBERS.format(first));
            System.out.println("\tLast value: \t" + NUMBERS.format(last));
            
            if (SeqFileHeader.bitsRequiredForPrimeOffset(h.maxOffset()) < h.bitsPerOffsetEntry()) {
                System.out.println("\t\tThis file can be optimized to use fewer bits per offset");
            }
            if (SeqFileHeader.bitsRequired(last) < h.bitsPerFullEntry()) {
                System.out.println("\t\tThis file can be optimized to use fewer bits per full value");
            }
        }
    }
}
