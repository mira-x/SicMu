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

import android.graphics.Typeface;
import android.support.v4.media.MediaMetadataCompat;
import android.util.TypedValue;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.File;

public class RowGroup extends Row {
    protected String name;
    protected boolean folded;
    protected boolean selected;
    private boolean overrideBackgroundColor;
    private int nbRowSong;
    private String path;
    private long totalDurationMs = 0;
    public static Filter rowType;
    protected static int textSize = 18;
    private Parameters params;

    // must be set outside before calling setText
    public static int normalTextColor;
    public static int playingTextColor;
    public static int backgroundOverrideColor;

    public RowGroup(int pos, int level, String name, String path, int typeface,
                    boolean overrideBackgroundColor, Parameters params) {
        super(pos, level, typeface);
        this.name = name;
        setPath(path);
        folded = false;
        selected = false;
        this.overrideBackgroundColor = overrideBackgroundColor;
        this.params = params;
    }

    public String getName() { return name; }
    public String getPath() { return path; }

    private void setPath(String path) {
        this.path = "";
        if (path != null) {
            File f = new File(path);
            if (f.exists()) {
                if (f.isDirectory())
                    this.path = f.getAbsolutePath();
                else
                    // if path points to a file get the folder of that path
                    this.path = f.getParentFile().getAbsolutePath();
            }
        }
    }

    public boolean isFolded() { return folded; }
    public void setFolded(boolean fold) { folded = fold; }

    public void setSelected(boolean selected) { this.selected = selected; }
    public boolean isSelected() { return selected; }

    /// get number of songs (excluding RowGroup) inside this group
    public int getSongCount() { return nbRowSong; }
    public void increaseSongCount() { nbRowSong++; }

    public void incTotalDuration(long totalDurationMs) { this.totalDurationMs += totalDurationMs; }
    public long getTotalDuration() { return totalDurationMs; }

    public void setView(RowViewHolder holder, Main main, int position) {
        super.setView(holder, main, position);

        float factor = 1.5f;
        if (main.getMusicSrv().getRows().isLastRow(position))
            factor = 3f;
        holder.layout.getLayoutParams().height = convertDpToPixels((int) (textSize * factor),
                holder.layout.getResources());

        setText(holder.text);
        setDuration(holder.duration);
        holder.image.setImageDrawable(null);

        holder.ratingStar.setVisibility(View.INVISIBLE);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) holder.duration.getLayoutParams();
        params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        holder.duration.setLayoutParams(params);

        if (overrideBackgroundColor && backgroundOverrideColor != 0) {
            setBackgroundColor(holder, backgroundOverrideColor);
        }
        else {
            setBackgroundColor(holder, backgroundColor);
        }
    }

    private void setText(TextView text) {
        String prefix = "";
        if (rowType == Filter.TREE) {
            if (isFolded())
                prefix = "| ";
            else
                prefix = "\\ ";

        }
        text.setText(prefix + name);

        if (isFolded() && isSelected())
            text.setTextColor(playingTextColor);
        else
            text.setTextColor(normalTextColor);

        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize);
        //text.setPaintFlags(text.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        //text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
    }

    static public String msToTime(long durationMs){
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        long hours = seconds / 3600;
        if (seconds < 60) {
            return (seconds < 10 ? "0:0" : "0:")  + seconds;
        }
        else if (minutes < 60) {
            seconds %= 60;
            return minutes + (seconds < 10 ? ":0" : ":") + seconds;
        }
        else {
            seconds %= 60;
            minutes %= 60;
            return hours + (minutes < 10 ? ":0" : ":") + minutes +
                    (seconds < 10 ? ":0" : ":") + seconds;
        }
//        if (seconds < 60) {
//            return seconds + "s";
//        }
//        else if (seconds < 600) {
//            seconds = seconds % 60;
//            return minutes + (seconds < 10 ? "m0" : "m") + seconds;
//        }
//        else if (seconds < 3600) {
//            return minutes + "m";
//        }
//        else if (minutes < 60 * 5) {
//            minutes = minutes % 60;
//            return hours + (minutes < 10 ? "h0" : "h") + minutes;
//        }
//        else {
//            return hours + "h";
//        }
    }

    private void setDuration(TextView duration) {
        String rightSpace = getStringOffset();
        //super.setText(text);
        if (isFolded()) {
            if (isSelected())
                duration.setTextColor(playingTextColor);
            else
                duration.setTextColor(normalTextColor);
            if (params.getShowGroupTotalTime())
                duration.setText(msToTime(totalDurationMs) + " |" + rightSpace);
            else
                duration.setText(nbRowSong + " |" + rightSpace);
        }
        else {
            duration.setText("/" + rightSpace);
            duration.setTextColor(normalTextColor);
        }

        duration.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize);
        duration.setTypeface(null, typeface == Typeface.ITALIC ? Typeface.NORMAL : typeface);
        /*
        duration.setBackgroundColor(Color.argb(0x88, 0x30, 0x30, 0x30));
        duration.setId(position);
        duration.setOnClickListener(new View.OnClickListener() {
            public void onClick(View durationView) {
                Log.d("Main", "durationView.getId(): " + durationView.getId());
                durationView.setBackgroundColor(Color.argb(0x88, 0x65, 0x65, 0x65));

                class InvertFold implements Runnable {
                    View view;
                    InvertFold(View view) { this.view = view; }
                    public void run() {
                        main.invertFold(view.getId());
                        // todo: reset highlight color for a few ms after invertFold?
                    }
                }
                durationView.postDelayed(new InvertFold(durationView), 200);
            }
        });
        */
    }


    public String toString() {
        return "Group pos: " + genuinePos + " level: " + level + " name: " + name;
    }

    public MediaMetadataCompat getMediaMetadata() {
        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
        builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, name);
        return builder.build();
    }
}
