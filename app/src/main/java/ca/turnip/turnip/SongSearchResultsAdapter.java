package ca.turnip.turnip;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import static ca.turnip.turnip.SongQueueAdapter.cancelPotentialWork;

public class SongSearchResultsAdapter
        extends RecyclerView.Adapter<SongSearchResultsAdapter.SongViewHolder> {

    private ArrayList<Song> suggestions;
    private Context context;
    private SongSearchActivity.SongClickedCallback songClickedCallback;

    public SongSearchResultsAdapter(ArrayList<Song> suggestions,
                                    Context context,
                                    SongSearchActivity.SongClickedCallback callback) {
        this.suggestions = suggestions;
        this.context = context;
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
    public void onBindViewHolder(@NonNull SongSearchResultsAdapter.SongViewHolder songViewHolder, int i) {
        // SongViewHolder.
        int albumArtImageViewId = songViewHolder.albumArt.getId();
        Song song = suggestions.get(i);

        songViewHolder.songName.setText(song.getString("name"));
        songViewHolder.artist.setText(TextUtils.join(", ", song.getArtists()));
        try {
            if (!song.hasAlbumArt()) {
                JSONObject album = song.get("album");
                JSONArray albumImages = album.getJSONArray("images");
                Log.i(TAG, albumImages.toString());
                JSONObject imageInfoJSON = albumImages.getJSONObject(albumImages.length()-1);
                String albumArtUrl = imageInfoJSON.getString("url");
                if (cancelPotentialWork(albumArtImageViewId, songViewHolder.albumArt)) {
                    RetrieveAlbumArtTask task = new RetrieveAlbumArtTask(song,
                                                                         albumArtUrl,
                                                                         songViewHolder.albumArt,
                                                                         songViewHolder.progressBar);
                    Bitmap placeholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_logo_svg);
                    final SongQueueAdapter.AsyncDrawable asyncDrawable =
                            new SongQueueAdapter.AsyncDrawable(context.getResources(), placeholder, task);
                    songViewHolder.albumArt.setImageDrawable(asyncDrawable);
                    task.execute(songViewHolder.albumArt.getId());
                }

            }
            else {
                songViewHolder.albumArt.setImageBitmap(song.getAlbumArt());
                songViewHolder.albumArt.setVisibility(View.VISIBLE);
                songViewHolder.progressBar.setVisibility(View.INVISIBLE);
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
        ProgressBar progressBar;

        public SongViewHolder(View itemView) {
            super(itemView);
            songName = (TextView) itemView.findViewById(R.id.songName);
            artist = (TextView) itemView.findViewById(R.id.artist);
            albumArt = (ImageView) itemView.findViewById(R.id.albumArt);
            progressBar = (ProgressBar) itemView.findViewById(R.id.progressBar);

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
