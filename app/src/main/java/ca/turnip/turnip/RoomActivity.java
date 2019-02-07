package ca.turnip.turnip;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.ImageViewCompat;
import android.support.v7.app.AlertDialog;
import android.os.Bundle;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

import static ca.turnip.turnip.RoomConfiguration.MAJORITY;
import static ca.turnip.turnip.RoomConfiguration.NO_SKIP;


public class RoomActivity extends BackgroundServiceConnectedActivity {

    private static final String TAG = RoomActivity.class.getSimpleName();

    // Constants

    static final CharSequence connectingToastText = "Connecting to host";
    static final CharSequence voteSkipToastText = "Voting to skip";

    // Context

    Context context = this;
    Activity activity = this;
    Intent hostActivityIntent;

    // UI elements

    private AlertDialog dialog;
    private ErrorDialog spotifyTokenFailure;
    private FloatingActionButton fab;
    private FrameLayout loadingRoomSpinnerLayout;
    private LinearLayoutManager layoutManager;
    private LinearLayout noSongsContainer;
    private LinearLayout currentSongContainer;
    private LinearLayout currentSongLinearLayout;
    private RecyclerView recyclerView;
    private SongQueueAdapter adapter;
    private Toast connectingToast;
    private Toast voteSkipToast;

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

    // Skip mode

    private int skipMode = MAJORITY;

    // State information

    private boolean wasSearching = false;
    private boolean votedToSkip = false;

    // Intent extras

    private String roomName;
    private String roomPassword;
    private Boolean isHost;
    private Boolean spotifyEnabled;
    private String endpointId;

    // Jukebox listener

