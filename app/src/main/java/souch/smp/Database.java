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

import androidx.room.Room;

public class Database {
    private SongDAO songDAO;
    private ConfigurationDAO configurationDAO;

    public Database(Context context) {
        SongDatabase db = Room.databaseBuilder(context,
                SongDatabase.class, "database-SMP")
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
            configurationDAO.insert(config);
        }
        return config;
    }

    public interface DoesDonateMustBeShownInterface {
        void donateMustBeShown(boolean mustBeShown) ;
    }
    public void doesDonateMustBeShownAsync(DoesDonateMustBeShownInterface ddmbsi) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                ddmbsi.donateMustBeShown(doesDonateMustBeShown());
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
        ConfigurationORM config = getConfigurationORM();
        config.nbTimeAppStartedSinceShowDonate++;
        boolean showDonateDisabled = config.lastShowDonateMs < 0;
        if (showDonateDisabled) {
            Log.d("Database", "Show donate disabled");
        }
        else {
            final long donatePeriodInDay = 31*3;
            final long appOpenedOften = 20;
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
        }
        configurationDAO.update(config);
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
}
