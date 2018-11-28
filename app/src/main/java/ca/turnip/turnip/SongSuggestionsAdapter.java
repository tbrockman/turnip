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

public class SongSuggestionsAdapter
        extends RecyclerView.Adapter<SongSuggestionsAdapter.SongViewHolder> {

    private ArrayList<Song> suggestions;

    public SongSuggestionsAdapter(ArrayList<Song> suggestions) { this.suggestions = suggestions; }

//    private static final String TAG = SongSuggestionsAdapter.class.getSimpleName();
////    @Override
////    public void onBindSuggestionHolder(Song suggestion, SuggestionHolder holder, int position) {
////
////        holder.songName.setText(suggestion.getString("name"));
////        holder.artist.setText(TextUtils.join(", ", suggestion.getArtists()));
////
////        try {
////            if (!suggestion.hasAlbumArt()) {
////                JSONObject album = suggestion.get("album");
////                JSONArray albumImages = album.getJSONArray("images");
////                JSONObject imageInfoJSON = albumImages.getJSONObject(albumImages.length()-1);
////                String albumArtUrl = imageInfoJSON.getString("url");
//                Thread retrieveAlbumArt = new Thread(new RetrieveAlbumArtThread(suggestion,
//                                                                                albumArtUrl,
//                                                                                holder.albumArt));
//                retrieveAlbumArt.start();
//            }
//        } catch (JSONException e) {
//            e.printStackTrace();
//        }
//    }

    @NonNull
    @Override
    public SongSuggestionsAdapter.SongViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup,
                                                                    int i) {
        View v = LayoutInflater.from(
                viewGroup.getContext()).inflate(R.layout.item_custom_suggestion,
                viewGroup,
                false
        );

        SongSuggestionsAdapter.SongViewHolder vh = new SongSuggestionsAdapter.SongViewHolder(v);

        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder songViewHolder, int i) {

    }

    @Override
    public int getItemCount() {
        return 0;
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

//            itemView.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    listener.songChosen(self.getLayoutPosition());
//                }
//            });
        }
    }
}
