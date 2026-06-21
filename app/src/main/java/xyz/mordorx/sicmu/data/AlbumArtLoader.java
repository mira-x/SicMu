package xyz.mordorx.sicmu.data;

import android.content.ContentUris;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;

import androidx.annotation.Nullable;

import com.google.common.cache.CacheBuilderSpec;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

import xyz.mordorx.sicmu.R;
import xyz.mordorx.sicmu.collections.DeduplicationCache;
import xyz.mordorx.sicmu.collections.LastElementCollector;

/**
 * This class is responsive for loading album art data from multiple sources (metadata, jpg files, etc).
 * It also implements caching. For now, album art is limited to at most one image per song.
 */
public class AlbumArtLoader {
    public interface Callback {
        void callback(long rowSongId, @Nullable Bitmap bitmap) ;
    }

    ///  Maps a RowSongID to an Optional<Bitmap>. It can have three states:
    /// 1. Key not in cache / null: We have not yet looked for this image
    /// 2. Optional.isEmpty(): We looked for an image, but there is none
    /// 3. Optional.isPresent(): We looked for an image and found one
    private final static DeduplicationCache<Long, Optional<Bitmap>> cache =
            new DeduplicationCache<>(CacheBuilderSpec.parse("maximumSize=10, expireAfterAccess=6h"), AlbumArtLoader::bitmapsAreSame);

    private final Size thumbSize = new Size(getScreenWidth(), getScreenWidth());

    private final Context ctx;
    private final RowSong song;
    private static volatile boolean terminated = false;

    public static void terminate() {
        terminated = true;
    }

    public static void resetTermination() {
        terminated = false;
    }

    public static boolean isTerminated() {
        return terminated;
    }
    private static Bitmap fallback = null;

    public AlbumArtLoader(Context ctx, RowSong song) {
        this.ctx = ctx.getApplicationContext();
        this.song = song;

        if (fallback == null) {
            fallback = BitmapFactory.decodeResource(ctx.getResources(), R.drawable.ic_default_coverart);
        }
    }

    /// This spins up a thread to load an album image, if it's not cached currently. If it is,
    /// the callback is called instantly, in sync.
    public void loadAsync(Callback albumBmpCallback)
    {
        var cachedBmp = cache.getIfPresent(song.getID());
        //noinspection OptionalAssignedToNull We compare the optional against null on purpose.
        if (cachedBmp != null) {
            Log.d("AlbumArtLoader", "Cache Hit. RowSongID=" + song.getID() + " SongPath=" + song.getPath() + " Bitmap=" + cachedBmp.orElse(fallback));
            albumBmpCallback.callback(song.getID(), cachedBmp.orElse(fallback));
            return;
        }

        new Thread(() -> {
            if (terminated) return;
            var bmp = load();
            if (terminated) return;
            albumBmpCallback.callback(song.getID(), bmp);
        }).start();
    }

    @Nullable
    public Bitmap load() {
        // Cache lookup
        var cachedBmp = cache.getIfPresent(song.getID());
        //noinspection OptionalAssignedToNull We compare the optional against null on purpose.
        if (cachedBmp != null) {
            Log.d("AlbumArtLoader", "Cache Hit. RowSongID=" + song.getID() + " SongPath=" + song.getPath() + " Bitmap=" + cachedBmp.orElse(null));
            return cachedBmp.orElse(null);
        }

        var file = new File(song.getPath());
        Bitmap bmp;

        // Try to load art from different sources, starting at embedded art and falling back to image files near the song file.
        bmp = loadViaLoadThumbnail();
        if (bmp == null)
            bmp = loadViaCreateAudioThumbnail(file);
        if (bmp == null)
            bmp = loadViaMediaMetadataRetriever();
        if (bmp == null)
            bmp = loadViaMediaStore();
        if (bmp == null)
            bmp = loadViaLocalImages();

        Log.d("AlbumArtLoader", "Cache Miss. RowSongID=" + song.getID() + " SongPath=" + song.getPath() + " Bitmap=" + bmp);
        cache.put(song.getID(), Optional.ofNullable(bmp));

        // Fallback: load generic placeholder image (which also might be null)
        if (bmp == null) {
            return fallback;
        } else {
            return bmp;
        }
    }

