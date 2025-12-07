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
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Typeface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Random;
import java.util.Timer;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.NonNull;


public class Rows {
    Context context;

    private Random random = new Random();
    private ArrayList<Integer> shuffleSavedPos;

    private ContentResolver musicResolver;
    private Parameters params;

    private Filter filter;

    // id of the song at last exiting
    private long savedID;

    // todo: see if another Collection than ArrayList would give better perf and code simplicity
    private ArrayList<Row> rows;
    private ArrayList<Row> rowsUnfolded;
    /// Current selected position within rowsUnfolded.
    /// Never assign this directly, instead use setCurrPos
    private int currPos;

    private Database database;
    private AtomicBoolean ratingsMustBeSynchronized, ratingsSynchronizing;

    private Timer timer;

    static final public String defaultStr = "<null>";
    private RepeatMode repeatMode;

    private boolean fileToOpenFound = false;

    public Rows(Context context, ContentResolver resolver, Parameters params, Resources resources,
                Database database) {
        this.context = context;
        this.params = params;
        this.database = database;
        musicResolver = resolver;
        currPos = -1;

        random = new Random();
        shuffleSavedPos = new ArrayList<>();

        rowsUnfolded = new ArrayList<>();
        rows = new ArrayList<>();

        ratingsMustBeSynchronized = new AtomicBoolean(false);
        ratingsSynchronizing = new AtomicBoolean(false);

        restore();
        init();
    }

    // size of the foldable array
    public int size() {
        return rows.size();
    }

    // the user choose a row
    /*
    public void select(int pos) {
        if(rows.get(pos).getClass() == RowSong.class) {
            setCurrPos(rows.get(pos).getGenuinePos());
        }
        else {
            invertFold(pos);
        }
    }
    */

    // select first song encountered from pos
    public void selectNearestSong(int pos) {
        Row row = rows.get(pos);
        while (row.getClass() != RowSong.class)
            row = rowsUnfolded.get(row.getGenuinePos() + 1);
        setCurrPos(row.getGenuinePos());
    }

    // get row from the foldable array
    public Row get(int pos) {
        Row row = null;
        if (pos >= 0 && pos < rows.size())
            row = rows.get(pos);
        return row;
    }

    public boolean getAndSetFileToOpenFound() {
        boolean isFileToOpenFound = fileToOpenFound;
        fileToOpenFound = false;
        return isFileToOpenFound;
    }

    public static String intentExtraFileScanComplete = "SicMuFileScanned";
    private MediaScannerConnection.OnScanCompletedListener scanFileCompletedCallback = new MediaScannerConnection.OnScanCompletedListener () {
        @Override
        public void onScanCompleted(String path, Uri uri){
            int pos = -1;
            if (uri != null) {
                Log.d("Rows", "onScanCompleted file " + path + " found");
                reinit();
                pos = getGenuinePosFromPath(path);
                if (pos != -1) {
                    setCurrPos(pos);
                    Log.d("Rows", "onScanCompleted file " + path + " pos setted");
                    fileToOpenFound = true;
                }
            }
            if (pos == -1) {
                String wrn = context.getString(R.string.app_name) + ": playing file " + path + " failed !";
                Log.i("Rows", wrn);
                Toast.makeText(context, wrn, Toast.LENGTH_LONG).show();
            }
        }
    };

    public boolean setCurrPosFromUri(Context context, Uri uri)
    {
        boolean found = false;
        String path = Path.getSongPathFromUri(context, uri);
        if (path != null) {
            Log.d("MusicService", "getFilePathFromUri -> " + path);
            int pos = getGenuinePosFromPath(path);
            if (pos != -1) {
                setCurrPos(pos);
                found = true;
                // rescan path so that it can see deleted file at next SicMu restart
                Path.scanMediaFolder(context, path, null);
            }
            else {
                Log.d("Rows", "Launch scan file for file " + path);
                // scan folder first so that when the callback fire the folder is already scanned (hopefully)
                Path.scanMediaFolder(context, path, null);
                Path.scanMediaFile(context, path, scanFileCompletedCallback);
            }
        }
        else {
            String wrn = context.getString(R.string.app_name) + ": playing uri " + uri.toString() + " failed !";
            Log.i("Rows", wrn);
            Toast.makeText(context, wrn, Toast.LENGTH_LONG).show();
        }
        return found;
    }

    private int getGenuinePosFromPath(String path) {
        int pos = -1;
        // todo: optimize search ?
        for (int i = 0; i < rowsUnfolded.size(); i++) {
            Row row = rowsUnfolded.get(i);
            if (row.getClass() == RowSong.class) {
                //Log.d("MusicService", "path " + ((RowSong) row).getPath());
                if (path.equals(((RowSong) row).getPath())) {
                    pos = row.getGenuinePos();
                    break;
                }
            }
        }
        return pos;
    }

    // get the song currently selected (playing or paused) from the unfoldable array
    public RowSong getCurrSong() {
        Row row = null;
        if (currPos >= 0 && currPos < rowsUnfolded.size()) {
            row = rowsUnfolded.get(currPos);
            if (row.getClass() != RowSong.class)
                row = null;
        }
        return (RowSong) row;
    }

    // get the currently selected row (group or song) from the foldable array
    public int getCurrPos() {
        int pos = -1;
        Row song = getCurrSong();
        int i;
        for (i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (row == song ||
                    (row.getClass() == RowGroup.class &&
                            ((RowGroup) row).isSelected() &&
                            ((RowGroup) row).isFolded()))
                break;
        }
        if (i < rows.size())
            pos = i;
        return pos;
    }

    private void setCurrPos(int pos) {
        setGroupSelectedState(currPos, false);
        currPos = pos;
        setGroupSelectedState(currPos, true);
    }

