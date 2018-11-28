package ca.turnip.turnip;

import android.graphics.Bitmap;
import android.widget.ImageView;

public class ChangeAlbumArtThread implements Runnable {

    private ImageView albumArt;
    private Bitmap bitmap;

    public ChangeAlbumArtThread(ImageView albumArt, Bitmap bitmap) {
        this.albumArt = albumArt;
        this.bitmap = bitmap;
    }

    @Override
    public void run() {
        albumArt.setImageBitmap(bitmap);
    }
}
