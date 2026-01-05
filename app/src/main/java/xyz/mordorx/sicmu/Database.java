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

public class Database {
    private SongDAO songDAO;
    private ConfigurationDAO configurationDAO;
    private Context context;

    public Database(Context context) {
        this.context = context;
        SongDatabase db = Room.databaseBuilder(context,
                SongDatabase.class, "database-SicMuNeo")
                .addMigrations(MIGRATION_1_2)
                //.allowMainThreadQueries()
                .build();
        songDAO = db.getSongDAO();
        configurationDAO = db.getConfigurationDAO();
    }

    public SongDAO getSongDAO() {
        return songDAO;
    }

    public synchronized ConfigurationORM getConfigurationORM() {
        ConfigurationORM config = configurationDAO.getConfiguration();
        if (config == null) {
            config = new ConfigurationORM();
            config.lastSongsCleanupMs = (new Date()).getTime();
            config.lastShowDonateMs = config.lastSongsCleanupMs;
            config.nbTimeAppStartedSinceShowDonate = 0;
            config.lastVersionCodeStarted = 0;
            configurationDAO.insert(config);
        }
        return config;
    }

    public interface DoesChangelogsMustBeShownInterface {
        void changelogsMustBeShown(boolean mustBeShown) ;
    }
    public void doesChangelogsMustBeShownAsync(DoesChangelogsMustBeShownInterface intf) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                intf.changelogsMustBeShown(doesChangelogsMustBeShown());
            }
        };
        thread.start();
    }

    private boolean doesChangelogsMustBeShown() {
        boolean mustBeShown = false;
        ConfigurationORM config = getConfigurationORM();
        if (BuildConfig.VERSION_CODE != config.lastVersionCodeStarted) {
            if (config.lastVersionCodeStarted != 0)
                mustBeShown = true;
            config.lastVersionCodeStarted = BuildConfig.VERSION_CODE;
            configurationDAO.update(config);
        }
        return mustBeShown;
    }

    // tell whether wy should start a DB cleanup (if return true : set last cleanup date to today)
    private boolean songsDBNeedCleanup() {
        ConfigurationORM config = getConfigurationORM();
        long nowMs = (new Date()).getTime();
        final long cleanupPeriodInDay = 31;
        if ((nowMs - config.lastSongsCleanupMs) > cleanupPeriodInDay*24*3600*1000) {
            config.lastSongsCleanupMs = nowMs;
            configurationDAO.update(config);
            return true;
        }
        else {
            Log.d("Database", "Cleanup DB not useful, already done the " +
                    new Date(config.lastSongsCleanupMs));
        }
        return false;
    }

    public void cleanupSongsDB() {
        Thread thread = new Thread() {
            @Override
            public void run() {
                if (songsDBNeedCleanup()) {
                    Date beg = new Date();
                    List<SongORM> songORMs = songDAO.getAll();
                    int nbDelete = 0;
                    for (SongORM songORM : songORMs) {
                        if (!(new File(songORM.path).exists())) {
                            Log.d("Database", "Delete songORM for path=" + songORM.path);
                            songDAO.delete(songORM);
                            nbDelete++;
                        }
                    }
                    Date end = new Date();
                    Log.i("Database", "Cleanup DB: " + nbDelete + "/" + songORMs.size() +
                            " songORM deleted in " + (end.getTime() - beg.getTime()) + "ms");
                }
            }
        };
        thread.start();
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
                StringBuilder retMsg = new StringBuilder();
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
                        retMsg.append(msg);
                        Log.d("Database", msg);
                        nbSyncTried++;
                    }
                }
                String msg = "Synchronized rating " + nbSyncSucceed + "/" + nbSyncTried + " succeed";
//                Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
                Log.d("Database", msg);
                retMsg.append(msg);
                if (nbSyncTried == 0)
                    retMsg = new StringBuilder();
                callback.ratingCallback(succeed, retMsg.toString());
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
                StringBuilder msg = new StringBuilder();
                List<SongORM> songORMs = songDAO.getAll();
                for (SongORM songORM : songORMs) {
                    if (!songORM.ratingSynchronized && (new File(songORM.path).exists())) {
                        msg.append(songORM.rating).append(" -> ").append(songORM.path).append("\n");
                    }
                }
                callback.ratingCallback(msg.toString());
            }
        };
        thread.start();
    }
}
