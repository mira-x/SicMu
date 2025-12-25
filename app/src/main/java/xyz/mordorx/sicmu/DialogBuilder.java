package xyz.mordorx.sicmu;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Pair;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a wrapper for AlertDialog.Builder which makes dynamic option lists easier.
 */
public class DialogBuilder {
    private final List<Pair<String, Runnable>> options = new ArrayList<>();
    private final Context ctx;
    private final AlertDialog.Builder builder;
    public DialogBuilder(Context ctx, @DrawableRes int iconId, String title) {
        this.ctx = ctx;
        this.builder = new AlertDialog.Builder(ctx);
        builder.setIcon(iconId);
        builder.setTitle(title);
    }

    public void addOption(String label, Runnable callback) {
        options.add(Pair.create(label, callback));
    }
    public void addOption(@StringRes int labelStringId, Runnable callback) {
        options.add(Pair.create(ctx.getString(labelStringId), callback));
    }

    public AlertDialog build() {
        var itemLabels = options
                .stream()
                .map(opt -> opt.first)
                .toArray(String[]::new);
        builder.setItems(itemLabels, (DialogInterface dialog, int item) -> {
            options.get(item).second.run();
            });
        return builder.create();
    }
}
