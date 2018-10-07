package ca.passtheaux.passtheaux;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;


public class ConnectionService extends Service {

    private static final String TAG = ConnectionService.class.getSimpleName();

    // Service binder and context

    private final IBinder binder = new LocalBinder();
    private Context context;

    // Network communication

    private final ArrayList<String> connectedClients = new ArrayList();
    private final OkHttpClient okHttpClient = new OkHttpClient();
    private String serverEndpoint;
    private ConnectionsClient connectionsClient;
    private String spotifyToken;
    private Call lastRequest;

    // Potential listeners

    private RoomFoundListener roomFoundListener;
    private RoomLostListener roomLostListener;
    private SongQueueListener songQueueListener;

    // Jukebox

    private Jukebox jukebox;

    public class LocalBinder extends Binder {
        ConnectionService getService() {
            return ConnectionService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        context = this;
        connectionsClient = Nearby.getConnectionsClient(context);
        jukebox = new ServerJukebox(context, jukeboxListener);
        return binder;
    }

    @Override
    public void onDestroy() {
        destroyRoom();
        super.onDestroy();
    }


    // TODO: probably change these listeners
    // Various subscriptions and listener notification functions

    private Jukebox.JukeboxListener jukeboxListener = new Jukebox.JukeboxListener() {
        @Override
        public void onSongPlaying(Song song) {
            emitSongPlaying(song);
            notifySongPlaying(song);
        }

        @Override
        public void onSongRemoved(Song song) {
            notifySongRemoved(song);
        }
    };

    public void subscribeRoomFound(RoomFoundListener listener) {
        roomFoundListener = listener;
    }

    public void unsubscribeRoomFound() {
        roomFoundListener = null;
    }

    private void notifyHostFound(Endpoint host) {
        Log.i(TAG, host.toString());
        if (roomFoundListener != null) {
            roomFoundListener.onRoomFound(host);
        }
    }

    public void subscribeRoomLost(RoomLostListener listener) { roomLostListener = listener; }

    public void unsubscribeRoomLost() { roomLostListener = null; }

    private void notifyHostLost(String endpointId) {
        Log.i(TAG, "Host lost: " + endpointId);
        if (roomLostListener != null) {
            roomLostListener.onRoomLost(endpointId);
        }
    }

    public void subscribeSongQueue(SongQueueListener listener) { songQueueListener = listener; }

    public void unsubscribeSongQueue() { songQueueListener = null; }

    private void notifySongAdded(Song song) {
        if (songQueueListener != null) {
            songQueueListener.onSongAdded(song);
        }
    }

    private void notifySongRemoved(Song song) {
        if (songQueueListener != null) {
            songQueueListener.onSongRemoved(song);
        }
    }

    private void notifySongPlaying(Song song) {
        if (songQueueListener != null) {
            songQueueListener.onSongPlaying(song);
        }
    }

    // Spotify token getter/setter

    public void setSpotifyToken(String spotifyToken) {
        this.spotifyToken = spotifyToken;
    }

    public String getSpotifyToken() {
        return spotifyToken;
    }

    // Server payload logic

    private final PayloadCallback serverPayloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String endpointId, @NonNull Payload payload) {
            String raw = new String(payload.asBytes());
            try {
                String token = raw.substring(0, 3);
                try {
                    switch (token) {
                        // Someone wants to add a song to the queue
                        case "add":
                            String stringJson = raw.substring(4);
                            JSONObject jsonSong = new JSONObject(stringJson);
                            Song song = new SpotifySong(jsonSong);
                            enqueueSong(song);
                            break;
                        default:
                            break;
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing JSON object in received payload.");
                }
            } catch (Exception e) {
                Log.e(TAG, "Invalid length payload.");
            }
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId,
                                            @NonNull PayloadTransferUpdate payloadTransferUpdate) {

        }
    };

    // Client payload logic

