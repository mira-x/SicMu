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

package souch.smp;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.Date;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.room.Room;

import static souch.smp.SongDatabase.MIGRATION_1_2;

public class Database {
    private SongDAO songDAO;
    private ConfigurationDAO configurationDAO;
    private Context context;

    public Database(Context context) {
        this.context = context;
        SongDatabase db = Room.databaseBuilder(context,
                SongDatabase.class, "database-SMP")
                .addMigrations(MIGRATION_1_2)
                //.allowMainThreadQueries()
                .build();
        songDAO = db.getSongDAO();
        configurationDAO = db.getConfigurationDAO();
    }

    public SongDAO getSongDAO() {
        return songDAO;
    }

    public ConfigurationORM getConfigurationORM() {
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

    public interface DoesDonateMustBeShownInterface {
        void donateMustBeShown(boolean mustBeShown) ;
    }
    public void doesDonateMustBeShownAsync(DoesDonateMustBeShownInterface intf) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                intf.donateMustBeShown(doesDonateMustBeShown());
            }
        };
        thread.start();
    }

    public void disableShowDonate() {
        Thread thread = new Thread() {
            @Override
            public void run() {
                ConfigurationORM config = getConfigurationORM();
                config.lastShowDonateMs = -1;
            }
        };
        thread.start();
    }

    /*
     * return whether donate view must be shown (donate view should be shown sometimes)
     *
     * @return if return true, lastShowDonateMs is reset to now
     *   if lastShowDonateMs is set to -1 in DB, doesDonateMustBeShown will always return false
     */
    private boolean doesDonateMustBeShown() {
        boolean mustBeShown = false;

        if (Flavor.getCurrentFlavor(context) == Flavor.SMP_FLAVOR.PRO) {
            return false;
        }

        ConfigurationORM config = getConfigurationORM();
        boolean showDonateDisabled = config.lastShowDonateMs < 0;
        if (showDonateDisabled) {
            Log.d("Database", "Show donate disabled");
        }
        else {
            final long appOpenedOften = 20;
            config.nbTimeAppStartedSinceShowDonate++;
            final long donatePeriodInDay = 31*1;
            long nowMs = (new Date()).getTime();
            if ((config.nbTimeAppStartedSinceShowDonate > appOpenedOften) &&
                (nowMs - config.lastShowDonateMs) > donatePeriodInDay*24*3600*1000)
            {
                config.lastShowDonateMs = nowMs;
                config.nbTimeAppStartedSinceShowDonate = 0;
                mustBeShown = true;
            }
            else {
                Log.d("Database", "Show donate not useful : app used " +
                        config.nbTimeAppStartedSinceShowDonate + " times or already shown the " +
                        new Date(config.lastShowDonateMs));
            }
            configurationDAO.update(config);
        }
        return mustBeShown;
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
