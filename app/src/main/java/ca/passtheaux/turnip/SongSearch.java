package ca.passtheaux.turnip;

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
import java.util.Timer;
import java.util.TimerTask;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class SongSearch extends AppCompatActivity {

    private static final String TAG = SongSearch.class.getSimpleName();

    // UI

    private MaterialSearchBar searchBar;
    private LayoutInflater inflater;
    private SongSuggestionsAdapter customSuggestionsAdapter;

    // Search results

    private ArrayList<Song> songs;

    // Search delay

    private Timer searchTimer;
    private TimerTask searchTask;
    private static final int searchDelay = 300;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_queue_song);
        bindConnectionService();

        songs = new ArrayList<>();
        searchTimer = new Timer();
        searchBar = (MaterialSearchBar) findViewById(R.id.searchBar);
        searchBar.addTextChangeListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (searchTask != null) {
                    searchTask.cancel();

                }
                searchTask = new TimerTask() {
                    @Override
                    public void run() {
                        connectionService.searchSpotifyAPI(searchBar.getText(),
                                                          "track",
                                                           spotifySearchCallback);
                    }
                };
                searchTimer.schedule(searchTask, searchDelay);
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
                        customSuggestionsAdapter = new SongSuggestionsAdapter(inflater,
                                                                              songClickedCallback);
                        customSuggestionsAdapter.setSuggestions(songs);
                        searchBar.setCustomSuggestionAdapter(customSuggestionsAdapter);
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

    private void bindConnectionService() {
        Intent serviceIntent = new Intent(this, ConnectionService.class);
        bindService(serviceIntent,
                    connection,
                    Context.BIND_AUTO_CREATE);
    }

    protected interface SongClickedCallback {
        void songChosen(int pos);
    }
}
