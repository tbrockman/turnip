package ca.turnip.turnip;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URL;

import static ca.turnip.turnip.SongQueueAdapter.getRetrieveAlbumArtTask;

public class RetrieveAlbumArtTask extends AsyncTask<Void, Void, Bitmap> {

    private Song song;
    public final String albumArtUrl;
    private final WeakReference<ImageView> albumArtImageViewReference;
    private final WeakReference<ProgressBar> albumArtProgressBarReference;

    public RetrieveAlbumArtTask(Song song, String albumArtUrl, ImageView albumArt, ProgressBar albumArtProgressBarReference) {
        this.song = song;
        this.albumArtUrl = albumArtUrl;
        this.albumArtImageViewReference = new WeakReference<>(albumArt);
        this.albumArtProgressBarReference = new WeakReference<>(albumArtProgressBarReference);
    }

    @Override
    protected void onPreExecute() {
        final ImageView imageView = albumArtImageViewReference.get();
        final ProgressBar progressBar = albumArtProgressBarReference.get();
        imageView.setVisibility(View.INVISIBLE);
        progressBar.setVisibility(View.VISIBLE);
    }

    @Override
    protected Bitmap doInBackground(Void... params) {
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
            final ProgressBar progressBar = albumArtProgressBarReference.get();
            final RetrieveAlbumArtTask retrieveAlbumArtTask = getRetrieveAlbumArtTask(imageView);
            if (this == retrieveAlbumArtTask && imageView != null) {
                song.setAlbumArt(bitmap);
                imageView.setVisibility(View.VISIBLE);
                imageView.setImageBitmap(bitmap);
                progressBar.setVisibility(View.INVISIBLE);
            }
        }
    }
}