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

import android.content.Context
import android.content.Intent
import android.util.Log
import xyz.mordorx.sicmu.R

class Scrobble(
    private val rows: Rows,
    private val params: Preferences,
    private val context: Context
) {
    private var started = false
    private var artist: String? = null
    private var album: String? = null
    private var track: String? = null
    private var durationMs: Long = 0


    // can be called with SCROBBLE_COMPLETE state twice or more
    fun send(scrobbleState: Int) {
        if (!params.scrobble) return

        val rowSong = rows.currSong
        if (rowSong == null) {
            Log.w("MusicService", "scrobbleSend exit as rowSong is null!")
            return
        }

        if (scrobbleState == SCROBBLE_START) {
            started = true
            // save scrobble info for next state
            artist = rowSong.artist
            album = rowSong.album
            track = rowSong.title
            durationMs = rowSong.durationMs
        } else if (scrobbleState == SCROBBLE_COMPLETE) {
            // send complete only if SCROBBLE_START was send before
            if (!started) return

            started = false
        }

        // from https://github.com/tgwizard/sls/blob/master/Developer%27s%20API.md
        var bCast = Intent("com.adam.aslfms.notify.playstatechanged")
        bCast.putExtra("state", scrobbleState)
        bCast.putExtra("app-name", context.getResources().getString(R.string.app_name))
        bCast.putExtra("app-package", context.packageName)
        bCast.putExtra("artist", artist)
        bCast.putExtra("album", album)
        bCast.putExtra("track", track)
        bCast.putExtra("duration", durationMs / 1000)
        bCast.putExtra("source", "P")
        context.sendBroadcast(bCast)

        // from https://github.com/JJC1138/scrobbledroid/wiki/Developer-API
        bCast = Intent("net.jjc1138.android.scrobbler.action.MUSIC_STATUS")
        bCast.putExtra(
            "playing",
            scrobbleState == SCROBBLE_START || scrobbleState == SCROBBLE_RESUME
        )
        bCast.putExtra("artist", artist)
        bCast.putExtra("album", album)
        bCast.putExtra("track", track)
        bCast.putExtra("secs", durationMs / 1000)
        bCast.putExtra("source", "P")
        context.sendBroadcast(bCast)

        Log.d("MusicService", "scrobbleSend " + scrobbleState + " : " + artist + " - " + track)
    }

    companion object {
        // from API specification
        const val SCROBBLE_START: Int = 0
        const val SCROBBLE_RESUME: Int = 1
        const val SCROBBLE_PAUSE: Int = 2
        const val SCROBBLE_COMPLETE: Int = 3
    }
}
