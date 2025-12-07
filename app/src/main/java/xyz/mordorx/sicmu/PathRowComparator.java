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

import java.util.Comparator;

public class PathRowComparator implements Comparator<Row> {
    private boolean showFilename;
    AlphaNumComparator alphaNumComparator;

    public PathRowComparator(boolean showFilename) {
        this.showFilename = showFilename;
        alphaNumComparator = new AlphaNumComparator();
    }

    public int compare(Row first, Row second) {
        // only Song has been added so far, so unchecked cast is ok
        RowSong a = (RowSong) first;
        RowSong b = (RowSong) second;
        int cmp = a.getFolder().compareToIgnoreCase(b.getFolder());
        if (cmp == 0) {
            cmp = a.getArtist().compareToIgnoreCase(b.getArtist());
            if (cmp == 0) {
                if (!showFilename) {
                    cmp = a.getAlbum().compareToIgnoreCase(b.getAlbum());
                    if (cmp == 0) {
                        cmp = a.getTrack() - b.getTrack();
                    }
                }
                else {
                    cmp = alphaNumComparator.compare(a.getFilename(), b.getFilename());
                    //cmp = Path.getFilename(a.getPath()).compareToIgnoreCase(Path.getFilename(b.getPath()));
                }
            }
        }
        return cmp;
    }
}
