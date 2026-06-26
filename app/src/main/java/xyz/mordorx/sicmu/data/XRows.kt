package xyz.mordorx.sicmu.data

import android.content.ContentUris
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import android.provider.MediaStore.Audio.Media
import android.util.Log
import xyz.mordorx.sicmu.data.AlbumArtLoader.Companion.sanitizedAsFileName
import kotlin.collections.buildList
import kotlin.io.path.Path
import kotlin.io.path.name
import kotlin.io.path.pathString

object XRows {
    lateinit var appContext: Context

    var rowsFolded = MutableStateFlow<List<Row>>(emptyList())
        private set

    private var rowsUnfolded = MutableStateFlow<List<Row>>(emptyList())

    fun init(appContext: Context) {

    }

    fun initRowsFromMediaStore() = CoroutineScope(Dispatchers.IO).launch {
        // First, we add all songs. Then we add groups/folders

        val startTime = System.currentTimeMillis()

        val musicUri = Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            Media._ID,
            Media.ALBUM_ID,
            Media.ALBUM,
            Media.TITLE,
            Media.ARTIST,
            Media.DURATION,
            Media.YEAR,
            Media.MIME_TYPE,
            Media.RELATIVE_PATH,
            Media.DISPLAY_NAME
        )

        val sortOrder: String =
            Media.ARTIST +
                    ", " + Media.ALBUM +
                    ", " + Media.TRACK +
                    ", " + Media.TITLE

        val rows = mutableListOf<XRow>()
        val folders = mutableSetOf<String>()

        appContext.contentResolver.query(musicUri, projection, null, null, sortOrder)?.use { musicCursor ->
            val colId = musicCursor.getColumnIndexOrThrow(Media._ID)
            val colAlbumId = musicCursor.getColumnIndexOrThrow(Media.ALBUM_ID)
            val colAlbum = musicCursor.getColumnIndexOrThrow(Media.ALBUM)
            val colTitle = musicCursor.getColumnIndexOrThrow(Media.TITLE)
            val colArtist = musicCursor.getColumnIndexOrThrow(Media.ARTIST)
            val colDuration = musicCursor.getColumnIndexOrThrow(Media.DURATION)
            val colYear = musicCursor.getColumnIndexOrThrow(Media.YEAR)
            val colMimeType = musicCursor.getColumnIndexOrThrow(Media.MIME_TYPE)
            val colRelativePath = musicCursor.getColumnIndexOrThrow(Media.RELATIVE_PATH)
            val colDisplayName = musicCursor.getColumnIndexOrThrow(Media.DISPLAY_NAME)

            while(musicCursor.moveToNext()) {
                val id = musicCursor.getLong(colId)
                val albumId = musicCursor.getLong(colAlbumId)
                val album = musicCursor.getString(colAlbum)
                val title = musicCursor.getString(colTitle)
                val artist = musicCursor.getString(colArtist)
                val duration = musicCursor.getLong(colDuration)
                val year = musicCursor.getInt(colYear)
                val mimeType = musicCursor.getString(colMimeType)
                val relativePath = musicCursor.getString(colRelativePath)
                val displayName = musicCursor.getString(colDisplayName).sanitizedAsFileName

                val indentationLevel = relativePath.slashCount
                val uri = ContentUris.withAppendedId(Media.EXTERNAL_CONTENT_URI, id)

                val song = XRowSong(
                    level = indentationLevel,
                    containingFolder = relativePath,
                    fileName = displayName,
                    isSelected = false,
                    id = id,
                    albumId = albumId,
                    durationMs = duration,
                    externalContentUri = uri,
                    title = title,
                    artist = artist,
                    album = album,
                    year = year,
                    mimeType = mimeType
                )

                rows.add(song)
                folders.add(relativePath)
            }
        }
        Log.d("XRows-Time", "MediaStore read took ${System.currentTimeMillis()-startTime} Ms")

        // This is necessary for easy insertion of folders/groups later
        rows.sortBy { row -> (row as XRowSong).path }

        Log.d("XRows-Time", "Sorting took ${System.currentTimeMillis()-startTime} Ms")

