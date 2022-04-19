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

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class DonateActivity extends AppCompatActivity {
    private LinearLayout donateLayout;
    private Button closeButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.donate);

        ImageView appButton = (ImageView) findViewById(R.id.app_button);
        appButton.setBackgroundResource(R.drawable.ic_actionbar_launcher_anim);
        findViewById(R.id.actions_bar).setOnClickListener(view -> {
            openDonate(null);
        });

        TextView donateText = findViewById(R.id.donate_text);
        Flavor.SMP_FLAVOR flavor = Flavor.getCurrentFlavor(getApplicationContext());
        if (flavor == Flavor.SMP_FLAVOR.FDROID) {
            donateText.setOnClickListener(view -> {
                openDonate(null);
            });
            findViewById(R.id.pro_button).setVisibility(View.GONE);
        }
        if (flavor == Flavor.SMP_FLAVOR.FREEWARE) {
            donateText.setText(R.string.buy_pro_summary);
            donateText.setOnClickListener(view -> {
                openPro(null);
            });
            //findViewById(R.id.donate_button).setVisibility(View.GONE);
        }

        closeButton = findViewById(R.id.close_button);
        closeButton.setOnClickListener(view -> {
            finish();
        });
    }

    public void openDonate(View view) {
        startActivity(SettingsPreferenceFragment.GetDonateWebsiteIntent());
    }

    public void openPro(View view) {
        startActivity(SettingsPreferenceFragment.GetProWebsiteIntent());
    }
}
