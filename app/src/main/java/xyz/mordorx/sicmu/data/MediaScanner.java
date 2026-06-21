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

package xyz.mordorx.sicmu.data;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;


import android.database.Cursor;

import java.util.ArrayList;
import java.io.File;
import java.util.Collection;
import java.util.Formatter;
import java.util.HashSet;
import java.util.Vector;

import androidx.annotation.Nullable;

import xyz.mordorx.sicmu.R;

public class MediaScanner {
    public static String[] rootFolders = {""};

    /**
     @example
     if rootFolders = "" and path = /mnt/sdcard/toto/tata.mp3 -> return /mnt/sdcard/toto
     @example
     if rootFolders = "/mnt/sdcard" and path = /mnt/sdcard/toto/tata.mp3 -> return toto
     @example
     if rootFolders = "/mnt/sdcard/" and path = /mnt/sdcard/toto/tata.mp3 -> return toto

     @param path must not be null. rootFolders is removed from the beginning of the path.
     */
    static public String getFolder(String path) {
        String folder = null;

        // remove rootFolders
        if (rootFolders != null) {
            for (String rootFolder : rootFolders) {
                if (rootFolder.length() <= path.length() && rootFolder.equals(path.substring(0, rootFolder.length()))) {
                    int rootFolderSize = rootFolder.length();
                    // remove / remaining at the beginning of path
                    if (path.length() > rootFolderSize && path.charAt(rootFolderSize) == File.separatorChar)
                        rootFolderSize++;

                    folder = path.substring(rootFolderSize);
                }
            }
        }
        if (folder == null) {
            folder = path;
        }

        // remove filename
        int index = folder.lastIndexOf(File.separatorChar);
        if (index == -1) // no folder: remove everything
            index = 0;
        folder = folder.substring(0, index);

        // no folder get the name "."
        if (folder.isEmpty())
            folder = ".";

        return folder;
    }

