package ca.passtheaux.passtheaux;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;

import com.mancj.materialsearchbar.MaterialSearchBar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class QueueSong extends AppCompatActivity {

    private static final String TAG = QueueSong.class.getSimpleName();

    // UI

    private MaterialSearchBar searchBar;
    LayoutInflater inflater;
    CustomSuggestionsAdapter customSuggestionsAdapater;

    // Search results

    private ArrayList<Song> songs = new ArrayList<>();

    // Network

    private ConnectionService connectionService;
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            connectionService = ((ConnectionService.LocalBinder)service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            connectionService = null;
        }
    };

    // Search result click listener

    private SongClickedCallback songClickedCallback = new SongClickedCallback() {
        @Override
        public void songChosen(int pos) {
            Intent data = new Intent();
            Song result = songs.get(pos);
            data.putExtra("song", result.toString());
            data.putExtra("type", "spotify");
            setResult(RESULT_OK, data);
            finish();
        }
    };

    private Callback spotifySearchCallback = new Callback() {

        @Override
        public void onFailure(Call call, IOException e) {

        }

        @Override
        public void onResponse(Call call, final Response response) throws IOException {

            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    try {
                        final JSONObject jsonResponse = new JSONObject(response.body().string());
                        final JSONArray jsonArray = jsonResponse.getJSONObject("tracks")
                                                                .getJSONArray("items");
                        songs.clear();
                        for (int i = 0; i < jsonArray.length(); i++) {
                            Song song = new SpotifySong(jsonArray.getJSONObject(i));
                            songs.add(song);
                        }
                        inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
                        customSuggestionsAdapater = new CustomSuggestionsAdapter(inflater,
                                                                                 songClickedCallback);
                        searchBar.setCustomSuggestionAdapter(customSuggestionsAdapater);
                        customSuggestionsAdapater.setSuggestions(songs);
                        searchBar.showSuggestionsList();
                    } catch (JSONException e) {
                        Log.e(TAG, "Error converting search response body to JSON.");
                    } catch (IOException e) {
                        Log.e(TAG, e.toString());
                    }
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_queue_song);
        bindConnectionService();

        searchBar = (MaterialSearchBar) findViewById(R.id.searchBar);
        searchBar.addTextChangeListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                connectionService.searchSpotifyAPI(searchBar.getText(),
                                              "track",
                                                    spotifySearchCallback);
            }

            @Override
            public void afterTextChanged(Editable editable) {}
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(connection);
    }

    private void bindConnectionService() {
        Intent serviceIntent = new Intent(this, ConnectionService.class);
        bindService(serviceIntent,
                connection,
                Context.BIND_AUTO_CREATE);
    }

    protected interface SongClickedCallback {
        public void songChosen(int pos);
    }
}
