package ca.turnip.turnip;

import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;

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

public class SongSearchActivity extends AppCompatActivity {

    private static final String TAG = SongSearchActivity.class.getSimpleName();
    private Context context = this;

    // UI

    private LayoutInflater inflater;
    private RecyclerView.LayoutManager songSearchLayoutManager;
    private RecyclerView songSearchResultsRecyclerView;
    private SearchView searchView;
    private SongSearchResultsAdapter songSearchResultsAdapter;
    private Toolbar toolbar;


    // Search results

    private ArrayList<Song> songs;

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

        toolbar = findViewById(R.id.searchToolbar);

        if (toolbar != null) {
            setSupportActionBar(toolbar);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            toolbar.inflateMenu(R.menu.search_activity_menu);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate( R.menu.search_activity_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.search_bar);
        searchView = (SearchView) searchItem.getActionView();
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setIconified(false);
        searchView.setMaxWidth( Integer.MAX_VALUE );
        searchView.setOnQueryTextListener(
            new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String s) {
                    backgroundService.searchSpotifyAPI(s,
                            "track",
                            spotifySearchCallback);
                    return true;
                }

                @Override
                public boolean onQueryTextChange(final String s) {

                    if (searchTask != null) {
                        searchTask.cancel();

                    }
                    searchTask = new TimerTask() {
                        @Override
                        public void run() {
                            if (s.length() > 0) {
                                backgroundService.searchSpotifyAPI(s,
                                        "track",
                                        spotifySearchCallback);
                            }
                        }
                    };
                    searchTimer.schedule(searchTask, searchDelay);
                    return true;
                }
            }
        );

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            searchView.setQuery(String.valueOf(query), false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(connection);
    }

    // Process intents

    // Search result click listener

    private SongClickedCallback songClickedCallback = new SongClickedCallback() {
        @Override
        public void songChosen(Song result) {
            Intent data = new Intent();
            //Song result = songs.get(pos);
            data.putExtra("song", result.toString());
            data.putExtra("type", "spotify");
            data.putExtra("albumArt", result.getAlbumArt());
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
                            Log.i(TAG, "Song: " + song.toString());
                        }

                        songSearchResultsAdapter.notifyDataSetChanged();
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
        Intent serviceIntent = new Intent(this, BackgroundService.class);
        bindService(serviceIntent,
                    connection,
                    Context.BIND_AUTO_CREATE);
    }

    protected interface SongClickedCallback {
        void songChosen(Song result);
    }
}
