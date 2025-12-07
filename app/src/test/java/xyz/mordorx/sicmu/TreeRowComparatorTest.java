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


public class TreeRowComparatorTest {

    private RowSong createSong(String title, int track, String path) {
        return new RowSong(null, 0, 0, 0, title, "artist",
                "album", 0, track, path, 0, 2000,
                "", null);
    }

    String[] filenameSongsSorted = {
            "01 filename only.mp3",
            "02 filename+title.mp3",
            "03 filename+tracknr.mp3",
            "04 filename+title+tracknr.mp3",
            "05 filename+wrong title.mp3",
            "06 filename+wrong tracknr.mp3",
            "07 filename+title+tracknr.mp3",
            "afilename.mp3",
            "zfilename.mp3",
    };

    void checkSortedPath(String[] expectedSongsPath, ArrayList<Row> rows) {
        assertEquals(rows.size(), expectedSongsPath.length);
        for(int i = 0; i < expectedSongsPath.length; i++)
            assertEquals(expectedSongsPath[i], ((RowSong) rows.get(i)).getPath());
    }
    void checkSortedTrack(ArrayList<Row> rows) {
        for(int i = 0; i < rows.size(); i++)
            assertEquals(i, ((RowSong) rows.get(i)).getTrack());
    }

    @Test
    public void testSimple() {
        ArrayList<Row> rows = new ArrayList<>();
        rows.add(createSong("title2", 0, "/path/title2"));
        rows.add(createSong("title1", 0, "/path/title1"));
        @SuppressWarnings("unchecked")
        ArrayList<Row> rowsSorted = (ArrayList<Row>) rows.clone();
        TreeRowComparator treeRowComparator = new TreeRowComparator(true);
        Collections.sort(rowsSorted, treeRowComparator);
        assertEquals(rows.get(0), rowsSorted.get(1));
        assertEquals(rows.get(1), rowsSorted.get(0));
    }

    @Test
    public void testShowFilename1() {
        ArrayList<Row> rows = new ArrayList<>();
        Arrays.stream(filenameSongsSorted).forEach(str ->
                rows.add(createSong("title2", 0, str)));
        TreeRowComparator treeRowComparator = new TreeRowComparator(true);
        Collections.sort(rows, treeRowComparator);

        checkSortedPath(filenameSongsSorted, rows);
    }

    @Test
    public void testShowFilename2() {
        ArrayList<Row> rows = new ArrayList<>();
        Arrays.stream(new Integer[]{8, 4, 2, 3, 1, 7, 0, 6, 5}).forEach(i ->
                rows.add(createSong("title2", 0, filenameSongsSorted[i])));
        TreeRowComparator treeRowComparator = new TreeRowComparator(true);
        Collections.sort(rows, treeRowComparator);
        checkSortedPath(filenameSongsSorted, rows);
    }

    @Test
    public void testTrack() {
        ArrayList<Row> rows = new ArrayList<>();
        Arrays.stream(new Integer[]{4, 8, 2, 3, 7, 1, 0, 6, 5}).forEach(i ->
                rows.add(createSong(filenameSongsSorted[i], i, filenameSongsSorted[i])));
        TreeRowComparator treeRowComparator = new TreeRowComparator(false);
        Collections.sort(rows, treeRowComparator);
        checkSortedTrack(rows);
    }

    // todo more test


    @Test
    public void testPathComparator() {
        ArrayList<Row> rows = new ArrayList<>();
        Arrays.stream(new Integer[]{4, 8, 2, 3, 7, 1, 0, 6, 5}).forEach(i ->
                rows.add(createSong(filenameSongsSorted[i], i, filenameSongsSorted[i])));
        TreeRowComparator treeRowComparator = new TreeRowComparator(false);
        Collections.sort(rows, treeRowComparator);
        checkSortedTrack(rows);
    }
}

