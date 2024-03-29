package ca.turnip.turnip.services;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.annotation.NonNull;

import android.preference.PreferenceManager;
import android.util.Base64;
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
import com.google.android.gms.tasks.Task;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.UnrecoverableEntryException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import ca.turnip.turnip.R;
import ca.turnip.turnip.models.RoomConfiguration;
import ca.turnip.turnip.activities.MainActivity;
import ca.turnip.turnip.encryption.Decryptor;
import ca.turnip.turnip.encryption.Encryptor;
import ca.turnip.turnip.listeners.AuthenticationListener;
import ca.turnip.turnip.listeners.JukeboxListener;
import ca.turnip.turnip.listeners.RoomJukeboxListener;
import ca.turnip.turnip.listeners.RoomListListener;
import ca.turnip.turnip.controllers.Jukebox;
import ca.turnip.turnip.models.RoomSession;
import ca.turnip.turnip.models.RoomSessionHistory;
import ca.turnip.turnip.controllers.ServerJukebox;
import ca.turnip.turnip.models.Song;
import ca.turnip.turnip.models.SongHistory;
import ca.turnip.turnip.models.SpotifySong;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static ca.turnip.turnip.activities.MainActivity.API_ENDPOINT;


public class BackgroundService extends Service {

    private static final String TAG = BackgroundService.class.getSimpleName();
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    // Binder, context

    private final IBinder binder = new LocalBinder();
    private Context context;

    // Room state information

    private boolean isHost;
    private boolean isDiscovering = false;
    private HashMap<String, Integer> votes = new HashMap<>();
    private SongHistory songHistory;
    private RoomSession currentRoomSession;
    private RoomSessionHistory roomSessionHistory;

    // Room configuration
    private RoomConfiguration roomConfiguration = null;
    private boolean spotifyEnabled = false;
    private JSONObject roomInfo = null;
    private int serverVersionCode;

    // Handler for running asynchronous code on UI thread

    private Handler enqueueSongHandler = new Handler(Looper.getMainLooper());

    // Runnables

    private class SongQueueRunnable implements Runnable {
        private Song added;

        public SongQueueRunnable(JSONObject song) {
            this.added = new SpotifySong(song);
        }

        @Override
        public void run() {
            ((ServerJukebox) jukebox).playSongAddedBySpotify(added);
        }
    }

    // Network communication

    private final ArrayList<String> connectedClients = new ArrayList();
    private final ArrayList<String> authenticatedClients = new ArrayList(); // TODO: password related
    private final ArrayList<String> blacklistedClients = new ArrayList(); // TODO: rate-limiting
    private Set<String> votedClients = Collections.synchronizedSet(new HashSet<String>());
    private final ArrayList<Endpoint> discoveredHosts = new ArrayList();
    private final OkHttpClient okHttpClient = new OkHttpClient();
    private String serverEndpoint;
    private Call lastRequest;
    private ConnectionsClient connectionsClient;

    // Token storage and encryption

    private Encryptor encryptor;
    private Decryptor decryptor;
    private String spotifyAccessToken;
    private String spotifyRefreshToken;

