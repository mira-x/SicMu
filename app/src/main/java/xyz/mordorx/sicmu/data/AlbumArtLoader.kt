package xyz.mordorx.sicmu.data

import android.content.ContentUris
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import com.google.common.cache.CacheBuilderSpec
import xyz.mordorx.sicmu.R
import xyz.mordorx.sicmu.collections.DeduplicationCache
import java.io.File
import java.util.Arrays
import java.util.Locale
import java.util.Optional
import kotlin.concurrent.Volatile
import kotlin.math.max

/**
 * This class is responsive for loading album art data from multiple sources (metadata, jpg files, etc).
 * It also implements caching. For now, album art is limited to at most one image per song.
 */
class AlbumArtLoader(ctx: Context, private val song: RowSong) {
    fun interface Callback {
        fun callback(rowSongId: Long, bitmap: Bitmap?)
    }

    private val thumbSize = Size(this.screenWidth, this.screenWidth)

    private val ctx: Context = ctx.applicationContext
    private var fallback: Bitmap = BitmapFactory.decodeResource(ctx.resources, R.drawable.ic_default_coverart)

    /** This spins up a thread to load an album image, if it's not cached currently. If it is,
     * the callback is called instantly, in sync. */
    fun loadAsync(albumBmpCallback: Callback) {
        val cachedBmp: Bitmap? = cache.getIfPresent(song.iD)
        if (cachedBmp != null) {
            Log.d(
                "AlbumArtLoader",
                "Cache Hit. RowSongID=" + song.iD + " SongPath=" + song.path + " Bitmap=" + cachedBmp
            )
            albumBmpCallback.callback(song.iD, cachedBmp)
            return
        }

        Thread(Runnable {
            if (isTerminated) return@Runnable
            val bmp = load()
            if (isTerminated) return@Runnable
            albumBmpCallback.callback(song.iD, bmp)
        }).start()
    }

    fun load(): Bitmap {
        // Cache lookup
        val cachedBmp: Bitmap? = cache.getIfPresent(song.iD)
        if (cachedBmp != null) {
            Log.d("AlbumArtLoader", "Cache Hit. RowSongID=" + song.iD + " SongPath=" + song.path + " Bitmap=" + cachedBmp)
            return cachedBmp
        }

        val file = File(song.path)
        var bmp: Bitmap?

        // Try to load art from different sources, starting at embedded art and falling back to image files near the song file.
        bmp = loadViaLoadThumbnail()
        if (bmp == null) bmp = loadViaCreateAudioThumbnail(file)
        if (bmp == null) bmp = loadViaMediaMetadataRetriever()
        if (bmp == null) bmp = loadViaMediaStore()
        if (bmp == null) bmp = loadViaLocalImages()

        // Fallback: load generic placeholder image (which also might be null)
        if (bmp == null) bmp = fallback

        Log.d(
            "AlbumArtLoader",
            "Cache Miss. RowSongID=" + song.iD + " SongPath=" + song.path + " Bitmap=" + bmp
        )
        cache.put(song.iD, bmp)

        return bmp
    }

    private fun loadViaLoadThumbnail(): Bitmap? {
        return try {
            ctx.contentResolver.loadThumbnail(song.externalContentUri, thumbSize, null)
        } catch (ignored: Exception) {
            null
        }
    }

    private fun loadViaCreateAudioThumbnail(audioFile: File): Bitmap? {
        return try {
            ThumbnailUtils.createAudioThumbnail(audioFile, thumbSize, null)
        } catch (ignored: Exception) {
            null
        }
    }

    private fun loadViaMediaMetadataRetriever(): Bitmap? {
        try {
            MediaMetadataRetriever().use { mmr ->
                mmr.setDataSource(song.path)
                val imgBytes = mmr.embeddedPicture
                if (imgBytes != null) return BitmapFactory.decodeByteArray(
                    imgBytes,
                    0,
                    imgBytes.size,
                    BitmapFactory.Options()
                )
            }
        } catch (ignored: Exception) {
        }

        return null
    }

