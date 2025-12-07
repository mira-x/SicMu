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

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import java.util.ArrayList;


public class PathTest {

    private void tryGetFolder(String path, String expectedFolder) {
        String folder = Path.getFolder(path);
        assertEquals(expectedFolder, folder);
    }

    @Test
    public void testGetFolderEmpty() {
        tryGetFolder("", ".");
    }

    @Test
    public void testGetFolderUsual() {
        Path.rootFolders = "/mnt/sdcard";
        tryGetFolder("/mnt/sdcard/toto/tata.mp3", "toto");
    }

    @Test
    public void testGetFolderUsual2() {
        Path.rootFolders = "/mnt/sdcard";
        tryGetFolder("/mnt/sdcard/toto/titi/tata.mp3", "toto/titi");
    }

    @Test
    public void testGetFolderUsualSlashRootFolder() {
        Path.rootFolders = "/mnt/sdcard/";
        tryGetFolder("/mnt/sdcard/toto/titi/tata.mp3", "toto/titi");
    }

    @Test
    public void testGetFolderAllRootFolder() {
        Path.rootFolders = "/mnt/sdcard";
        tryGetFolder("/mnt/sdcard/tata.mp3", ".");

        Path.rootFolders = "/mnt/sdcard/";
        tryGetFolder("/mnt/sdcard/tata.mp3", ".");
    }

    @Test
    public void testGetFolderMangleRootFolder() {
        Path.rootFolders = "/mnt/sdcard";
        tryGetFolder("/mnt/sdcard.mp3", ".");

        Path.rootFolders = "/mnt/sdcard/";
        tryGetFolder("/mnt/sdcard.mp3", "/mnt");
    }

    private void tryTokenizeFolder(String folder, String[] expectedFolders) {
        ArrayList<String> folders = Path.tokenizeFolder(folder);
        assertEquals(expectedFolders.length, folders.size());
        for (int i = 0; i < folders.size(); i++) {
            assertEquals(expectedFolders[i], folders.get(i));
        }
    }

    @Test
    public void testTokenizeFolderUsual() {
        tryTokenizeFolder("/mnt/sdcard/toto", new String[]{"mnt", "sdcard", "toto"});
        tryTokenizeFolder("/mnt/sdcard/toto/", new String[]{"mnt", "sdcard", "toto"});
        tryTokenizeFolder("/mnt/sdcard/toto/o", new String[]{"mnt", "sdcard", "toto", "o"});
    }

    @Test
    public void testTokenizeFolderOne() {
        tryTokenizeFolder("/mnt/", new String[]{"mnt"});
        tryTokenizeFolder("/mnt", new String[]{"mnt"});
    }

    @Test
    public void testTokenizeFolderStrange() {
        tryTokenizeFolder("/./", new String[]{"."});
        tryTokenizeFolder(".", new String[]{"."});
        tryTokenizeFolder("/toot///yo", new String[]{"toot", "yo"});
        tryTokenizeFolder("/toot///", new String[]{"toot"});
        tryTokenizeFolder("//", new String[]{});
        tryTokenizeFolder("/", new String[]{});
        tryTokenizeFolder("", new String[]{});
    }
}
