/*
 * SicMu Player - Lightweight music player for Android
 * Copyright (C) 2022  Mathieu Souchaud
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package xyz.mordorx.sicmu;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


public class AlphaNumComparatorTest {
    @Test
    public void testTwoSorted() {
        List<String> values = Arrays.asList("1", "2");
        List<String> valuesSorted = Arrays.asList("1", "2");
        Collections.sort(values, new AlphaNumComparator());
        assertEquals(valuesSorted, values);
    }

    @Test
    public void testTwoUnsorted() {
        List<String> values = Arrays.asList("2", "1");
        List<String> valuesSorted = Arrays.asList("1", "2");
        Collections.sort(values, new AlphaNumComparator());
        assertEquals(valuesSorted, values);
    }

    @Test
    public void testUsualFilenames() {
        List<String> filenameSongsSorted = Arrays.asList("01 filename only.mp3",
                "02 filename+title.mp3",
                "03 filename+tracknr.mp3",
                "04 filename+title+tracknr.mp3",
                "05 filename+wrong title.mp3",
                "06 filename+wrong tracknr.mp3",
                "07 filename+title+tracknr.mp3",
                "007.afilename.mp3",
                "afilename04.mp3",
                "afilename5.mp3",
                "afilename006.mp3",
                "afilename.mp3",
                "zfilename4.mp3",
                "zfilename.mp3"
                );
        List<String> values = new ArrayList<>();
        Arrays.stream(new Integer[]{8, 4, 2, 9, 10, 3, 1, 11, 12, 7, 13, 0, 6, 5}).forEach(i ->
                values.add(filenameSongsSorted.get(i)));
        Collections.sort(values, new AlphaNumComparator());
        assertEquals(filenameSongsSorted, values);
    }

    @Test
    public void testNumberAtTheEnd() {
//        System.out.println(values.stream().sorted(new AlphaNumComparator()).collect(Collectors.joining(" ")));
        List<String> values = Arrays.asList("dazzle2", "dazzle10", "dazzle1", "dazzle2.7", "dazzle2.10",
                "2", "10", "1", "EctoMorph6", "EctoMorph62", "EctoMorph7");
        List<String> valuesSorted = Arrays.asList("1", "2", "10", "EctoMorph6", "EctoMorph7",
                "EctoMorph62", "dazzle1", "dazzle2", "dazzle2.7", "dazzle2.10", "dazzle10");
        Collections.sort(values, new AlphaNumComparator());
        assertEquals(valuesSorted, values);
    }

    @Test
    public void testMp3() {
        List<String> values = Arrays.asList("aa.mp3", "bb2.mp3");
        List<String> valuesSorted = Arrays.asList("aa.mp3", "bb2.mp3");
        Collections.sort(values, new AlphaNumComparator());
        assertEquals(valuesSorted, values);
    }
}

