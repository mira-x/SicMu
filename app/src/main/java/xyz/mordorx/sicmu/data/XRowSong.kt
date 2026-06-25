/*
 * SicMu Player - Lightweight music player for Android
 * Copyright (C) 2015  Mathieu Souchaud
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
package xyz.mordorx.sicmu.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlin.io.path.Path

/**
 * This subclass of `Row` represents a single media file.
 */
data class XRowSong(
    override val level: Int,
    //override val parent: XRow?,
    override val containingFolder: String,
    /** Sanitized file name */
    override val fileName: String,
    override val isSelected: Boolean,
    /** The ID of this song provided by the MediaStore API */
    val id: Long,
    /** The Album ID of this song provided by the MediaStore API */
    val albumId: Long,
    val durationMs: Long,
    val externalContentUri: Uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),

    val title: String?,
    val artist: String?,
    val album: String?,
    val year: Int?,
    val mimeType: String?,
) : XRow {
    val fullPath = Path(containingFolder, fileName)



    val youtubeSearchTerm: String
        /**
         * Returns a list of keywords extracted from file path and metadata. This also removes disturbing things like punctuation marks or things in parenthesis. This is meant to be used in search engines like Google, Genius.com or YouTube.
         * 
         * @return A list of keywords in no particular order or form.
         */
        get() {
            var searchTerm = ""
            val title = this.title ?: ""
            val artist = this.artist ?: ""
            if (title.isBlank() || artist.isBlank()) {
                searchTerm = this.fileName
                // remove extension (any dot that is in the last 5 characters)
                if (searchTerm.contains(".") && searchTerm.lastIndexOf('.') >= searchTerm.length - 5) {
                    searchTerm = searchTerm.substring(0, searchTerm.lastIndexOf('.'))
                }
            } else {
                searchTerm = this.artist + " " + this.title
            }


            // Remove dashes
            searchTerm = searchTerm.replace(" - ".toRegex(), " ")
            // Remove remixes, like "Simply Red - Something got me started (Hourleys House Remix)"
            // We only delete those at the end of the string, for instance, to keep this song title intact:
            // "(This song is just) six words long.mp3" (A song from 'Weird Al' Yankovic)
            searchTerm = searchTerm.replace("\\(([^)]+)\\)$".toRegex(), "")
            // Remove leading numbers (01. some song, 2. some song)
            searchTerm = searchTerm.replace("^(\\d+).".toRegex(), "")
            // Remove braces, Genius will cut off the string beginning at the first brace
            searchTerm = searchTerm.replace("\\(".toRegex(), "")
            searchTerm = searchTerm.replace("\\)".toRegex(), "")

            Log.d(
                "SearchTerm",
                String.format("Search term is '%s' for file %s/%s", searchTerm, this.containingFolder, fileName)
            )

            return searchTerm
        }

    companion object {
        @JvmOverloads
        fun msToMinutes(durationMs: Long, showSeconds: Boolean = true): String {
            var seconds = durationMs / 1000
            val minutes = seconds / 60
            if (showSeconds) {
                seconds %= 60
                return minutes.toString() + (if (seconds < 10) ":0" else ":") + seconds
            } else {
                return minutes.toString()
            }
        }
    }
}