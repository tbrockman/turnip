package ca.passtheaux.passtheaux;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.mancj.materialsearchbar.adapter.SuggestionsAdapter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class SongSuggestionsAdapter extends SuggestionsAdapter<Song, SongSuggestionsAdapter.SuggestionHolder> {

    private static final String TAG = SongSuggestionsAdapter.class.getSimpleName();

    private SongSearch.SongClickedCallback listener = null;

    public SongSuggestionsAdapter(LayoutInflater inflater) {
        super(inflater);
    }

    public SongSuggestionsAdapter(LayoutInflater inflater, SongSearch.SongClickedCallback callback) {
        super(inflater);
        this.listener = callback;
    }

    @Override
    public void onBindSuggestionHolder(Song suggestion, SuggestionHolder holder, int position) {

        holder.songName.setText(suggestion.getString("name"));
        holder.artist.setText(TextUtils.join(",", suggestion.getArtists()));

        try {
            if (!suggestion.hasAlbumArt()) {
                JSONObject album = suggestion.get("album");
                JSONArray albumImages = album.getJSONArray("images");
                JSONObject imageInfoJSON = albumImages.getJSONObject(albumImages.length()-1);
                String albumArtUrl = imageInfoJSON.getString("url");
                Thread retrieveAlbumArt = new Thread(new RetrieveAlbumArtThread(suggestion,
                                                                                albumArtUrl,
                                                                                holder.albumArt));
                retrieveAlbumArt.start();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public class RetrieveAlbumArtThread implements Runnable {

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

    @Override
    public int getSingleViewHeight() {
        return 75;
    }

    @Override
    public SongSuggestionsAdapter.SuggestionHolder onCreateViewHolder(ViewGroup parent,
                                                                      int viewType) {
        View view = getLayoutInflater().inflate(R.layout.item_custom_suggestion,
                                                parent,
                                    false);
        return new SuggestionHolder(view);
    }

    public class SuggestionHolder extends RecyclerView.ViewHolder {
        TextView songName;
        TextView artist;
        ImageView albumArt;

        SuggestionHolder self = this;

        public SuggestionHolder(View itemView) {
            super(itemView);
            songName = (TextView) itemView.findViewById(R.id.songName);
            artist = (TextView) itemView.findViewById(R.id.artist);
            albumArt = (ImageView) itemView.findViewById(R.id.albumArt);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.songChosen(self.getLayoutPosition());
                }
            });
        }
    }
}
