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
package xyz.mordorx.sicmu.collections

import xyz.mordorx.sicmu.data.Row
import xyz.mordorx.sicmu.data.RowSong

class PathRowComparator(private val showFilename: Boolean) : Comparator<Row?> {
    val alphaNumComparator: AlphaNumComparator

    init {
        alphaNumComparator = AlphaNumComparator()
    }

    override fun compare(first: Row?, second: Row?): Int {
        // only Song has been added so far, so unchecked cast is ok
        val a = first as RowSong
        val b = second as RowSong
        var cmp = a.folder.compareTo(b.folder, ignoreCase = true)
        if (cmp == 0) {
            cmp = a.artist!!.compareTo(b.artist!!, ignoreCase = true)
            if (cmp == 0) {
                if (!showFilename) {
                    cmp = a.album!!.compareTo(b.album!!, ignoreCase = true)
                    if (cmp == 0) {
                        cmp = a.track - b.track
                    }
                } else {
                    cmp = alphaNumComparator.compare(a.filename, b.filename)
                    //cmp = Path.getFilename(a.getPath()).compareToIgnoreCase(Path.getFilename(b.getPath()));
                }
            }
        }
        return cmp
    }
}