    private fun loadViaMediaStore(): Bitmap? {
        try {
            ctx.contentResolver.query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                arrayOf<String>(MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM_ART),
                MediaStore.Audio.Albums._ID + "=?",
                arrayOf<String>(song.albumId.toString()),
                null
            ).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    val colIdx = cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM_ART)
                    val path = cursor.getString(max(colIdx, 0))
                    return BitmapFactory.decodeFile(path)
                }
            }
        } catch (ignored: Exception) {
        }

        return null
    }

    private fun loadViaLocalImages(): Bitmap? {
        /* Fallback: Search for image files in the same directory.
         *
         * The algorithm looks for the longest prefix match and similarity between image file name
         * and song file name and album/folder name. It also looks for
         * files like "album.jpg" and "playlist.jpg"
         **/
        val songFile = File(song.path)
        val songAlbum = song.album!!
        val songFolder = song.folder

        val res = ctx.contentResolver
        val proj = ArrayList<String?>()
        /*
         * Real world example data for these three column:
         * ID = 28353
         * RelativePath = Music/_/East Los FM/
         * DisplayName = Fandango.jpg
         */
        proj.add(MediaStore.Images.Media._ID)
        proj.add(MediaStore.Images.Media.RELATIVE_PATH)
        proj.add(MediaStore.Images.Media.DISPLAY_NAME)

        // Sanitize path. This is only temporarily neccessary as we find our song files currently using absolute paths. But the absolute paths don't play well along MediaStore's relative image paths.
        // TODO: Remove sanitization when possible
        val relativeFolderPath = song.folder
            .removePrefix("/") // If present
            .replace(Regex("^storage\\/emulated\\/\\d+\\/?"), "")
            .replace(Regex("^storage\\/[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}\\/?"), "")
            .removePrefix("sdcard/")
        val sel = MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?"
        val selParams: Array<String> = arrayOf("%$relativeFolderPath/")
        Log.d("AlbumArtLoader", "Querying folder: $relativeFolderPath for song: ${song.title}")

        val candidates: ArrayList<AlbumImageCandidate?> = ArrayList()

        res.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            proj.toArray<String?>(arrayOf<String?>()),
            sel,
            selParams,
            null
        ).use { cursor ->
            val idxID = cursor!!.getColumnIndex(MediaStore.Images.Media._ID)
            val idxRelativePath = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
            val idxDisplayName = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idxID)
                val relativePath = cursor.getString(idxRelativePath)
                val displayName = cursor.getString(idxDisplayName).sanitizedAsFileName
                val imgFile = File(relativePath, displayName)

                candidates.add(AlbumImageCandidate(imgFile, songFile, songAlbum, id))

                Log.d("AlbumArtLoader", "Found local image: id=$id, path=$relativePath \t image name=$displayName \t path query=$songFolder")
            }
            Log.d("AlbumArtLoader", "Searching local images done.")
        }
        val sorter: Comparator<AlbumImageCandidate?>? = Comparator
            .comparing(AlbumImageCandidate::significantFilenamePrefixMatch)
            .thenComparing(AlbumImageCandidate::significantAlbumPrefixMatch)
            .thenComparing(AlbumImageCandidate::isGenericAlbumArtName)
            .thenComparing(AlbumImageCandidate::insignificantFilenamePrefixMatch)
            .thenComparing(AlbumImageCandidate::insignificantAlbumPrefixMatch)
            .reversed()

        val albumArt: Optional<AlbumImageCandidate?> = candidates
            .stream()
            .sorted(sorter)
            .findFirst()

        if (albumArt.isEmpty) {
            return null
        }

        val imgId = albumArt.get().imageID
        val imgUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imgId)

        try {
            ctx.contentResolver.openInputStream(imgUri).use { bmpStream ->
                return BitmapFactory.decodeStream(bmpStream)
            }
        } catch (e: Exception) {
            Log.i(
                "AlbumArtLoader",
                "Exception while trying to read bitmap stream for MediaStore image No." + imgId,
                e
            )
        }

        return null
    }

    private val screenWidth: Int
        get() {
            var width =
                Resources.getSystem().displayMetrics.widthPixels
            if (width < 128) width = 128
            return width
        }

    /**
     * This is a wrapper class for two strings: an image file path and a song file path. This class
     * helps with comparing these two paths and determining whether the image file is a suited
     * *album image candidate*
     */
    private class AlbumImageCandidate(
        val imageFile: File,
        private val songPath: File,
        albumName: String,
        val imageID: Long
    ) {
        private val albumName: String = (if (albumName == "<unknown>") "" else albumName)

        val isValidImageFile: Boolean
            /** Returns whether the image file contains a valid image file extension like PNG or JPEG.
             * This is also only useful when using standard File/IO, where you deal with unknown data. */
            get() {
                val f =
                    imageFile.getName().trim { it <= ' ' }.lowercase(Locale.getDefault())
                val extensions: Array<String> = arrayOf(
                    "jpg",
                    "jpeg",
                    "png",
                    "webp",
                    "gif",
                    "bmp",
                    "avif",
                    "heif",
                    "jxl",
                    "bmp"
                )
                return Arrays.stream(extensions).anyMatch { f.endsWith(it) }
            }

        val isInSameFolder: Boolean
            /** This only really works when using standard File/IO, does not work that well with relative paths provided by the MediaStore API */
            get() {
                val imgParent = imageFile.getParent()
                val songParent = songPath.getParent()
                return (imgParent != null && imgParent == songParent)
            }

        val isGenericAlbumArtName: Boolean
            /** Whether the image file has a common, generic name like "album.jpg" */
            get() {
                val f =
                    imageFile.name.trim { it <= ' ' }.lowercase(Locale.getDefault())
                val nameSnippets: Array<String> = arrayOf(
                    "album.",
                    "playlist.",
                    "cover.",
                    "front.",
                    "artwork.",
                    "folder.",
                    "albumart.",
                    "albumartsmall.",
                    "coverart."
                )
                return Arrays.stream<String?>(nameSnippets).anyMatch { f.contains(it) }
            }

        val significantFilenamePrefixMatch: Int
            /** This is useful for matching song files in this format:
             * "(album name) - (song name)"
             * accompanied by images in this format:
             * "(album name).jpg" */
            get() {
                val img =
                    imageFile.getName().trim { it <= ' ' }.lowercase(Locale.getDefault())
                val song =
                    songPath.getName().trim { it <= ' ' }.lowercase(Locale.getDefault())
                return getSignificantCommonPrefix(img, song)
            }

        val insignificantFilenamePrefixMatch: Int
            get() {
                val img =
                    imageFile.getName().trim { it <= ' ' }.lowercase(Locale.getDefault())
                val song =
                    songPath.getName().trim { it <= ' ' }.lowercase(Locale.getDefault())
                return getCommonPrefix(img, song)
            }

        val significantAlbumPrefixMatch: Int
            /** If no album is specified, the folder name is used instead. */
            get() {
                val song = songPath.getName().trim { it <= ' ' }.lowercase(Locale.getDefault())
                if (albumName.isBlank() || albumName == "<unknown>") {
                    val folder = Optional.ofNullable<String?>(imageFile.getParent()).orElse("").trim { it <= ' ' }.lowercase(Locale.getDefault())
                    return getSignificantCommonPrefix(folder, song)
                } else {
                    return getSignificantCommonPrefix(albumName, song)
                }
            }

        val insignificantAlbumPrefixMatch: Int
            get() {
                val song = songPath.getName().trim { it <= ' ' }.lowercase(Locale.getDefault())
                if (albumName.isBlank() || albumName == "<unknown>") {
                    val folder = Optional.ofNullable<String?>(imageFile.getParent()).orElse("").trim { it <= ' ' }.lowercase(Locale.getDefault())
                    return getCommonPrefix(folder, song)
                } else {
                    return getCommonPrefix(albumName, song)
                }
            }

        /** Wrapper to getCommonPrefix() that returns 0 if the minimum threshold is not succeeded.
         * This rules out coincidental common prefix matches. */
        fun getSignificantCommonPrefix(a: String, b: String): Int {
            val match = getCommonPrefix(a, b)
            /* 5 is a tough choice that comes with tradeoffs:

             - This rules out coincidental common prefix matches
             - It prevents matches with similarly beginning bands, as 5 is longer
               than "the " (English) or "die " (German) which are common prefixes for bands.
             - Bands with similar names after the article might collide, for instance:
               "The Cords - Sh-Boom.mp3" might get matched to "The Chordettes - Mr Sandman.png"
             - Short artist names are problematic. For instance "C418.png" won't get matched to
               "C418 - Floating Trees.mp3".
             */
            if (match < 5) {
                return 0
            } else {
                return match
            }
        }

        companion object {
            private fun getCommonPrefix(a: String, b: String): Int {
                var commonPrefix = 0
                while (true) {
                    if (commonPrefix >= a.length || commonPrefix >= b.length) break

                    if (a.get(commonPrefix) == b.get(commonPrefix)) commonPrefix++
                    else break
                }

                return commonPrefix
            }
        }
    }

    companion object {
        private val cache = DeduplicationCache<Long, Bitmap>(
            CacheBuilderSpec.parse("maximumSize=10, expireAfterAccess=3h"),
            Companion::bitmapsAreSame
        )

        @Volatile
        var isTerminated: Boolean = false
            private set

        fun terminate() {
            isTerminated = true
        }

        fun resetTermination() {
            isTerminated = false
        }


        private fun bitmapsAreSame(b1: Bitmap?, b2: Bitmap?): Boolean {
            // If they are the same reference or both null, they are the same
            if (b1 === b2) return true

            // If only one is null, return false
            if (b1 == null || b2 == null) return false

            // Compare by value
            return b1.sameAs(b2)
        }

        /**
         * This sanitizes file names provided by ContentResolver's displayName column.
         */
        val String.sanitizedAsFileName: String
            get() {
                if (this.isEmpty()) {
                    return "default_file_" + System.currentTimeMillis()
                }

                // Only take characters after the final slash
                val fileName = File(this).name

                // Replace illegal symbols with underscores
                return fileName.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
            }
    }
}
