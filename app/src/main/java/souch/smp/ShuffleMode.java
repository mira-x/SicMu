package souch.smp;

import static android.widget.Toast.LENGTH_SHORT;

import android.view.View;
import android.widget.Toast;

public enum ShuffleMode {
    SEQUENTIAL(0),
    RANDOM(1),
    RADIO(2); // Radio is basically random selection (above) plus random song start time when playing the first song

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

    /// Returns true for such modes that are meant to play music in random order
    public boolean isRandomish() {
        return (this == RANDOM || this == RADIO);
    }

    public void showExplainToast(View v) {
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
        Toast.makeText(ctx, txt, LENGTH_SHORT).show();
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
