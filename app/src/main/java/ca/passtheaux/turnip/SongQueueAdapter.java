package ca.passtheaux.turnip;

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
        songViewHolder.artist.setText(TextUtils.join(", ", song.getArtists()));
        songViewHolder.albumArt.setImageBitmap(song.getAlbumArt());
        try {
            if (!song.hasAlbumArt()) {
                JSONObject album = song.get("album");
                JSONArray albumImages = album.getJSONArray("images");
                JSONObject imageInfoJSON = albumImages.getJSONObject(albumImages.length()-1);
                String albumArtUrl = imageInfoJSON.getString("url");
                Thread retrieveAlbumArt = new Thread(
                                             new RetrieveAlbumArtThread(song,
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
        return songQueue.size();
    }

    public class SongViewHolder extends RecyclerView.ViewHolder {

        TextView songName;
        TextView artist;
        ImageView albumArt;
        ImageView plusSign;

        SongViewHolder self = this;

        public SongViewHolder(View itemView) {
            super(itemView);
            songName = (TextView) itemView.findViewById(R.id.songName);
            artist = (TextView) itemView.findViewById(R.id.artist);
            albumArt = (ImageView) itemView.findViewById(R.id.albumArt);
            plusSign = (ImageView) itemView.findViewById(R.id.addSongPlus);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                }
            });

            plusSign.setVisibility(View.INVISIBLE);
        }
    }

}
