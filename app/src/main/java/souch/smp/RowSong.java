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

package souch.smp;

import android.content.ContentUris;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.support.v4.media.MediaMetadataCompat;
import android.util.Size;
import android.util.TypedValue;
import android.widget.ImageView;
import android.widget.TextView;
import android.database.Cursor;

import java.io.File;
import java.io.FilenameFilter;

public class RowSong extends Row {
    private long id;
    private long albumId;
    private String title;
    private String artist;
    private String album;
    private long durationMs;
    private int track;
    private int year;
    // full filename
    private String path;
    // folder of the path (i.e. last folder containing the file's song)
    private String folder;

    protected static int textSize = 15;

    // must be set outside before calling setText
    public static int normalSongTextColor;
    public static int normalSongDurationTextColor;

    public RowSong(int pos, int level, long songID, String songTitle, String songArtist, String songAlbum,
                   long durationMs, int songTrack, String songPath, long albumId, int year) {
        super(pos, level, Typeface.NORMAL);
        id = songID;
        title = songTitle;
        artist = songArtist;
        album = songAlbum;
        this.durationMs = durationMs;
        track = songTrack;
        path = songPath;
        this.albumId = albumId;
        this.year = year;
        if(path != null) {
            folder = Path.getFolder(path);
        }
    }

    public long getID(){return id;}
    public String getTitle(){return title;}
    public int getYear(){return year;}
    public String getArtist(){return artist;}
    public String getAlbum(){return album;}
    public long getDurationMs(){return durationMs;}
    public int getTrack(){return track;}
    public String getPath(){return path;}
    public String getFolder(){return folder;}
    public long getAlbumId(){return albumId;}


    public void setView(RowViewHolder holder, Main main, int position) {
        super.setView(holder, main, position);

        float factor = 1.5f;
        if (main.getMusicSrv().getRows().isLastRow(position))
            factor = 3f;
        holder.layout.getLayoutParams().height = convertDpToPixels((int) (textSize * factor),
                holder.layout.getResources());

        setText(holder.text);
        setDuration(holder.duration);
        setCurrIcon(holder.image, main);
    }

    private void setText(TextView text) {
        text.setText(title);
        text.setTextColor(normalSongTextColor);
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize);
    }

    private void setDuration(TextView duration) {
        duration.setText(msToMinutes(getDurationMs()) + getStringOffset());
        duration.setTextColor(normalSongDurationTextColor);
        duration.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize);
        duration.setTypeface(null, typeface);
        /*
        duration.setBackgroundColor(Color.argb(0x00, 0x0, 0x0, 0x0));
        duration.setOnClickListener(null);
        */
    }

    private void setCurrIcon(ImageView img, Main main) {
        int currIcon = android.R.color.transparent;
        if (this == main.getMusicSrv().getRows().getCurrSong()) {
            if (main.getMusicSrv().playingLaunched())
                currIcon = R.drawable.ic_curr_play;
            else
                currIcon = R.drawable.ic_curr_pause;
        }
        img.setImageResource(currIcon);
        // useful only for the tests
        img.setTag(currIcon);
    }

    public String toString() {
        return "Song  pos: " + genuinePos + " level: " + level + " ID: " + id + " artist: " + artist +
                " album: " + album + " title: " + title + " " +
                msToMinutes(durationMs) + " track:" + track + " path: " + path;
    }

    static public String msToMinutes(long durationMs){
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.valueOf(minutes) + (seconds < 10 ? ":0" : ":") + String.valueOf(seconds);
    }


    public boolean delete(Context context) {
        if ((new File(path)).delete()) {
            // delete it from media store too
            Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
            context.getContentResolver().delete(uri, null, null);

            return true;
        }
        return false;
    }

    private int getScreenWidth() {
        int width = Resources.getSystem().getDisplayMetrics().widthPixels;//displayMetrics.widthPixels;
        if (width < 512)
            width = 512;
        return width;
    }

    public Bitmap getAlbumBmp(Context context) {
        return getAlbumBmp(context, 0);
    }

    // implement simple cache: cache is discard as soon as song changes
    private static long cachedAlbumBmpID = -1;
    private static Bitmap cachedAlbumBmp = null;

    /** if imageNum > 0: try to get Nth bitmap from same folder
     * @return null if bitmap not found
     */
    public synchronized Bitmap getAlbumBmp(Context context, int imageNum) {
        if (imageNum == 0 && cachedAlbumBmpID == id)
            return cachedAlbumBmp;

        Bitmap bmp = null;
        try {
            // try first by searching manually in song's path (as it gives better resolution)
            if (bmp == null && path != null) {
                File dir = new File(path).getParentFile();
                if (dir.exists() && dir.isDirectory()) {
                    File[] files = dir.listFiles(new FilenameFilter() {
                        public boolean accept(File dir, String name) {
                            return Path.filenameIsImage(name);
                        }
                    });
                    int currImgIdx = imageNum;
                    if (files != null)
                        for (File file : files) {
                            if (currImgIdx == 0) {
                                // found
                                bmp = BitmapFactory.decodeFile(file.getAbsolutePath());
                                break;
                            }
                            currImgIdx--;
                        }
                }
            }

            if (imageNum == 0) {
                if (bmp == null) {
                    final int thumb_size = getScreenWidth();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // try with loadThumbnail
                        Size size = new Size(thumb_size, thumb_size);
                        bmp = context.getContentResolver().loadThumbnail(getExternalContentUri(), size, null);

                        // try with createAudioThumbnail
                        if (bmp == null) {
                            bmp = ThumbnailUtils.createAudioThumbnail(
                                    new File(path),
                                    new Size(thumb_size, thumb_size),
                                    null);
                        }
                    }
                }

                // try with MediaMetadataRetriever
                if (bmp == null) {
                    MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                    mmr.setDataSource(path);
                    byte[] img_byte = mmr.getEmbeddedPicture();
                    if (img_byte != null)
                        bmp = BitmapFactory.decodeByteArray(img_byte, 0, img_byte.length,
                                new BitmapFactory.Options());
                }

                // try with media store ?
                if (bmp == null) {
                    Cursor cursor = context.getContentResolver().query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                            new String[]{MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM_ART},
                            MediaStore.Audio.Albums._ID + "=?",
                            new String[]{String.valueOf(albumId)},
                            null);
                    if (cursor.moveToFirst()) {
                        String path = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM_ART));
                        bmp = BitmapFactory.decodeFile(path);
                    }
                }
            }
        }
        catch(Exception e) {
            bmp = null;
        }

        if (imageNum == 0) {
            cachedAlbumBmpID = id;
            cachedAlbumBmp = bmp; // cache even if bmp is null so that we do not search again
        }

        return bmp;
    }

    public Uri getExternalContentUri() {
        return ContentUris.withAppendedId(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
    }

    public MediaMetadataCompat getMediaMetadata(Context context) {
        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
        builder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, String.valueOf(id));
        builder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, getExternalContentUri().toString());
        builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title);
        builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist);
        builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album);
        builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs);
        builder.putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, track);
        builder.putLong(MediaMetadataCompat.METADATA_KEY_YEAR, year);
        builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, getAlbumBmp(context)); // todo: optimize
        return builder.build();
    }
}
