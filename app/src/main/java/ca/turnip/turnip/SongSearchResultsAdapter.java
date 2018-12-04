package ca.turnip.turnip;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class SongSearchResultsAdapter
        extends RecyclerView.Adapter<SongSearchResultsAdapter.SongViewHolder> {

    private ArrayList<Song> suggestions;
    private SongSearchActivity.SongClickedCallback songClickedCallback;

    public SongSearchResultsAdapter(ArrayList<Song> suggestions,
                                    SongSearchActivity.SongClickedCallback callback) {
        this.suggestions = suggestions;
        this.songClickedCallback = callback;
    }

    private static final String TAG = SongSearchResultsAdapter.class.getSimpleName();

    @NonNull
    @Override
    public SongSearchResultsAdapter.SongViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup,
                                                                      int i) {
        View v = LayoutInflater.from(
                viewGroup.getContext()).inflate(R.layout.song,
                viewGroup,
                false
        );

        SongSearchResultsAdapter.SongViewHolder vh = new SongSearchResultsAdapter.SongViewHolder(v);

        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder songViewHolder, int i) {

        Song suggestion = suggestions.get(i);
        songViewHolder.songName.setText(suggestion.getString("name"));
        songViewHolder.artist.setText(TextUtils.join(", ", suggestion.getArtists()));

        try {
            if (!suggestion.hasAlbumArt()) {
                JSONObject album = suggestion.get("album");
                JSONArray albumImages = album.getJSONArray("images");
                JSONObject imageInfoJSON = albumImages.getJSONObject(albumImages.length()-1);
                String albumArtUrl = imageInfoJSON.getString("url");
                Thread retrieveAlbumArt = new Thread(new RetrieveAlbumArtThread(suggestion,
                        albumArtUrl,
                        songViewHolder.albumArt));
                retrieveAlbumArt.start();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getItemCount() {
        return suggestions.size();
    }

    public class SongViewHolder extends RecyclerView.ViewHolder {
        TextView songName;
        TextView artist;
        ImageView albumArt;

        public SongViewHolder(View itemView) {
            super(itemView);
            songName = (TextView) itemView.findViewById(R.id.songName);
            artist = (TextView) itemView.findViewById(R.id.artist);
            albumArt = (ImageView) itemView.findViewById(R.id.albumArt);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int i = getAdapterPosition();
                    songClickedCallback.songChosen(suggestions.get(i));
                }
            });
        }
    }
}
