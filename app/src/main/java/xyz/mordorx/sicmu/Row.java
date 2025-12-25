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

import android.content.Context;
import android.content.res.Resources;
import android.util.TypedValue;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import kotlin.NotImplementedError;

public class Row {
    // level from the left
    protected int level;
    // position of the row within the unfolded rows array
    protected int genuinePos;
    protected int typeface;
    // null if no parent
    protected Row parent;
    protected String name;
    protected String path;

    // must be set outside before calling setText
    public static int backgroundColor;
    public static int levelOffset;

    public Row(int position, int theLevel, int theTypeface) {
        genuinePos = position;
        level = theLevel;
        typeface = theTypeface;
        parent = null;
    }

    public void setGenuinePos(int position) { genuinePos = position; }
    public int getGenuinePos() { return genuinePos; }
    public Row getParent() { return parent; }
    public void setParent(Row parent) { this.parent = parent; }
    public int getLevel() {
        return level;
    }
    public void setLevel(int level) {
        this.level = level;
    }
    public String getPath() { return path; }
    protected void setPath(String path) { this.path = path; }
    public String getName() { return name; }
    protected void setName(String name) { this.name = name; }

    public void setView(RowViewHolder holder, Main main, int position) {
        holder.text.setTypeface(null, typeface);
        holder.text.setPadding(convertDpToPixels(level * levelOffset, holder.layout.getResources()), 0, 0, 0);
    }

    public void setBackgroundColor(RowViewHolder holder, int backgroundColor) {
        holder.layout.setBackgroundColor(backgroundColor);
        holder.text.setBackgroundColor(backgroundColor);
        holder.image.setBackgroundColor(backgroundColor);
        holder.duration.setBackgroundColor(backgroundColor);
        holder.ratingStar.setBackgroundColor(backgroundColor);
    }

    protected String getStringOffset() {
        String offset = "", s = " ";
        for(int i = level ; i > 0 ; i--) {
            offset += s;
        }
        return offset;
    }

    // cache result
    private static Map<Integer, Integer> converted = new HashMap<>();
    public static int convertDpToPixels(int dp, Resources resources) {
        int px;
        if (converted.containsKey(dp)) {
            px = converted.get(dp);
        }
        else {
            px = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                    resources.getDisplayMetrics());
            converted.put(dp, px);
        }
        return px;
    }

    public boolean rename(Context ctx, File newPath) {
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (!o.getClass().equals(getClass())) return false;
        if (o instanceof RowGroup) {
            var thisGroup = (RowGroup)this;
            var otherGroup = (RowGroup)o;
            return otherGroup.getPath().equals(thisGroup.getPath());
        } else if (o instanceof RowSong) {
            var thisSong = (RowSong)this;
            var otherSong = (RowSong)o;
            return otherSong.getPath().equals(thisSong.getPath()) && otherSong.getID() == thisSong.getID();
        } else {
            return o.equals(this);
        }
    }
}
