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

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import xyz.mordorx.sicmu.R
import xyz.mordorx.sicmu.collections.PathRowComparator
import xyz.mordorx.sicmu.collections.TreeRowComparator
import xyz.mordorx.sicmu.data.XPreferences.Companion.P
import xyz.mordorx.sicmu.media.RepeatMode
import java.io.File
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.Random
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.Boolean
import kotlin.Char
import kotlin.Exception
import kotlin.Int
import kotlin.Long
import kotlin.NumberFormatException
import kotlin.also
import kotlin.arrayOf
import kotlin.concurrent.Volatile
import kotlin.math.max
import kotlin.text.compareTo
import kotlin.text.contains
import kotlin.text.equals
import kotlin.text.isEmpty
import kotlin.text.lowercase
import kotlin.text.split
import kotlin.text.toInt
import kotlin.text.toRegex

/**
 * This class manages `Row` objects, performs media queries and builds hierarchies
 * of foldable rows that reflect artists, albums or the file system structure.
 */
class Rows(
    val context: Context,
    private val musicResolver: ContentResolver,
    private val db: SongDAO
) {
    private val random: Random
    private val shuffleSavedPos: ArrayList<Int>

    /// ID of the song at last exiting
    private var savedID: Long = 0

    // todo: see if another Collection than ArrayList would give better perf and code simplicity
    private var rows: ArrayList<Row>
    private val rowsUnfolded: ArrayList<Row>

    /** Current selected position within rowsUnfolded.
     * Never assign this directly, instead use setCurrPos */
    private var currPosUnfolded: Int

    private val ratingsMustBeSynchronized: AtomicBoolean
    private val ratingsSynchronizing: AtomicBoolean

    @Volatile
    private var terminated = false

    private var fileToOpenFound = false

    fun terminate() {
        terminated = true
        AlbumArtLoader.terminate()
    }

    /** size of the foldable array */
    fun size(): Int {
        return rows.size
    }

    // the user choose a row
    /** select first song encountered from pos */
    fun selectNearestSong(pos: Int) {
        var row = rows.get(pos)
        while (row.javaClass != RowSong::class.java) row = rowsUnfolded.get(row.genuinePos + 1)
        setCurrPosUnfolded(row.genuinePos)
    }

    /** get row from the foldable array */
    fun get(pos: Int): Row? {
        var row: Row? = null
        if (pos >= 0 && pos < rows.size) row = rows.get(pos)
        return row
    }

    fun getAndSetFileToOpenFound(): Boolean {
        val isFileToOpenFound = fileToOpenFound
        fileToOpenFound = false
        return isFileToOpenFound
    }

    private val scanFileCompletedCallback: MediaScannerConnection.OnScanCompletedListener =
        object : MediaScannerConnection.OnScanCompletedListener {
            override fun onScanCompleted(path: String, uri: Uri) {
                if (terminated) return
                var pos = -1
                if (uri != null) {
                    Log.d("Rows", "onScanCompleted file " + path + " found")
                    reinit()
                    pos = getGenuinePosFromPath(path)
                    if (pos != -1) {
                        setCurrPosUnfolded(pos)
                        Log.d("Rows", "onScanCompleted file " + path + " pos setted")
                        fileToOpenFound = true
                    }
                }
                if (pos == -1) {
                    val wrn =
                        context.getString(R.string.app_name) + ": playing file " + path + " failed !"
                    Log.i("Rows", wrn)
                    Toast.makeText(context, wrn, Toast.LENGTH_LONG).show()
                }
            }
        }

    fun setCurrPosFromUri(context: Context, uri: Uri): Boolean {
        var found = false
        val path = MediaScanner.getSongPathFromUri(context, uri)
        if (path != null) {
            Log.d("MusicService", "getFilePathFromUri -> " + path)
            val pos = getGenuinePosFromPath(path)
            if (pos != -1) {
                setCurrPosUnfolded(pos)
                found = true
                // rescan path so that it can see deleted file at next SicMu restart
                MediaScanner.scanMediaFolder(context, path, null)
            } else {
                Log.d("Rows", "Launch scan file for file " + path)
                // scan folder first so that when the callback fire the folder is already scanned (hopefully)
                MediaScanner.scanMediaFolder(context, path, null)
                MediaScanner.scanMediaFile(context, path, scanFileCompletedCallback)
            }
        } else {
            val wrn = context.getString(R.string.app_name) + ": playing uri " + uri + " failed !"
            Log.i("Rows", wrn)
            Toast.makeText(context, wrn, Toast.LENGTH_LONG).show()
        }
        return found
    }

    private fun getGenuinePosFromPath(path: String): Int {
        var pos = -1
        // todo: optimize search ?
        for (i in rowsUnfolded.indices) {
            val row = rowsUnfolded.get(i)
            if (row.javaClass == RowSong::class.java) {
                //Log.d("MusicService", "path " + ((RowSong) row).getPath());
                if (path.equals((row as RowSong).path)) {
                    pos = row.genuinePos
                    break
                }
            }
        }
        return pos
    }

    val currSong: RowSong?
        /** get the song currently selected (playing or paused) from the unfoldable array */
        get() {
            var row: Row? = null
            if (currPosUnfolded >= 0 && currPosUnfolded < rowsUnfolded.size) {
                row = rowsUnfolded.get(currPosUnfolded)
                if (row.javaClass != RowSong::class.java) row = null
            }
            return row as RowSong?
        }

    val currPosFolded: Int
        /** get the currently selected row (group or song) from the foldable array */
        get() {
            var pos = -1
            val song: Row? = this.currSong
            var i = 0
            while (i < rows.size) {
                val row = rows.get(i)
                if (row === song ||
                    (row.javaClass == RowGroup::class.java &&
                            (row as RowGroup).isSelected &&
                            row.isFolded)
                ) break
                i++
            }
            if (i < rows.size) pos = i
            return pos
        }

    private fun setCurrPosUnfolded(pos: Int) {
        setGroupSelectedState(currPosUnfolded, false)
        currPosUnfolded = pos
        setGroupSelectedState(currPosUnfolded, true)
    }

    private fun setGroupSelectedState(pos: Int, selected: Boolean) {
        if (pos >= 0 && pos < rowsUnfolded.size) {
            var group = rowsUnfolded.get(pos).parent as RowGroup?
            while (group != null) {
                group.isSelected = (selected)
                group = group.parent as RowGroup?
            }
        }
    }

    fun moveToRandomSong() {
        if (rowsUnfolded.isEmpty()) return

        if (P.value.repeatMode == RepeatMode.REPEAT_GROUP) {
            val firstSongPos = getFirstSongPosInGroup(currPosUnfolded)
            val lastSongPos = getLastSongPosInGroup(currPosUnfolded)
            if (lastSongPos <= firstSongPos) return
            val nbSongInCurGroup = (lastSongPos - firstSongPos) + 1

            // remove pos not in specified limit
            shuffleSavedPos.removeIf { pos: Int? -> pos!! < firstSongPos || pos > lastSongPos }

            // save the previously song chosen
            shuffleSavedPos.add(currPosUnfolded)
            Collections.sort(shuffleSavedPos)

            // reset shuffleSavedPos if we filled it entirely, add only curr pos
            if (shuffleSavedPos.size >= nbSongInCurGroup) {
                shuffleSavedPos.clear()
                shuffleSavedPos.add(currPosUnfolded)
                Log.d("Rows", "shuffleSavedPos.clear")
            }

            // random on remaining part
            val randNum = random.nextInt(nbSongInCurGroup - shuffleSavedPos.size)
            currPosUnfolded = randNum + firstSongPos
            // shift song already done
            for (pos in shuffleSavedPos) {
                if (pos!! <= currPosUnfolded) currPosUnfolded++
            }
        } else {
            // save the random song chosen
            shuffleSavedPos.add(currPosUnfolded)

            var pos: Int
            do {
                pos = random.nextInt(rowsUnfolded.size)
            } while (pos == currPosUnfolded || rowsUnfolded.get(pos).javaClass != RowSong::class.java)

            setGroupSelectedState(currPosUnfolded, false)

            currPosUnfolded = pos

            setGroupSelectedState(currPosUnfolded, true)
        }
    }

    // return the pos of the last song belonging to the given songPos group
    fun getLastSongPosInGroup(songPos: Int): Int {
        var songPos = songPos
        val currParent = rowsUnfolded.get(songPos).parent
        // if next row is the end of the list or a group or a different group, we reached another group
        do {
            songPos++
        } while (songPos < rowsUnfolded.size && rowsUnfolded.get(songPos).javaClass == RowSong::class.java && rowsUnfolded.get(
                songPos
            ).parent === currParent
        )
        return songPos - 1
    }

    // return the pos of the first song belonging to the given songPos group
    fun getFirstSongPosInGroup(songPos: Int): Int {
        var songPos = songPos
        val currParent = rowsUnfolded.get(songPos).parent
        do {
            songPos--
        } while (songPos > 0 && rowsUnfolded.get(songPos).javaClass == RowSong::class.java && rowsUnfolded[songPos].parent === currParent)
        return songPos + 1
    }

    fun currPosIsLastSongInGroup(): Boolean {
        val song = this.currSong
        return song != null && getLastSongPosInGroup(song.genuinePos) == song.genuinePos
    }

    // go back to previous random song done
    fun moveToRandomSongBack() {
        if (rowsUnfolded.isEmpty()) return

        var backOk = false
        if (!shuffleSavedPos.isEmpty()) {
            val pos = shuffleSavedPos.removeAt(shuffleSavedPos.size - 1)!!
            // check
            if (pos < rowsUnfolded.size && rowsUnfolded[pos].javaClass == RowSong::class.java) {
                backOk = true
                setGroupSelectedState(currPosUnfolded, false)
                currPosUnfolded = pos
                setGroupSelectedState(currPosUnfolded, true)
            }
        }
        // if no saved pos, fallback to prevsong
        if (!backOk) moveToPrevSong()
    }

    fun moveToNextSong() {
        if (rowsUnfolded.isEmpty()) return

        if (P.value.repeatMode == RepeatMode.REPEAT_GROUP) {
            val lastSongPos = getLastSongPosInGroup(currPosUnfolded)
            val firstSongPos = getFirstSongPosInGroup(currPosUnfolded)
            if (lastSongPos == firstSongPos) return

            val lastCurrPos = currPosUnfolded
            var rowSong: RowSong
            // rowSong must have load the rating because we are in repeat group and the group
            // should have been loaded
            do {
                if (currPosUnfolded == lastSongPos) currPosUnfolded = firstSongPos
                else currPosUnfolded++
                // next song with suitable rating not found => return next song regardless of rating
                if (currPosUnfolded == lastCurrPos) {
                    if (currPosUnfolded == lastSongPos) currPosUnfolded = firstSongPos
                    else currPosUnfolded++
                    Log.d(
                        "Rows", "move to next in REPEAT_GROUP with suitable " +
                                "rating not found => return next song regardless of rating"
                    )
                    break
                }
                rowSong = rowsUnfolded.get(currPosUnfolded) as RowSong
            } while (rowSong.isRatingInsufficient)
        } else {
            val lastCurrPos = currPosUnfolded

            var rowSong: RowSong
            do {
                currPosUnfolded++
                if (currPosUnfolded >= rowsUnfolded.size) currPosUnfolded = 0
                // skip RowGroup
                while (currPosUnfolded < rowsUnfolded.size &&
                    rowsUnfolded.get(currPosUnfolded).javaClass != RowSong::class.java
                ) currPosUnfolded++
                // next song with suitable rating not found
                if (currPosUnfolded == lastCurrPos) {
                    Log.d(
                        "Rows", "move to next song with suitable rating not " +
                                "found => return next song regardless of rating"
                    )
                    moveToNextSongNoRating()
                    return
                }
                rowSong = rowsUnfolded.get(currPosUnfolded) as RowSong
            } while (rowSong.isRatingInsufficient)

            setGroupSelectedState(lastCurrPos, false)
            setGroupSelectedState(currPosUnfolded, true)
        }
    }

    private fun moveToNextSongNoRating() {
        if (rowsUnfolded.isEmpty()) return

        if (P.value.repeatMode == RepeatMode.REPEAT_GROUP) {
            val lastSongPos = getLastSongPosInGroup(currPosUnfolded)
            if (currPosUnfolded == lastSongPos) currPosUnfolded =
                getFirstSongPosInGroup(currPosUnfolded)
            else currPosUnfolded++
        } else {
            setGroupSelectedState(currPosUnfolded, false)

            currPosUnfolded++
            if (currPosUnfolded >= rowsUnfolded.size) currPosUnfolded = 0

            while (currPosUnfolded < rowsUnfolded.size &&
                rowsUnfolded.get(currPosUnfolded).javaClass != RowSong::class.java
            ) currPosUnfolded++

            if (currPosUnfolded == rowsUnfolded.size) currPosUnfolded = -1

            setGroupSelectedState(currPosUnfolded, true)
        }
    }

    fun FoldedToUnfoldedIndex(index: Int): Int {
        val foldedRow = rows.get(index)
        for (i in rowsUnfolded.indices) {
            val unfoldedRow = rowsUnfolded.get(i)
            if (unfoldedRow === foldedRow) return i
        }
        return -1 // This should never occur
    }

    // Get the next song position, where a keyword is contained in the song metadata.
    // Returns -1 when not found
    fun getNextSongByKeyword(keyword: String): Row? {
        if (rowsUnfolded.isEmpty()) return null

        val currPos = FoldedToUnfoldedIndex(this.currPosFolded)

        // Iterate from (currently selected song + 1) -> end of playlist
        for (i in currPos + 1..<rowsUnfolded.size) {
            val row = rowsUnfolded.get(i)

            if (row.toString().lowercase(Locale.getDefault())
                    .contains(keyword.lowercase(Locale.getDefault()))
            ) {
                return row
            }
        }
        // Iterate from beginning of playlist -> current song
        for (i in 0..<currPos) {
            val row = rowsUnfolded.get(i)

            if (row.toString().lowercase(Locale.getDefault())
                    .contains(keyword.lowercase(Locale.getDefault()))
            ) {
                return row
            }
        }

        return null
    }

    fun getFoldedIndex(row: Row?): Int {
        if (row == null) return -1

        for (i in 0..row.genuinePos) {
            if (rows.get(i) === row) return i
        }

        return -1
    }

    fun moveToPrevSong() {
        if (rowsUnfolded.isEmpty()) return

        if (P.value.repeatMode == RepeatMode.REPEAT_GROUP) {
            val firstSongPos = getFirstSongPosInGroup(currPosUnfolded)
            if (currPosUnfolded == firstSongPos) currPosUnfolded =
                getLastSongPosInGroup(currPosUnfolded)
            else currPosUnfolded--
        } else {
            setGroupSelectedState(currPosUnfolded, false)

            do {
                currPosUnfolded--
                if (currPosUnfolded < 0) currPosUnfolded = rowsUnfolded.size - 1
            } while (currPosUnfolded >= 0 && rowsUnfolded.get(currPosUnfolded).javaClass != RowSong::class.java)

            setGroupSelectedState(currPosUnfolded, true)
        }
    }

    fun moveToPrevGroup() {
        if (rowsUnfolded.isEmpty()) return

        setGroupSelectedState(currPosUnfolded, false)

        currPosUnfolded = getFirstSongPosInGroup(currPosUnfolded)

        do {
            currPosUnfolded--
            if (currPosUnfolded < 0) currPosUnfolded = rowsUnfolded.size - 1
        } while (currPosUnfolded >= 0 && rowsUnfolded.get(currPosUnfolded).javaClass != RowSong::class.java)

        if (currPosUnfolded < 0) currPosUnfolded = rowsUnfolded.size - 1

        currPosUnfolded = getFirstSongPosInGroup(currPosUnfolded)

        setGroupSelectedState(currPosUnfolded, true)
    }

    fun moveToNextGroup() {
        if (rowsUnfolded.isEmpty()) return

        setGroupSelectedState(currPosUnfolded, false)

        currPosUnfolded = getLastSongPosInGroup(currPosUnfolded)
        currPosUnfolded++

        // if last song go to beginning
        if (currPosUnfolded == rowsUnfolded.size) {
            currPosUnfolded = 0
        }

        // skip RowGroups
        while (currPosUnfolded < rowsUnfolded.size &&
            rowsUnfolded.get(currPosUnfolded).javaClass != RowSong::class.java
        ) currPosUnfolded++

        if (currPosUnfolded == rowsUnfolded.size) {
            currPosUnfolded = -1
        }

        setGroupSelectedState(currPosUnfolded, true)
    }

    // fold everything
    fun fold() {
        if (rowsUnfolded.isEmpty()) return

        // todo: better to recopy first level from unfolded?
        for (i in rows.indices) {
            val row = rows.get(i)
            if (row.javaClass == RowGroup::class.java) fold((rows.get(i) as RowGroup?)!!, i)
        }
    }

    // unfold everything
    fun unfold() {
        if (rowsUnfolded.isEmpty()) return

        rows = rowsUnfolded.clone() as ArrayList<Row>
        for (row in rows) if (row.javaClass == RowGroup::class.java) (row as RowGroup).isFolded = false
    }

    fun invertFold(pos: Int) {
        if (rowsUnfolded.isEmpty()) return

        if (pos < 0 || pos >= rows.size) {
            return
        }
        if (rows.get(pos).javaClass != RowGroup::class.java) {
            Log.w("Rows", "invertFold called on class that is not SongGroup!")
            return
        }
        val group = rows.get(pos) as RowGroup

        if (group.isFolded) {
            unfold(group, pos)
        } else {
            fold(group, pos)
        }
    }

    /**
     * group and pos must correspond in the foldable rows
      */
    private fun fold(group: RowGroup, pos: Int) {
        var pos = pos
        pos++
        // remove every following rows that has a higher level
        while (pos < rows.size && rows.get(pos).level > group.level) {
            //Log.d("Rows", "Item removed pos: " + pos + " row: " + songItems.get(pos));
            rows.removeAt(pos)
        }
        group.isFolded = (true)
    }

    /**
     * Unfold only the groups that contain pos.
     *
     * @return true if at least one group has been unfolded
     */
    fun unfoldCurrPos(): Boolean {
        if (rowsUnfolded.isEmpty()) return false

        var changed = false
        val pos = this.currPosFolded
        if (pos < 0 || pos >= rows.size) return false

        val row = rows[pos]
        if (row is RowGroup) {
            val group = row as RowGroup
            if (group.isFolded) {
                unfold(group, pos)
                unfoldCurrPos()
                changed = true
            }
        }
        return changed
    }

    private fun hasOneSubGroup(group: RowGroup, pos: Int): Boolean {
        if (group.level != 0) return true

        var nbSubGroup = 0
        var row: Row? = null
        var i = 1
        while (group.genuinePos + i < rowsUnfolded.size &&
            (rowsUnfolded.get(group.genuinePos + i)
                .also { row = it }).level > group.level
        ) {
            if (row!!.javaClass == RowGroup::class.java) {
                nbSubGroup++
                if (nbSubGroup > 1) return false
            }
            i++
        }

        return true
    }

    /// group and pos must correspond in the foldable rows
    /// group must be folded
    private fun unfold(group: RowGroup, pos: Int) {
        var row: Row? = null
        var nbRowGroupUnfold = 0
        var nbRowSongUnfold = 0
        // unfold only next level
        var i = 1
        var j = 1
        while (group.genuinePos + i < rowsUnfolded.size &&
            (rowsUnfolded[group.genuinePos + i]
                .also { row = it }).level > group.level
        ) {
            if (row!!.level == group.level + 1) {
                if (row.javaClass == RowGroup::class.java) {
                    (row as RowGroup).isFolded = (true)
                    nbRowGroupUnfold++
                } else {
                    nbRowSongUnfold++
                }
                rows.add(pos + j++, row)
            }
            i++
        }
        group.isFolded = false

        // unfold subgroup if group contains only one subgroup
        if (nbRowGroupUnfold == 1 && nbRowSongUnfold == 0) {
            if (group.genuinePos + 1 < rowsUnfolded.size) {
                val subGroupSingle = rowsUnfolded.get(group.genuinePos + 1) as RowGroup?
                if (subGroupSingle != null && subGroupSingle.level == group.level + 1) unfold(
                    subGroupSingle,
                    pos + 1
                )
            }
        }
    }

    fun isLastRow(pos: Int): Boolean {
        return pos == rows.size - 1
    }


    fun init() {
        terminated = false
        AlbumArtLoader.resetTermination()
        rowsUnfolded.clear()
        rows.clear()

        val startTime = System.currentTimeMillis()
        val musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.MediaColumns.DATA,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.MIME_TYPE
        )

        val sortOrder: String =
                       MediaStore.Audio.Media.ARTIST +
                ", " + MediaStore.Audio.Media.ALBUM +
                ", " + MediaStore.Audio.Media.TRACK +
                ", " + MediaStore.Audio.Media.TITLE
        try {
            musicResolver.query(musicUri, projection, null, null, sortOrder).use { musicCursor ->
                initByTree(musicCursor)
            }
        } catch (e: Exception) {
            val msg = "No songItems found!"
            //Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
            Log.e("MusicService", msg, e)
            return
        }

        // if no songPos saved : search the first song
        if (currPosUnfolded == -1) {
            val song = rowsUnfolded.withIndex().firstOrNull { it is RowSong }
            if (song != null) {
                setCurrPosUnfolded(song.index)
            }
        }

        // Fold all rows
        initRowsFolded()

        // to comment in release mode:
        Log.d(
            "Rows",
            "======> songItems initialized in " + (System.currentTimeMillis() - startTime) + "ms"
        )
        Log.d("Rows", "songPos: $currPosUnfolded")

        //preloadDBSongsAsync();
        preloadSongRatingAsync()


        P.value.lastPlayedSongID?.also { lastSongId ->
            rowsUnfolded
                .withIndex()
                .firstOrNull { r -> r.value is RowSong && (r.value as RowSong).iD == lastSongId }?.also { r ->
                    setCurrPosUnfolded(r.index)
                }

        }
    }

    private fun preloadDBSongsAsync() {
        val thread = Thread(Runnable { this.preloadDBSongs() })
        thread.start()
    }

    // must not be called from main thread !
    private fun preloadDBSongs() {
        val beg = Date()
        var nbLoaded = 0
        // preload in db every songs
        for (row in rowsUnfolded) {
            if (terminated) return
            if (row.javaClass == RowSong::class.java) {
                val rowSong = row as RowSong
                val songORM = db.findByPath(rowSong.path)
                nbLoaded++
                if (songORM == null) {
                    Log.d("Rows", "New songORM for path=" + rowSong.path)
                    try {
                        db.insert(SongORM(rowSong.path, rowSong.loadRating(), true))
                    } catch (e: Exception) {
                        Log.w(
                            "Rows", ("Unable to add songORM for path=" + rowSong.path
                                    + " e=" + e)
                        )
                    }
                } else {
                    val lastModified = (File(songORM.path)).lastModified()
                    if (lastModified > songORM.lastModifiedMs) {
                        Log.d(
                            "Rows", "Found songORM for path=" + songORM.path +
                                    ", update it"
                        )
                        songORM.rating = rowSong.loadRating()
                        songORM.lastModifiedMs = lastModified
                        try {
                            db.update(songORM)
                        } catch (e: Exception) {
                            Log.w(
                                "Rows", ("Unable to update songORM for path=" + rowSong.path
                                        + " e=" + e)
                            )
                        }
                    }
                    //                    else {
//                        Log.d("Rows", "Found songORM for path="   songORM.path);
//                    }
                }
            }
        }
        val end = Date()
        Log.d(
            "Rows", "preloadDBSongs: " + nbLoaded + " songORM loaded in " +
                    (end.getTime() - beg.getTime()) + "ms"
        )
    }

    private fun preloadSongRatingAsync() {
        val thread = Thread(Runnable { this.preloadSongsRatings() })
        thread.start()
    }

    // must not be called from main thread !
    private fun preloadSongsRatings() {
        Log.d("Rows", "preloadSongsRatings start")
        val beg = Date()
        var nbLoaded = 0
        // preload from the currpos so that next songs are loaded earlier
        val startPos = max(currPosUnfolded, 0)
        for (i in startPos..<rowsUnfolded.size) {
            if (terminated) return
            val row = rowsUnfolded.get(i)
            if (row.javaClass == RowSong::class.java) {
                (row as RowSong).loadRating()
                nbLoaded++
            }
        }
        var i = 0
        while (i < startPos && i < rowsUnfolded.size) {
            if (terminated) return
            val row = rowsUnfolded.get(i)
            if (row.javaClass == RowSong::class.java) {
                (row as RowSong).loadRating()
                nbLoaded++
            }
            i++
        }
        val end = Date()
        Log.d(
            "Rows", "preloadSongsRatings: " + nbLoaded + " songs loaded in " +
                    (end.getTime() - beg.getTime()) + "ms"
        )
    }

    private fun initRowsFolded() {
        for (row in rowsUnfolded) {
            if (row.javaClass == RowGroup::class.java && row.level == 0) {
                rows.add(row)
                (row as RowGroup).isFolded = (true)
            }
        }
    }

    private fun initByTree(musicCursor: Cursor?) {
        if (musicCursor != null && musicCursor.moveToFirst()) {
            val titleCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val idCol = musicCursor.getColumnIndex(MediaStore.Audio.Media._ID)
            val artistCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val albumCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
            val durationCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val pathCol = musicCursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            val trackCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
            val albumIdCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
            val yearCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
            val mimeCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)

            do {
                val id = musicCursor.getLong(idCol)
                val title = getDefaultStrIfNull(musicCursor.getString(titleCol))
                val artist = getDefaultStrIfNull(musicCursor.getString(artistCol))
                val album = getDefaultStrIfNull(musicCursor.getString(albumCol))
                val durationMs = musicCursor.getLong(durationCol)
                val path = getDefaultStrIfNull(musicCursor.getString(pathCol))
                val track = getTrackNumber(musicCursor.getString(trackCol), path)
                val albumId = musicCursor.getLong(albumIdCol)
                val year = musicCursor.getInt(yearCol)
                val mime = musicCursor.getString(mimeCol)

                val pos = -1
                val level = 2
                val rowSong = RowSong(
                    db, pos, level, id, title, artist, album, durationMs,
                    track, path, albumId, year, mime
                )
                rowsUnfolded.add(rowSong)
                //Log.d("Rows", "song added: " + rowSong.toString());
            } while (musicCursor.moveToNext())
        }

        //        long beforeMs = (new Date()).getTime();
        val treeRowComparator = TreeRowComparator(true)
        rowsUnfolded.sortWith(treeRowComparator)

        //        Log.w("Rows==========", "Sort time: " + ((new Date()).getTime() - beforeMs) + " ms");
