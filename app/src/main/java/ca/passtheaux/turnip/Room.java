package ca.passtheaux.turnip;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;


public class Room extends AppCompatActivity {

    static final int ADD_SONG_REQUEST = 1;
    private static final String TAG = Room.class.getSimpleName();

    // Context

    Context context = this;

    // UI elements

    private FloatingActionButton fab;
    private LinearLayoutManager layoutManager;
    private LinearLayout noSongsContainer;
    private LinearLayout currentSongContainer;
    private RecyclerView recyclerView;
    private SongQueueAdapter adapter;
    private AppCompatImageView skipButton;
    private TextView artist;
    private TextView songName;
    private TextView songTime;
    private ImageView albumArt;
    private ProgressBar timeProgressBar;

    // Currently playing data

    private int songLength; // in seconds
    private int timeElapsed; // in seconds
    private Song currentlyPlaying;
    private final Handler songTimerHandler = new Handler();
    private final Runnable songTimer =  new Runnable() {
        @Override
        public void run() {
            timeElapsed++;
            renderCurrentlyPlayingProgress();
            songTimerHandler.postDelayed(this, 1000);
        }
    };

    // Song queue data

    private ArrayList<Song> songQueue;

    // Intent extras

    private boolean isHost;
    private int expiresIn;
    private String roomName;
    private String roomPassword;
    private String spotifyToken;
    private String endpointId;

    // Network communication

    private ConnectionService connectionService;
    private ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            connectionService = ((ConnectionService.LocalBinder)service).getService();

            if (connectionService != null) {
                connectionService.setUpJukebox(isHost);

                if (isHost) {
                    connectionService.setSpotifyAccessToken(spotifyToken);
                    connectionService.startAdvertising(roomName);
                }
                else {
                    connectionService.connectToRoom(endpointId);
                }
                connectionService.subscribeRoomNetwork(songQueueListener);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "disconnected from service");
            connectionService = null;
        }
    };

    // Network listener

    private ConnectionService.RoomNetworkListener songQueueListener =
        new ConnectionService.RoomNetworkListener() {
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
                songTimerHandler.removeCallbacks(songTimer);
                if (song.has("timeElapsed")) {
                    try {
                        timeElapsed = Integer.valueOf(song.getString("timeElapsed"));
                    } catch(Exception e) {
                        timeElapsed = 0;
                    }
                }
                songLength = Integer.parseInt(song.getString("duration_ms")) / 1000;
                songTimerHandler.postDelayed(songTimer, 1000);
                renderCurrentlyPlaying();
                renderSongQueueEmpty();
            }

            //TODO: implement network method for pause/resume song

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

    // Currently playing

    private void renderCurrentlyPlaying() {
        if (currentlyPlaying != null) {
            if (songQueue.size() == 0) {
                skipButton.setVisibility(View.GONE);
            }
            else {
                skipButton.setVisibility(View.VISIBLE);
            }
            artist.setText(TextUtils.join(", ", currentlyPlaying.getArtists()));
            albumArt.setImageBitmap(currentlyPlaying.getAlbumArt());
            songName.setText(currentlyPlaying.getString("name"));
            currentSongContainer.setVisibility(View.VISIBLE);
            renderCurrentlyPlayingProgress();
        }
        else {
            currentSongContainer.setVisibility(View.GONE);
        }
    }

    private void renderCurrentlyPlayingProgress() {
        songTime.setText(secondsToString(timeElapsed));
        timeProgressBar.setProgress((int) Math.ceil(timeElapsed * 100 / songLength));
    }

    private void renderSongQueueEmpty() {
        if (currentlyPlaying == null) {
            noSongsContainer.setVisibility(View.VISIBLE);
        }
        else {
            noSongsContainer.setVisibility(View.GONE);
        }
    }

    public String secondsToString(int seconds) {
        return String.format(Locale.US, "%01d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room);

        bindConnectionService();

        Intent intent = getIntent();
        isHost = intent.getBooleanExtra("isHost", false);
        roomName = intent.getStringExtra("roomName");
        roomPassword = intent.getStringExtra("roomPassword");
        endpointId = intent.getStringExtra("endpointId");
        spotifyToken = intent.getStringExtra("spotifyToken");
        expiresIn = intent.getIntExtra("expiresIn", 60 * 60 * 2);

        setTitle(roomName);

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
                    connectionService.skipCurrentSong();
                }
                else {
                    // TODO: vote to skip
                }
            }
        });

        songQueue = new ArrayList();
        layoutManager = new LinearLayoutManager(this);
        adapter = new SongQueueAdapter(songQueue);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        renderCurrentlyPlaying();
        renderSongQueueEmpty();
    }

    @Override
    public void onDestroy() {
        if (this.isHost) {
            Log.i(TAG, "Calling onDestroy here");
            connectionService.stopAdvertising();
        }
        Log.i(TAG, "calling Room on destroy now");
        songTimerHandler.removeCallbacks(songTimer);
        connectionService.unsubscribeRoomNetwork();
        connectionService.destroyRoom();
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
                        JSONObject jsonSong = new JSONObject(jsonStringSong);

                        if (type.equals("spotify")) {
                            song = new SpotifySong(jsonSong);
                        }

                        if (song != null) {
                            if (isHost) {
                                // Add to local song queue and emit to all clients
                                connectionService.enqueueSong(song);
                            }
                            else {
                                // Send to server
                                connectionService.addSong(song);
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
            i = new Intent(this, Host.class);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            i.putExtra("isHost", isHost);
            i.putExtra("roomName", roomName);
            i.putExtra("roomPassword", roomPassword);
            i.putExtra("spotifyToken", spotifyToken);
            i.putExtra("expiresIn", expiresIn);
        } else {
            i = new Intent(this, RoomList.class);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        }

        return i;
    }

    private void startSongAddActivity() {
        Intent queueSong = new Intent(this, SongSearch.class);
        startActivityForResult(queueSong, ADD_SONG_REQUEST);
    }

    private void bindConnectionService() {
        Intent serviceIntent = new Intent(this, ConnectionService.class);
        bindService(serviceIntent,
                    connection,
                    Context.BIND_AUTO_CREATE);
    }
}
