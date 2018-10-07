package ca.passtheaux.passtheaux;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

public class SongQueueAdapter extends RecyclerView.Adapter<SongQueueAdapter.SongViewHolder> {

    private ArrayList<Song> songQueue;

    public SongQueueAdapter(ArrayList<Song> songQueue) {
        this.songQueue = songQueue;
    }

    @NonNull
    @Override
    public SongQueueAdapter.SongViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View v = LayoutInflater.from(
                viewGroup.getContext()).inflate(R.layout.item_custom_suggestion,
                viewGroup,
                false
        );

        SongQueueAdapter.SongViewHolder vh = new SongQueueAdapter.SongViewHolder(v);

        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull SongQueueAdapter.SongViewHolder songViewHolder, int i) {
        // SongViewHolder.
        Song song = songQueue.get(i);
        songViewHolder.songName.setText(song.getString("name"));
        songViewHolder.artist.setText(TextUtils.join(",", song.getArtists()));
    }

    @Override
    public int getItemCount() {
        return songQueue.size();
    }

    public class SongViewHolder extends RecyclerView.ViewHolder {

        TextView songName;
        TextView artist;
        ImageView albumArt;

        SongViewHolder self = this;

        public SongViewHolder(View itemView) {
            super(itemView);
            songName = (TextView) itemView.findViewById(R.id.songName);
            artist = (TextView) itemView.findViewById(R.id.artist);
            albumArt = (ImageView) itemView.findViewById(R.id.albumArt);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                }
            });
        }
    }

}
