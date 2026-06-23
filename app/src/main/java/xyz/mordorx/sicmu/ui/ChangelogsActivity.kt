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
package xyz.mordorx.sicmu.ui

import android.os.Bundle
import android.text.Html
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import xyz.mordorx.sicmu.R
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.Arrays
import java.util.Collections

class ChangelogsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.changelogs)

        val appButton = findViewById<ImageView>(R.id.app_button)
        appButton.setBackgroundResource(R.drawable.ic_actionbar_launcher_anim)
        findViewById<View?>(R.id.actions_bar).setOnClickListener(View.OnClickListener { view: View? -> finish() })

        val closeButton = findViewById<Button>(R.id.close_button)
        closeButton.setOnClickListener(View.OnClickListener { view: View? -> finish() })

        val changelogsTextview = findViewById<TextView>(R.id.changelogs_text)
        changelogsTextview.setMovementMethod(ScrollingMovementMethod())
        changelogsTextview.setText(
            Html.fromHtml(
                this.changelogsHTMLText,
                Html.FROM_HTML_MODE_LEGACY
            )
        )
    }

    val changelogsHTMLText: String
        get() {
            val logText = StringBuilder()

            Log.d("Changelogs", "loading logs")
            val assetManager = getAssets()
            val changelogsAssetDir = "changelogs"
            try {
                val logs = assetManager.list(changelogsAssetDir)
                Collections.reverse(Arrays.asList<String?>(*logs))

                for (log in logs!!) {
                    Log.d("Changelogs", "loading log from file " + log)
                    val logFilepath = changelogsAssetDir + "/" + log
                    BufferedReader(InputStreamReader(assetManager.open(logFilepath)))
                        .use { reader ->
                            var firstLine = true
                            var mLine: String?
                            while ((reader.readLine().also { mLine = it }) != null) {
                                if (firstLine) logText.append("<b>").append(mLine).append("</b>")
                                else logText.append(mLine)
                                logText.append("<br />")
                                firstLine = false
                                // <small>yo</small>
                            }
                            logText.append("<br />")
                        }
                }
            } catch (ioe: IOException) {
                Log.w("Changelogs", "error listing log: " + ioe)
            }
            return logText.toString()
        }
}
