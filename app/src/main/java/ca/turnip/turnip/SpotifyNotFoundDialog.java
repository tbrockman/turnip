package ca.turnip.turnip;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;

public class SpotifyNotFoundDialog {
    AlertDialog dialog;
    AlertDialog.Builder builder;

    public SpotifyNotFoundDialog(final Activity activity) {
        builder = new AlertDialog.Builder(activity);
        builder.setMessage("No valid installation of Spotify could be found, install Spotify to continue.")
               .setTitle("Spotify not found");
        builder.setPositiveButton(R.string.install, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(
                        "https://play.google.com/store/apps/details?id=com.spotify.music"));
                activity.startActivity(intent);
                dialog.dismiss();
            }
        });
        builder.setNegativeButton(R.string.cancel_button_text, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialog.dismiss();
            }
        });
        dialog = builder.create();
        dialog.show();
    }
}
