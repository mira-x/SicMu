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
package xyz.mordorx.sicmu.collections;

import java.util.Comparator;

import xyz.mordorx.sicmu.data.MediaScanner;
import xyz.mordorx.sicmu.data.Row;
import xyz.mordorx.sicmu.data.RowSong;

public class TreeRowComparator implements Comparator<Row> {
    private final boolean showFilename;
    final AlphaNumComparator alphaNumComparator;

    public TreeRowComparator(boolean showFilename) {
        this.showFilename = showFilename;
        alphaNumComparator = new AlphaNumComparator();
    }

    public int compare(Row first, Row second) {
        // only Song has been added so far, so unchecked cast is ok
        RowSong a = (RowSong) first;
        RowSong b = (RowSong) second;
        int cmp = compareToIgnoreCaseShorterFolderLast(a.getFolder(), b.getFolder());
        if (cmp == 0) {
            if (!showFilename) {
                //cmp = a.getArtist().compareToIgnoreCase(b.getArtist());
                cmp = a.getAlbum().compareToIgnoreCase(b.getAlbum());
                if (cmp == 0)
                    cmp = a.getTrack() - b.getTrack();
                if (cmp == 0)
                    cmp = a.getTitle().compareToIgnoreCase(b.getTitle());
            }
            else {
                cmp = alphaNumComparator.compare(a.getFilename(), b.getFilename());
                //cmp = Path.getFilename(a.getPath()).compareToIgnoreCase(Path.getFilename(b.getPath()));
            }
        }
        return cmp;
    }


    /**
     * From Android String.java
     * <p>
     * modify compareToIgnoreCase in order to put shorter group to the end e.g.
     * normal compareToIgnoreCase order
     * /toto
     * /toto/tata
     * /toto/titi
     * <p>
     * modified order (here)
     * /toto/tata
     * /toto/titi
     * /toto
     * <p>
     * Compares this string to the given string, ignoring case differences.
     * <p>
     * The drawback of this method being outside of String.java is that it is slower as it does not
     * play with internal string data (especially charAt calls). Rows initialization lose 15% of speed.
     */
    public static int compareToIgnoreCaseShorterFolderLast(String string1, String string2) {
        int o1 = 0, o2 = 0, result;
        int end = (Math.min(string1.length(), string2.length()));
        char c1, c2;
        while (o1 < end) {
            if ((c1 = string1.charAt(o1++)) == (c2 = string2.charAt(o2++))) {
                continue;
            }
            c1 = foldCase(c1);
            c2 = foldCase(c2);
            if ((result = c1 - c2) != 0) {
                return result;
            }
        }
        return string2.length() - string1.length(); // modified here
    }

    /**
     * useful for compareToIgnoreCaseShorterFolderLast
     */
    private static char foldCase(char ch) {
        if (ch < 128) {
            if ('A' <= ch && ch <= 'Z') {
                return (char) (ch + ('a' - 'A'));
            }
            return ch;
        }
        return Character.toLowerCase(Character.toUpperCase(ch));
    }

}
