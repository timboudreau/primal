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
import com.mastfrog.util.strings.AlignedText;
import java.io.IOException;
import java.nio.BufferUnderflowException;
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
            SeqFileHeader h = file.header();

            StringBuilder sb = new StringBuilder();

            long estimated = h.estimatedCount(Files.size(pth));

            sb.append("Sequence File\t").append(pth.getFileName()).append("\t").append(Files.size(pth)).append("\tbytes\n\n");
            sb.append("\tFormat version:\t").append(h.version()).append("\n");
            sb.append("\tHeader length:\t").append(h.headerLength()).append("\n");
            sb.append("\tData section length:\t").append(NUMBERS.format(h.dataLength())).append(" bytes\n");
            sb.append("\tFile size:\t").append(NUMBERS.format(Files.size(pth))).append(" bytes\n");
            sb.append("\tEntry count recorded in header:\t").append(NUMBERS.format(h.count())).append("\n");
            sb.append("\tEstimated entry count from file size:\t").append(NUMBERS.format(estimated)).append("\n");
            sb.append("\tBits per full value:\t").append(h.bitsPerFullEntry()).append("\n");
            sb.append("\tBits per offset value:\t").append(h.bitsPerOffsetEntry()).append("\n");
            sb.append("\tOffset values per full value:\t").append(NUMBERS.format(h.offsetEntriesPerFullEntry())).append("\n");
            sb.append("\tMax gap between values:\t").append(h.maxOffset() * 2).append("\n");
            sb.append("\tActual bits required for gaps:\t").append(SeqFileHeader.bitsRequiredForPrimeOffset(h.maxOffset())).append("\n");
            long first = 0;
            try {
                first = file.first();
                sb.append("\tFirst value: \t").append(NUMBERS.format(first)).append("\n");
            } catch (BufferUnderflowException ex) {
                sb.append("\n\t!!!! tFile is truncated to just its header\n\n");
            }
            long last = 0;
            try {
                last = file.last();
                sb.append("\tActual bits required for full values:\t").append(SeqFileHeader.bitsRequired(last)).append("\n");
                sb.append("\tLast value: \t").append(NUMBERS.format(last)).append("\n");
            } catch (BufferUnderflowException ex) {
                sb.append("!!!!\tFile is truncated to shorter than expected length\n\n");
            }
            sb.append("\n");
            int bitsForOffsets = SeqFileHeader.bitsRequiredForPrimeOffset(h.maxOffset());
            if (bitsForOffsets > 1 && bitsForOffsets < h.bitsPerOffsetEntry()) {
                sb.append("!!!\tThis file can be optimized to use fewer\n!!!\tbits per offset:\n").append(bitsForOffsets).append("\n\n");
            }
            if (last != 0) {
                int bitsForFull = SeqFileHeader.bitsRequired(last);
                if (bitsForFull > 1 && bitsForFull < h.bitsPerFullEntry()) {
                    sb.append("!!!\tThis file can be optimized to use\n!!!\tfewer bits per full value:\t").append(bitsForFull).append("\n\n");
                }
            }
            if (estimated != h.count()) {
                if (h.count() == 0) {
                    sb.append("!!!\tHeader says file is empty. Probably\n!!!\tsieving was interrupted.\n!!!\tTry using `primal repair -i "
                            + pth.getFileName() + "`\n");
                } else {
                    sb.append("!!!\tActual entry count and file-sized based\n!!!\testimate differ.  Try using `primal repair -i "
                            + pth.getFileName() + "`\n");
                }
            }
            sb.append("\n");
            System.out.println(AlignedText.formatTabbed(sb));
        }
    }
}