        /**
         * Isolate all folders (even those without song files in them) along the hierarchy.
         *
         * For instance, when we have Music/Songs/Shakira/ we also want Music/Songs even if that
         * folder contains no song files.
         */
        folders.addAll(buildList {
            for(folder in folders) {
                var path = Path(folder)
                while(path.parent != null) {
                    path = path.parent
                    add(path.pathString)
                }
            }
        })
        Log.d("XRows-Time", "Loading folders took ${System.currentTimeMillis()-startTime} Ms")

        /**
         * For each folder, sum up statistics and create XRowGroup objects.
         */
        val groups = folders.sortedBy { str -> str }.mapNotNull {
            val p = Path(it)
            val displayName = p.name
            val containingFolder = p.parent?.pathString ?: return@mapNotNull null
            val fullPath = p.pathString
            val level = it.slashCount
            // This sums up all child songs' numbers. Benchmarks show that using drop/take is
            // faster than binary search for the first/last relevant XRowSong (i.e. 9ms vs 21ms).
            // I suppose that's CPU branch prediction.
            val (playtimeMs, songCount) = rows
                .asSequence()
                .dropWhile { r -> !(r as XRowSong).path.startsWith(fullPath) }
                .takeWhile { r -> (r as XRowSong).path.startsWith(fullPath) }
                .map { r -> Pair((r as XRowSong).durationMs, 1) }
                .reduce { acc, row -> Pair(acc.first + row.first, acc.second + row.second) }

            XRowGroup(
                level = level,
                containingFolder = containingFolder,
                fileName = displayName,
                isSelected = false,
                isFolded = false,
                songCount = songCount,
                durationMs = playtimeMs
            )
        }
        Log.d("XRows-Time", "Creating folders took ${System.currentTimeMillis()-startTime} Ms")

        /**
         * Insert XRowGroup's at appropriate spaces
         */
        groups.forEach { rowGroup ->
            val above = rows.withIndex().first { row -> row.value.path.startsWith(rowGroup.path) }

            Log.d("XRows-GroupInsert", "Inserting group " + rowGroup.path + " above " + above.value.path + " (index=" + above.index + ")")
            /*
            Logs like this:
            Inserting group Music/_/_/BTTFM above Music/_/_/BTTFM/1/BTTF-Commentary.opus (index=1650)
            Inserting group Music/_/_/BTTFM/1 above Music/_/_/BTTFM/1/BTTF-Commentary.opus (index=1650)
            Inserting group Music/_/_/BTTFM/2 above Music/_/_/BTTFM/2/PartII_001.opus (index=1768)
            Inserting group Music/_/_/BTTFM/3 above Music/_/_/BTTFM/3/PartIII_001.opus (index=1876)
             */

            rows.add(above.index, rowGroup)
        }
        Log.d("XRows-Time", "Inserting folders took ${System.currentTimeMillis()-startTime} Ms")

        // Log them all
        /*rows.forEach { finalRow ->
            val pad = " ".repeat(finalRow.level)
            if (finalRow is XRowGroup) {
                val g = finalRow as XRowGroup
                Log.d("XRows-Finale", pad + "\\ " + g.fileName + " (" + g.songCount + "/" + XRowSong.msToMinutes(g.durationMs) + ") /")
            }
            else if (finalRow is XRowSong) {
                val s = finalRow as XRowSong
                Log.d("XRows-Finale", pad + "| " + s.fileName + " (" + XRowSong.msToMinutes(s.durationMs) + ") |")
            }
            /*
            Logs like this:
             \ media (5/2:09) /
              \ com.whatsapp (5/2:09) /
               \ WhatsApp (5/2:09) /
                \ Media (5/2:09) /
                  \ WhatsApp Audio (5/2:09) /
                  | AUD-20260124-WA0012.opus (0:22) |
                  | AUD-20260124-WA0022.opus (0:23) |
                  | AUD-20260319-WA0001.opus (0:12) |
                  ...
             */
        }*/

    }
}

val String.slashCount: Int
    get() { return this.count { it == '/' }}
