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

package xyz.mordorx.sicmu;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v4.media.MediaMetadataCompat;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import java.io.File;

public class RowSong extends Row {
    private final long id;
    private final long albumId;
    private final String title;
    private final String artist;
    private final String album;
    private final long durationMs;
    private final int track;
    private final int year;
    private final String mime;
    // RATING_NOT_INITIALIZED means we did not tried to read id3 rating
    // actually 0 means not initialized too (we handle reading 0, but it is not very useful, cause
    // we cannot set a rating of 0 star in the UI)
    public static final int RATING_NOT_INITIALIZED = -2;
    // RATING_UNKNOWN means we did read id3 rating, but it was not set
    public static final int RATING_UNKNOWN = -1;
    private int rating;
    // full filename
    private String path;
    private String filename;
    // folder of the path (i.e. last folder containing the file's song)
    private String folder;

    private final SongDAO songDAO;

    private final Settings params;

    protected static int textSize = 15;

    // must be set outside before calling setText
    public static int normalSongTextColor;
    public static int normalSongDurationTextColor;
    public static int backgroundSongColor;

    public RowSong(SongDAO songDAO, int pos, int level, long songID, String songTitle, String songArtist, String songAlbum,
                   long durationMs, int songTrack, String songPath, long albumId, int year, String mime, Settings params) {
        super(pos, level, Typeface.NORMAL);
        this.songDAO = songDAO;
        id = songID;
        title = songTitle;
        artist = songArtist;
        album = songAlbum;
        this.durationMs = durationMs;
        track = songTrack;
//        // some songs are numbered from 1000, usually when there is 2 CD
//        if (track > 1000 && track < 2000)
//            track -= 1000;
//        if (track > 2000 && track < 3000)
//            track -= 2000;
//        if (track > 3000 && track < 4000)
//            track -= 3000;
        path = songPath;
        File f = new File(path);
        filename = f.getName();
        rating = RATING_NOT_INITIALIZED;
        this.albumId = albumId;
        this.year = year;
        this.mime = mime;
        folder = Path.getFolder(path);
        this.params = params;
    }

    public long getID(){return id;}
    public String getTitle(){return title;}
    public int getYear(){return year;}
    public String getMime(){return mime;}
    public String getArtist(){return artist;}
    /// If no album metadata is set, this will return the top level folder name of this song
    public String getAlbum(){return album;}
    public long getDurationMs(){return durationMs;}
    public int getTrack(){return track;}
    /// For Example: "/storage/emulated/0/Music/_/Unterhaltung/HintShot - Welcome to Team Fortress.opus"
    public String getPath(){return path;}
    /// For Example: "_/Unterhaltung"
    public String getFolder(){return folder;}
    public long getAlbumId(){return albumId;}
    /// For example: "HintShot - Welcome to Team Fortress.opus"
    public String getFilename() {return filename;}