    private void setGroupSelectedState(int pos, boolean selected) {
        if (pos >= 0 && pos < rowsUnfolded.size()) {
            RowGroup group = (RowGroup) rowsUnfolded.get(pos).getParent();
            while (group != null) {
                group.setSelected(selected);
                group = (RowGroup) group.getParent();
            }
        }
    }

    public void moveToRandomSong() {
        if (rowsUnfolded.size() <= 0)
            return;

        if(false) {
            // A more stupid, but simpler Shuffle function.
            // Might delete later. Or the one below. Disabled for now.
            int _firstSongPos = getFirstSongPosInGroup(currPos);
            int _lastSongPos = getLastSongPosInGroup(currPos);
            int len = _lastSongPos - _firstSongPos;
            int pos = _firstSongPos + Math.abs(random.nextInt() % len);
            setCurrPos(pos);
            return;
        }

        if (repeatMode == RepeatMode.REPEAT_GROUP) {
            int firstSongPos = getFirstSongPosInGroup(currPos);
            int lastSongPos = getLastSongPosInGroup(currPos);
            if (lastSongPos <= firstSongPos)
                return;
            int nbSongInCurGroup = (lastSongPos - firstSongPos) + 1;

            // remove pos not in specified limit
            for (Iterator<Integer> iterator = shuffleSavedPos.iterator(); iterator.hasNext(); ) {
                int pos = iterator.next();
                if (pos < firstSongPos || pos > lastSongPos)
                    iterator.remove();
            }

            // save the previously song chosen
            shuffleSavedPos.add(currPos);
            Collections.sort(shuffleSavedPos);

            // reset shuffleSavedPos if we filled it entirely, add only curr pos
            if (shuffleSavedPos.size() >= nbSongInCurGroup ) {
                shuffleSavedPos.clear();
                shuffleSavedPos.add(currPos);
                Log.d("Rows", "shuffleSavedPos.clear");
            }

            // random on remaining part
            int randNum = random.nextInt(nbSongInCurGroup - shuffleSavedPos.size());
            currPos = randNum + firstSongPos;
            // shift song already done
            for (Iterator<Integer> iterator = shuffleSavedPos.iterator(); iterator.hasNext(); ) {
                int pos = iterator.next();
                if (pos <= currPos)
                    currPos++;
            }
        }
        else {
            // save the random song chosen
            shuffleSavedPos.add(currPos);

            int pos;
            do {
                pos = random.nextInt(rowsUnfolded.size());
            } while (pos == currPos || rowsUnfolded.get(pos).getClass() != RowSong.class);

            setGroupSelectedState(currPos, false);

            currPos = pos;

            setGroupSelectedState(currPos, true);
        }
    }

    // return the pos of the last song belonging to the given songPos group
    int getLastSongPosInGroup(int songPos) {
        Row currParent = rowsUnfolded.get(songPos).parent;
        songPos++;
        // if next row is the end of the list or a group or a different group, we reached another group
        while (songPos < rowsUnfolded.size() &&
                rowsUnfolded.get(songPos).getClass() == RowSong.class &&
                rowsUnfolded.get(songPos).parent == currParent) {
            songPos++;
        }
        return songPos - 1;
    }

    // return the pos of the first song belonging to the given songPos group
    int getFirstSongPosInGroup(int songPos) {
        Row currParent = rowsUnfolded.get(songPos).parent;
        songPos--;
        while (songPos > 0 &&
                rowsUnfolded.get(songPos).getClass() == RowSong.class &&
                rowsUnfolded.get(songPos).parent == currParent) {
            songPos--;
        }
        return songPos + 1;
    }

    boolean currPosIsLastSongInGroup() {
        RowSong song = getCurrSong();
        return song != null &&
                getLastSongPosInGroup(song.getGenuinePos()) == song.getGenuinePos();
    }

    // go back to previous random song done
    public void moveToRandomSongBack() {
        if (rowsUnfolded.size() <= 0)
            return;

        boolean backOk = false;
        if (shuffleSavedPos.size() > 0) {
            int pos = shuffleSavedPos.remove(shuffleSavedPos.size() - 1);
            // check
            if (pos < rowsUnfolded.size() && rowsUnfolded.get(pos).getClass() == RowSong.class) {
                backOk = true;
                setGroupSelectedState(currPos, false);
                currPos = pos;
                setGroupSelectedState(currPos, true);
            }
        }
        // if no saved pos, fallback to prevsong
        if (!backOk)
            moveToPrevSong();
    }

    public void moveToNextSong() {
        if (!params.getEnableRating() || params.getMinRating() <= 1)
            moveToNextSongNoRating();
        else {
            moveToNextSongRatingEnabled();
        }
    }

    private void moveToNextSongRatingEnabled() {
        if (rowsUnfolded.size() <= 0)
            return;

        if (repeatMode == RepeatMode.REPEAT_GROUP) {
            int lastSongPos = getLastSongPosInGroup(currPos);
            int firstSongPos = getFirstSongPosInGroup(currPos);
            if (lastSongPos == firstSongPos)
                return;

            int lastCurrPos = currPos;
            RowSong rowSong;
            // rowSong must have load the rating because we are in repeat group and the group
            // should have been loaded
            do {
                if (currPos == lastSongPos)
                    currPos = firstSongPos;
                else
                    currPos++;
                // next song with suitable rating not found => return next song regardless of rating
                if (currPos == lastCurrPos) {
                    if (currPos == lastSongPos)
                        currPos = firstSongPos;
                    else
                        currPos++;
                    Log.d("Rows", "move to next in REPEAT_GROUP with suitable " +
                            "rating not found => return next song regardless of rating");
                    break;
                }
                rowSong = (RowSong) rowsUnfolded.get(currPos);
            } while (rowSong.isRatingInsufficient());
        }
        else {
            int lastCurrPos = currPos;

            RowSong rowSong;
            do {
                currPos++;
                if (currPos >= rowsUnfolded.size())
                    currPos = 0;
                // skip RowGroup
                while (currPos < rowsUnfolded.size() &&
                        rowsUnfolded.get(currPos).getClass() != RowSong.class)
                    currPos++;
                // next song with suitable rating not found
                if (currPos == lastCurrPos) {
                    Log.d("Rows", "move to next song with suitable rating not " +
                            "found => return next song regardless of rating");
                    moveToNextSongNoRating();
                    return;
                }
                rowSong = (RowSong) rowsUnfolded.get(currPos);
            } while (rowSong.isRatingInsufficient());

            setGroupSelectedState(lastCurrPos, false);
            setGroupSelectedState(currPos, true);
        }
    }

