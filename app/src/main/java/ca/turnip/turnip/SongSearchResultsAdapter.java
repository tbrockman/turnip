package ca.turnip.turnip;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
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

import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import org.json.JSONException;

import java.util.ArrayList;

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
        View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.song,
                                                                     viewGroup,
                                                                     false
        );

        SongSearchResultsAdapter.SongViewHolder vh = new SongSearchResultsAdapter.SongViewHolder(v);

        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull final SongSearchResultsAdapter.SongViewHolder songViewHolder, int i) {
        Song song = suggestions.get(i);
        // SongViewHolder.
        songViewHolder.songName.setText(song.getString("name"));
        songViewHolder.artist.setText(TextUtils.join(", ", song.getArtists()));

        if (i == 0) {
            songViewHolder.itemView.getLayoutParams();
        }

        try {
            String albumArtUrl = song.getAlbumArtURL();
            songViewHolder.progressBar.setVisibility(View.VISIBLE);
            songViewHolder.albumArt.setVisibility(View.INVISIBLE);
            Picasso.get().load(albumArtUrl).into(songViewHolder.albumArt, new Callback() {

                @Override
                public void onSuccess() {
                    songViewHolder.albumArt.setVisibility(View.VISIBLE);
                    songViewHolder.progressBar.setVisibility(View.INVISIBLE);
                }

                @Override
                public void onError(Exception e) {

                }
            });
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