    private RoomJukeboxListener roomJukeboxListener =
        new RoomJukeboxListener() {
            @Override
            public void onSongAdded(Song song) {
                addSongToQueue(song);
            }

            @Override
            public void onSongRemoved(Song song) {
                int index = songQueue.indexOf(song);
                if (index > -1) {
                    songQueue.remove(index);
                    adapter.notifyItemRemoved(index);
                }
                renderSongQueueEmpty();
                renderCurrentlyPlaying();
            }

            @Override
            public void onSongPlaying(Song song) {
                setCurrentlyPlaying(song);
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
            public void onSpotifyAddedSong(String id, int time) { timeElapsed = time; }

            @Override
            public void onSongTick(int time) {
                timeElapsed = time;
                renderCurrentlyPlayingProgress();
            }

            //TODO: implement network method for pause/resume song
            @Override
            public void onConnect() {
                connectingToast.cancel();
                fillCurrentRoomConfiguration();
                renderSongQueueEmpty();
                renderCurrentlyPlaying();
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

            @Override
            public void onSpotifyDisconnected() {
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        finish();
                    }
                });
                builder.setCancelable(false);
                builder.setTitle("Spotify disconnected");
                builder.setMessage("Lost connection to Spotify.");
                builder.create();
                builder.show();
            }
    };

    // Auth listener

    AuthenticationListener authenticationListener = new AuthenticationListener() {
        @Override
        public void onTokenSuccess() {

        }

        @Override
        public void onTokenFailure() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    spotifyTokenFailure = new ErrorDialog(activity, "Authentication failed", "Client failed to authenticate with Spotify. Possible causes could be network connectivity issues or invalid Spotify credentials.", true);
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room);

        bindRoomActivityConnectionService();

        hostActivityIntent = assignIntentVariables();

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
                    if (skipMode != NO_SKIP && !votedToSkip) {
                        votedToSkip = true;
                        createVoteSkipToast();
                        renderCurrentlyPlaying();
                        backgroundService.voteSkip(currentlyPlaying);
                    }
                }
            }
        });

        currentSongLinearLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (currentlyPlaying != null) {
                    DialogFragment fragment = SongInfoDialogFragment.newInstance(currentlyPlaying);
                    fragment.show(getSupportFragmentManager(), "dialog");
                }
            }
        });

        createLeaveRoomConfirmationDialog();

        if (!isHost) {
            createConnectingToast();
        }

        songQueue = new ArrayList();
        layoutManager = new LinearLayoutManager(this);
        adapter = new SongQueueAdapter(this, getSupportFragmentManager(), songQueue);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        renderSongQueueEmpty();
        renderCurrentlyPlaying();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (backgroundService != null) {
            backgroundService.onRoomResume(this);
            if (!wasSearching) {
                setSongQueue(backgroundService.getSongQueue());
                setCurrentlyPlaying(backgroundService.getCurrentlyPlaying());
            }
        }
        wasSearching = false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                break;
        }
        return true;
    }

    @Override
    public void onBackPressed () {
        // TODO: display toast confirming navigation away
        dialog.show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isFinishing()) {
            if (this.isHost) {
                backgroundService.stopAdvertising();
            }
            Log.d(TAG, "Destroying room.");
            backgroundService.unsubscribeRoomJukeboxListener();
            backgroundService.destroyRoom();
            unbindConnectionService();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == MainActivity.ADD_SONG_REQUEST) {
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

    // Confirm leave room dialog

    private void createLeaveRoomConfirmationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setPositiveButton(R.string.leave_room_positive, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User clicked Leave button
                RoomActivity.super.onBackPressed();
            }
        });
        builder.setNegativeButton(R.string.cancel_button_text, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User clicked Cancel button
            }
        });
        builder.setTitle(R.string.leave_room_title);
        if (isHost) {
            builder.setMessage(R.string.leave_room_text_host);
        }
        else {
            builder.setMessage(R.string.leave_room_text_user);
        }
        dialog = builder.create();
    }

    // Connecting Toast

    private void createConnectingToast() {
        connectingToast = Toast.makeText(this, connectingToastText, Toast.LENGTH_LONG );
        connectingToast.show();
    }

    // Voting to skip Toast
    private void createVoteSkipToast() {
        voteSkipToast = Toast.makeText(this, voteSkipToastText, Toast.LENGTH_SHORT);
        voteSkipToast.show();
    }

    // Currently playing

    private void setCurrentlyPlaying(Song song) {
        if (song != null) {
            votedToSkip = false;
            currentlyPlaying = song;
            timeElapsed = Integer.parseInt(currentlyPlaying.getString("timeElapsed"));
            songLength = Integer.parseInt(currentlyPlaying.getString("duration_ms")) / 1000;
            renderSongQueueEmpty();
            renderCurrentlyPlaying();
        }
    }

    private void renderCurrentlyPlaying() {
        if (currentlyPlaying != null) {
            if (songQueue.size() == 0) {
                skipButton.setVisibility(View.GONE);
            }
            else {
                if (isHost || skipMode != NO_SKIP) {
                    skipButton.setVisibility(View.VISIBLE);
                    if (votedToSkip) {
                        ImageViewCompat.setImageTintList(
                                skipButton,
                                ColorStateList.valueOf(ContextCompat.getColor(context,
                                                                              R.color.spotifyGreen)
                                )
                        );
                    } else {
                        ImageViewCompat.setImageTintList(
                                skipButton,
                                ColorStateList.valueOf(ContextCompat.getColor(context,
                                                                              R.color.white))
                        );
                    }
                }
            }

            String albumArtUrl = null;
            try {
                albumArtUrl = currentlyPlaying.getAlbumArtURL("small");
            } catch(JSONException e) {
              Log.e(TAG, e.toString());
            } finally {
                if (albumArtUrl != null) {
                    albumArtSpinner.setVisibility(View.VISIBLE);
                    albumArt.setVisibility(View.INVISIBLE);
                    Picasso.get().load(albumArtUrl).into(albumArt, new Callback() {

                        @Override
                        public void onSuccess() {
                            albumArt.setVisibility(View.VISIBLE);
                            albumArtSpinner.setVisibility(View.INVISIBLE);
                        }

                        @Override
                        public void onError(Exception e) {

                        }
                    });
                }
                artist.setText(currentlyPlaying.getArtistsAsString());
                songName.setText(currentlyPlaying.getString("name"));
                currentSongContainer.setVisibility(View.VISIBLE);
                renderCurrentlyPlayingProgress();
            }
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

    // Song queue

    private void addSongToQueue(Song song) {
        songQueue.add(song);
        adapter.notifyItemInserted(songQueue.size() - 1);
        renderSongQueueEmpty();
        renderCurrentlyPlaying();
    }

    private void setSongQueue(ArrayList<Song> queue) {
        songQueue.clear();
        for (int i = 0; i < queue.size(); i++) {
            songQueue.add(queue.get(i));
        }
        adapter.notifyDataSetChanged();
        renderSongQueueEmpty();
        renderCurrentlyPlaying();
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

    private void bindRoomActivityConnectionService() {
        connection = new ServiceConnection() {

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                backgroundService = ((BackgroundService.LocalBinder)service).getService();

                if (backgroundService != null) {
                    backgroundService.setUpJukebox(isHost);

                    if (isHost) {
                        backgroundService.setRoomConfiguration(buildCurrentRoomConfiguration());
                        backgroundService.startAdvertising(roomName);
                        if (spotifyEnabled) {
                            backgroundService.subscribeAuthListener(authenticationListener);
                        }
                    }
                    else {
                        backgroundService.connectToRoom(endpointId);
                    }
                    Log.d(TAG, "Binding Room connection service");
                    backgroundService.subscribeRoomJukeboxListener(roomJukeboxListener);
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                backgroundService = null;
            }
        };
        startAndBindConnectionService(this);
    }

    @NonNull
    private Intent assignIntentVariables() {
        Intent intent = getIntent();
        isHost = intent.getBooleanExtra("isHost", false);
        roomName = intent.getStringExtra("roomName");
        roomPassword = intent.getStringExtra("roomPassword");
        endpointId = intent.getStringExtra("endpointId");
        spotifyEnabled = intent.getBooleanExtra("spotifyEnabled", false);
        skipMode = intent.getIntExtra("skipMode", MAJORITY);
        Log.d(TAG, "skip mode here:" + String.valueOf(skipMode));
        return intent;
    }

    private void assignViewElementVariables() {
        recyclerView = findViewById(R.id.roomRecyclerView);
        currentSongContainer = findViewById(R.id.currentSongContainer);
        currentSongLinearLayout = findViewById(R.id.currentSongLinearLayout);
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
    public Intent getSupportParentActivityIntent() { return getParentActivityIntentImpl(); }

    @Override
    public Intent getParentActivityIntent() { return getParentActivityIntentImpl(); }

    private Intent getParentActivityIntentImpl() {
        Intent i;
        Intent intent = getIntent();
        boolean isHost = intent.getBooleanExtra("isHost", false);

        if (isHost) {
            i = new Intent(this, HostActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            i.putExtra("isHost", isHost);
            i.putExtra("roomName", roomName);
            i.putExtra("roomPassword", roomPassword);
        } else {
            i = new Intent(this, RoomListActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        }

        return i;
    }

    private void startSongAddActivity() {
        wasSearching = true;
        Intent queueSong = new Intent(this, SongSearchActivity.class);
        startActivityForResult(queueSong, MainActivity.ADD_SONG_REQUEST);
    }

    private RoomConfiguration buildCurrentRoomConfiguration() {
        PackageInfo pInfo = null;
        try {
            pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        RoomConfiguration roomConfiguration = new RoomConfiguration(roomPassword != null,
                                                                    spotifyEnabled,
                                                                    skipMode,
                                                                    pInfo.versionCode,
                                                                    roomName);

        return roomConfiguration;
    }

    private void fillCurrentRoomConfiguration() {
        RoomConfiguration roomConfiguration = backgroundService.getRoomConfiguration();
        spotifyEnabled = roomConfiguration.isSpotifyEnabled();
        skipMode = roomConfiguration.getSkipMode();
    }
}