    private Bitmap loadViaLoadThumbnail() {
        // Android 10 "Quince Tart"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null;
        }
        try {
            return ctx.getContentResolver().loadThumbnail(song.getExternalContentUri(), thumbSize, null);
        } catch (Exception ignored) {return null; }
    }

    private Bitmap loadViaCreateAudioThumbnail(File audioFile) {
        // Android 10 "Quince Tart"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null;
        }
        try {
            return ThumbnailUtils.createAudioThumbnail(audioFile, thumbSize, null);
        } catch (Exception ignored) { return null; }
    }

    private Bitmap loadViaMediaMetadataRetriever() {
        try (MediaMetadataRetriever mmr = new MediaMetadataRetriever()){
            mmr.setDataSource(song.getPath());
            var img_bytes = mmr.getEmbeddedPicture();
            if (img_bytes != null)
                return BitmapFactory.decodeByteArray(img_bytes, 0, img_bytes.length, new BitmapFactory.Options());
        } catch (Exception ignored) { }

        return null;
    }

    private Bitmap loadViaMediaStore() {
        try (Cursor cursor = ctx.getContentResolver().query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM_ART},
                MediaStore.Audio.Albums._ID + "=?",
                new String[]{String.valueOf(song.getAlbumId())},
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int colIdx = cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM_ART);
                String path = cursor.getString(Math.max(colIdx, 0));
                return BitmapFactory.decodeFile(path);
            }
        } catch (Exception ignored) { }

        return null;
    }

    private Bitmap loadViaLocalImages() {
        /* Fallback: Search for image files in the same directory.
         *
         * The algorithm looks for the longest prefix match and similarity between image file name
         * and song file name and album/folder name. It also looks for
         * files like "album.jpg" and "playlist.jpg"
         **/
        var songFile = new File(song.getPath());
        var songAlbum = song.getAlbum();
        var songFolder = song.getFolder();

        var res = ctx.getContentResolver();
        var proj = new ArrayList<String>();
        /*
         * Real world example data for these three column:
         * ID = 28353
         * RelativePath = Music/_/East Los FM/
         * DisplayName = Fandango.jpg
         */
        proj.add(MediaStore.Images.Media._ID);
        proj.add(MediaStore.Images.Media.RELATIVE_PATH);
        proj.add(MediaStore.Images.Media.DISPLAY_NAME);

        var sel = MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?";
        var selParams = new String[]{ "%" + song.getFolder() + "/" };

        var candidates = new ArrayList<AlbumImageCandidate>();

        try (var cursor = res.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj.toArray(new String[]{}), sel, selParams, null)) {
            var idxID = cursor.getColumnIndex(MediaStore.Images.Media._ID);
            var idxRelativePath = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH);
            var idxDisplayName = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);

            while(cursor.moveToNext()) {
                var ID = cursor.getLong(idxID);
                var relativePath = cursor.getString(idxRelativePath);
                var displayName = cursor.getString(idxDisplayName);
                var imgFile = new File(relativePath, displayName);

                candidates.add(new AlbumImageCandidate(imgFile, songFile, songAlbum, ID));

                Log.d("AlbumArtLoader", "Found local image: id=" + ID + ", path=" + relativePath + " \t image name=" + displayName + " \t path query=" + songFolder);
            }
            
            Log.d("AlbumArtLoader", "Searching local images done.");
        }

        final var sorter = Comparator
                .comparing(AlbumImageCandidate::getSignificantFilenamePrefixMatch)
                .thenComparing(AlbumImageCandidate::getSignificantAlbumPrefixMatch)
                .thenComparing(AlbumImageCandidate::isGenericAlbumArtName)
                .thenComparing(AlbumImageCandidate::getInsignificantFilenamePrefixMatch)
                .thenComparing(AlbumImageCandidate::getInsignificantAlbumPrefixMatch);

        final var albumArt = candidates
                .stream()
                .sorted(sorter)
                .collect(new LastElementCollector<>());

        if (albumArt.isEmpty()) {
            return null;
        }

        final var imgId = albumArt.get().getImageID();
        final var imgUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imgId);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return ctx.getContentResolver().loadThumbnail(imgUri, thumbSize, null);
            } catch (Exception ignored) { }
        }

        try (var bmpStream = ctx.getContentResolver().openInputStream(imgUri)) {
            return BitmapFactory.decodeStream(bmpStream);
        } catch (Exception e) {
            Log.i("AlbumArtLoader", "Exception while trying to read bitmap stream for MediaStore image No." + imgId, e);
        }

        return null;
    }

    private int getScreenWidth() {
        int width = Resources.getSystem().getDisplayMetrics().widthPixels;
        if (width < 128)
            width = 128;
        return width;
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static boolean bitmapsAreSame(Optional<Bitmap> b1, Optional<Bitmap> b2) {
        if (b1.isEmpty() && b2.isEmpty()) return true;

        if (b1.isPresent() && b2.isEmpty()) return false;
        if (b1.isEmpty()/* && b2.isPresent() */) return false;

        var bmp1 = b1.get();
        var bmp2 = b2.get();

        return bmp1.sameAs(bmp2);
    }

    /**
     * This is a wrapper class for two strings: an image file path and a song file path. This class
     * helps with comparing these two paths and determining whether the image file is a suited
     * *album image candidate*
     */
    private static class AlbumImageCandidate {
        private final String albumName;
        private final File imgPath;
        private final File songPath;
        private final long imageID;

        /// This handles relative and absolute paths.
        public AlbumImageCandidate(File img, File song, String albumName, long imageID) {
            this.imgPath = img;
            this.songPath = song;
            this.albumName = (albumName.equals("<unknown>") ? "" : albumName);
            this.imageID = imageID;
        }

        public File getImageFile() {
            return imgPath;
        }
        public long getImageID() { return imageID; }

        /// Returns whether the image file contains a valid image file extension like PNG or JPEG.
        /// This is also only useful when using standard File/IO, where you deal with unknown data.
        public boolean isValidImageFile() {
            var f = imgPath.getName().trim().toLowerCase();
            final var extensions = new String[]{"jpg", "jpeg", "png", "webp", "gif", "bmp", "avif", "heif", "jxl", "bmp"};
            return Arrays.stream(extensions).anyMatch(f::endsWith);
        }

        /// This only really works when using standard File/IO, does not work that well with relative paths provided by the MediaStore API
        public boolean isInSameFolder() {
            var imgParent = imgPath.getParent();
            var songParent = songPath.getParent();
            return (imgParent != null && imgParent.equals(songParent));
        }

        /// Whether the image file has a common, generic name like "album.jpg"
        public boolean isGenericAlbumArtName() {
            var f = imgPath.getName().trim().toLowerCase();
            final var nameSnippets = new String[]{"album.", "playlist.", "cover.", "front.", "artwork.", "folder.", "albumart.", "albumartsmall.", "coverart."};
            return Arrays.stream(nameSnippets)
                    .anyMatch(f::contains);
        }

        /// This is useful for matching song files in this format:
        ///     "(album name) - (song name)"
        /// accompanied by images in this format:
        ///     "(album name).jpg"
        public int getSignificantFilenamePrefixMatch() {
            var img = imgPath.getName().trim().toLowerCase();
            var song = songPath.getName().trim().toLowerCase();
            return getSignificantCommonPrefix(img, song);
        }

        public int getInsignificantFilenamePrefixMatch() {
            var img = imgPath.getName().trim().toLowerCase();
            var song = songPath.getName().trim().toLowerCase();
            return getCommonPrefix(img, song);
        }

        /// If no album is specified, the folder name is used instead.
        public int getSignificantAlbumPrefixMatch() {
            var song = songPath.getName().trim().toLowerCase();
            if (albumName.isBlank() || albumName.equals("<unknown>")) {
                var folder = Optional.ofNullable(imgPath.getParent()).orElse("").trim().toLowerCase();
                return getSignificantCommonPrefix(folder, song);
            } else {
                return getSignificantCommonPrefix(albumName, song);
            }
        }

        public int getInsignificantAlbumPrefixMatch() {
            var song = songPath.getName().trim().toLowerCase();
            if (albumName.isBlank() || albumName.equals("<unknown>")) {
                var folder = Optional.ofNullable(imgPath.getParent()).orElse("").trim().toLowerCase();
                return getCommonPrefix(folder, song);
            } else {
                return getCommonPrefix(albumName, song);
            }
        }

        /// Wrapper to getCommonPrefix() that returns 0 if the minimum threshold is not succeeded.
        /// This rules out coincidental common prefix matches.
        private int getSignificantCommonPrefix(String a, String b) {
            var match = getCommonPrefix(a, b);
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
                return 0;
            } else {
                return match;
            }
        }

        private static int getCommonPrefix(String a, String b) {
            var commonPrefix = 0;
            while (true) {
                if (commonPrefix >= a.length() || commonPrefix >= b.length())
                    break;

                if (a.charAt(commonPrefix) == b.charAt(commonPrefix))
                    commonPrefix++;
                else
                    break;
            }

            return commonPrefix;
        }
    }
}
