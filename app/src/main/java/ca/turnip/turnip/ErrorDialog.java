package ca.turnip.turnip;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;

public class ErrorDialog {

    AlertDialog dialog;
    AlertDialog.Builder builder;

    public ErrorDialog(Activity activity, String title, String message) {
        builder = new AlertDialog.Builder(activity);
        builder.setMessage(message)
               .setTitle(title);
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
            }
        });
        dialog = builder.create();
        dialog.show();
    }
}
