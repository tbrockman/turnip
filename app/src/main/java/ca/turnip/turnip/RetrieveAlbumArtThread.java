package ca.turnip.turnip;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class RetrieveAlbumArtThread implements Runnable {

    private static final String TAG = SongSearchResultsAdapter.class.getSimpleName();

    private Song suggestion;
    private String albumArtUrl;
    private ImageView albumArt;

    public RetrieveAlbumArtThread(Song suggestion, String albumArtUrl, ImageView albumArt) {
        this.suggestion = suggestion;
        this.albumArtUrl = albumArtUrl;
        this.albumArt = albumArt;
    }

    @Override
    public void run() {
        try {
            Bitmap bitmap = BitmapFactory.decodeStream(
                                           (InputStream) new URL(albumArtUrl).getContent());
            // store downloaded album art for future re-drawings
            suggestion.setAlbumArt(bitmap);
            albumArt.post(new ChangeAlbumArtThread(albumArt, bitmap));
        } catch (IOException e) {
            Log.e(TAG, "Error retrieving album art from: " + albumArtUrl);
        }
    }  
}
