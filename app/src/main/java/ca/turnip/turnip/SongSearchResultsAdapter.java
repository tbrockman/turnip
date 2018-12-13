package ca.turnip.turnip;

import android.content.Context;
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
        extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final int VIEW_ITEM = 1;
    private final int VIEW_PROG = 0;
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

    @Override
    public int getItemViewType(int position) {
        return suggestions.get(position) != null ? VIEW_ITEM: VIEW_PROG;
    }


    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup,
                                                      int i) {
        RecyclerView.ViewHolder vh;
        if (i == VIEW_ITEM) {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.song,
                                                                         viewGroup,
                                                                        false
            );
            vh = new SongSearchResultsAdapter.SongViewHolder(v);
        } else {
            View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.recyclerview_progress_bar,
                                                                         viewGroup,
                                                                         false);

            vh = new ProgressViewHolder(v);
        }
        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int i) {
        if (holder instanceof SongViewHolder) {
            Song song = suggestions.get(i);
            final SongViewHolder songViewHolder = (SongViewHolder) holder;
            // SongViewHolder.
            songViewHolder.songName.setText(song.getString("name"));
            songViewHolder.artist.setText(TextUtils.join(", ", song.getArtists()));

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
        } else {
            ((ProgressViewHolder)holder).progressBar.setIndeterminate(true);
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

    public class ProgressViewHolder extends RecyclerView.ViewHolder {
        ProgressBar progressBar;

        public ProgressViewHolder(View itemView) {
            super(itemView);
            progressBar = (ProgressBar)itemView.findViewById(R.id.progressBar);
        }
    }
}