    private void moveToNextSongNoRating() {
        if (rowsUnfolded.size() <= 0)
            return;

        if (repeatMode == RepeatMode.REPEAT_GROUP) {
            int lastSongPos = getLastSongPosInGroup(currPos);
            if (currPos == lastSongPos)
                currPos = getFirstSongPosInGroup(currPos);
            else
                currPos++;
        }
        else {
            setGroupSelectedState(currPos, false);

            currPos++;
            if (currPos >= rowsUnfolded.size())
                currPos = 0;

            while (currPos < rowsUnfolded.size() &&
                    rowsUnfolded.get(currPos).getClass() != RowSong.class)
                currPos++;

            if (currPos == rowsUnfolded.size())
                currPos = -1;

            setGroupSelectedState(currPos, true);
        }
    }

    public int FoldedToUnfoldedIndex(int index) {
        Row foldedRow = rows.get(index);
        if(foldedRow == null)
            return -1;
        for (int i = 0; i < rowsUnfolded.size(); i++) {
            Row unfoldedRow = rowsUnfolded.get(i);
            if(unfoldedRow == foldedRow)
                return i;
        }
        return -1; // This should never occur
    }

    // Get the next song position, where a keyword is contained in the song metadata.
    // Returns -1 when not found
    public Row getNextSongByKeyword(String keyword) {
        if (rowsUnfolded.isEmpty())
            return null;

        int currPos = FoldedToUnfoldedIndex(getCurrPos());

        // Iterate from (currently selected song + 1) -> end of playlist
        for(int i = currPos+1; i < rowsUnfolded.size(); i++) {
            Row row = rowsUnfolded.get(i);

            if(row.toString().toLowerCase().contains(keyword.toLowerCase())) {
                return row;
            }
        }
        // Iterate from beginning of playlist -> current song
        for(int i = 0; i < currPos; i++) {
            Row row = rowsUnfolded.get(i);

            if(row.toString().toLowerCase().contains(keyword.toLowerCase())) {
                return row;
            }
        }

        return null;
    }

    public int getFoldedIndex(Row row) {
        if(row == null)
            return -1;

        for (int i = 0; i < rows.size(); i++) {
            if(rows.get(i) == row)
                return i;
        }

        return -1;
    }

    public void moveToPrevSong() {
        if (rowsUnfolded.size() <= 0)
            return;

        if (repeatMode == RepeatMode.REPEAT_GROUP) {
            int firstSongPos = getFirstSongPosInGroup(currPos);
            if (currPos == firstSongPos)
                currPos = getLastSongPosInGroup(currPos);
            else
                currPos--;
        }
        else {
            setGroupSelectedState(currPos, false);

            currPos--;
            if (currPos < 0)
                currPos = rowsUnfolded.size() - 1;

            while (currPos >= 0 && rowsUnfolded.get(currPos).getClass() != RowSong.class) {
                currPos--;
                if (currPos < 0)
                    currPos = rowsUnfolded.size() - 1;
            }

            setGroupSelectedState(currPos, true);
        }
    }

    public void moveToPrevGroup() {
        if (rowsUnfolded.size() <= 0)
            return;

        setGroupSelectedState(currPos, false);

        currPos = getFirstSongPosInGroup(currPos);
        currPos--;

        if (currPos < 0)
            currPos = rowsUnfolded.size() - 1;

        while (currPos >= 0 && rowsUnfolded.get(currPos).getClass() != RowSong.class) {
            currPos--;
            if (currPos < 0)
                currPos = rowsUnfolded.size() - 1;
        }

        if (currPos < 0)
            currPos = rowsUnfolded.size() - 1;

        currPos = getFirstSongPosInGroup(currPos);

        setGroupSelectedState(currPos, true);
    }

    public void moveToNextGroup() {
        if (rowsUnfolded.size() <= 0)
            return;

        setGroupSelectedState(currPos, false);

        currPos = getLastSongPosInGroup(currPos);
        currPos++;

        // if last song go to beginning
        if (currPos == rowsUnfolded.size()) {
            currPos = 0;
        }

        // skip RowGroups
        while (currPos < rowsUnfolded.size() &&
                rowsUnfolded.get(currPos).getClass() != RowSong.class)
            currPos++;

        if (currPos == rowsUnfolded.size()) {
            currPos = -1;
        }

        setGroupSelectedState(currPos, true);
    }

