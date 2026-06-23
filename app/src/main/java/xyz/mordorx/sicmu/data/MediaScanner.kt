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
package xyz.mordorx.sicmu.data

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import xyz.mordorx.sicmu.R
import java.io.File
import java.util.Formatter
import java.util.Vector

object MediaScanner {
    @JvmField
    var rootFolders = arrayOf("")

    /**
     * @example
     * if rootFolders = "" and path = /mnt/sdcard/toto/tata.mp3 -> return /mnt/sdcard/toto
     * @example
     * if rootFolders = "/mnt/sdcard" and path = /mnt/sdcard/toto/tata.mp3 -> return toto
     * @example
     * if rootFolders = "/mnt/sdcard/" and path = /mnt/sdcard/toto/tata.mp3 -> return toto
     * @param path must not be null. rootFolders is removed from the beginning of the path.
     */
    @JvmStatic
    fun getFolder(path: String): String {
        var folder: String? = null

        // remove rootFolders
        if (rootFolders != null) {
            for (rootFolder in rootFolders) {
                if (rootFolder.length <= path.length && rootFolder == path.substring(
                        0,
                        rootFolder.length
                    )
                ) {
                    var rootFolderSize = rootFolder.length
                    // remove / remaining at the beginning of path
                    if (path.length > rootFolderSize && path.get(rootFolderSize) == File.separatorChar) rootFolderSize++

                    folder = path.substring(rootFolderSize)
                }
            }
        }
        if (folder == null) {
            folder = path
        }

        // remove filename
        var index = folder.lastIndexOf(File.separatorChar)
        if (index == -1)  // no folder: remove everything
            index = 0
        folder = folder.substring(0, index)

        // no folder get the name "."
        if (folder.isEmpty()) folder = "."

        return folder
    }

    /*
     path = "toto/tata" -> return {"toto", "tata"}
     */
    @JvmStatic
    fun tokenizeFolder(path: String): ArrayList<String?> {
        val folders = ArrayList<String?>()
        var beg = 0
        var folderFound = false
        for (i in 0..<path.length) {
            if (path.get(i) == File.separatorChar) {
                if (folderFound) {
                    folders.add(path.substring(beg, i))
                    folderFound = false
                }
                beg = i + 1
            } else {
                folderFound = true
            }
        }
        // path do not finish by /
        if (folderFound) {
            folders.add(path.substring(beg))
        }

        return folders
    }

    fun getMusicStoragesStr(context: Context): String {
        var dirsStr = StringBuilder()
        val dirs = getMusicStorages(context)
        for (dir in dirs) {
            dirsStr.append(dir.getAbsolutePath()).append(";")
        }
        if (dirsStr.toString().endsWith(";")) dirsStr =
            StringBuilder(dirsStr.substring(0, dirsStr.length - 1))
        return dirsStr.toString()
    }

    fun getMusicStorages(context: Context): MutableCollection<File> {
        val musicDirs = ArrayList<File>()
        val dirs = getStorages(context)
        for (dir in dirs) {
            musicDirs.add(File(dir, "Music/"))
        }
        return musicDirs
    }

    fun getStorages(context: Context): MutableCollection<File> {
        val dirsToScan = HashSet<File>()

        dirsToScan.add(Environment.getExternalStorageDirectory())

        // hack. Don't know if it work well on other devices!
        val userPathToRemove = "Android/data/xyz.mordorx.sicmu/files"
        val files = context.getExternalFilesDirs(null)
        if (files != null) for (dir in files) {
            if (dir != null && dir.getAbsolutePath().endsWith(userPathToRemove)) {
                dirsToScan.add(dir.getParentFile().getParentFile().getParentFile().getParentFile())
            }
        }

        for (dir in dirsToScan) {
            Log.d("Settings", "userDir: " + dir.getAbsolutePath())
        }
        return dirsToScan
    }

    fun listFiles(directory: File, files: ArrayList<File>) {
        // get all the files from a directory
        val fList = directory.listFiles()
        if (fList != null) for (file in fList) {
            if (file.isFile()) {
                files.add(file)
            } else if (file.isDirectory()) {
                listFiles(file, files)
            }
        }
    }