    private final PayloadCallback clientPayloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String endpointId, @NonNull Payload payload) {
            String raw = new String(payload.asBytes());
            // First three characters of payload denote type of request
            try {
                String token = raw.substring(0, 3);
                String stringJson;
                JSONObject jsonSong;
                Song song;

                try {
                    switch (token) {
                        // We're receiving a spotify token from a server
                        case "tok":
                            String spotifyToken = raw.substring(4);
                            setSpotifyToken(spotifyToken);
                            break;
                        // Someone has added a new song to the queue
                        case "que":
                            stringJson = raw.substring(4);
                            jsonSong = new JSONObject(stringJson);
                            song = new SpotifySong(jsonSong);
                            enqueueSong(song);
                            break;
                        // Server playing a new song
                        case "ply":
                            stringJson = raw.substring(4);
                            jsonSong = new JSONObject(stringJson);
                            song = new SpotifySong(jsonSong);
                            jukebox.playSong(song);
                            break;
                        default:
                            break;
                    }
                }
                catch (JSONException e) {
                    e.printStackTrace();
                }
                Log.i(TAG, "Payload received: " + raw);
            } catch (Exception e) {
                Log.e(TAG, "Invalid length payload.");
            }
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId,
                                            @NonNull PayloadTransferUpdate payloadTransferUpdate) {

        }
    };

    // Server connection logic

    private final ConnectionLifecycleCallback serverConnectionLifecycleCallback =
        new ConnectionLifecycleCallback() {

            @Override
            public void onConnectionInitiated(@NonNull String endpointId,
                                              @NonNull ConnectionInfo connectionInfo) {
                Log.i(TAG, "Accepting connection from: " + endpointId);
                Nearby.getConnectionsClient(context).acceptConnection(endpointId,
                                                                      serverPayloadCallback);
            }

            @Override
            public void onConnectionResult(@NonNull String endpointId,
                                           @NonNull ConnectionResolution connectionResolution) {
                Log.i(TAG, "Finished accepting connection from: " + endpointId);
                connectedClients.add(endpointId);
                sendSpotifyToken(endpointId, spotifyToken);
            }

            @Override
            public void onDisconnected(@NonNull String endpointId) {
                Log.i(TAG, "Connection disconnected: " + endpointId);
            }
        };

    // Client connection logic

    private final ConnectionLifecycleCallback clientConnectionLifecycleCallback =
        new ConnectionLifecycleCallback() {

            @Override
            public void onConnectionInitiated(@NonNull String endpointId,
                                              @NonNull ConnectionInfo connectionInfo) {
                Log.i(TAG, "Accepting connection from: " + endpointId);
                Nearby.getConnectionsClient(context)
                      .acceptConnection(endpointId, clientPayloadCallback);
            }

            @Override
            public void onConnectionResult(@NonNull String endpointId,
                                           @NonNull ConnectionResolution connectionResolution) {
                Log.i(TAG, "Finished accepting connection from: " + endpointId);
                serverEndpoint = endpointId;
            }

            @Override
            public void onDisconnected(@NonNull String endpointId) {
                Log.i(TAG, "Connection disconnected: " + endpointId);
            }
        };

    private final EndpointDiscoveryCallback endpointDiscoveryCallback =
        new EndpointDiscoveryCallback() {

            @Override
            public void onEndpointFound(@NonNull String endpointId,
                                        @NonNull DiscoveredEndpointInfo discoveredEndpointInfo) {
                Endpoint endpoint = new Endpoint(endpointId,
                                                 discoveredEndpointInfo.getEndpointName());
                notifyHostFound(endpoint);
            }

            @Override
            public void onEndpointLost(@NonNull String endpointId) {
                Log.i(TAG, "endpoint lost");
                notifyHostLost(endpointId);
            }
        };

    public void startAdvertising(final String roomName) {
        connectionsClient
            .startAdvertising(
                roomName,
                Main.APP_ENDPOINT,
                serverConnectionLifecycleCallback,
                new AdvertisingOptions(Strategy.P2P_STAR))
            .addOnSuccessListener(
                new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unusedResult) {
                    Log.i(TAG, "Now advertising endpoint " + roomName);
                    }
                })
            .addOnFailureListener(
                new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                    Log.w(TAG, "startAdvertising() failed.", e);
                    }
                });
    }

    public void stopAdvertising() {
        connectionsClient.stopAdvertising();
    }

    public void startDiscovery() {
        connectionsClient
            .startDiscovery(
            Main.APP_ENDPOINT,
            endpointDiscoveryCallback,
            new DiscoveryOptions(Strategy.P2P_STAR))
            .addOnSuccessListener(
                new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unusedResult) {
                    Log.i(TAG, "Discovering...");
                    // We're discovering!
                    }
                })
            .addOnFailureListener(
                new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                    // We were unable to start discovering.
                    Log.i(TAG, "Failed discovery...", e);
                    }
                });
    }

    public void stopDiscovery() {
        connectionsClient.stopDiscovery();
    }

    public void connectToRoom(String endpointId) {
        connectionsClient.requestConnection("test", endpointId, clientConnectionLifecycleCallback);
    }

    public void sendSpotifyToken(String endpointId, String spotifyToken) {
        String payload = "tok " + spotifyToken;
        sendPayload(endpointId, Payload.fromBytes(payload.getBytes()));
    }

    // Nearby network and jukebox song communication

    public void enqueueSong(Song song) {
        jukebox.enqueueSong(song);
        emitSongAdded(song);
        notifySongAdded(song);
    }

    public void emitSongAdded(Song song) {
        String payload = "que " + song.toString();
        emitPayload(connectedClients, Payload.fromBytes(payload.getBytes()));
    }

    public void addSong(Song song) {
        String payload = "add " + song.toString();
        sendPayload(serverEndpoint, Payload.fromBytes(payload.getBytes()));
    }

    public void emitSongPlaying(Song song) {
        String payload = "ply " + song.toString();
        emitPayload(connectedClients, Payload.fromBytes(payload.getBytes()));
    }

    private void sendPayload(String endpointId, Payload payload) {
        connectionsClient.sendPayload(endpointId, payload);
    }

    private void emitPayload(ArrayList<String> endpointIds, Payload payload) {
        connectionsClient.sendPayload(endpointIds, payload);
    }

    // Spotify API related functions

    public void searchSpotifyAPI(String search, String type, Callback callback) {
        final Request request = new Request.Builder()
                .url("https://api.spotify.com/v1/search?q=" + search + "&type=" + type)
                .addHeader("Authorization", "Bearer " + spotifyToken)
                .addHeader("Accept", "application/json")
                .build();
        lastRequest = okHttpClient.newCall(request);
        lastRequest.enqueue(callback);
    }

    private void cancelLastRequest() {
        if (lastRequest != null) {
            lastRequest.cancel();
        }
    }

    public void destroyRoom() {
        stopAdvertising();
        stopDiscovery();
        cancelLastRequest();
        connectedClients.clear();
        jukebox.turnOff();
    }

    protected static class Endpoint {
        private final String id;
        private final String name;

        private Endpoint(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj != null && obj instanceof Endpoint) {
                Endpoint other = (Endpoint) obj;
                return id.equals(other.id);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public String toString() {
            return String.format("Endpoint{id=%s, name=%s}", id, name);
        }
    }

    protected interface RoomFoundListener {
        public void onRoomFound(Endpoint endpoint);
    }

    protected interface RoomLostListener {
        public void onRoomLost(String endpointId);
    }

    protected interface SongQueueListener {
        public void onSongAdded(Song song);
        public void onSongRemoved(Song song);
        public void onSongPlaying(Song song);
    }
}
