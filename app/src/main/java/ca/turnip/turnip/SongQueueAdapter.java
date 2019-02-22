package ca.turnip.turnip;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;
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

public class SongQueueAdapter extends RecyclerView.Adapter<SongQueueAdapter.SongViewHolder> {

    private static final String TAG = SongQueueAdapter.class.getSimpleName();

    private Context context;
    private FragmentManager supportFragmentManager;
    private volatile ArrayList<Song> songQueue;

    public SongQueueAdapter(Context context, FragmentManager supportFragmentManager, ArrayList<Song> songQueue) {
        this.context = context;
        this.supportFragmentManager = supportFragmentManager;
        this.songQueue = songQueue;
    }

    @NonNull
    @Override
    public SongQueueAdapter.SongViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View v = LayoutInflater.from(
                viewGroup.getContext()).inflate(R.layout.song,
                viewGroup,
                false
        );

        SongQueueAdapter.SongViewHolder vh = new SongQueueAdapter.SongViewHolder(v);

        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull final SongQueueAdapter.SongViewHolder songViewHolder, int i) {
        // SongViewHolder.
        final Song song = songQueue.get(i);

        songViewHolder.view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DialogFragment fragment = SongInfoDialogFragment.newInstance(song);
                fragment.show(supportFragmentManager, "dialog");
            }
        });

        songViewHolder.songName.setText(song.getString("name"));
        songViewHolder.artist.setText(song.getArtistsAsString());
        try {
            String albumArtUrl = song.getAlbumArtURL("small");
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
        return songQueue.size();
    }

    public class SongViewHolder extends RecyclerView.ViewHolder {

        TextView songName;
        TextView artist;
        ImageView albumArt;
        ImageView plusSign;
        ProgressBar progressBar;
        View view;

        SongViewHolder self = this;

        public SongViewHolder(View itemView) {
            super(itemView);
            songName = (TextView) itemView.findViewById(R.id.songName);
            artist = (TextView) itemView.findViewById(R.id.artist);
            albumArt = (ImageView) itemView.findViewById(R.id.albumArt);
            plusSign = (ImageView) itemView.findViewById(R.id.addSongPlus);
            progressBar = (ProgressBar) itemView.findViewById(R.id.progressBar);
            view = itemView;

            plusSign.setVisibility(View.INVISIBLE);
        }
    }
}

