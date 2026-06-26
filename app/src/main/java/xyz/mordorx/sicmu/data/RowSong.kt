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
import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.MediaStore
import android.support.v4.media.MediaMetadataCompat
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import xyz.mordorx.sicmu.Main
import xyz.mordorx.sicmu.media.MusicService
import xyz.mordorx.sicmu.ui.RowViewHolder
import java.io.File

/**
 * This subclass of `Row` represents a single media file.
 */
class RowSong(
    private val songDAO: SongDAO, pos: Int, level: Int,
    /** The ID of this song provided by the MediaStore API */
    val iD: Long, val title: String, val artist: String?,
    /** If no album metadata is set, this will return the top level folder name of this song */
    val album: String?,
    val durationMs: Long, @JvmField val track: Int,
    /** For Example: "/storage/emulated/0/Music/_/Unterhaltung/HintShot - Welcome to Team Fortress.opus" */
    // full filename
    @JvmField val path: String, albumId: Long, year: Int, mime: String?) : Row(pos, level, Typeface.NORMAL) {
    /** The Album ID of this song provided by the MediaStore API */
    val albumId: Long
    val year: Int
    val mime: String?
    var rating: Int
        private set

    /** For example: "HintShot - Welcome to Team Fortress.opus" */
    val filename: String

    /** For Example: "_/Unterhaltung" */
    // folder of the path (i.e. last folder containing the file's song)
    val folder: String

    private var metadata: Tag? = null

    override fun setView(holder: RowViewHolder, main: Main?, position: Int) {
        super.setView(holder, main, position)

        if (main == null) return

        var factor = 1.5f
        if (main.musicSrv!!.getRows().isLastRow(position)) factor = 2f
        holder.layout!!.getLayoutParams().height = convertDpToPixels(
            (textSize * factor).toInt(),
            holder.layout.getResources()
        )

        setText(holder.text!!)
        setDuration(holder.duration!!)
        setCurrIcon(holder.image!!, main)
        if (MusicService.enableRating) {
            holder.ratingStar!!.visibility = View.VISIBLE
            holder.ratingStar!!.setImageResource(this.drawableStarFromRating)

            val params = holder.duration.layoutParams as RelativeLayout.LayoutParams
            // removeRule is not in sdk < 17
            params.removeRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            holder.duration.layoutParams = params
        } else {
            holder.ratingStar!!.visibility = View.INVISIBLE

            val params = holder.duration.layoutParams as RelativeLayout.LayoutParams
            params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            holder.duration.layoutParams = params
        }
        setBackgroundColor(holder, backgroundSongColor)
    }

    private fun setText(text: TextView) {
        text.text = this.text
        text.setTextColor(normalSongTextColor)
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize.toFloat())
    }

    val text: String
        get() {
            return filename
        }

    private fun setDuration(duration: TextView) {
        duration.text = msToMinutesStripSecondIfLongDuration(this.durationMs) + stringOffset
        duration.setTextColor(normalSongDurationTextColor)
        duration.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize.toFloat())
        duration.setTypeface(null, typeface)
    }

    private fun setCurrIcon(img: ImageView, main: Main) {
        var currIcon = android.R.color.transparent
        if (this === main.musicSrv!!.getRows().currSong) {
            if (main.musicSrv!!.playingLaunched()) currIcon =
                xyz.mordorx.sicmu.R.drawable.ic_curr_play
            else currIcon = xyz.mordorx.sicmu.R.drawable.ic_curr_pause
        }
        img.setImageResource(currIcon)
        // useful only for the tests
        img.setTag(currIcon)
    }

    override fun toString(): String {
        return "title: " + title + " album: " + album + " artist: " + artist +
                " pos: " + genuinePos + " level: " + level + " ID: " + this.iD +
                msToMinutes(durationMs) + " track:" + track + " path: " + path
    }

    fun deleteFile(context: Context): Boolean {
        if ((File(path)).delete()) {
            // delete it from media store too
            val uri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                this.iD
            )
            context.getContentResolver().delete(uri, null, null)

            return true
        }
        return false
    }

    val isRatingInsufficient: Boolean
        get() {
            val uninitialized =
                rating == RATING_NOT_INITIALIZED || rating == RATING_UNKNOWN
            val minRating = 1 // preferences.minRating // TODO: Add proper logic
            return (!uninitialized /* || preferences.uninitializedDefaultRating < minRating -- TODO: Add proper logic */) &&
                    rating < minRating
        }

    fun interface LoadRatingCallbackInterface {
        /** @param ratingChanged set to true if loadRating brings new RowSong's rating
         * i.e. set to false if RowSong's rating did not change
         */
        fun ratingCallback(rating: Int, ratingChanged: Boolean)
    }

    fun interface LoadMetadataCallbackInterface {
        fun callback(tags: Tag?)
    }

    @Synchronized
    fun loadRatingAsync(ratingCallbackInterface: LoadRatingCallbackInterface) {
        if (rating != RATING_NOT_INITIALIZED) {
            ratingCallbackInterface.ratingCallback(rating, false)
            return
        }

        Thread(Runnable {
            if (AlbumArtLoader.Companion.isTerminated) return@Runnable
            Log.d("RowSong", "loadRating")
            val someRatingChanged = (rating == RATING_NOT_INITIALIZED && loadRating() > 0)
            if (AlbumArtLoader.Companion.isTerminated) return@Runnable
            ratingCallbackInterface.ratingCallback(rating, someRatingChanged)
        }).start()
    }

    /** Make sure to use runOnUiThread() if your callback mutates the UI state!  */
    @Synchronized
    fun loadMetadataAsync(callback: LoadMetadataCallbackInterface) {
        if (metadata != null) {
            callback.callback(metadata)
            return
        }

        Thread(Runnable {
            if (AlbumArtLoader.Companion.isTerminated) return@Runnable
            try {
                val f = File(path)
                val file = AudioFileIO.read(f)
                metadata = file.getTag()
                if (metadata == null) {
                    throw RuntimeException("AudioFileIO.getTag() returned null")
                }
                if (AlbumArtLoader.Companion.isTerminated) return@Runnable
                callback.callback(metadata)
            } catch (e: Exception) {
                Log.e("RowSong", "Could not load metadata tags:", e)
            }
        }).start()
    }

    // @param ratingSynchronized set to true if corresponding file has the rating property correctly set
    private fun updateOrInsertSongOrm(songORM: SongORM?, ratingSynchronized: Boolean = true) {
        try {
            if (songORM != null) {
                Log.d("RowSong", "Update songORM for path=" + path)
                songORM.rating = rating
                songORM.lastModifiedMs = (File(songORM.path)).lastModified()
                songORM.ratingSynchronized = ratingSynchronized
                songDAO.update(songORM)
            } else {
                Log.d("RowSong", "New songORM for path=" + path)
                songDAO.insert(SongORM(path, rating, ratingSynchronized))
            }
        } catch (e: Exception) {
            Log.w(
                "RowSong", ("Unable to update/insert songORM for path=" + path
                        + " e=" + e)
            )
        }
    }

    // ! must not be called from main thread !
    @Synchronized
    fun loadRating(): Int {
        // get rating is computed on demand cause it is slow
        if (rating == RATING_NOT_INITIALIZED) {
            // try to load rating from cache
            val fileLastModifiedMs: Long
            val songORM = songDAO.findByPath(path)
            if (songORM != null) {
                fileLastModifiedMs = (File(songORM.path)).lastModified()
                if (fileLastModifiedMs <= songORM.lastModifiedMs) {
                    //Log.d("RowSong", "Found songORM for path=" + path);
                    rating = songORM.rating
                } else {
                    Log.d("RowSong", "Found songORM for path=" + path + " but cache is obsolete")
                }
            }

            // cache miss, read the ID3
            if (rating == RATING_NOT_INITIALIZED) {
                try {
                    val audioFile = AudioFileIO.read(File(path))
                    val tag = audioFile.getTag()
                    if (tag != null) {
                        if (tag.hasField(FieldKey.RATING)) {
                            rating = convertToRating0to5(tag.getFirst(FieldKey.RATING))
                            Log.d("RowSong", "song rating " + path + " = " + rating)
                        } else {
                            Log.d("RowSong", "song rating " + path + " rating not available")
                        }
                    } else {
                        Log.d("RowSong", "song rating " + path + " tag not available")
                    }
                    if (rating < 0) rating = RATING_UNKNOWN

                    updateOrInsertSongOrm(songORM)
                } catch (e: Exception) {
                    Log.w(
                        "RowSong", "Unable to get rating of song " + path +
                                ". Exception msg: " + e.javaClass + " - " + e.message
                    )
                    // if id3tag read failed we do not update or create an entry in the database
                    // that means, the database will not be used as cached and
                    // if several files can not be read : that will slow down the app
                }
            }
        }
        return rating
    }

    // this func must not be called from main thread !
    // return true if set rating succeed
    @Synchronized
    fun setRating(rating: Int): Boolean {
        this.rating = rating
        val ok: Boolean = WriteRatingToFile(path, rating)
        updateOrInsertSongOrm(songDAO.findByPath(path), ok)

        return ok
    }

    // write rating to file later (useful to modify file when it is not currently reading)
    @Synchronized
    fun scheduleSetRating(rating: Int, async: Boolean) {
        // sync part
        this.rating = rating

        if (async) {
            val thread = Thread(Runnable { updateOrInsertSongOrm(songDAO.findByPath(path), false) })
            thread.start()
        } else {
            updateOrInsertSongOrm(songDAO.findByPath(path), false)
        }
    }

    init {
        val f = File(path)
        filename = f.getName()
        rating = RATING_NOT_INITIALIZED
        this.albumId = albumId
        this.year = year
        this.mime = mime
        folder = MediaScanner.getFolder(path)
    }

    /* rating can be from 0 to 255
     * ex: "64" returns 2
     */
    fun convertToRating0to5(rating: String): Int {
        var note: Int
        try {
            note = rating.toInt()
        } catch (e: Exception) {
            note = 0
        }
        for (i in id3ConventionRating.indices) if (note <= id3ConventionRating[i]) return i
        return id3ConventionRating.size - 1
    }

    val drawableStarFromRating: Int
        get() {
            val drawable: Int
            when (this.rating) {
                1 -> drawable = xyz.mordorx.sicmu.R.drawable.ic_star_1
                2 -> drawable = xyz.mordorx.sicmu.R.drawable.ic_star_2
                3 -> drawable = xyz.mordorx.sicmu.R.drawable.ic_star_3
                4 -> drawable = xyz.mordorx.sicmu.R.drawable.ic_star_4
                5 -> drawable = xyz.mordorx.sicmu.R.drawable.ic_star_5
                else -> drawable = xyz.mordorx.sicmu.R.drawable.ic_star_0
            }
            return drawable
        }

    val externalContentUri: Uri
        get() = ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, this.iD
        )

    fun getMediaMetadata(context: Context): MediaMetadataCompat? {
        val builder = MediaMetadataCompat.Builder()
        builder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, iD.toString())
        builder.putString(
            MediaMetadataCompat.METADATA_KEY_MEDIA_URI,
            this.externalContentUri.toString()
        )
        builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
        builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
        builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
        builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)
        builder.putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, track.toLong())
        builder.putLong(MediaMetadataCompat.METADATA_KEY_YEAR, year.toLong())
        builder.putBitmap(
            MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
            AlbumArtLoader(context, this).load()
        ) // todo: optimize
        //builder.putLong(MediaMetadataCompat.METADATA_KEY_RATING, Integer.valueOf(convertToRating0to255(getRating())));
        return builder.build()
    }

    val searchTerm: String
        /**
         * Returns a list of keywords extracted from file path and metadata. This also removes disturbing things like punctuation marks or things in parenthesis. This is meant to be used in search engines like Google, Genius.com or YouTube.
         * 
         * @return A list of keywords in no particular order or form.
         */
        get() {
            var searchTerm = ""
            if (this.title.isBlank() || this.title == "<unknown>" || this.artist!!.isBlank() || this.artist == "<unknown>") {
                searchTerm = this.filename
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
                String.format("Search term is '%s' for file %s", searchTerm, this.path)
            )

            return searchTerm
        }

    companion object {
        // RATING_NOT_INITIALIZED means we did not tried to read id3 rating
        // actually 0 means not initialized too (we handle reading 0, but it is not very useful, cause
        // we cannot set a rating of 0 star in the UI)
        val RATING_NOT_INITIALIZED: Int = -2

        // RATING_UNKNOWN means we did read id3 rating, but it was not set
        val RATING_UNKNOWN: Int = -1
        var textSize: Int = 15

        // must be set outside before calling setText
        var normalSongTextColor: Int = 0
        var normalSongDurationTextColor: Int = 0
        var backgroundSongColor: Int = 0

        @JvmOverloads
        fun msToMinutes(durationMs: Long, showSeconds: Boolean = true): String {
            var seconds = durationMs / 1000
            val minutes = seconds / 60
            if (showSeconds) {
                seconds = seconds % 60
                return minutes.toString() + (if (seconds < 10) ":0" else ":") + seconds
            } else {
                return minutes.toString()
            }
        }

        fun msToMinutesStripSecondIfLongDuration(durationMs: Long): String {
            return msToMinutes(durationMs, durationMs < 100 * 60 * 1000)
        }

        fun WriteRatingToFile(path: String, rating: Int): Boolean {
            var ok = false
            try {
                val audioFile = AudioFileIO.read(File(path))
                val tag = audioFile.getTagOrCreateAndSetDefault()
                if (tag.hasField(FieldKey.RATING)) tag.setField(
                    FieldKey.RATING,
                    convertToRating0to255(rating)
                )
                else tag.addField(FieldKey.RATING, convertToRating0to255(rating))
                audioFile.commit()
                ok = true
                Log.i("RowSong", "set file rating : " + path + " to " + rating)
            } catch (e: Exception) {
                val wrn = "Unable to set rating for song:" + path +
                        ". Exception msg: " + e.javaClass + " - " + e.message
                Log.w("RowSong", wrn)
            }
            return ok
        }

        /*
        224–255 = 5 stars when READ with Windows Explorer, writes 255
        160–223 = 4 stars when READ with Windows Explorer, writes 196
        096-159 = 3 stars when READ with Windows Explorer, writes 128
        032-095 = 2 stars when READ with Windows Explorer, writes 64
        001-031 = 1 star when READ with Windows Explorer, writes 1
    */
        // convert table 0-5 -> 0-255
        val id3ConventionRating: IntArray = intArrayOf(0, 1, 64, 128, 196, 255)

        /* rating can be from 0 to 5
     * ex: 3 return "128"
     */
        fun convertToRating0to255(rating: Int): String {
            var rating = rating
            if (rating < 0) rating = 0
            if (rating > 5) rating = 5
            return id3ConventionRating[rating].toString()
        }
    }
}