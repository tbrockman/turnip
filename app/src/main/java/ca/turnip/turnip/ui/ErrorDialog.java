package ca.turnip.turnip.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;

import ca.turnip.turnip.R;

public class ErrorDialog {

    AlertDialog dialog;
    AlertDialog.Builder builder;

    public ErrorDialog(Activity activity, String title, String message) {
        this(activity, title, message, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
    }

    public ErrorDialog(Activity activity, String title, String message, DialogInterface.OnClickListener clickListener) {
        builder = new AlertDialog.Builder(activity);
        builder.setMessage(message)
                .setTitle(title);
        builder.setPositiveButton(R.string.ok, clickListener);
        dialog = builder.create();
        dialog.show();
    }

    public ErrorDialog(final Activity activity, String title, String message, final Boolean finishActivity) {
        this(activity, title, message, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                if (finishActivity) {
                    activity.finish();
                }
            }
        });
        this.dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if (finishActivity) {
                    activity.finish();
                }
            }
        });
    }
}
