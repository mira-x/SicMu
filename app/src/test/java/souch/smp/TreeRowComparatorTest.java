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

package souch.smp;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;


public class TreeRowComparatorTest {

    private RowSong createSong(String title, int track, String path) {
        return new RowSong(null, 0, 0, 0, title, "artist",
                "album", 0, track, path, 0, 2000,
                "", null);
    }

    @Test
    public void testSimple() {
        ArrayList<Row> rows = new ArrayList<>();
        rows.add(createSong("title2", 0, "/path/title2"));
        rows.add(createSong("title1", 0, "/path/title1"));
        ArrayList<Row> rowsSorted = (ArrayList<Row>) rows.clone();
        TreeRowComparator treeRowComparator = new TreeRowComparator(true);
        Collections.sort(rowsSorted, treeRowComparator);
        assertEquals(rows.get(0), rowsSorted.get(1));
        assertEquals(rows.get(1), rowsSorted.get(0));
    }

    @Test
    public void testDaald() {
        ArrayList<Row> rows = new ArrayList<>();
        String[] strAr = {
                "01 filename only.mp3",
                "02 filename+title.mp3",
                "03 filename+tracknr.mp3",
                "04 filename+title+tracknr.mp3",
                "05 filename+wrong title.mp3",
                "06 filename+wrong tracknr.mp3",
                "07 filename+title+tracknr.mp3"
        };
        Arrays.stream(strAr).forEach(str ->
                rows.add(createSong("title2", 0, str)));
        ArrayList<Row> rowsSorted = (ArrayList<Row>) rows.clone();
        TreeRowComparator treeRowComparator = new TreeRowComparator(true);
        Collections.sort(rowsSorted, treeRowComparator);

        for(int i = 0; i < strAr.length; i++)
                assertEquals(rows.get(i), rowsSorted.get(i));
    }

    @Test
    public void testDaald2() {
        ArrayList<Row> rows = new ArrayList<>();
        String[] strAr = {
                "05 filename+wrong title.mp3",
                "03 filename+tracknr.mp3",
                "04 filename+title+tracknr.mp3",
                "02 filename+title.mp3",
                "01 filename only.mp3",
                "07 filename+title+tracknr.mp3",
                "06 filename+wrong tracknr.mp3",
        };
        Arrays.stream(strAr).forEach(str ->
                rows.add(createSong("title2", 0, str)));
        ArrayList<Row> rowsSorted = (ArrayList<Row>) rows.clone();
        TreeRowComparator treeRowComparator = new TreeRowComparator(true);
        Collections.sort(rowsSorted, treeRowComparator);

        assertEquals(rows.get(0), rowsSorted.get(4));
        assertEquals(rows.get(1), rowsSorted.get(2));
        assertEquals(rows.get(2), rowsSorted.get(3));
        assertEquals(rows.get(3), rowsSorted.get(1));
        assertEquals(rows.get(4), rowsSorted.get(0));
        assertEquals(rows.get(5), rowsSorted.get(6));
        assertEquals(rows.get(6), rowsSorted.get(5));
    }
}

