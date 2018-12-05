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
import android.widget.TextView;

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
    public void onBindViewHolder(@NonNull SongQueueAdapter.SongViewHolder songViewHolder, int i) {
        // SongViewHolder.
        int albumArtImageViewId = songViewHolder.albumArt.getId();
        Song song = songQueue.get(i);

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
                                                                         songViewHolder.albumArt);
                    Bitmap placeholder = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_logo_svg);
                    final AsyncDrawable asyncDrawable =
                            new AsyncDrawable(context.getResources(), placeholder, task);
                    songViewHolder.albumArt.setImageDrawable(asyncDrawable);
                    task.execute(songViewHolder.albumArt.getId());
                }

            }
            else {
                songViewHolder.albumArt.setImageBitmap(song.getAlbumArt());
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

    static class AsyncDrawable extends BitmapDrawable {
        private final WeakReference<RetrieveAlbumArtTask> retrieveAlbumArtTaskReference;

        public AsyncDrawable(Resources res, Bitmap bitmap,
                             RetrieveAlbumArtTask retrieveAlbumArtTask) {
            super(res, bitmap);
            retrieveAlbumArtTaskReference =
                    new WeakReference<RetrieveAlbumArtTask>(retrieveAlbumArtTask);
        }

        public RetrieveAlbumArtTask getRetrieveAlbumArtTask() {
            return retrieveAlbumArtTaskReference.get();
        }
    }

    static boolean cancelPotentialWork(int id, ImageView imageView) {
        final RetrieveAlbumArtTask retrieveAlbumArtTask = getRetrieveAlbumArtTask(imageView);
        if (retrieveAlbumArtTask != null) {
            final int resId = retrieveAlbumArtTask.resId;
            if (id != resId) {
                retrieveAlbumArtTask.cancel(true);
            }
            else {
                return false;
            }
        }
        return true;
    }

    static RetrieveAlbumArtTask getRetrieveAlbumArtTask(ImageView imageView) {
        if (imageView != null) {
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof AsyncDrawable) {
                final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
                return asyncDrawable.getRetrieveAlbumArtTask();
            }
        }
        return null;
    }
}