    // fold everything
    public void fold() {
        if (rowsUnfolded.size() <= 0)
            return;

        // todo: better to recopy first level from unfolded?
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (row.getClass() == RowGroup.class)
                fold((RowGroup) rows.get(i), i);
        }
    }

    // unfold everything
    public void unfold() {
        if (rowsUnfolded.size() <= 0)
            return;

        rows = (ArrayList<Row>) rowsUnfolded.clone();
        for(Row row : rows)
            if (row.getClass() == RowGroup.class)
                ((RowGroup) row).setFolded(false);
    }

    public void invertFold(int pos) {
        if (rowsUnfolded.size() <= 0)
            return;

        if (pos < 0 || pos >= rows.size()) {
            return;
        }
        if(rows.get(pos).getClass() != RowGroup.class) {
            Log.w("Rows", "invertFold called on class that is not SongGroup!");
            return;
        }
        RowGroup group = (RowGroup) rows.get(pos);

        if(group.isFolded()) {
            unfold(group, pos);
        }
        else {
            fold(group, pos);
        }
    }

    // group and pos must correspond in the foldable rows
    private void fold(RowGroup group, int pos) {
        pos++;
        // remove every following rows that has a higher level
        while(pos < rows.size() && rows.get(pos).getLevel() > group.getLevel()) {
            //Log.d("Rows", "Item removed pos: " + pos + " row: " + songItems.get(pos));
            rows.remove(pos);
        }
        group.setFolded(true);
    }


    // @desc unfold only the group(s) that contains pos.
    //
    // @return true if at least one group has been unfold
    public boolean unfoldCurrPos() {
        if (rowsUnfolded.size() <= 0)
            return false;

        boolean changed = false;
        int pos = getCurrPos();
        if (pos < 0 || pos >= rows.size())
            return changed;

        Row row = rows.get(pos);
        if (row != null && row.getClass() == RowGroup.class) {
            RowGroup group = (RowGroup) row;
            if (group.isFolded()) {
                unfold(group, pos);
                unfoldCurrPos();
                changed = true;
            }
        }
        return changed;
    }

    private boolean hasOneSubGroup(RowGroup group, int pos) {
        if (group.getLevel() != 0)
            return true;

        int nbSubGroup = 0;
        Row row;
        for (int i = 1;
             group.getGenuinePos() + i < rowsUnfolded.size() &&
                     (row = rowsUnfolded.get(group.getGenuinePos() + i)).getLevel() > group.getLevel();
             i++) {
            if (row.getClass() == RowGroup.class) {
                nbSubGroup++;
                if (nbSubGroup > 1)
                    return false;
            }
        }

        return true;
    }

    // @desc unfold a group following settings
    //
    // group and pos must correspond in the foldable rows
    // group must be folded
    private void unfold(RowGroup group, int pos) {
        if (filter == Filter.TREE) {
            unfoldTree(group, pos);
            return;
        }

        // add every missing rows
        Row row;
        final int autoUnfoldThreshold = params.getUnfoldSubGroupThreshold();
        if (params.getUnfoldSubGroup() ||
                group.getLevel() != 0 ||
                group.getSongCount() < autoUnfoldThreshold ||
                hasOneSubGroup(group, pos)) {
            // unfold everything
            for (int i = 1;
                 group.getGenuinePos() + i < rowsUnfolded.size() &&
                         (row = rowsUnfolded.get(group.getGenuinePos() + i)).getLevel() > group.getLevel();
                 i++) {
                // unfold if previously folded
                if (row.getClass() == RowGroup.class)
                    ((RowGroup) row).setFolded(false);

                rows.add(pos + i, row);
                //Log.d("Rows", "Item added pos: " + pos + i + " row: " + songItem);
            }
        }
        else {
            // unfold only first subgroup
            for (int i = 1, j = 1;
                 group.getGenuinePos() + i < rowsUnfolded.size() &&
                         (row = rowsUnfolded.get(group.getGenuinePos() + i)).getLevel() > group.getLevel();
                 i++) {
                if (row.getClass() == RowGroup.class) {
                    ((RowGroup) row).setFolded(true);
                    rows.add(pos + j++, row);
                }
            }
        }
        group.setFolded(false);
    }

    private void unfoldTree(RowGroup group, int pos) {
        Row row;
        int nbRowGroupUnfold = 0;
        int nbRowSongUnfold = 0;
        // unfold only next level
        for (int i = 1, j = 1;
             group.getGenuinePos() + i < rowsUnfolded.size() &&
                     (row = rowsUnfolded.get(group.getGenuinePos() + i)).getLevel() > group.getLevel();
             i++) {
            if (row.getLevel() == group.getLevel() + 1) {
                if (row.getClass() == RowGroup.class) {
                    ((RowGroup) row).setFolded(true);
                    nbRowGroupUnfold++;
                }
                else {
                    nbRowSongUnfold++;
                }
                rows.add(pos + j++, row);
            }
        }
        group.setFolded(false);

        // unfold subgroup if group contains only one subgroup
        if (nbRowGroupUnfold == 1 && nbRowSongUnfold == 0 ) {
            if (group.getGenuinePos() + 1 < rowsUnfolded.size()) {
                RowGroup subGroupSingle = (RowGroup) rowsUnfolded.get(group.getGenuinePos() + 1);
                if (subGroupSingle != null && subGroupSingle.getLevel() == group.getLevel() + 1)
                    unfoldTree(subGroupSingle,pos + 1);
            }
        }
    }


    public boolean isLastRow(int pos) {
        return pos == rows.size() - 1;
    }


    public void init() {
        rowsUnfolded.clear();
        rows.clear();

        long startTime = System.currentTimeMillis();
        Uri musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Cursor musicCursor;
        String[] projection = new String[] {
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
        };
        String where = MediaStore.Audio.Media.IS_MUSIC + "=1";

        String sortOrder = null;
        switch(filter) {
            case ARTIST:
                sortOrder = MediaStore.Audio.Media.ARTIST +
                        ", " + MediaStore.Audio.Media.ALBUM +
                        ", " + MediaStore.Audio.Media.TRACK +
                        ", " + MediaStore.Audio.Media.TITLE;
                break;
            case TREE:
            case FOLDER:
                // presort it even if it will be restorted by tree and folder, in order to have a
                // title sort if there is no ID3 track
                sortOrder = MediaStore.Audio.Media.ARTIST +
                        ", " + MediaStore.Audio.Media.ALBUM +
                        ", " + MediaStore.Audio.Media.TRACK +
                        ", " + MediaStore.Audio.Media.TITLE;
                // did not find a way to sort by folder through query
                break;
            default:
                return;
        }
        try {
            musicCursor = musicResolver.query(musicUri, projection, where, null, sortOrder);
        } catch (Exception e) {
            final String msg = "No songItems found!";
            //Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
            Log.e("MusicService", msg);
            return;
        }

        switch(filter) {
            case ARTIST:
                initByArtist(musicCursor);
                break;
            case FOLDER:
                initByPath(musicCursor);
                break;
            case TREE:
                initByTree(musicCursor);
                break;
            default:
                return;
        }

        if(musicCursor != null)
            musicCursor.close();

        // if no songPos saved : search the first song
        if(currPos == -1) {
            int idx;
            for(idx = 0; idx < rowsUnfolded.size(); idx++) {
                if (rowsUnfolded.get(idx).getClass() == RowSong.class) {
                    setCurrPos(idx);
                    break;
                }
            }
        }

        switch (params.getDefaultFold()) {
            case 0:
                // fold
                initRowsFolded();
                break;
            default:
                // unfolded
                // shallow copy
                rows = (ArrayList<Row>) rowsUnfolded.clone();
        }

        // to comment in release mode:
        /*
        int idx;
        for(idx = 0; idx < rowsUnfolded.size(); idx++)
            Log.d("Rows", "songItem " + idx + " added: " + rowsUnfolded.get(idx).toString());
        */
        Log.d("Rows", "======> songItems initialized in " + (System.currentTimeMillis() - startTime) + "ms");
        Log.d("Rows", "songPos: " + currPos);

        //preloadDBSongsAsync();
        if (params.getEnableRating())
            preloadSongRatingAsync();
    }

    private void preloadDBSongsAsync() {
        Thread thread = new Thread() {
            @Override
            public void run() {
                preloadDBSongs();
            }
        };
        thread.start();
    }

    // must not be called from main thread !
    private void preloadDBSongs() {
        Date beg = new Date();
        int nbLoaded = 0;
        // preload in db every songs
        for (Row row : rowsUnfolded) {
            if (row.getClass() == RowSong.class) {
                RowSong rowSong = (RowSong) row;
                SongORM songORM = database.getSongDAO().findByPath(rowSong.getPath());
                nbLoaded++;
                if (songORM == null) {
                    Log.d("Rows", "New songORM for path=" + rowSong.getPath());
                    try {
                        database.getSongDAO().insert(new SongORM(rowSong.getPath(), rowSong.loadRating(), true));
                    } catch (Exception e) {
                        Log.w("Rows", "Unable to add songORM for path=" + rowSong.getPath()
                                + " e=" + e);
                    }
                }
                else {
                    long lastModified = (new File(songORM.path)).lastModified();
                    if (lastModified > songORM.lastModifiedMs) {
                        Log.d("Rows", "Found songORM for path=" + songORM.path +
                                ", update it");
                        songORM.rating = rowSong.loadRating();
                        songORM.lastModifiedMs = lastModified;
                        try {
                            database.getSongDAO().update(songORM);
                        } catch (Exception e) {
                            Log.w("Rows", "Unable to update songORM for path=" + rowSong.getPath()
                                    + " e=" + e);
                        }
                    }
//                    else {
//                        Log.d("Rows", "Found songORM for path="   songORM.path);
//                    }
                }
            }
        }
        Date end = new Date();
        Log.d("Rows", "preloadDBSongs: " + nbLoaded + " songORM loaded in " +
                (end.getTime() - beg.getTime()) + "ms");
    }

    private void preloadSongRatingAsync() {
        Thread thread = new Thread() {
            @Override
            public void run() {
                preloadSongsRatings();
            }
        };
        thread.start();
    }

    // must not be called from main thread !
    private void preloadSongsRatings() {
        Log.d("Rows", "preloadSongsRatings start");
        Date beg = new Date();
        int nbLoaded = 0;
        // preload from the currpos so that next songs are loaded earlier
        int startPos = currPos < 0 ? 0 : currPos;
        for (int i = startPos; i < rowsUnfolded.size(); i++) {
            Row row = rowsUnfolded.get(i);
            if (row.getClass() == RowSong.class) {
                ((RowSong) row).loadRating();
                nbLoaded++;
            }
        }
        for (int i = 0; i < startPos && i < rowsUnfolded.size(); i++) {
            Row row = rowsUnfolded.get(i);
            if (row.getClass() == RowSong.class) {
                ((RowSong) row).loadRating();
                nbLoaded++;
            }
        }
        Date end = new Date();
        Log.d("Rows", "preloadSongsRatings: " + nbLoaded + " songs loaded in " +
                (end.getTime() - beg.getTime()) + "ms");
    }

    private void initRowsFolded() {
        for(Row row : rowsUnfolded) {
            if(row.getClass() == RowGroup.class && row.getLevel() == 0) {
                rows.add(row);
                ((RowGroup) row).setFolded(true);
            }
        }
    }

    private void initByArtist(Cursor musicCursor) {
        RowGroup.rowType = Filter.ARTIST;
        if (musicCursor != null && musicCursor.moveToFirst()) {
            int titleCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
            int idCol = musicCursor.getColumnIndex(MediaStore.Audio.Media._ID);
            int artistCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
            int albumCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM);
            int durationCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
            int pathCol = musicCursor.getColumnIndex(MediaStore.MediaColumns.DATA);
            int trackCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.TRACK);
            int albumIdCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);
            int yearCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.YEAR);
            int mimeCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE);

            RowGroup prevArtistGroup = null;
            RowGroup prevAlbumGroup = null;
            do {
                long id = musicCursor.getLong(idCol);
                String title = getDefaultStrIfNull(musicCursor.getString(titleCol));
                String artist = getDefaultStrIfNull(musicCursor.getString(artistCol));
                String album = getDefaultStrIfNull(musicCursor.getString(albumCol));
                long durationMs = musicCursor.getLong(durationCol);
                int track = musicCursor.getInt(trackCol);
                long albumId = musicCursor.getLong(albumIdCol);
                int year = musicCursor.getInt(yearCol);
                String mime = musicCursor.getString(mimeCol);
                String path = getDefaultStrIfNull(musicCursor.getString(pathCol));

                if (prevArtistGroup == null || artist.compareToIgnoreCase(prevArtistGroup.getName()) != 0) {
                    RowGroup artistGroup = new RowGroup(rowsUnfolded.size(), 0, artist,
                            path, Typeface.BOLD, false, params);
                    rowsUnfolded.add(artistGroup);
                    prevArtistGroup = artistGroup;
                    prevAlbumGroup = null;
                }

                if (prevAlbumGroup == null || album.compareToIgnoreCase(prevAlbumGroup.getName()) != 0) {
                    RowGroup albumGroup = new RowGroup(rowsUnfolded.size(), 1, album,
                            path, Typeface.ITALIC, true, params);
                    albumGroup.setParent(prevArtistGroup);
                    rowsUnfolded.add(albumGroup);
                    prevAlbumGroup = albumGroup;
                }

                RowSong rowSong = new RowSong(database.getSongDAO(), rowsUnfolded.size(), 2, id, title, artist, album,
                        durationMs, track, path, albumId, year, mime, params);
                rowSong.setParent(prevAlbumGroup);

                if(id == savedID)
                    currPos = rowsUnfolded.size();

                rowsUnfolded.add(rowSong);
                prevArtistGroup.increaseSongCount();
                prevArtistGroup.incTotalDuration(rowSong.getDurationMs());
                prevAlbumGroup.increaseSongCount();
                prevAlbumGroup.incTotalDuration(rowSong.getDurationMs());
            }
            while (musicCursor.moveToNext());
            setGroupSelectedState(currPos, true);
        }
    }


    private void initByPath(Cursor musicCursor) {
        RowGroup.rowType = Filter.FOLDER;
        if (musicCursor != null && musicCursor.moveToFirst()) {
            int titleCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
            int idCol = musicCursor.getColumnIndex(MediaStore.Audio.Media._ID);
            int artistCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
            int albumCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM);
            int durationCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
            int pathCol = musicCursor.getColumnIndex(MediaStore.MediaColumns.DATA);
            int trackCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.TRACK);
            int albumIdCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);
            int yearCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.YEAR);
            int mimeCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE);

            do {
                long id = musicCursor.getLong(idCol);
                String title = getDefaultStrIfNull(musicCursor.getString(titleCol));
                String artist = getDefaultStrIfNull(musicCursor.getString(artistCol));
                String album = getDefaultStrIfNull(musicCursor.getString(albumCol));
                long durationMs = musicCursor.getLong(durationCol);
                int track = musicCursor.getInt(trackCol);
                String path = getDefaultStrIfNull(musicCursor.getString(pathCol));
                long albumId = musicCursor.getLong(albumIdCol);
                int year = musicCursor.getInt(yearCol);
                String mime = musicCursor.getString(mimeCol);

                RowSong rowSong = new RowSong(database.getSongDAO(), -1, 2, id, title, artist, album,
                        durationMs, track, path, albumId, year, mime, params);
                rowsUnfolded.add(rowSong);
                //Log.d("Rows", "song added: " + rowSong.toString());
            }
            while (musicCursor.moveToNext());
        }

        Collections.sort(rowsUnfolded, new PathRowComparator(params.getShowFilename()));

        // add group
        RowGroup prevFolderGroup = null;
        RowGroup prevArtistGroup = null;

        for (int idx = 0; idx < rowsUnfolded.size(); idx++) {
            RowSong rowSong = (RowSong) rowsUnfolded.get(idx);

            String curFolder = rowSong.getFolder();
            if (prevFolderGroup == null || curFolder.compareToIgnoreCase(prevFolderGroup.getName()) != 0) {
                RowGroup folderGroup = new RowGroup(idx, 0, curFolder,
                        rowSong.getPath(), Typeface.BOLD, false, params);
                rowsUnfolded.add(idx, folderGroup);
                idx++;
                prevFolderGroup = folderGroup;
                prevArtistGroup = null;
            }

            String curArtist = rowSong.getArtist();
            if (prevArtistGroup == null || curArtist.compareToIgnoreCase(prevArtistGroup.getName()) != 0) {
                RowGroup artistGroup = new RowGroup(idx, 1, curArtist,
                        rowSong.getPath(), Typeface.BOLD, true, params);
                artistGroup.setParent(prevFolderGroup);
                rowsUnfolded.add(idx, artistGroup);
                idx++;
                prevArtistGroup = artistGroup;
            }

            if (rowSong.getID() == savedID)
                currPos = idx;

            rowSong.setGenuinePos(idx);
            rowSong.setParent(prevArtistGroup);

            prevFolderGroup.increaseSongCount();
            prevFolderGroup.incTotalDuration(rowSong.getDurationMs());
            prevArtistGroup.increaseSongCount();
            prevArtistGroup.incTotalDuration(rowSong.getDurationMs());
        }
        setGroupSelectedState(currPos, true);
    }

    private void initByTree(Cursor musicCursor) {
        RowGroup.rowType = Filter.TREE;
        if (musicCursor != null && musicCursor.moveToFirst()) {
            int titleCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
            int idCol = musicCursor.getColumnIndex(MediaStore.Audio.Media._ID);
            int artistCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
            int albumCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM);
            int durationCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
            int pathCol = musicCursor.getColumnIndex(MediaStore.MediaColumns.DATA);
            int trackCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.TRACK);
            int albumIdCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);
            int yearCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.YEAR);
            int mimeCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE);

            do {
                long id = musicCursor.getLong(idCol);
                String title = getDefaultStrIfNull(musicCursor.getString(titleCol));
                String artist = getDefaultStrIfNull(musicCursor.getString(artistCol));
                String album = getDefaultStrIfNull(musicCursor.getString(albumCol));
                long durationMs = musicCursor.getLong(durationCol);
                String path = getDefaultStrIfNull(musicCursor.getString(pathCol));
                int track = getTrackNumber(musicCursor.getString(trackCol), path);
                long albumId = musicCursor.getLong(albumIdCol);
                int year = musicCursor.getInt(yearCol);
                String mime = musicCursor.getString(mimeCol);

                final int pos = -1, level = 2;
                RowSong rowSong = new RowSong(database.getSongDAO(), pos, level, id, title, artist, album, durationMs,
                        track, path, albumId, year, mime, params);
                rowsUnfolded.add(rowSong);
                //Log.d("Rows", "song added: " + rowSong.toString());
            }
            while (musicCursor.moveToNext());
        }

