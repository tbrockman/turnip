package ca.turnip.turnip;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URL;

import static ca.turnip.turnip.SongQueueAdapter.getRetrieveAlbumArtTask;

public class RetrieveAlbumArtTask extends AsyncTask<Integer, Void, Bitmap> {

    private Song song;
    private final String albumArtUrl;
    private final WeakReference<ImageView> albumArtImageViewReference;

    Integer resId = 0;

    public RetrieveAlbumArtTask(Song song, String albumArtUrl, ImageView albumArt) {
        this.song = song;
        this.albumArtUrl = albumArtUrl;
        this.albumArtImageViewReference = new WeakReference<>(albumArt);
    }

    @Override
    protected Bitmap doInBackground(Integer... data) {
        this.resId = data[0];
        Bitmap bitmap = null;
        try {
            bitmap = BitmapFactory.decodeStream(
                    (InputStream) new URL(albumArtUrl).getContent());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    @Override
    protected void onPostExecute(Bitmap bitmap) {
        if (isCancelled()) {
            bitmap = null;
        }

        if (albumArtImageViewReference != null && bitmap != null) {
            final ImageView imageView = albumArtImageViewReference.get();
            final RetrieveAlbumArtTask retrieveAlbumArtTask = getRetrieveAlbumArtTask(imageView);
            if (this == retrieveAlbumArtTask && imageView != null) {
                song.setAlbumArt(bitmap);
                imageView.setImageBitmap(bitmap);
            }
        }
    }
}