    fun rescanWhole(context: Context) {
        scanMediaFiles(context)
    }

    fun rescanDir(context: Context?, dir: File): Boolean {
        if (!dir.exists()) return false
        Log.d("Settings", "fileToScan: " + dir.getAbsolutePath())
        val filesToScan = ArrayList<File>()
        listFiles(dir, filesToScan)
        scanMediaFiles(context, filesToScan, null)
        return true
    }

    private fun scanMediaFiles(context: Context) {
        // http://stackoverflow.com/questions/13270789/how-to-run-media-scanner-in-android
        Toast.makeText(
            context,
            context.getString(R.string.settings_rescan_triggered),
            Toast.LENGTH_SHORT
        ).show()

        val dirsToScan = getStorages(context) // getBaseContext()
        for (dir in dirsToScan) {
            Toast.makeText(
                context,
                (Formatter()).format(
                    context.getResources()
                        .getString(R.string.settings_rescan_storage), dir
                )
                    .toString(),
                Toast.LENGTH_LONG
            ).show()
        }

        // add whole storage at the end
        for (dir in dirsToScan) {
            rescanDir(context, dir)
        }

        Toast.makeText(
            context,
            context.getResources().getString(R.string.settings_rescan_finished),
            Toast.LENGTH_LONG
        ).show()
    }


    fun scanMediaFiles(
        context: Context?, filesToScan: MutableCollection<File>,
        mediaScannerCallback: MediaScannerConnection.OnScanCompletedListener?
    ) {
        val filesToScanArray = arrayOfNulls<String>(filesToScan.size)
        var i = 0
        for (file in filesToScan) {
            filesToScanArray[i] = file.getAbsolutePath()
            //if (filesToScanArray[i].contains("emulated/0"))
            Log.d("Settings", "fileToScan: " + filesToScanArray[i])
            i++
        }

        if (filesToScanArray.size != 0) {
            MediaScannerConnection.scanFile(context, filesToScanArray, null, mediaScannerCallback)
        } else {
            Log.e("Settings", "Media scan requested when nothing to scan")
        }
    }

    fun scanMediaFolder(
        context: Context?,
        path: String?,
        mediaScannerCallback: MediaScannerConnection.OnScanCompletedListener?
    ) {
        if (path == null) return
        val file = File(path)
        if (!file.exists()) return
        val files = Vector<File>()
        if (file.isDirectory()) files.add(file)
        else files.add(file.getParentFile())
        scanMediaFiles(context, files, mediaScannerCallback)
    }

    fun scanMediaFile(
        context: Context?, path: String,
        mediaScannerCallback: MediaScannerConnection.OnScanCompletedListener?
    ) {
        val file = File(path)
        val files = Vector<File>()
        files.add(file)
        scanMediaFiles(context, files, mediaScannerCallback)
    }

    fun getSongPathFromUri(context: Context, uri: Uri): String? {
        var songPath: String? = null
        var songFile: File? = null
        if (uri.getAuthority() != null && uri.getAuthority() == "com.android.externalstorage.documents") {
            songFile = File(
                Environment.getExternalStorageDirectory(),
                uri.getPath()!!.split(":".toRegex(), limit = 2).toTypedArray()[1]
            )
        }
        if (songFile == null) {
            val path = getFilePathFromUri(context, uri)
            if (path != null) songFile = File(path)
        }
        if (songFile == null && uri.getPath() != null) {
            songFile = File(uri.getPath())
        }
        if (songFile != null) {
            songPath = songFile.getAbsolutePath()
        }
        return songPath
    }

    private fun getFilePathFromUri(context: Context, uri: Uri): String? {
        val column = "_data"
        val projection = arrayOf<String?>(
            column
        )
        try {
            context.getContentResolver().query(
                uri, projection, null, null,
                null
            ).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    val column_index = cursor.getColumnIndexOrThrow(column)
                    return cursor.getString(column_index)
                }
            }
        } catch (e: Exception) {
            Log.e("Rows", "getFilePathFromUri :" + e.message)
        }
        return null
    }
}