    /*
     path = "toto/tata" -> return {"toto", "tata"}
     */
    static public ArrayList<String> tokenizeFolder(String path) {
        ArrayList<String> folders = new ArrayList<>();
        int beg = 0;
        boolean folderFound = false;
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) == File.separatorChar) {
                if (folderFound) {
                    folders.add(path.substring(beg, i));
                    folderFound = false;
                }
                beg = i + 1;
            } else {
                folderFound = true;
            }
        }
        // path do not finish by /
        if (folderFound) {
            folders.add(path.substring(beg));
        }

        return folders;
    }

    public static String getMusicStoragesStr(Context context) {
        StringBuilder dirsStr = new StringBuilder();
        Collection<File> dirs = getMusicStorages(context);
        for (File dir : dirs) {
            dirsStr.append(dir.getAbsolutePath()).append(";");
        }
        if (dirsStr.toString().endsWith(";"))
            dirsStr = new StringBuilder(dirsStr.substring(0, dirsStr.length() - 1));
        return dirsStr.toString();
    }

    public static Collection<File> getMusicStorages(Context context) {
        ArrayList<File> musicDirs = new ArrayList<>();
        Collection<File> dirs = getStorages(context);
        for (File dir : dirs) {
            musicDirs.add(new File(dir, "Music/"));
        }
        return musicDirs;
    }

    public static Collection<File> getStorages(Context context) {
        HashSet<File> dirsToScan = new HashSet<>();

        dirsToScan.add(Environment.getExternalStorageDirectory());

        // hack. Don't know if it work well on other devices!
        String userPathToRemove = "Android/data/xyz.mordorx.sicmu/files";
        File[] files = context.getExternalFilesDirs(null);
        if (files != null)
            for (File dir : files) {
                if (dir != null && dir.getAbsolutePath().endsWith(userPathToRemove)) {
                    dirsToScan.add(dir.getParentFile().getParentFile().getParentFile().getParentFile());
                }
            }

        for (File dir : dirsToScan) {
            Log.d("Settings", "userDir: " + dir.getAbsolutePath());
        }
        return dirsToScan;
    }

    public static void listFiles(File directory, ArrayList<File> files) {
        // get all the files from a directory
        File[] fList = directory.listFiles();
        if (fList != null)
            for (File file : fList) {
                if (file.isFile()) {
                    files.add(file);
                } else if (file.isDirectory()) {
                    listFiles(file, files);
                }
            }
    }

    public static void rescanWhole(Context context) {
        scanMediaFiles(context);
    }

    public static boolean rescanDir(Context context, File dir) {
        if (!dir.exists())
            return false;
        Log.d("Settings", "fileToScan: " + dir.getAbsolutePath());
        ArrayList<File> filesToScan = new ArrayList<>();
        MediaScanner.listFiles(dir, filesToScan);
        scanMediaFiles(context, filesToScan, null);
        return true;
    }

    private static void scanMediaFiles(Context context) {
        // http://stackoverflow.com/questions/13270789/how-to-run-media-scanner-in-android
        Toast.makeText(context,
                context.getString(R.string.settings_rescan_triggered),
                Toast.LENGTH_SHORT).show();

        Collection<File> dirsToScan = MediaScanner.getStorages(context); // getBaseContext()
        for (File dir : dirsToScan) {
            Toast.makeText(context,
                    (new Formatter()).format(context.getResources()
                                    .getString(R.string.settings_rescan_storage), dir)
                            .toString(),
                    Toast.LENGTH_LONG).show();
        }

        // add Music folder in first to speedup music folder discovery
        for (File dir : dirsToScan) {
            File musicDir = new File(dir, "Music");
            rescanDir(context, musicDir);
        }

        // add whole storage at the end
        for (File dir : dirsToScan) {
            rescanDir(context, dir);
        }

        Toast.makeText(context,
                context.getResources().getString(R.string.settings_rescan_finished),
                Toast.LENGTH_LONG).show();
    }


    public static void scanMediaFiles(Context context, Collection<File> filesToScan,
                                      MediaScannerConnection.OnScanCompletedListener mediaScannerCallback) {
        String[] filesToScanArray = new String[filesToScan.size()];
        int i = 0;
        for (File file : filesToScan) {
            filesToScanArray[i] = file.getAbsolutePath();
            //if (filesToScanArray[i].contains("emulated/0"))
            Log.d("Settings", "fileToScan: " + filesToScanArray[i]);
            i++;
        }

        if (filesToScanArray.length != 0) {
            MediaScannerConnection.scanFile(context, filesToScanArray, null, mediaScannerCallback);
        } else {
            Log.e("Settings", "Media scan requested when nothing to scan");
        }
    }

    public static void scanMediaFolder(Context context, String path, MediaScannerConnection.OnScanCompletedListener mediaScannerCallback) {
        if (path == null)
            return;
        File file = new File(path);
        if (!file.exists())
            return;
        Vector<File> files = new Vector<>();
        if (file.isDirectory())
            files.add(file);
        else
            files.add(file.getParentFile());
        scanMediaFiles(context, files, mediaScannerCallback);
    }

    public static void scanMediaFile(Context context, String path,
                                     MediaScannerConnection.OnScanCompletedListener mediaScannerCallback) {
        File file = new File(path);
        Vector<File> files = new Vector<>();
        files.add(file);
        scanMediaFiles(context, files, mediaScannerCallback);
    }

    public static String getSongPathFromUri(Context context, Uri uri) {
        String songPath = null;
        File songFile = null;
        if (uri.getAuthority() != null && uri.getAuthority().equals("com.android.externalstorage.documents")) {
            songFile = new File(Environment.getExternalStorageDirectory(), uri.getPath().split(":", 2)[1]);
        }
        if (songFile == null) {
            String path = getFilePathFromUri(context, uri);
            if (path != null)
                songFile = new File(path);
        }
        if (songFile == null && uri.getPath() != null) {
            songFile = new File(uri.getPath());
        }
        if (songFile != null) {
            songPath = songFile.getAbsolutePath();
        }
        return songPath;
    }

    @Nullable
    private static String getFilePathFromUri(Context context, Uri uri)
    {
        final String column = "_data";
        final String[] projection = {
                column
        };
        try (Cursor cursor = context.getContentResolver().query(uri, projection, null, null,
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } catch (Exception e) {
            Log.e("Rows", "getFilePathFromUri :" + e.getMessage());
        }
        return null;
    }

}