    public void setView(RowViewHolder holder, Main main, int position) {
        super.setView(holder, main, position);

        float factor = 1.5f;
        if (main.getMusicSrv().getRows().isLastRow(position))
            factor = 2f;
        holder.layout.getLayoutParams().height = convertDpToPixels((int) (textSize * factor),
                holder.layout.getResources());

        setText(holder.text);
        setDuration(holder.duration);
        setCurrIcon(holder.image, main);
        if (MusicService.getEnableRating()) {
            holder.ratingStar.setVisibility(View.VISIBLE);
            holder.ratingStar.setImageResource(getDrawableStarFromRating());

            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) holder.duration.getLayoutParams();
            // removeRule is not in sdk < 17
            params.removeRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            holder.duration.setLayoutParams(params);
        }
        else {
            holder.ratingStar.setVisibility(View.INVISIBLE);

            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) holder.duration.getLayoutParams();
            params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            holder.duration.setLayoutParams(params);
        }
        setBackgroundColor(holder, backgroundSongColor);
    }

    private void setText(TextView text) {
        text.setText(getText());
        text.setTextColor(normalSongTextColor);
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize);
    }

    public String getText() {
        if (params.getShowFilename())
            return filename;
        else if (track > 0)
            return track + ". " + title;
        else
            return title;
    }

    private void setDuration(TextView duration) {
        duration.setText(msToMinutesStripSecondIfLongDuration(getDurationMs()) + getStringOffset());
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

    @NonNull
    public String toString() {
        return "title: " + title + " album: " + album + " artist: " + artist +
                " pos: " + genuinePos + " level: " + level + " ID: " + id +
                msToMinutes(durationMs) + " track:" + track + " path: " + path;
    }

    static public String msToMinutes(long durationMs, boolean showSeconds){
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        if (showSeconds) {
            seconds = seconds % 60;
            return minutes + (seconds < 10 ? ":0" : ":") + seconds;
        }
        else {
            return String.valueOf(minutes);
        }
    }
    static public String msToMinutes(long durationMs) {
        return msToMinutes(durationMs, true);
    }

    static public String msToMinutesStripSecondIfLongDuration(long durationMs){
        return msToMinutes(durationMs, durationMs < 100*60*1000);
    }

    @Override
    public boolean rename(Context ctx, File newPath) {
        var oldPath = new File(getPath());
        if(!oldPath.renameTo(newPath)) {
            return false;
        }

        this.path = newPath.getPath();
        this.filename = newPath.getName();
        this.folder = Path.getFolder(path);
        Path.rescanFile(ctx, oldPath);
        Path.rescanFile(ctx, newPath);
        
        return true;
    }

    public boolean deleteFile(Context context) {
        if ((new File(path)).delete()) {
            // delete it from media store too
            Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
            context.getContentResolver().delete(uri, null, null);

            return true;
        }
        return false;
    }

    public int getRating() {
        return rating;
    }

    public boolean isRatingInsufficient() {
        final boolean uninitialized = rating == RATING_NOT_INITIALIZED || rating == RATING_UNKNOWN;
        final int minRating = params.getMinRating();
        return (!uninitialized || params.getUninitializedDefaultRating() < minRating) &&
                rating < minRating;
    }

    public interface LoadRatingCallbackInterface {
        // @param someRatingChanged set to true if loadRating brings new RowSong's rating
        // i.e. set to false if RowSong's rating did not change
        void ratingCallback(int rating, boolean ratingChanged);
    }

    public synchronized void loadRatingAsync(LoadRatingCallbackInterface ratingCallbackInterface) {
        if (rating != RowSong.RATING_NOT_INITIALIZED) {
            ratingCallbackInterface.ratingCallback(rating, false);
        }
        else {
            Thread thread = new Thread() {
                @Override
                public void run() {
                    Log.d("RowSong", "loadRating");
                    boolean someRatingChanged = (rating == RowSong.RATING_NOT_INITIALIZED && loadRating() > 0);
                    ratingCallbackInterface.ratingCallback(rating, someRatingChanged);
                }
            };
            thread.start();
        }
    }

    private void updateOrInsertSongOrm(SongORM songORM) {
        updateOrInsertSongOrm(songORM, true);
    }

    // @param ratingSynchronized set to true if corresponding file has the rating property correctly set
    private void updateOrInsertSongOrm(SongORM songORM, boolean ratingSynchronized) {
        try {
            if (songORM != null) {
                Log.d("RowSong", "Update songORM for path=" + path);
                songORM.rating = rating;
                songORM.lastModifiedMs = (new File(songORM.path)).lastModified();
                songORM.ratingSynchronized = ratingSynchronized;
                songDAO.update(songORM);
            }
            else {
                Log.d("RowSong", "New songORM for path=" + path);
                songDAO.insert(new SongORM(path, rating, ratingSynchronized));
            }
        } catch (Exception e) {
            Log.w("RowSong", "Unable to update/insert songORM for path=" + path
                    + " e=" + e);
        }
    }

    // ! must not be called from main thread !
    public synchronized int loadRating() {
        // get rating is computed on demand cause it is slow
        if (rating == RATING_NOT_INITIALIZED) {
            // try to load rating from cache
            long fileLastModifiedMs;
            SongORM songORM = songDAO.findByPath(path);
            if (songORM != null) {
                fileLastModifiedMs = (new File(songORM.path)).lastModified();
                if (fileLastModifiedMs <= songORM.lastModifiedMs) {
                    //Log.d("RowSong", "Found songORM for path=" + path);
                    rating = songORM.rating;
                }
                else {
                    Log.d("RowSong", "Found songORM for path=" + path + " but cache is obsolete");
                }
            }

            // cache miss, read the ID3
            if (rating == RATING_NOT_INITIALIZED) {
                try {
                    AudioFile audioFile = AudioFileIO.read(new File(path));
                    Tag tag = audioFile.getTag();
                    if (tag != null) {
                        if (tag.hasField(FieldKey.RATING)) {
                            rating = convertToRating0to5(tag.getFirst(FieldKey.RATING));
                            Log.d("RowSong", "song rating " + path + " = " + rating);
                        } else {
                            Log.d("RowSong", "song rating " + path + " rating not available");
                        }
                    } else {
                        Log.d("RowSong", "song rating " + path + " tag not available");
                    }
                    if (rating < 0)
                        rating = RATING_UNKNOWN;

                    updateOrInsertSongOrm(songORM);
                } catch (Exception e) {
                    Log.w("RowSong", "Unable to get rating of song " + path +
                            ". Exception msg: " + e.getClass() + " - " + e.getMessage());
                    // if id3tag read failed we do not update or create an entry in the database
                    // that means, the database will not be used as cached and
                    // if several files can not be read : that will slow down the app
                }
            }
        }
        return rating;
    }

    public static boolean WriteRatingToFile(String path, int rating) {
        boolean ok = false;
        try {
            AudioFile audioFile = AudioFileIO.read(new File(path));
            Tag tag = audioFile.getTagOrCreateAndSetDefault();
            if (tag.hasField(FieldKey.RATING))
                tag.setField(FieldKey.RATING, convertToRating0to255(rating));
            else
                tag.addField(FieldKey.RATING, convertToRating0to255(rating));
            audioFile.commit();
            ok = true;
            Log.i("RowSong", "set file rating : " + path + " to " + rating);
        } catch (Exception e) {
            String wrn = "Unable to set rating for song:" + path +
                    ". Exception msg: " + e.getClass() + " - " + e.getMessage();
            Log.w("RowSong", wrn);
        }
        return ok;
    }

    // this func must not be called from main thread !
    // return true if set rating succeed
    public synchronized boolean setRating(int rating) {
        this.rating = rating;
        boolean ok = WriteRatingToFile(path, rating);
        updateOrInsertSongOrm(songDAO.findByPath(path), ok);

        return ok;
    }

    // write rating to file later (useful to modify file when it is not currently reading)
    public synchronized void scheduleSetRating(int rating, boolean async)
    {
        // sync part
        this.rating = rating;

        if (async) {
            Thread thread = new Thread() {
                @Override
                public void run() {
                    updateOrInsertSongOrm(songDAO.findByPath(path), false);
                }
            };
            thread.start();
        }
        else {
            updateOrInsertSongOrm(songDAO.findByPath(path), false);
        }
    }

    /*
        224–255 = 5 stars when READ with Windows Explorer, writes 255
        160–223 = 4 stars when READ with Windows Explorer, writes 196
        096-159 = 3 stars when READ with Windows Explorer, writes 128
        032-095 = 2 stars when READ with Windows Explorer, writes 64
        001-031 = 1 star when READ with Windows Explorer, writes 1
    */
    // convert table 0-5 -> 0-255
    public static final int[] id3ConventionRating = {0, 1, 64, 128, 196, 255};

    /* rating can be from 0 to 5
     * ex: 3 return "128"
     */
    public static String convertToRating0to255(int rating) {
        if (rating < 0)
            rating = 0;
        if (rating > 5)
            rating = 5;
        return String.valueOf(id3ConventionRating[rating]);
    }

    /* rating can be from 0 to 255
     * ex: "64" returns 2
     */
    public int convertToRating0to5(String rating) {
        int note;
        try {
            note = Integer.parseInt(rating);
        } catch (Exception e) {
            note = 0;
        }
        for (int i = 0; i < id3ConventionRating.length; i++)
            if (note <= id3ConventionRating[i])
                return i;
        return id3ConventionRating.length - 1;
    }

    public int getDrawableStarFromRating() {
        int drawable;
        switch (getRating()) {
            case 1: drawable = R.drawable.ic_star_1; break;
            case 2: drawable = R.drawable.ic_star_2; break;
            case 3: drawable = R.drawable.ic_star_3; break;
            case 4: drawable = R.drawable.ic_star_4; break;
            case 5: drawable = R.drawable.ic_star_5; break;
            default: drawable = R.drawable.ic_star_0;
        }
        return  drawable;
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
        builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, new AlbumArtLoader(context, this).load()); // todo: optimize
        //builder.putLong(MediaMetadataCompat.METADATA_KEY_RATING, Integer.valueOf(convertToRating0to255(getRating())));
        return builder.build();
    }
}
