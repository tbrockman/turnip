package ca.turnip.turnip;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.os.Bundle;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;


public class RoomActivity extends SpotifyAuthenticatedActivity {

    static final int ADD_SONG_REQUEST = 1;
    private static final String TAG = RoomActivity.class.getSimpleName();

    // Context

    Context context = this;

    // UI elements

    private FloatingActionButton fab;
    private LinearLayoutManager layoutManager;
    private LinearLayout noSongsContainer;
    private LinearLayout currentSongContainer;
    private RecyclerView recyclerView;
    private SongQueueAdapter adapter;
    private FrameLayout loadingRoomSpinnerLayout;

    // Currently playing

    private AppCompatImageView skipButton;
    private TextView artist;
    private TextView songName;
    private TextView songTime;
    private ImageView albumArt;
    private ProgressBar timeProgressBar;
    private ProgressBar albumArtSpinner;

    // Currently playing data

    private int songLength = 0; // in seconds
    private int timeElapsed; // in seconds
    private Song currentlyPlaying;

    // Song queue data

    private ArrayList<Song> songQueue;

    // Intent extras

    private String roomName;
    private String roomPassword;
    private Boolean isHost;

    private String endpointId;

    // Network listener

    private RoomJukeboxListener roomJukeboxListener =
        new RoomJukeboxListener() {
            @Override
            public void onSongAdded(Song song) {
                songQueue.add(song);
                adapter.notifyItemInserted(songQueue.size() - 1);
                renderCurrentlyPlaying();
                renderSongQueueEmpty();
            }

            @Override
            public void onSongRemoved(Song song) {
                int index = songQueue.indexOf(song);
                if (index > -1) {
                    songQueue.remove(index);
                    adapter.notifyItemRemoved(index);
                }
                renderCurrentlyPlaying();
                renderSongQueueEmpty();
            }

            @Override
            public void onSongPlaying(Song song) {
                currentlyPlaying = song;
                timeElapsed = Integer.parseInt(currentlyPlaying.getString("timeElapsed"));
                songLength = Integer.parseInt(currentlyPlaying.getString("duration_ms")) / 1000;
                renderCurrentlyPlaying();
                renderSongQueueEmpty();
            }

            @Override
            public void onSongPaused(int time) {
                timeElapsed = time;
            }

            @Override
            public void onSongResumed(int time) {
                timeElapsed = time;
            }

            @Override
            public void onSongTick(int time) {
                timeElapsed = time;
                renderCurrentlyPlayingProgress();
            }

            //TODO: implement network method for pause/resume song
            @Override
            public void onConnect() {
                renderSongQueueEmpty();
            }

            @Override
            public void onDisconnect() {
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        finish();
                    }
                });
                builder.setCancelable(false);
                builder.setTitle("Disconnected");
                builder.setMessage("Lost connection to host.");
                builder.create();
                builder.show();
            }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room);

        if (savedInstanceState == null) {
            bindRoomActivityConnectionService();

            Intent intent = assignIntentVariables();

            if (spotifyEnabled && isHost) {
                initializeSpotifyAuthentication(intent);
            }

            setTitle(roomName);

            assignViewElementVariables();

            fab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startSongAddActivity();
                }
            });

            skipButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (isHost) {
                        backgroundService.skipCurrentSong();
                    }
                    else {
                        // TODO: vote to skip
                    }
                }
            });

            songQueue = new ArrayList();
            layoutManager = new LinearLayoutManager(this);
            adapter = new SongQueueAdapter(songQueue, this);
            recyclerView.setLayoutManager(layoutManager);
            recyclerView.setAdapter(adapter);

            renderCurrentlyPlaying();
            renderSongQueueEmpty();
        }
    }

    // Currently playing

    private void renderCurrentlyPlaying() {
        if (currentlyPlaying != null) {
            if (songQueue.size() == 0) {
                skipButton.setVisibility(View.GONE);
            }
            else {
                skipButton.setVisibility(View.VISIBLE);
            }

            if (currentlyPlaying.hasAlbumArt()) {
                albumArt.setImageBitmap(currentlyPlaying.getAlbumArt());
                albumArtSpinner.setVisibility(View.GONE);
                albumArt.setVisibility(View.VISIBLE);
            }
            else {
                String albumArtUrl = null;
                try {
                    albumArtUrl = currentlyPlaying.getAlbumArtURL();
                } catch(JSONException e) {
                  Log.e(TAG, e.toString());
                } finally {
                    if (albumArtUrl != null) {
                        RetrieveAlbumArtTask task = new RetrieveAlbumArtTask(currentlyPlaying,
                                                                             albumArtUrl,
                                                                             albumArt,
                                                                             albumArtSpinner);
                        Bitmap placeholder = BitmapFactory.decodeResource(context.getResources(),
                                                                          R.drawable.ic_logo_svg);
                        final SongQueueAdapter.AsyncDrawable asyncDrawable =
                                new SongQueueAdapter.AsyncDrawable(context.getResources(),
                                                                   placeholder,
                                                                   task);
                        albumArt.setImageDrawable(asyncDrawable);
                        task.execute();
                    }
                }
            }

            artist.setText(TextUtils.join(", ", currentlyPlaying.getArtists()));
            songName.setText(currentlyPlaying.getString("name"));
            currentSongContainer.setVisibility(View.VISIBLE);
            renderCurrentlyPlayingProgress();
        }
        else {
            currentSongContainer.setVisibility(View.GONE);
        }
    }

    private void renderCurrentlyPlayingProgress() {
        if (timeElapsed >= 0) {
            songTime.setText(secondsToString(timeElapsed));
        }
        if (songLength > 0) {
            timeProgressBar.setProgress((int) Math.ceil(timeElapsed * 100 / songLength));
        }
    }

    private void renderSongQueueEmpty() {
        if (currentlyPlaying == null) {
            if (isHost ||
                (backgroundService != null && backgroundService.getSpotifyAccessToken() != null)) {
                noSongsContainer.setVisibility(View.VISIBLE);
                loadingRoomSpinnerLayout.setVisibility(View.GONE);
            }
        }
        else {
            loadingRoomSpinnerLayout.setVisibility(View.GONE);
            noSongsContainer.setVisibility(View.GONE);
        }
    }

    public String secondsToString(int seconds) {
        return String.format(Locale.US, "%01d:%02d", seconds / 60, seconds % 60);
    }

    private void initializeSpotifyAuthentication(Intent intent) {
        spotifyAccessToken = intent.getStringExtra("spotifyAccessToken");
        spotifyRefreshToken = intent.getStringExtra("spotifyRefreshToken");
        spotifyExpiresIn = intent.getIntExtra("spotifyExpiresIn", 60 * 60 * 2);
        spotifyTimerHandler.postDelayed(spotifyRefreshTokenTimer, 1000);
    }

    private void bindRoomActivityConnectionService() {
        connection = new ServiceConnection() {

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                backgroundService = ((BackgroundService.LocalBinder)service).getService();

                if (backgroundService != null) {
                    backgroundService.setUpJukebox(isHost);

                    if (isHost) {
                        backgroundService.startAdvertising(roomName);
                    }
                    else {
                        backgroundService.connectToRoom(endpointId);
                    }
                    backgroundService.subscribeRoomJukeboxListener(roomJukeboxListener);
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.i(TAG, "disconnected from service");
                backgroundService = null;
            }
        };
        bindConnectionService(this);
    }

    @NonNull
    private Intent assignIntentVariables() {
        Intent intent = getIntent();
        isHost = intent.getBooleanExtra("isHost", false);
        roomName = intent.getStringExtra("roomName");
        roomPassword = intent.getStringExtra("roomPassword");
        endpointId = intent.getStringExtra("endpointId");
        spotifyEnabled = intent.getBooleanExtra("spotifyEnabled", false);
        return intent;
    }

    private void assignViewElementVariables() {
        recyclerView = findViewById(R.id.roomRecyclerView);
        currentSongContainer = findViewById(R.id.currentSongContainer);
        noSongsContainer = findViewById(R.id.noSongsContainer);
        fab =  findViewById(R.id.fab);
        artist = findViewById(R.id.artist);
        albumArt = findViewById(R.id.albumArt);
        songName = findViewById(R.id.songName);
        songTime = findViewById(R.id.songTime);
        skipButton = findViewById(R.id.skipButton);
        timeProgressBar = findViewById(R.id.timeElapsed);
        albumArtSpinner = findViewById(R.id.albumArtSpinner);
        loadingRoomSpinnerLayout = findViewById(R.id.loadingRoomSpinnerLayout);
    }

    @Override
    public void onDestroy() {
        if (this.isHost) {
            Log.i(TAG, "Calling onDestroy here");
            backgroundService.stopAdvertising();
        }
        Log.i(TAG, "calling RoomActivity on destroy now");
        backgroundService.unsubscribeRoomJukeboxListener();
        backgroundService.destroyRoom();
        unbindService(connection);
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ADD_SONG_REQUEST) {
            if (resultCode == RESULT_OK) {
                try {
                    Song song = null;

                    if (data.hasExtra("song")) {
                        String jsonStringSong = data.getStringExtra("song");
                        String type = data.getStringExtra("type");
                        Bitmap albumArt = data.getParcelableExtra("albumArt");
                        JSONObject jsonSong = new JSONObject(jsonStringSong);

                        if (type.equals("spotify")) {
                            song = new SpotifySong(jsonSong);
                            song.setAlbumArt(albumArt);
                        }

                        if (song != null) {
                            if (isHost) {
                                // Add to local song queue and emit to all clients
                                backgroundService.enqueueSong(song);
                            }
                            else {
                                // Send to server
                                backgroundService.addSong(song);
                            }
                        }
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Error converting JSON string to JSON.");
                }
            }
        }
    }

    @Override
    public Intent getSupportParentActivityIntent() { return getParentActivityIntentImpl(); }

    @Override
    public Intent getParentActivityIntent() { return getParentActivityIntentImpl(); }

    private Intent getParentActivityIntentImpl() {
        Intent i = null;
        Intent intent = getIntent();
        boolean isHost = intent.getBooleanExtra("isHost", false);

        if (isHost) {
            i = new Intent(this, HostActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            i.putExtra("isHost", isHost);
            i.putExtra("roomName", roomName);
            i.putExtra("roomPassword", roomPassword);
            i.putExtra("spotifyAccessToken", spotifyAccessToken);
            i.putExtra("spotifyExpiresIn", spotifyExpiresIn);
        } else {
            i = new Intent(this, RoomListActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        }

        return i;
    }

    private void startSongAddActivity() {
        Intent queueSong = new Intent(this, SongSearchActivity.class);
        startActivityForResult(queueSong, ADD_SONG_REQUEST);
    }
}
