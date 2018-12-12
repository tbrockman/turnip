package ca.turnip.turnip;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class SongQueueAdapter extends RecyclerView.Adapter<SongQueueAdapter.SongViewHolder> {

    private static final String TAG = SongQueueAdapter.class.getSimpleName();

    private Context context;
    private volatile ArrayList<Song> songQueue;

    public SongQueueAdapter(ArrayList<Song> songQueue, Context context) {
        this.songQueue = songQueue;
        this.context = context;
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
        Song song = songQueue.get(i);

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

        SongViewHolder self = this;

        public SongViewHolder(View itemView) {
            super(itemView);
            songName = (TextView) itemView.findViewById(R.id.songName);
            artist = (TextView) itemView.findViewById(R.id.artist);
            albumArt = (ImageView) itemView.findViewById(R.id.albumArt);
            plusSign = (ImageView) itemView.findViewById(R.id.addSongPlus);
            progressBar = (ProgressBar) itemView.findViewById(R.id.progressBar);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                }
            });

            plusSign.setVisibility(View.INVISIBLE);
        }
    }
}

