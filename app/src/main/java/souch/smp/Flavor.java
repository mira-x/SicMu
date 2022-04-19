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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Flavor {
    public enum SMP_FLAVOR {
        // fdroid version (unlimited)
        FDROID,
        // googleplay free version (some functionality are disabled)
        FREEWARE,
        // googleplay pro version (unlimited)
        PRO
    }

    public static boolean isFlavorFDroid(Context context) {
        return getCurrentFlavor(context) == SMP_FLAVOR.FDROID;
    }

    public static boolean isFlavorFreeware(Context context) {
        return getCurrentFlavor(context) == SMP_FLAVOR.FREEWARE;
    }

    public static boolean isFlavorPro(Context context) {
        return getCurrentFlavor(context) == SMP_FLAVOR.PRO;
    }

    public static SMP_FLAVOR getCurrentFlavor(Context context) {
        SMP_FLAVOR v = SMP_FLAVOR.FDROID;
        if (BuildConfig.FLAVOR.equals("pro"))
            v = SMP_FLAVOR.PRO;
        else if (isInstalledFromGooglePlay(context))
            v = SMP_FLAVOR.FREEWARE;
        return v;
    }

    public static boolean isInstalledFromGooglePlay(Context context) {
        // A list with valid installers package name
        List<String> validInstallers = new ArrayList<>(
                Arrays.asList("com.android.vending",
                        "com.google.android.feedback"));

        // The package name of the app that has installed your app
        final String installer = context.getPackageManager().getInstallerPackageName(context.getPackageName());

        // true if your app has been downloaded from Play Store
        return installer != null && validInstallers.contains(installer);
    }
}
