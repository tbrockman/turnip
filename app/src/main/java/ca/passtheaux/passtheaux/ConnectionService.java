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
import java.util.Collections;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;


public class ConnectionService extends Service {

    private static final String TAG = ConnectionService.class.getSimpleName();

    // Binder, context, and context variables

    private final IBinder binder = new LocalBinder();
    private Context context;
    private boolean isHost;
    private boolean inRoom;

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
    private RoomNetworkListener roomNetworkListener;

    // Jukebox

    private Jukebox jukebox;

    private Jukebox.JukeboxListener jukeboxListener = new Jukebox.JukeboxListener() {
        @Override
        public void onSongPlaying(Song song) {
            emitSongPlaying(connectedClients, song);
            notifySongPlaying(song);
        }

        @Override
        public void onSongRemoved(Song song) {
            notifySongRemoved(song);
        }
    };

    public class LocalBinder extends Binder {
        ConnectionService getService() {
            return ConnectionService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        context = this;
        connectionsClient = Nearby.getConnectionsClient(context);
        isHost = intent.getBooleanExtra("isHost", false);
        inRoom = intent.getBooleanExtra("inRoom", false);

        if (inRoom) {
            if (isHost) {
                jukebox = new ServerJukebox(context, jukeboxListener);
            }
            else {
                jukebox = new Jukebox(jukeboxListener);
            }
        }

        return binder;
    }

    // Clean-up

    @Override
    public void onDestroy() {
        destroyRoom();
        super.onDestroy();
    }

    public void destroyRoom() {
        cancelLastRequest();
        stopAdvertising();
        unsubscribeRoomNetwork();
        connectionsClient.stopAllEndpoints();
        connectedClients.clear();
        jukebox.turnOff();
    }

    // TODO: probably refactor these listeners
    // Various subscriptions and listener notification functions

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

    public void subscribeRoomNetwork(RoomNetworkListener listener) { roomNetworkListener = listener; }

    public void unsubscribeRoomNetwork() { roomNetworkListener = null; }

    private void notifySongAdded(Song song) {
        if (roomNetworkListener != null) {
            roomNetworkListener.onSongAdded(song);
        }
    }

    private void notifySongRemoved(Song song) {
        if (roomNetworkListener != null) {
            roomNetworkListener.onSongRemoved(song);
        }
    }

    private void notifySongPlaying(Song song) {
        if (roomNetworkListener != null) {
            roomNetworkListener.onSongPlaying(song);
        }
    }

    private void notifyDisconnected() {
        if (roomNetworkListener != null) {
            roomNetworkListener.onDisconnect();
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
                JSONObject jsonPayload;
                Song song;

                try {
                    switch (token) {
                        // We're receiving a spotify token from a server
                        case "tok":
                            String spotifyToken = raw.substring(4);
                            setSpotifyToken(spotifyToken);
                            break;
                        // Someone has added a new song to the queue
                        case "add":
                            stringJson = raw.substring(4);
                            jsonPayload = new JSONObject(stringJson);
                            song = new SpotifySong(jsonPayload);
                            enqueueSong(song);
                            break;
                        // Server playing a new song
                        case "ply":
                            stringJson = raw.substring(4);
                            jsonPayload = new JSONObject(stringJson);
                            // TODO: handle different song types
                            song = new SpotifySong(jsonPayload);
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
                if (jukebox.getSongQueueLength() > 0) {
                    sendClientSongQueue(endpointId, jukebox.getSongQueue());
                }
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
                notifyDisconnected();
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
        // TODO: better naming scheme than hardcoded 'test'
        connectionsClient.requestConnection("test", endpointId, clientConnectionLifecycleCallback);
    }

    private void sendSpotifyToken(String endpointId, String spotifyToken) {
        String payload = "tok " + spotifyToken;
        sendPayload(endpointId, Payload.fromBytes(payload.getBytes()));
    }

    // Nearby network and jukebox song communication

    public void enqueueSong(Song song) {
        if (jukebox.getCurrentlyPlaying() != null) {
            // TODO: jukebox should probably do notification itself
            notifySongAdded(song);
            jukebox.enqueueSong(song);
            emitSongAdded(connectedClients, song);
        }
        else {
            jukebox.playSong(song);
        }
    }

    private void sendClientSongQueue(String endpointId, ArrayList<Song> songQueue) {
        for (int i = 0; i < songQueue.size(); i++) {
            // TODO: is the code reuse worth multiple payload sending overhead?
            // Will this lead to race conditions when songs are added by one client
            // while currently sending the queue to another?
            // Max payload size is (allegedly) 32768 bytes, depending on song queue size
            // we may not have a choice
            sendSongAdded(endpointId, songQueue.get(i));
        }
    }

    // TODO: this works for now, but in the future think about a better way of
    // sending payloads then by having to convert json object to string
    // AND THEN convert concatenated string to bytes

    public void sendSongAdded(String endpointId, Song song) {
        emitSongAdded(Collections.singletonList(endpointId), song);
    }

    public void emitSongAdded(List<String> clients, Song song) {
        String payload = "add " + song.toString();
        emitPayload(clients, Payload.fromBytes(payload.getBytes()));
    }

    public void addSong(Song song) {
        String payload = "add " + song.toString();
        sendPayload(serverEndpoint, Payload.fromBytes(payload.getBytes()));
    }

    public void emitSongPlaying(List<String> clients, Song song) {
        String payload = "ply " + song.toString();
        emitPayload(clients, Payload.fromBytes(payload.getBytes()));
    }

    private void sendPayload(String endpointId, Payload payload) {
        emitPayload(Collections.singletonList(endpointId), payload);
    }

    private void emitPayload(List<String> endpointIds, Payload payload) {
        connectionsClient.sendPayload(endpointIds, payload);
    }

    // Spotify related functions

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

    protected interface RoomNetworkListener {
        public void onSongAdded(Song song);
        public void onSongRemoved(Song song);
        public void onSongPlaying(Song song);
        public void onDisconnect();
    }
}
