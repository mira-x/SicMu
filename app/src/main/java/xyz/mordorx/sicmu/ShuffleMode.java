package xyz.mordorx.sicmu;

import android.view.View;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

public enum ShuffleMode {
    SEQUENTIAL(0),
    RANDOM(1),
    RADIO(2); /// Radio is basically random selection (above) plus random song start time when playing the first song

    public final int num;
    ShuffleMode(int num) {
        this.num = num;
    }

    /// This returns the next shuffle mode in sequence, and loops when reaching the last one.
    public ShuffleMode next() {
        var modes = ShuffleMode.values().length;
        var next = (this.num + 1) % modes;
        return ShuffleMode.valueOf(next);
    }

    /// Whether to start playback mid-song. This applies to RADIO.
    public boolean startMidSong() {
        return (this == RADIO);
    }

    public boolean randomSongOrder() {
        return (this == RANDOM || this == RADIO);
    }

    public void showExplainSnackbar(View v) {
        var ctx = v.getContext();
        var txt = "";
        switch (this) {
            case SEQUENTIAL:
                txt = ctx.getString(R.string.settings_shuffle_explainer_sequential);
                break;
            case RANDOM:
                txt = ctx.getString(R.string.settings_shuffle_explainer_random);
                break;
            case RADIO:
                txt = ctx.getString(R.string.settings_shuffle_explainer_radio);
                break;
        }

        Snackbar.make(v, txt, BaseTransientBottomBar.LENGTH_SHORT).show();
    }

    public static ShuffleMode valueOf(int a) {
        for (ShuffleMode m : ShuffleMode.values()) {
            if (m.num == a) {
                return m;
            }
        }
        throw new IllegalArgumentException("Shuffle mode is invalid");
    }
}
