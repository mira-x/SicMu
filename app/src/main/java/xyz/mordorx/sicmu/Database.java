/*
 * SicMu Player - Lightweight music player for Android
 * Copyright (C) 2022  Mathieu Souchaud
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
import android.util.Log;

import java.io.File;
import java.util.Date;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.room.Room;

import static xyz.mordorx.sicmu.SongDatabase.MIGRATION_1_2;
import static xyz.mordorx.sicmu.SongDatabase.MIGRATION_2_3;

public class Database {
    private SongDAO songDAO;
    private Context context;

    public Database(Context context) {
        this.context = context;
        SongDatabase db = Room.databaseBuilder(context,
                SongDatabase.class, "database-SicMuNeo")
                .addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3)
                //.allowMainThreadQueries()
                .build();
        songDAO = db.getSongDAO();
    }

    public SongDAO getSongDAO() {
        return songDAO;
    }

    public interface SyncronizeRatingsCallbackInterface {
        // if at least one synchronize fail -> succeed = false
        void ratingCallback(boolean succeed, String msg);
    }
    public void trySyncronizeRatingsAsync(@NonNull SyncronizeRatingsCallbackInterface callback) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                boolean succeed = true;
                String retMsg = "";
                List<SongORM> songORMs = songDAO.getAll();
                int nbSyncSucceed = 0;
                int nbSyncTried = 0;
                for (SongORM songORM : songORMs) {
                    if (!songORM.ratingSynchronized && (new File(songORM.path).exists())) {
                        Log.d("Database", "Trying synchronize rating of path=" + songORM.path);
                        String msg = "trySyncronizeRating: synchronize rating of " +
                                songORM.path + " to " + songORM.rating;
                        if (RowSong.WriteRatingToFile(songORM.path, songORM.rating)) {
                            msg += " succeed\n\n";
                            songORM.lastModifiedMs = (new File(songORM.path)).lastModified();
                            songORM.ratingSynchronized = true;
                            songDAO.update(songORM);
                            nbSyncSucceed++;
                        }
                        else {
                            succeed = false;
                            msg += " failed !\n\n";
//                            Toast.makeText(context,"msg", Toast.LENGTH_LONG).show();
                        }
                        retMsg += msg;
                        Log.d("Database", msg);
                        nbSyncTried++;
                    }
                }
                String msg = "Synchronized rating " + nbSyncSucceed + "/" + nbSyncTried + " succeed";
//                Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
                Log.d("Database", msg);
                retMsg += msg;
                if (nbSyncTried == 0)
                    retMsg = "";
                callback.ratingCallback(succeed, retMsg);
            }
        };
        thread.start();
    }

    public interface RatingsToSyncronizeCallbackInterface {
        void ratingCallback(String msg);
    }
    public void getRatingsToSynchronizeAsync(@NonNull RatingsToSyncronizeCallbackInterface callback) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                String msg = "";
                List<SongORM> songORMs = songDAO.getAll();
                for (SongORM songORM : songORMs) {
                    if (!songORM.ratingSynchronized && (new File(songORM.path).exists())) {
                        msg += songORM.rating + " -> " + songORM.path + "\n";
                    }
                }
                callback.ratingCallback(msg);
            }
        };
        thread.start();
    }
}
