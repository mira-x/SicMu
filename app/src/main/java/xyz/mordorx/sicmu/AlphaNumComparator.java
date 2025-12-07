package xyz.mordorx.sicmu;

/*
 * found at: https://stackoverflow.com/a/104709/1353930
 * downloaded from: http://www.davekoelle.com/files/AlphanumComparator.java
 * location of fixed file: https://pastebin.com/tbEYj2zf
 * local modifications by Daniel Alder:
 * - static alphaNumOrder() / instance
 * - package declaration
 * - fixed sorting of leading zeroes
 */

/*
 * The Alphanum Algorithm is an improved sorting algorithm for strings
 * containing numbers.  Instead of sorting numbers in ASCII order like
 * a standard sort, this algorithm sorts numbers in numeric order.
 *
 * The Alphanum Algorithm is discussed at http://www.DaveKoelle.com
 *
 * Released under the MIT License - https://opensource.org/licenses/MIT
 *
 * Copyright 2007-2017 David Koelle
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE
 * USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

import java.util.Comparator;

/**
 * This is an updated version with enhancements made by Daniel Migowski,
 * Andre Bogus, and David Koelle. Updated by David Koelle in 2017.
 *
 * To use this class:
 *   Use the static "sort" method from the java.util.Collections class:
 *   Collections.sort(your list, new AlphanumComparator());
 */
public class AlphaNumComparator implements Comparator<String>
{
    private boolean isDigit(char ch)
    {
        return ((ch >= 48) && (ch <= 57));
    }

    /** Length of string is passed in for improved efficiency (only need to calculate it once) **/
    private String getChunk(String s, int len, int marker)
    {
        StringBuilder chunk = new StringBuilder();
        char c = s.charAt(marker);
        chunk.append(c);
        marker++;
        if (isDigit(c))
        {
            while (marker < len)
            {
                c = s.charAt(marker);
                if (!isDigit(c))
                    break;
                chunk.append(c);
                marker++;
            }
        } else
        {
            while (marker < len)
            {
                c = s.charAt(marker);
                if (isDigit(c))
                    break;
                chunk.append(c);
                marker++;
            }
        }
        return chunk.toString();
    }

    public int compare(String s1, String s2)
    {
        if ((s1 == null) || (s2 == null))
        {
            return 0;
        }

        int thisMarker = 0;
        int thatMarker = 0;
        int s1Length = s1.length();
        int s2Length = s2.length();

        while (thisMarker < s1Length && thatMarker < s2Length)
        {
            String thisChunk = getChunk(s1, s1Length, thisMarker);
            thisMarker += thisChunk.length();

            String thatChunk = getChunk(s2, s2Length, thatMarker);
            thatMarker += thatChunk.length();

            // If both chunks contain numeric characters, sort them numerically
            int result = 0;
            if (isDigit(thisChunk.charAt(0)) && isDigit(thatChunk.charAt(0)))
            {
                final int thisLen = thisChunk.length();
                final int thatLen = thatChunk.length();
                final int bothLen = Math.max(thisLen, thatLen);
                for (int thisPos = thisLen - bothLen, thatPos = thatLen - bothLen;
                     thisPos < thisLen; thisPos++, thatPos++)
                {
                    final char thisChar = thisPos < 0 ? '0' : thisChunk.charAt(thisPos);
                    final char thatChar = thatPos < 0 ? '0' : thatChunk.charAt(thatPos);
                    result = thisChar - thatChar;
                    if (result != 0) {
                        return result;
                    }
                }
            }
            else
            {
                result = thisChunk.compareTo(thatChunk);
            }

            if (result != 0)
                return result;
        }

        int result = s1Length - s2Length;
        if (result != 0)
            return result;

        return s1.compareTo(s2);
    }
}