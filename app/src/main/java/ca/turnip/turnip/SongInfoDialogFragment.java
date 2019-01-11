package ca.turnip.turnip;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.DialogFragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import org.json.JSONException;
import org.json.JSONObject;

public class SongInfoDialogFragment extends DialogFragment {

    private static final String TAG = SongInfoDialogFragment.class.getSimpleName();

    Song song;

    // UI Elements
    private ConstraintLayout songAlbumArtPlaceholder;
    private ImageView songAlbumArt;
    private LinearLayout shareLinearLayout;
    private LinearLayout openInSpotifyLinearLayout;
    private TextView songAlbumText;
    private TextView songArtistText;
    private TextView songNameText;

    public static SongInfoDialogFragment newInstance(Song song) {
        SongInfoDialogFragment frag = new SongInfoDialogFragment();
        Bundle bundle = new Bundle();
        bundle.putString("song", song.toString());
        bundle.putString("type", song.getSongType());
        frag.setArguments(bundle);
        return frag;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        String type = args.getString("type");
        song = null;
        if (type.equals("spotify")) {
            try {
                song = new SpotifySong(new JSONObject(args.getString("song")));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.song_info_dialog, container, false);
        assignViewElementVariables(view);
        Log.d(TAG, "creating new dialog for song: " + song.toString());

        if (song != null) {
            try {
                Picasso.get()
                       .load(song.getAlbumArtURL("medium"))
                       .fit()
                       .into(songAlbumArt, new Callback() {

                    @Override
                    public void onSuccess() {
                        if (songAlbumArtPlaceholder != null) {
                            songAlbumArtPlaceholder.setVisibility(View.GONE);
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                    }
                });
            } catch (JSONException e) {
                e.printStackTrace();
            }
            songAlbumText.setText(song.getAlbumName());
            songNameText.setText(song.getString("name"));
            songArtistText.setText(TextUtils.join(", ", song.getArtists()));
        }

        shareLinearLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //TODO:
                String message = "\"" + song.getSongTitle() + "\" by " + song.getArtistsAsString() + ". Listen on " + song.getSongTypeName() + ": " + song.getExternalLink();
                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, message);
                sendIntent.setType("text/plain");
                Intent chooser = Intent.createChooser(sendIntent, "Share song");
                startActivity(chooser);
            }
        });

        openInSpotifyLinearLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //TODO:
                Intent openIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(song.getExternalLink()));
                startActivity(openIntent);
            }
        });
        return view;
    }

    private void assignViewElementVariables(View view) {
        songAlbumArtPlaceholder = view.findViewById(R.id.songAlbumArtPlaceholder);
        shareLinearLayout = view.findViewById(R.id.shareLinearLayout);
        openInSpotifyLinearLayout = view.findViewById(R.id.openInSpotifyLinearLayout);
        songAlbumArt = view.findViewById(R.id.songAlbumArt);
        songAlbumText = view.findViewById(R.id.songAlbumText);
        songArtistText = view.findViewById(R.id.songArtistText);
        songNameText = view.findViewById(R.id.songNameText);
    }
}