    // Token refreshing
    private int spotifyExpiresIn;
    private Date spotifyExpirationDate;
    private Handler spotifyTimerHandler = new Handler();
    private Runnable spotifyRefreshTokenTimer = new Runnable() {
        @Override
        public void run() {
            try {
                if (spotifyRefreshToken == null) {
                    spotifyRefreshToken = getStoredSpotifyRefreshToken();
                }
                getAccessTokenFromRefreshToken(spotifyRefreshToken);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    // Potential listeners

    private RoomListListener roomListListener;
    private RoomJukeboxListener roomJukeboxListener;
    private AuthenticationListener authenticationListener;

    // Jukebox

    private Jukebox jukebox;

    private JukeboxListener jukeboxListener = new JukeboxListener() {
        @Override
        public void onSongPlaying(Song song) {

            if (songHistoryIsEnabled()) {
                songHistory.addSong(song);
            }

            if (isHost) {
                currentRoomSession.playSong(song);

                if (roomConfiguration.getSkipMode() != RoomConfiguration.NO_SKIP) {
                    if (votes != null) {
                        votes.clear();
                    }

                    if (votedClients != null) {
                        votedClients.clear();
                    }
                }
            }
            emitSongPlaying(connectedClients, song);
            notifySongPlaying(song);
        }

        @Override
        public void onSongRemoved(Song song) {
            notifySongRemoved(song);
        }

        @Override
        public void onSongPaused(int timeElapsed) {
            notifySongPaused(timeElapsed);
        }

        @Override
        public void onSongResumed(int timeElapsed) {
            notifySongResumed(timeElapsed);
        }

        @Override
        public void onSongTick(int timeElapsed) {
            notifySongTicked(timeElapsed);
        }

        @Override
        public void onSpotifyAddedSong(String id, int timeElapsed) {
            getSpotifyUrl("https://api.spotify.com/v1/tracks/" + id,
                          new SpotifySongAddedCallback(timeElapsed));
        }

        @Override
        public void onSpotifyDisconnected() {
            currentRoomSession.setError(true);
            notifySpotifyDisconnect();
            destroyRoom();
        }
    };

    public class LocalBinder extends Binder {
        public BackgroundService getService() {
            return BackgroundService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        context = this;
        connectionsClient = Nearby.getConnectionsClient(context);
        songHistory = new SongHistory(context);
        encryptor = new Encryptor();
        try {
            decryptor = new Decryptor();
        } catch (Exception e) {
            Log.e(TAG, "Error instantiating decryptor: " + e.toString());
        }
        return binder;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "Removing service.");
        stopSelf();
    }

    public void startupJukebox(boolean isHost, String roomName) {

        RoomSession lastRoomSession = null;
        String lastRoomSessionName;

        this.isHost = isHost;

        roomSessionHistory = RoomSessionHistory.fromDisk(context);
        lastRoomSessionName = roomSessionHistory.getMostRecentRoomSessionFilename();

        try {
            lastRoomSession = RoomSession.fromDisk(context, lastRoomSessionName);
            Log.i(TAG, lastRoomSession.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving last RoomSession." + e.toString());
            roomSessionHistory.removeRoomSession(lastRoomSessionName);
        }

        currentRoomSession = new RoomSession(context, roomName);
        currentRoomSession.writeToDisk();
        roomSessionHistory.addRoomSession(currentRoomSession.getSessionFilename());

        if (isHost) {
            jukebox = new ServerJukebox(context, jukeboxListener);

            if (lastRoomSession != null) {
                if (!lastRoomSession.hasError()) {
                    roomSessionHistory.removeRoomSession(lastRoomSessionName);
                }
                else {
                    Log.i(TAG, "Last session had error.");
                    notifyPastSessionError(lastRoomSession);
                }
            }
        }
        else {
            jukebox = new Jukebox(jukeboxListener);
        }
    }

    public RoomConfiguration getRoomConfiguration() {
        return this.roomConfiguration;
    }

    public void setRoomConfiguration(RoomConfiguration roomConfiguration) {
        if (roomConfiguration.getSkipMode() == RoomConfiguration.MAJORITY) {
            votes = new HashMap<>();
        }
        this.roomConfiguration = roomConfiguration;
    }

    // Clean-up

    public void onRoomResume(Activity roomActivity) {
//        if (isHost) {
//            ((ServerJukebox) jukebox).connectToSpotify(roomActivity);
//        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Destroying BackgroundService");
        destroyRoom();
    }

    public void destroyRoom() {
        cancelLastRequest();
        if (connectionsClient != null) {
            if (isHost) {
                connectionsClient.stopAllEndpoints();
            }
            else {
                if (serverEndpoint != null) {
                    connectionsClient.disconnectFromEndpoint(serverEndpoint);
                }
            }
        }

        if (isHost) {
            spotifyTimerHandler.removeCallbacks(spotifyRefreshTokenTimer);
            connectedClients.clear();
        }

        if (jukebox != null) {
            jukebox.turnOff();
        }
    }

    // TODO: probably refactor these listeners
    // Various subscriptions and listener notification functions

    public void subscribeRoomListListener(RoomListListener listener) { roomListListener = listener; }

    public void unsubscribeRoomListListener() { roomListListener = null; }

    private void notifyHostLost(String endpointId) {
        if (roomListListener != null) {
            roomListListener.onRoomLost(endpointId);
        }
    }

    private void notifyHostFound(Endpoint host) {
        Log.d(TAG, host.toString());
        if (roomListListener != null) {
            roomListListener.onRoomFound(host);
        }
    }

    public void subscribeRoomJukeboxListener(RoomJukeboxListener listener) { roomJukeboxListener = listener; }

    public void unsubscribeRoomJukeboxListener() { roomJukeboxListener = null; }

    private void notifySongAdded(Song song) {
        if (roomJukeboxListener != null) {
            roomJukeboxListener.onSongAdded(song);
        }
    }

    private void notifySongRemoved(Song song) {
        if (roomJukeboxListener != null) {
            roomJukeboxListener.onSongRemoved(song);
        }
    }

    private void notifySongPlaying(Song song) {
        if (roomJukeboxListener != null) {
            roomJukeboxListener.onSongPlaying(song);
        }
    }

    private void notifyConnected() {
        if (roomJukeboxListener != null) {
            roomJukeboxListener.onConnect();
        }
    }

    private void notifyDisconnected() {
        if (roomJukeboxListener != null) {
            roomJukeboxListener.onDisconnect();
        }
    }

    private void notifySpotifyDisconnect() {
        if (roomJukeboxListener != null) {
            roomJukeboxListener.onSpotifyDisconnected();
        }
    }

    private void notifySongPaused(int timeElapsed) {
        if (roomJukeboxListener != null) {
            roomJukeboxListener.onSongPaused(timeElapsed);
            emitSongPaused(connectedClients, timeElapsed);
        }
    }

    private void notifySongResumed(int timeElapsed) {
        if (roomJukeboxListener != null) {
            roomJukeboxListener.onSongResumed(timeElapsed);
            emitSongResumed(connectedClients, timeElapsed);
        }
    }

    private void notifySongTicked(int timeElapsed) {
        if (roomJukeboxListener != null) {
            roomJukeboxListener.onSongTick(timeElapsed);
        }
    }

    private void notifyPastSessionError(RoomSession session) {
        if (roomJukeboxListener != null) {
            roomJukeboxListener.onPastSessionError(session);
        }
    }

    public void subscribeAuthListener(AuthenticationListener listener) { authenticationListener = listener; }

    public void unsubscribeAuthListener() { authenticationListener = null; }

    private void notifyAuthFailed() {
        if (authenticationListener != null) {
            authenticationListener.onTokenFailure();
        }
    }

    private void notifyAuthSuccessful() {
        if (authenticationListener != null) {
            authenticationListener.onTokenSuccess();
        }
    }

    // Spotify tokens getter/setters

    public void setSpotifyAccessToken(String spotifyAccessToken) {
        this.spotifyAccessToken = spotifyAccessToken;
        emitSpotifyAccessTokenToConnectedClients(spotifyAccessToken);
    }

    public String getSpotifyAccessToken() {
        return spotifyAccessToken;
    }

    public String getSpotifyRefreshToken() {
        return spotifyRefreshToken;
    }

    public boolean existsStoredSpotifyRefreshToken() {
        SharedPreferences sharedPref =
                context.getSharedPreferences(getString(R.string.preference_file_key),
                        Context.MODE_PRIVATE);
        return sharedPref.contains(getString(R.string.shared_preference_token_key));
    }

    public void removeStoredSpotifyRefreshToken() {
        SharedPreferences sharedPref =
                context.getSharedPreferences(getString(R.string.preference_file_key),
                        Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();

        editor.remove(getString(R.string.shared_preference_token_key));
        editor.remove(getString(R.string.shared_preferences_iv));
        editor.commit();
    }

    public String getStoredSpotifyRefreshToken() throws IOException, BadPaddingException,
            NoSuchAlgorithmException, InvalidKeyException, UnrecoverableEntryException,
            InvalidAlgorithmParameterException, NoSuchPaddingException, NoSuchProviderException,
            KeyStoreException, IllegalBlockSizeException {

        String token = null;

        if (existsStoredSpotifyRefreshToken()) {
            SharedPreferences sharedPref =
                    context.getSharedPreferences(getString(R.string.preference_file_key),
                            Context.MODE_PRIVATE);
            String base64Token = sharedPref.getString(getString(R.string.shared_preference_token_key),
                    "");
            String base64IV = sharedPref.getString(getString(R.string.shared_preferences_iv),
                    "");
            byte[] encrypted = Base64.decode(base64Token, Base64.NO_WRAP);
            byte[] iv = Base64.decode(base64IV, Base64.NO_WRAP);
            token = decryptor.decryptData(getString(R.string.token_store_key), encrypted, iv);
        }

        return token;
    }

    public void storeSpotifyRefreshToken(String spotifyRefreshToken) throws IOException,
            BadPaddingException, NoSuchAlgorithmException, InvalidKeyException,
            UnrecoverableEntryException, InvalidAlgorithmParameterException,
            NoSuchPaddingException, NoSuchProviderException, SignatureException,
            KeyStoreException, IllegalBlockSizeException {

        SharedPreferences sharedPref =
                context.getSharedPreferences(getString(R.string.preference_file_key),
                                             Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        byte[] encrypted = encryptor.encryptText(getString(R.string.token_store_key),
                                                 spotifyRefreshToken);
        byte[] iv = encryptor.getIv();
        String storedToken = Base64.encodeToString(encrypted, Base64.NO_WRAP);
        String storedIV = Base64.encodeToString(iv, Base64.NO_WRAP);
        editor.putString(getString(R.string.shared_preference_token_key), storedToken);
        editor.putString(getString(R.string.shared_preferences_iv), storedIV);
        editor.apply();
    }

    public void setSpotifyRefreshToken(String spotifyRefreshToken) {
        this.spotifyRefreshToken = spotifyRefreshToken;
        try {
            storeSpotifyRefreshToken(spotifyRefreshToken);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setSpotifyTokenExpiration(int expiresIn) {
        Date now = new Date();
        spotifyExpirationDate = new Date(now.getTime() + (expiresIn * 1000));
    }

    // Server payload logic

    private final PayloadCallback serverPayloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String endpointId, @NonNull Payload payload) {
            String raw = new String(payload.asBytes());
            try {
                String token = raw.substring(0, 3);
                String jsonString;
                try {
                    switch (token) {
                        // Someone wants to add a song to the queue
                        case "add":
                            jsonString = raw.substring(4);
                            JSONObject jsonSong = new JSONObject(jsonString);
                            Song song = new SpotifySong(jsonSong);
                            enqueueOrPlaySong(song);
                            break;
                        // Someone is attempting to authenticate with the server
                        case "pwd":
                            jsonString = raw.substring(4);
                            // TODO: generate salt, store hash of salt and password
                            // keeping salt, when first creating a room
                            break;
                        case "skp":
                            String songURI = raw.substring(4);
                            if (roomConfiguration.getSkipMode() != RoomConfiguration.NO_SKIP) {
                                addSkipVote(songURI, endpointId);
                            }
                            break;
                        default:
                            break;
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing JSON object in received payload.");
                }
            } catch (Exception e) {
                Log.e(TAG, e.toString());
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
                String stringPayload;
                JSONObject jsonPayload;
                Song song;

                try {
                    switch (token) {
                        // We're receiving a spotify token from a server
                        case "tok":
                            String spotifyToken = raw.substring(4);
                            setSpotifyAccessToken(spotifyToken);
                            notifyConnected(); // TODO: this shouldn't be called here
                                                // should have an actual way of knowing when a connection
                                                // has been established and all initial data has been transferred
                            break;
                        // Room info on start-up, tells us if we need to send the server a password
                        case "inf":
                            stringPayload = raw.substring(4);
                            jsonPayload = new JSONObject(stringPayload);
                            setRoomConfiguration(RoomConfiguration.fromJSON(jsonPayload));

                            if (roomConfiguration.isPasswordProtected()) {
                                // TODO: send passsword
                            }

                            break;
                        // Someone has added a new song to the queue
                        case "add":
                            stringPayload = raw.substring(4);
                            jsonPayload = new JSONObject(stringPayload);
                            song = new SpotifySong(jsonPayload);
                            enqueueOrPlaySong(song);
                            break;
                        // Server playing a new song
                        case "ply":
                            stringPayload = raw.substring(4);
                            jsonPayload = new JSONObject(stringPayload);
                            // TODO: handle different song types
                            song = new SpotifySong(jsonPayload);
                            jukebox.playSong(song);
                            break;
                        // Currently playing
                        case "cur":
                            stringPayload = raw.substring(4);
                            jsonPayload = new JSONObject(stringPayload);
                            song = new SpotifySong(jsonPayload);
                            jukebox.playSong(song);
                            break;
                        // Server pausing song
                        case "pau":
                            stringPayload = raw.substring(4);
                            jukebox.pauseCurrent(Integer.valueOf(stringPayload));
                            break;
                        // Server resuming song
                        case "res":
                            stringPayload = raw.substring(4);
                            jukebox.unpauseCurrent(Integer.valueOf(stringPayload));
                        default:
                            break;
                    }
                }
                catch (JSONException e) {
                    e.printStackTrace();
                }
                Log.d(TAG, "Payload received: " + raw);
            } catch (Exception e) {
                Log.e(TAG, "Invalid length payload." +  e.toString());
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
                Log.d(TAG, "Accepting connection from: " + endpointId);
                Nearby.getConnectionsClient(context).acceptConnection(endpointId,
                                                                      serverPayloadCallback);
            }

            @Override
            public void onConnectionResult(@NonNull String endpointId,
                                           @NonNull ConnectionResolution connectionResolution) {
                Log.d(TAG, "Finished accepting connection from: " + endpointId);
                if (!roomConfiguration.isPasswordProtected()) {
                    connectedClients.add(endpointId);
                    // TODO: disconnect clients that dont provide a password
                    // within a sufficient amount of time
                    // also switch over use of connected clients to authenticated clients
                    authenticatedClients.add(endpointId);
                    sendCurrentRoomConfiguration(endpointId);
                    sendClientSongsAndToken(endpointId);
                } else {
                    sendCurrentRoomConfiguration(endpointId);
                }
            }

            @Override
            public void onDisconnected(@NonNull String endpointId) {
                authenticatedClients.remove(endpointId);
                connectedClients.remove(endpointId);
                Log.d(TAG, "Connection disconnected: " + endpointId);
            }
        };

    private void sendClientSongsAndToken(@NonNull String endpointId) {
        sendSpotifyToken(endpointId, spotifyAccessToken);
        sendCurrentlyPlaying(endpointId, jukebox.getCurrentlyPlaying());
        if (jukebox.getSongQueueLength() > 0) {
            sendClientSongQueue(endpointId, jukebox.getSongQueue());
        }
    }

    // Client connection logic

    private final ConnectionLifecycleCallback clientConnectionLifecycleCallback =
        new ConnectionLifecycleCallback() {

            @Override
            public void onConnectionInitiated(@NonNull String endpointId,
                                              @NonNull ConnectionInfo connectionInfo) {
                Log.d(TAG, "Accepting connection from: " + endpointId);
                Nearby.getConnectionsClient(context)
                      .acceptConnection(endpointId, clientPayloadCallback);
            }

            @Override
            public void onConnectionResult(@NonNull String endpointId,
                                           @NonNull ConnectionResolution connectionResolution) {
                Log.d(TAG, "Finished accepting connection from: " + endpointId);
                serverEndpoint = endpointId;
                stopDiscovery();
            }

            @Override
            public void onDisconnected(@NonNull String endpointId) {
                Log.d(TAG, "Connection disconnected: " + endpointId);
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
                discoveredHosts.add(endpoint);
                notifyHostFound(endpoint);
            }

            @Override
            public void onEndpointLost(@NonNull String endpointId) {
                Iterator<Endpoint> it = discoveredHosts.iterator();
                while (it.hasNext()) {
                    BackgroundService.Endpoint current = it.next();
                    if (current.getId().equals(endpointId)) {
                        it.remove();
                    }
                }
                notifyHostLost(endpointId);
            }
        };

    public void startAdvertising(final String roomName) {
        Log.d(TAG, "Advertising");
        connectionsClient
            .startAdvertising(
                roomName,
                MainActivity.APP_ENDPOINT,
                serverConnectionLifecycleCallback,
                new AdvertisingOptions(Strategy.P2P_STAR))
            .addOnSuccessListener(
                new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unusedResult) {
                    Log.d(TAG, "Now advertising endpoint " + roomName);
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
            MainActivity.APP_ENDPOINT,
            endpointDiscoveryCallback,
            new DiscoveryOptions(Strategy.P2P_STAR))
            .addOnSuccessListener(
                new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unusedResult) {
                        isDiscovering = true;
                    }
                })
            .addOnFailureListener(
                new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                    // We were unable to start discovering.
                    Log.d(TAG, "Failed discovery...", e);
                    }
                });
    }

    public void stopDiscovery() {
        isDiscovering = false;
        connectionsClient.stopDiscovery();
    }

    public boolean isDiscovering() {
        return isDiscovering;
    }

    public void connectToRoom(String endpointId) {
        // TODO: better naming scheme than hardcoded 'turnip'
        Log.d(TAG, "trying to connect here" + endpointId);
        Task<Void> result = connectionsClient.requestConnection("turnip", endpointId, clientConnectionLifecycleCallback);
        result.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.e(TAG, e.toString());
            }
        });
    }

    public ArrayList<Endpoint> getDiscoveredHosts() {
        return this.discoveredHosts;
    }

    private void sendCurrentRoomConfiguration(String endpointId) {
        if (roomConfiguration != null) {
            String payload = "inf " + roomConfiguration.toString();
            sendPayload(endpointId, Payload.fromBytes(payload.getBytes()));
        }
    }

    private void sendCurrentlyPlaying(String endpointId, Song song) {
        if (song != null) {
            String payload = "cur " + song.toString();
            sendPayload(endpointId, Payload.fromBytes(payload.getBytes()));
        }
    }

    private void sendSpotifyToken(String endpointId, String spotifyToken) {
        String payload = "tok " + spotifyToken;
        sendPayload(endpointId, Payload.fromBytes(payload.getBytes()));
    }

    public void sendPassword(String endpointId, String password) {
        String payload = "pwd " + password;
        sendPayload(endpointId, Payload.fromBytes(payload.getBytes()));
    }

    private void addSkipVote(String songURI, String endpointId) {
        if (songURI.equals(getCurrentlyPlaying().getString("uri")) &&
            !votedClients.contains(endpointId)) {

            Integer voteCount = 1;
            if (votes.containsKey(songURI)) {
                voteCount = votes.get(songURI);
                votes.put(songURI, voteCount + 1);
            }
            else {
                votes.put(songURI, voteCount);
            }

            votedClients.add(endpointId);

            if (roomConfiguration.getSkipMode() == RoomConfiguration.MAJORITY) {
                if (voteCount >= Math.floor(authenticatedClients.size() / 2)) {
                    skipCurrentSong();
                }
            }
        }
    }

    public void sendSkipSong(String endpointId, String songURI) {
        String payload = "skp " + songURI;
        sendPayload(endpointId, Payload.fromBytes(payload.getBytes()));
    }

    // Nearby network and jukebox song communication

    public void enqueueSong(Song song) {
        notifySongAdded(song);
        jukebox.enqueueSong(song);
        emitSongAdded(connectedClients, song);
    }

    public void enqueueOrPlaySong(Song song) {

        if (currentRoomSession != null) {
            currentRoomSession.addSong(song);
        }

        if (jukebox != null) {
            if (jukebox.getCurrentlyPlaying() != null || !isHost) {
                enqueueSong(song);
            }
            else {
                jukebox.playSong(song);
            }
        }
    }

    public void skipCurrentSong() {
        jukebox.playNextSong();
    }

    public void voteSkip(Song song) {
        sendSkipSong(serverEndpoint, song.getString("uri"));
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

    public void emitSongPaused(List<String> clients, int timeElapsed) {
        String payload = "pau " + String.valueOf(timeElapsed);
        emitPayload(clients, Payload.fromBytes(payload.getBytes()));
    }

    public void emitSongResumed(List<String> clients, int timeElapsed) {
        String payload = "res " + String.valueOf(timeElapsed);
        emitPayload(clients, Payload.fromBytes(payload.getBytes()));
    }

    public void emitSpotifyAccessTokenToConnectedClients(String spotifyAccessToken) {
        String payload = "tok " + spotifyAccessToken;
        emitPayload(connectedClients, Payload.fromBytes(payload.getBytes()));
    }

    private void sendPayload(String endpointId, Payload payload) {
        emitPayload(Collections.singletonList(endpointId), payload);
    }

    private void emitPayload(List<String> endpointIds, Payload payload) {
        connectionsClient.sendPayload(endpointIds, payload);
    }

    // Spotify API interactions

    public synchronized void searchSpotifyAPI(String search, String type, Callback callback) {
        final Request request =
                new Request.Builder()
                           .url("https://api.spotify.com/v1/search?q=" + search +
                                "*&market=from_token&type=" + type)
                           .addHeader("Authorization", "Bearer " + spotifyAccessToken)
                           .addHeader("Accept", "application/json")
                           .build();
        lastRequest = okHttpClient.newCall(request);
        lastRequest.enqueue(callback);
    }

    public synchronized void getSpotifyUrl(String url, Callback callback) {
        final Request request =
                new Request.Builder()
                           .url(url)
                           .addHeader("Authorization", "Bearer " + spotifyAccessToken)
                           .addHeader("Accept", "application/json")
                           .build();
        lastRequest = okHttpClient.newCall(request);
        lastRequest.enqueue(callback);
    }

    public synchronized void getCurrentUserProfile(Callback callback) {
        final Request request =
                new Request.Builder()
                        .url("https://api.spotify.com/v1/me")
                        .addHeader("Authorization", "Bearer " + spotifyAccessToken)
                        .addHeader("Accept", "application/json")
                        .build();
        lastRequest = okHttpClient.newCall(request);
        lastRequest.enqueue(callback);
    }

    public boolean spotifyIsAuthenticated() {
        Date now = new Date();

        if (spotifyExpirationDate == null) {
            return false;
        }
        return now.before(spotifyExpirationDate);
    }

    public void getAccessAndRefreshTokenFromCode(String authorizationCode) {
        try {
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("authorization_code", authorizationCode);
            jsonBody.put("grant_type", "authorization_code");
            Log.d(TAG, jsonBody.toString());
            RequestBody body = RequestBody.create(JSON, jsonBody.toString());
            final Request request =
                    new Request.Builder()
                            .url(API_ENDPOINT + "/spotify/token")
                            .post(body)
                            .build();

            lastRequest = okHttpClient.newCall(request);
            lastRequest.enqueue(accessAndRefreshTokenCallback);
        } catch (JSONException e) {
            Log.e(TAG, "Error sending JSON request for refresh token: " + e.toString());
        }
    }

    public void getAccessTokenFromRefreshToken(String refreshToken) {
        try {
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("refresh_token", refreshToken);
            jsonBody.put("grant_type", "refresh_token");
            Log.d(TAG, jsonBody.toString());
            RequestBody body = RequestBody.create(JSON, jsonBody.toString());
            final Request request =
                    new Request.Builder()
                            .url(API_ENDPOINT + "/spotify/token")
                            .post(body)
                            .build();

            lastRequest = okHttpClient.newCall(request);
            lastRequest.enqueue(accessTokenCallback);
        } catch (JSONException e) {
            Log.e(TAG, "Error sending JSON request for refresh token: " + e.toString());
        }
    }

    private Callback accessTokenCallback = new Callback() {

        @Override
        public void onFailure(Call call, IOException e) {
            Log.e(TAG, "Error retrieving access token:" + e.toString());
            notifyAuthFailed();
        }

        @Override
        public void onResponse(Call call, Response response) {
            try {
                final JSONObject jsonResponse = new JSONObject(response.body().string());
                Log.d(TAG, jsonResponse.toString());
                Boolean hasError = jsonResponse.has("error");

                if (hasError) {
                    removeStoredSpotifyRefreshToken();
                    notifyAuthFailed();
                }
                else {
                    spotifyExpiresIn = Integer.valueOf(jsonResponse.getString("expires_in"));
                    setSpotifyAccessToken(jsonResponse.getString("access_token"));
                    setSpotifyTokenExpiration(spotifyExpiresIn);
                    // refresh 5 minutes before token expiry
                    spotifyTimerHandler.postDelayed(spotifyRefreshTokenTimer,
                            (spotifyExpiresIn-300) * 1000);
                    notifyAuthSuccessful();
                }
            } catch (Exception e) {
                notifyAuthFailed();
                Log.e(TAG, "Error parsing/emitting access token: " + e.toString());
            }
        }
    };

    private Callback accessAndRefreshTokenCallback = new Callback() {

        @Override
        public void onFailure(Call call, IOException e) {
            Log.e(TAG, "Error retrieving access token:" + e.toString());
            notifyAuthFailed();
        }

        @Override
        public void onResponse(Call call, Response response) {
            // TODO: store access and refresh tokens on device
            try {
                final JSONObject jsonResponse = new JSONObject(response.body().string());
                Log.d(TAG, jsonResponse.toString());
                Boolean hasError = jsonResponse.has("error");

                if (hasError) {
                    removeStoredSpotifyRefreshToken();
                    notifyAuthFailed();
                }
                else {
                    spotifyExpiresIn = Integer.valueOf(jsonResponse.getString("expires_in"));
                    setSpotifyRefreshToken(jsonResponse.getString("refresh_token"));
                    setSpotifyAccessToken(jsonResponse.getString("access_token"));
                    setSpotifyTokenExpiration(spotifyExpiresIn);
                    spotifyTimerHandler.postDelayed(spotifyRefreshTokenTimer,
                            (spotifyExpiresIn-300) * 1000);
                    notifyAuthSuccessful();
                }
            } catch (Exception e) {
                notifyAuthFailed();
                Log.e(TAG, "Error parsing/emitting access token: " + e.toString());
            }
        }
    };

    private class SpotifySongAddedCallback implements Callback {

        private int timeElapsed;

        public SpotifySongAddedCallback(int timeElapsed) {
            this.timeElapsed = timeElapsed;
        }


        @Override
        public void onFailure(Call call, IOException e) {
            Log.e(TAG, "Error retrieving Spotify song: " + e.toString());
        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {
            try {
                final JSONObject jsonResponse = new JSONObject(response.body().string());
                // TODO: actually parse the error
                if (jsonResponse.has("error")) {
                    notifyAuthFailed();
                }
                else {
                    jsonResponse.put("timeElapsed", timeElapsed);
                    Log.d(TAG, jsonResponse.toString());
                    enqueueSongHandler.post(new SongQueueRunnable(jsonResponse));
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing JSON Spotify song: " + e.toString());
                notifyAuthFailed();
            }
        }
    }

    // Song history public methods for Room Activity

    public ArrayList<Song> getSongHistoryList() {
        return songHistory.getSongHistoryList();
    }

    public Boolean songHistoryIsEnabled() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean("save_queue", false);
    }

    // Room session public methods for Room Activity

    public void removeRoomSession(String roomSessionName) {
        roomSessionHistory.removeRoomSession(roomSessionName);
    }

    public void restoreRoomSession(RoomSession session) {
        roomSessionHistory.removeRoomSession(currentRoomSession.getSessionFilename());
        roomSessionHistory.removeRoomSession(session.getSessionFilename());

        int sessionRestoreIndex = session.getSessionQueueIndex();
        session.resetSessionQueueIndex();
        session.updateSessionDate();

        roomSessionHistory.addRoomSession(session.getSessionFilename());
        currentRoomSession = session;

        ArrayList<Song> originalQueue = session.getSessionQueue();
        ArrayList<Song> copyQueue = new ArrayList<>();

        for (int i = 0; i < originalQueue.size(); i++) {
            Log.i(TAG, "copying queue i: " + String.valueOf(i));
            copyQueue.add(originalQueue.get(i));
        }

        session.clearSessionQueue();

        for (int i = sessionRestoreIndex; i < copyQueue.size(); i++) {
            Song song = copyQueue.get(i);

            if (i == sessionRestoreIndex) {
                Log.i(TAG, "playing: " + song.toString());
                enqueueOrPlaySong(song);
            }
            else {
                Log.i(TAG, "queuinging: " + song.toString());
                enqueueSong(song);
            }
        }
    }

    // Room Activity jukebox accessing functions

    public ArrayList<Song> getSongQueue() {
        return jukebox.getSongQueue();
    }

    public Song getCurrentlyPlaying() {
        return jukebox.getCurrentlyPlaying();
    }

    private void cancelLastRequest() {
        if (lastRequest != null) {
            lastRequest.cancel();
        }
    }

    public static class Endpoint {
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
}
