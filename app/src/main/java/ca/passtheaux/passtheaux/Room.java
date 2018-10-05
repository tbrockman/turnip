package ca.passtheaux.passtheaux;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import org.json.JSONException;
import org.json.JSONObject;


public class Room extends AppCompatActivity {

    static final int ADD_SONG_REQUEST = 1;
    private static final String TAG = Room.class.getSimpleName();

    // Context
    Context context = this;

    // UI Elements
    FloatingActionButton fab;

    // Intent extras
    private boolean isHost;
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
                if (isHost) {
                    connectionService.setSpotifyToken(spotifyToken);
                    connectionService.startAdvertising(roomName);
                }
                else {
                    Log.i(TAG, "attempting to connect to room");
                    connectionService.connectToRoom(endpointId);
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            connectionService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room);

        Intent intent = getIntent();
        isHost = intent.getBooleanExtra("isHost", false);
        roomName = intent.getStringExtra("roomName");
        roomPassword = intent.getStringExtra("roomPassword");
        endpointId = intent.getStringExtra("endpointId");
        spotifyToken = intent.getStringExtra("spotifyToken");

        setTitle(roomName);

        fab =  findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startAddActivity();
            }
        });

        bindConnectionService();
    }

    @Override
    public void onDestroy() {
        if (this.isHost) {
            Log.i(TAG, "Calling onDestroy here");
            connectionService.stopAdvertising();
            connectionService.destroyRoom();
        }
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
                                // Add to local song queue
                                // Emit to all clients
                                // jukebox.enqueueSong(song);
                                connectionService.emitSongAdded(song);
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
        } else {
            i = new Intent(this, RoomList.class);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        }

        return i;
    }

    private void startAddActivity() {
        Intent queueSong = new Intent(this, QueueSong.class);
        startActivityForResult(queueSong, ADD_SONG_REQUEST);
    }

    private void bindConnectionService() {
        Intent serviceIntent = new Intent(this, ConnectionService.class);
        bindService(serviceIntent,
                    connection,
                    Context.BIND_AUTO_CREATE);
    }

    private void unbindConnectionService() {
        this.unbindService(connection);
    }
}
