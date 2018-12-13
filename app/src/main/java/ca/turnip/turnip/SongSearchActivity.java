package ca.turnip.turnip;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.speech.RecognizerIntent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class SongSearchActivity extends AppCompatActivity {

    private static final String TAG = SongSearchActivity.class.getSimpleName();
    private static final int SPEECH_REQUEST_CODE = 0;
    private Context context = this;

    // UI

    private RecyclerView.LayoutManager songSearchLayoutManager;
    private RecyclerView songSearchResultsRecyclerView;
    private EditText searchEditText;
    private SongSearchResultsAdapter songSearchResultsAdapter;
    private Toolbar toolbar;

    // Search results

    private boolean newSearch = false;
    private boolean isLoading = false;
    private ArrayList<Song> songs;
    private String nextUrl;

    // Search delay

    private Timer searchTimer;
    private TimerTask searchTask;
    private static final int searchDelay = 300;

    // Network

    private BackgroundService backgroundService;
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            backgroundService = ((BackgroundService.LocalBinder)service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            backgroundService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_song_search);
        bindConnectionService();

        songs = new ArrayList<>();
        searchTimer = new Timer();

        songSearchResultsRecyclerView = findViewById(R.id.searchRecyclerView);
        songSearchLayoutManager = new LinearLayoutManager(this);
        songSearchResultsAdapter = new SongSearchResultsAdapter(songs, this, songClickedCallback);
        songSearchResultsRecyclerView.setHasFixedSize(true);
        songSearchResultsRecyclerView.setAdapter(songSearchResultsAdapter);
        songSearchResultsRecyclerView.setLayoutManager(songSearchLayoutManager);
        songSearchResultsRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);

                if (!songSearchResultsRecyclerView.canScrollVertically(1)) {
                    if (nextUrl != null && !isLoading) {
                        isLoading = true;
                        newSearch = false;
                        songs.add(null);
                        songSearchResultsAdapter.notifyItemInserted(songs.size()-1);
                        songSearchResultsRecyclerView.scrollToPosition(songs.size() - 1);
                        backgroundService.getSpotifyUrl(nextUrl, spotifySearchCallback);
                    }
                }
            }
        });

        toolbar = findViewById(R.id.searchToolbar);

        if (toolbar != null) {
            searchEditText = (EditText) toolbar.findViewById(R.id.searchEditText);
            // Search text changed
            searchEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    final String search = charSequence.toString();

                    if (charSequence.length() > 0) {
                        if (searchTask != null) {
                            searchTask.cancel();

                        }
                        searchTask = new TimerTask() {
                            @Override
                            public void run() {
                                searchSpotify(search);
                            }
                        };
                        searchTimer.schedule(searchTask, searchDelay);
                    }
                }

                @Override
                public void afterTextChanged(Editable editable) {

                }
            });
            // Search text submit
            searchEditText.setOnEditorActionListener(new EditText.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        String search = searchEditText.getText().toString();
                        searchSpotify(search);
                    }
                    return true;
                }
            });

            setSupportActionBar(toolbar);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            toolbar.inflateMenu(R.menu.spotify_search_menu);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate( R.menu.spotify_search_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                finish();
                return true;
            case R.id.action_voice_search:
                displaySpeechRecognizer();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK) {
            List<String> results = data.getStringArrayListExtra(
                    RecognizerIntent.EXTRA_RESULTS);
            String spokenText = results.get(0);
            searchEditText.setText(spokenText);
            searchSpotify(spokenText);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(connection);
    }

    private void searchSpotify(String search) {
        newSearch = true;
        backgroundService.searchSpotifyAPI(search, "track", spotifySearchCallback);
    }

    private void displaySpeechRecognizer() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        startActivityForResult(intent, SPEECH_REQUEST_CODE);
    }

    // Search result click listener

    private SongClickedCallback songClickedCallback = new SongClickedCallback() {
        @Override
        public void songChosen(Song result) {
            Intent data = new Intent();
            data.putExtra("song", result.toString());
            data.putExtra("type", "spotify");
            setResult(RESULT_OK, data);
            finish();
        }
    };

    private Callback spotifySearchCallback = new Callback() {

        @Override
        public void onFailure(Call call, IOException e) {
            isLoading = false;
        }

        @Override
        public void onResponse(Call call, final Response response) throws IOException {
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    try {
                        final JSONObject jsonResponse = new JSONObject(response.body().string());
                        final JSONObject tracks = jsonResponse.getJSONObject("tracks");
                        final JSONArray jsonArray = tracks.getJSONArray("items");

                        if (tracks.has("next")) {
                            nextUrl = tracks.getString("next");
                        }
                        else {
                            nextUrl = null;
                        }

                        if (newSearch) {
                            songs.clear();
                            songSearchResultsAdapter.notifyDataSetChanged();
                        }

                        // Remove progress bar element from dataset
                        if (songs.size() > 0) {
                            songs.remove(songs.size()-1);
                            songSearchResultsAdapter.notifyItemRemoved(songs.size()-1);
                        }

                        for (int i = 0; i < jsonArray.length(); i++) {
                            Song song = new SpotifySong(jsonArray.getJSONObject(i));
                            songs.add(song);
                            Log.i(TAG, song.getString("name"));
                        }

                        //songSearchResultsAdapter.notifyItemRangeInserted(start, songs.size());
                        // TODO: figure out why only notifyDataSetChanged works here
                        songSearchResultsAdapter.notifyDataSetChanged();
                        isLoading = false;
                    } catch (JSONException e) {
                        Log.e(TAG, "Error converting search response body to JSON: " + e.toString());
                    } catch (IOException e) {
                        Log.e(TAG, e.toString());
                    }
                }
            });
        }
    };

    private void bindConnectionService() {
        Intent serviceIntent = new Intent(this, BackgroundService.class);
        bindService(serviceIntent,
                    connection,
                    Context.BIND_AUTO_CREATE);
    }

    protected interface SongClickedCallback {
        void songChosen(Song result);
    }
}
