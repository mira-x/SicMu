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
import kotlin.math.min

class TreeRowComparator(private val showFilename: Boolean) : Comparator<Row?> {
    val alphaNumComparator: AlphaNumComparator

    init {
        alphaNumComparator = AlphaNumComparator()
    }

    override fun compare(first: Row?, second: Row?): Int {
        // only Song has been added so far, so unchecked cast is ok
        val a = first as RowSong
        val b = second as RowSong
        var cmp: Int = compareToIgnoreCaseShorterFolderLast(a.folder, b.folder)
        if (cmp == 0) {
            if (!showFilename) {
                //cmp = a.getArtist().compareToIgnoreCase(b.getArtist());
                cmp = a.album!!.compareTo(b.album!!, ignoreCase = true)
                if (cmp == 0) cmp = a.track - b.track
                if (cmp == 0) cmp = a.title.compareTo(b.title, ignoreCase = true)
            } else {
                cmp = alphaNumComparator.compare(a.filename, b.filename)
                //cmp = Path.getFilename(a.getPath()).compareToIgnoreCase(Path.getFilename(b.getPath()));
            }
        }
        return cmp
    }


    companion object {
        /**
         * From Android String.java
         * 
         * 
         * modify compareToIgnoreCase in order to put shorter group to the end e.g.
         * normal compareToIgnoreCase order
         * /toto
         * /toto/tata
         * /toto/titi
         * 
         * 
         * modified order (here)
         * /toto/tata
         * /toto/titi
         * /toto
         * 
         * 
         * Compares this string to the given string, ignoring case differences.
         * 
         * 
         * The drawback of this method being outside of String.java is that it is slower as it does not
         * play with internal string data (especially charAt calls). Rows initialization lose 15% of speed.
         */
        fun compareToIgnoreCaseShorterFolderLast(string1: String, string2: String): Int {
            var o1 = 0
            var o2 = 0
            var result: Int
            val end = (min(string1.length, string2.length))
            var c1: Char
            var c2: Char
            while (o1 < end) {
                if ((string1.get(o1++).also { c1 = it }) == (string2.get(o2++).also { c2 = it })) {
                    continue
                }
                c1 = foldCase(c1)
                c2 = foldCase(c2)
                if (((c1.code - c2.code).also { result = it }) != 0) {
                    return result
                }
            }
            return string2.length - string1.length // modified here
        }

        /**
         * useful for compareToIgnoreCaseShorterFolderLast
         */
        private fun foldCase(ch: Char): Char {
            if (ch.code < 128) {
                if ('A' <= ch && ch <= 'Z') {
                    return (ch.code + ('a'.code - 'A'.code)).toChar()
                }
                return ch
            }
            return ch.uppercaseChar().lowercaseChar()
        }
    }
}