//        long beforeMs = (new Date()).getTime();
        TreeRowComparator treeRowComparator = new TreeRowComparator(params.getShowFilename());
        Collections.sort(rowsUnfolded, treeRowComparator);
//        Log.w("Rows==========", "Sort time: " + ((new Date()).getTime() - beforeMs) + " ms");
//        // 127 ms tree no show filename
//        // 283 ms tree show filename

        // add groups
        ArrayList<RowGroup> prevGroups = new ArrayList<>();
        for (int idx = 0; idx < rowsUnfolded.size(); idx++) {
            RowSong rowSong = (RowSong) rowsUnfolded.get(idx);
            // get folder list of current row
            ArrayList<String> folders = Path.tokenizeFolder(rowSong.getFolder());

            //// get the nearest common group parent
            // search from the bottom the last previous group that is the same with the current group
            // /toto/tata/youp, /to/tata/gruick -> firstDiff = 0
            // /toto/tata/youp, /toto/titi/gruick -> firstDiff = 1
            // /toto/tata/youp, /toto/tata/gruick -> firstDiff = 2
            int commonLevel = 0;
            while (commonLevel < prevGroups.size() &&
                    commonLevel < folders.size() &&
                    prevGroups.get(commonLevel).getName().equalsIgnoreCase(folders.get(commonLevel)))
                commonLevel++;
            // get corresponding RowGroup
            RowGroup commonGroup;
            if (commonLevel == 0)
                // everything is different: no parent
                commonGroup = null;
            else
                commonGroup = prevGroups.get(commonLevel - 1);

            //// add every groups that are missing
            RowGroup parentGroup = commonGroup;
            for (int level = commonLevel; level < folders.size(); level++) {
                // get the absolute path path of the current missing group
                int nbFolderBelow = folders.size() - level;
                String path = rowSong.getPath();
                while (nbFolderBelow-- > 0) {
                    path = (new File(path)).getParent();
                    if (path == null)
                        path = "";
                }

                RowGroup aGroup = new RowGroup(idx, level, folders.get(level),
                        path, Typeface.BOLD, false, params);
                aGroup.setParent(parentGroup);
                parentGroup = aGroup;
                rowsUnfolded.add(idx, aGroup);
                idx++;
            }

            //// recompute group list for next row
            prevGroups.clear();
            RowGroup groupIdx = parentGroup;
            while (groupIdx != null) {
                // update group
                groupIdx.increaseSongCount();
                groupIdx.incTotalDuration(rowSong.getDurationMs());

                prevGroups.add(0, groupIdx);
                groupIdx = (RowGroup) groupIdx.getParent();
            }

            //// update RowSong
            rowSong.setLevel(folders.size());
            rowSong.setGenuinePos(idx);
            rowSong.setParent(parentGroup);
            if (rowSong.getID() == savedID)
                currPos = idx;
        }

        setGroupSelectedState(currPos, true);
    }


    private String getDefaultStrIfNull(String str) { return str != null ? str : defaultStr; }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }
    private int getTrackNumber(String strTrack, String path) {
        int track = 0;
        try {
            track = Integer.parseInt(strTrack);
        }
        catch (NumberFormatException e) {}

        // get track number from path
//        if (track == 0 && path != null) {
//            String filename = Path.getFilename(path);
//            String filenameBegin = "";
//            if (filename.length() > 0 && isDigit(filename.charAt(0))) {
//                filenameBegin += filename.charAt(0);
//                if (filename.length() > 1 && isDigit(filename.charAt(1)))
//                    filenameBegin += filename.charAt(1);
//            }
//            if (!filenameBegin.isEmpty()) {
//                try {
//                    track = Integer.parseInt(filenameBegin);
//                } catch (Exception e) {
//                }
//            }
//        }

        return track;
    }

    private void restore() {
        savedID = params.getSongID();
        filter = params.getFilter();
        repeatMode = params.getRepeatMode();
        Path.rootFolders = params.getRootFolders();
    }

    public void save() {
        updateSavedId();
        params.setSongID(savedID);
        params.setFilter(filter);
        params.setRepeatMode(repeatMode);
    }

    
    public Filter getFilter() {
        return filter;
    }

    public void setFilter(Filter filter) {
        if (this.filter != filter) {
            this.filter = filter;
            // todo: handle the current playing song finish during reinitSongs()...
            reinit();
            params.setFilter(filter);
        }
    }

    public void reinit() {
        updateSavedId();
        init();
    }

    public RepeatMode getRepeatMode() {
        return repeatMode;
    }

    public void setRepeatMode(RepeatMode repeatMode) {
        this.repeatMode = repeatMode;
        params.setRepeatMode(repeatMode);
    }

    public boolean setRootFolders(String rootFolders) {
        boolean reinited = false;

        if (!Path.rootFolders.equals(rootFolders)) {
            Path.rootFolders = rootFolders;
            if (filter == Filter.FOLDER || filter == Filter.TREE) {
                // reinit everything is a bit heavy: nevermind, rootFolders will not be changed often
                updateSavedId();
                init();
                reinited = true;
            }
        }

        return  reinited;
    }

    private void updateSavedId() {
        RowSong rowSong = getCurrSong();
        if(rowSong != null)
            savedID = rowSong.getID();
    }

    public interface RatingCallbackInterface {
        // @param someRatingChanged set to true if loadRatings brings new RowSong's rating
        // i.e. set to false if RowSong's rating did not change
        void ratingCallback(boolean someRatingChanged) ;
    }

    // fetch song's rating that are currently visible to the user
    public synchronized void loadRatingsAsync(RatingCallbackInterface ratingCallbackInterface) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                Log.d("Rows", "loadRatings");
                boolean someRatingChanged = false;
                for (int i = 0; i < rows.size(); i++) {
                    Row row = rows.get(i);
                    if (row.getClass() == RowSong.class) {
                        RowSong rowSong = (RowSong) row;
                        if (rowSong.getRating() == RowSong.RATING_NOT_INITIALIZED &&
                                rowSong.loadRating() > 0)
                            someRatingChanged = true;
                    }
                }
                ratingCallbackInterface.ratingCallback(someRatingChanged);
            }
        };
        thread.start();
    }

    public interface RateGroupCallbackInterface {
        void ratingCallback(int nbSongsChanged, String errorMsg) ;
    }

    // rate songs from pos (if pos is folder rate every folder's songs)
    // return true if ratingsMustBeSynchronized (usually set when trying to rate the current played song)
    public void rateSongs(int pos, int rating, boolean overwriteRating,
                          RateGroupCallbackInterface callback) {
        Log.d("Rows", "rateGroup " + pos + " to " + rating +
                " overwrite=" + overwriteRating);

        if (ratingsSynchronizing.getAndSet(true)) {
            callback.ratingCallback(0,
                    context.getString(R.string.action_set_rating_song_failed));
        }
        else {
            Thread thread = new Thread() {
                @Override
                public void run() {
                    int nbChanged = 0;
                    Row row = rows.get(pos);
                    if (row.getClass() == RowSong.class) {
                        if (rateSong((RowSong) row, rating, overwriteRating))
                            nbChanged++;
                    } else {
                        RowGroup groupToRate = (RowGroup) row;
                        int i = 1;
                        while (groupToRate.getGenuinePos() + i < rowsUnfolded.size() &&
                                (row = rowsUnfolded.get(groupToRate.getGenuinePos() + i)).getLevel() >
                                        groupToRate.getLevel()) {
                            if (row.getClass() == RowSong.class) {
                                if (rateSong((RowSong) row, rating, overwriteRating))
                                    nbChanged++;
                            }
                            i++;
                        }
                    }
                    callback.ratingCallback(nbChanged, "");
                    ratingsSynchronizing.set(false);
                }
            };
            thread.start();
        }
    }

    // not must be called from main thread
    private boolean rateSong(RowSong rowSong, int rating, boolean overwriteRating) {
        boolean changed = false;
        if (rowSong.getRating() == rating)
            Log.d("Rows", "song " + rowSong.getTitle() + " rating already set to " + rating + " -> skipping");
        else if (overwriteRating || rowSong.getRating() <= 0) {
            Log.d("Rows", "set song " + rowSong.getTitle() + " rating to " + rating);
            RowSong currSong = getCurrSong();
            if (currSong == null || (rowSong.getGenuinePos() != currSong.getGenuinePos())) {
                rowSong.setRating(rating);
            }
            else {
                rowSong.scheduleSetRating(rating, false);
                ratingsMustBeSynchronized.set(true);
            }
            changed = true;
        }
        return changed;
    }

    public void rateCurrSong(int rating) {
        RowSong rowSong = getCurrSong();
        if (rowSong != null) {
            rowSong.scheduleSetRating(rating, true);
            ratingsMustBeSynchronized.set(true);
        }
    }

    public void synchronizeFailedRatings() {
        if (ratingsMustBeSynchronized.getAndSet(false)) {
            database.trySyncronizeRatingsAsync((succeed, msg) -> {
                if (!succeed)
                    ratingsMustBeSynchronized.set(true);
                Log.d("Rows", msg);
            });
        }
    }

    private void deleteSongFromList(@NonNull RowSong song) {
        for (int i = 0; i < rowsUnfolded.size(); i++) {
            Row row = rowsUnfolded.get(i);
            if (row == song) {
                rowsUnfolded.remove(i);
                break;
            }
        }
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (row == song) {
                rows.remove(i);
                break;
            }
        }
    }

    public boolean deleteSongFile(@NonNull RowSong song) {
        boolean succeed = song.deleteFile(context);
        if (succeed && song != getCurrSong())
            deleteSongFromList(song);

        return succeed;
    }
}
