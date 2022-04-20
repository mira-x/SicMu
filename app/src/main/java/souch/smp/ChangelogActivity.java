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

import android.content.res.AssetManager;
import android.os.Bundle;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collections;

import androidx.appcompat.app.AppCompatActivity;

public class ChangelogActivity extends AppCompatActivity {
    private Button closeButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.changelog);

        ImageView appButton = (ImageView) findViewById(R.id.app_button);
        appButton.setBackgroundResource(R.drawable.ic_actionbar_launcher_anim);
        findViewById(R.id.actions_bar).setOnClickListener(view -> {
            finish();
        });

        closeButton = findViewById(R.id.close_button);
        closeButton.setOnClickListener(view -> {
            finish();
        });

        TextView ChangelogTextview = findViewById(R.id.changelog_text);
        ChangelogTextview.setMovementMethod(new ScrollingMovementMethod());
        ChangelogTextview.setText(Html.fromHtml(getChangelogHTMLText()));
    }

    public String getChangelogHTMLText() {
        String logText = new String();

        Log.d("Changelog", "loading logs");
        AssetManager assetManager = getAssets();
        BufferedReader reader = null;
        final String changelogAssetDir = "changelog";
        try {
            String[] logs = assetManager.list(changelogAssetDir);
            Collections.reverse(Arrays.asList(logs));

            for (String log: logs) {
                Log.d("Changelog", "loading log from file " + log);
                final String logFilepath = changelogAssetDir + "/" + log;
                reader = new BufferedReader(new InputStreamReader(
                        assetManager.open(logFilepath)));

                boolean firstLine = true;
                String mLine;
                while ((mLine = reader.readLine()) != null) {
                    if (firstLine)
                        logText += "<b>" + mLine + "</b>";
                    else
                        logText += mLine;
                    logText += "<br />";
                    firstLine = false;
                    // <small>yo</small>
                }
                logText += "<br />";
            }
        }
        catch (IOException ioe) {
            Log.w("Changelog", "error listing log: " + ioe.toString());
        }
        finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    //log the exception
                }
            }
        }
        return logText;
    }
}