//        // 127 ms tree no show filename
//        // 283 ms tree show filename

        // add groups
        val prevGroups = ArrayList<RowGroup?>()
        var idx = 0
        while (idx < rowsUnfolded.size) {
            val rowSong = rowsUnfolded.get(idx) as RowSong
            // get folder list of current row
            val folders = MediaScanner.tokenizeFolder(rowSong.folder)

            /* get the nearest common group parent */
            // search from the bottom the last previous group that is the same with the current group
            // /toto/tata/youp, /to/tata/gruick -> firstDiff = 0
            // /toto/tata/youp, /toto/titi/gruick -> firstDiff = 1
            // /toto/tata/youp, /toto/tata/gruick -> firstDiff = 2
            var commonLevel = 0
            while (commonLevel < prevGroups.size && commonLevel < folders.size &&
                prevGroups.get(commonLevel)!!.name
                    .equals(folders.get(commonLevel), ignoreCase = true)
            ) commonLevel++
            // get corresponding RowGroup
            val commonGroup: RowGroup?
            if (commonLevel == 0)  // everything is different: no parent
                commonGroup = null
            else commonGroup = prevGroups.get(commonLevel - 1)

            /* add every groups that are missing */
            var parentGroup = commonGroup
            for (level in commonLevel..<folders.size) {
                // get the absolute path path of the current missing group
                var nbFolderBelow = folders.size - level
                var path = rowSong.path
                while (nbFolderBelow-- > 0) {
                    path = (File(path)).parent
                    if (path == null) path = ""
                }

                val aGroup = RowGroup(
                    idx, level, folders.get(level),
                    path, Typeface.BOLD, false
                )
                aGroup.parent = (parentGroup)
                parentGroup = aGroup
                rowsUnfolded.add(idx, aGroup)
                idx++
            }

            /* recompute group list for next row */
                    prevGroups.clear()
            var groupIdx = parentGroup
            while (groupIdx != null) {
                // update group
                groupIdx.increaseSongCount(1)
                groupIdx.incTotalDuration(rowSong.durationMs)

                prevGroups.add(0, groupIdx)
                groupIdx = groupIdx.parent as RowGroup?
            }

            /* update RowSong */
                    rowSong.level = (folders.size)
            rowSong.genuinePos = (idx)
            rowSong.parent = (parentGroup)
            if (rowSong.iD == savedID) currPosUnfolded = idx
            idx++
        }

        setGroupSelectedState(currPosUnfolded, true)
    }


    private fun getDefaultStrIfNull(str: String?): String {
        return str ?: "<null>"
    }

    private fun getTrackNumber(strTrack: String?, path: String?): Int {
        var track = 0
        try {
            track = strTrack?.toInt() ?: 0
        } catch (ignored: NumberFormatException) {
        }

        // get track number from path
        return track
    }

    fun save() {
        updateSavedId()
    }

    fun reinit() {
        updateSavedId()
        init()
    }

    /**
     * @param rootFolders CSV list of root folders (either semicolon or comma separated)
     * @return Whether the rows view was re-initialized
     */
    fun setRootFolders(rootFolders: String): Boolean {
        val reinited = false
        // We re-construct the new roots with a semicolon, because both semicolons and commas are
        // interchangable and valid delimiters. This way, we can make a fair comparison
        val currentRoots = MediaScanner.rootFolders.joinToString(";")
        val newRoots = rootFolders.split("[,;]".toRegex()).joinToString(";")

        if (currentRoots == newRoots) {
            return false
        }

        MediaScanner.rootFolders = rootFolders.split("[,;]".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        // reinit everything is a bit heavy: nevermind, rootFolders will not be changed often
        updateSavedId()
        init()
        return true
    }

    private fun updateSavedId() {
        val rowSong = this.currSong
        if (rowSong != null) savedID = rowSong.iD
    }

    fun interface RatingCallbackInterface {
        // @param someRatingChanged set to true if loadRatings brings new RowSong's rating
        // i.e. set to false if RowSong's rating did not change
        fun ratingCallback(someRatingChanged: Boolean)
    }

    // fetch song's rating that are currently visible to the user
    private var loadRatingsThread: Thread? = null

    init {
        currPosUnfolded = -1

        random = Random()
        shuffleSavedPos = ArrayList()

        rowsUnfolded = ArrayList()
        rows = ArrayList()

        ratingsMustBeSynchronized = AtomicBoolean(false)
        ratingsSynchronizing = AtomicBoolean(false)

        init()
    }

    @Synchronized
    fun loadRatingsAsync(ratingCallbackInterface: RatingCallbackInterface) {
        if (loadRatingsThread != null && loadRatingsThread!!.isAlive()) {
            loadRatingsThread!!.interrupt()
        }
        loadRatingsThread = object : Thread() {
            override fun run() {
                Log.d("Rows", "loadRatings")
                var someRatingChanged = false
                for (i in rows.indices) {
                    if (terminated || isInterrupted()) return
                    val row = rows.get(i)
                    if (row.javaClass == RowSong::class.java) {
                        val rowSong = row as RowSong
                        if (rowSong.rating == RowSong.Companion.RATING_NOT_INITIALIZED &&
                            rowSong.loadRating() > 0
                        ) someRatingChanged = true
                    }
                }
                if (!isInterrupted()) {
                    ratingCallbackInterface.ratingCallback(someRatingChanged)
                }
            }
        }
        loadRatingsThread!!.start()
    }

    fun interface RateGroupCallbackInterface {
        fun ratingCallback(nbSongsChanged: Int, errorMsg: kotlin.String?)
    }

    // rate songs from pos (if pos is folder rate every folder's songs)
    // return true if ratingsMustBeSynchronized (usually set when trying to rate the current played song)
    fun rateSongs(
        pos: Int, rating: Int, overwriteRating: Boolean,
        callback: RateGroupCallbackInterface
    ) {
        Log.d(
            "Rows", "rateGroup " + pos + " to " + rating +
                    " overwrite=" + overwriteRating
        )

        if (ratingsSynchronizing.getAndSet(true)) {
            callback.ratingCallback(
                0,
                context.getString(R.string.action_set_rating_song_failed)
            )
        } else {
            val thread = Thread(Runnable {
                var nbChanged = 0
                var row = rows.get(pos)
                if (row.javaClass == RowSong::class.java) {
                    if (rateSong(row as RowSong, rating, overwriteRating)) nbChanged++
                } else {
                    val groupToRate = row as RowGroup
                    var i = 1
                    while (groupToRate.genuinePos + i < rowsUnfolded.size &&
                        (rowsUnfolded.get(groupToRate.genuinePos + i)
                            .also { row = it }).level >
                        groupToRate.level
                    ) {
                        if (terminated) break
                        if (row.javaClass == RowSong::class.java) {
                            if (rateSong(row as RowSong, rating, overwriteRating)) nbChanged++
                        }
                        i++
                    }
                }
                callback.ratingCallback(nbChanged, "")
                ratingsSynchronizing.set(false)
            })
            thread.start()
        }
    }

    // not must be called from main thread
    private fun rateSong(rowSong: RowSong, rating: Int, overwriteRating: Boolean): Boolean {
        var changed = false
        if (rowSong.rating == rating) Log.d(
            "Rows",
            "song " + rowSong.title + " rating already set to " + rating + " -> skipping"
        )
        else if (overwriteRating || rowSong.rating <= 0) {
            Log.d("Rows", "set song " + rowSong.title + " rating to " + rating)
            val currSong = this.currSong
            if (currSong == null || (rowSong.genuinePos != currSong.genuinePos)) {
                rowSong.setRating(rating)
            } else {
                rowSong.scheduleSetRating(rating, false)
                ratingsMustBeSynchronized.set(true)
            }
            changed = true
        }
        return changed
    }

    fun rateCurrSong(rating: Int) {
        val rowSong = this.currSong
        if (rowSong != null) {
            rowSong.scheduleSetRating(rating, true)
            ratingsMustBeSynchronized.set(true)
        }
    }

    /**
     * Deletes a song from the list, but does not remove it form the filesystem.
     */
    private fun deleteSongFromList(songToDelete: RowSong) {
        val foldedIndex = rows.indexOf(songToDelete)
        val unfoldedIndex = rowsUnfolded.indexOf(songToDelete)

        // Adjust rows' position index
        rowsUnfolded.stream().skip(unfoldedIndex.toLong())
            .forEach { row: Row? -> row!!.genuinePos-- }

        // Adjust parents' song count and total duration
        var parent = songToDelete.parent
        while (parent != null) {
            val g = parent as RowGroup
            g.increaseSongCount(-1)
            g.incTotalDuration(songToDelete.durationMs)
            parent = parent.parent
        }

        // Remove it!
        rows.removeAt(foldedIndex)
        rowsUnfolded.removeAt(unfoldedIndex)

        // Adjust current selection index
        if (unfoldedIndex < currPosUnfolded) {
            currPosUnfolded--
        }
    }

    fun deleteSongFile(song: RowSong): Boolean {
        val succeed = song.deleteFile(context)
        if (succeed && song !== this.currSong) deleteSongFromList(song)

        return succeed
    }
}
