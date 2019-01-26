package ca.turnip.turnip;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;

import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.protocol.client.CallResult;
import com.spotify.protocol.client.ErrorCallback;
import com.spotify.protocol.client.Subscription;
import com.spotify.protocol.types.Empty;
import com.spotify.protocol.types.PlayerState;

import static ca.turnip.turnip.MainActivity.APP_REDIRECT_URI;
import static ca.turnip.turnip.MainActivity.CLIENT_ID;

class ServerJukebox extends Jukebox {

    private static final String TAG = ServerJukebox.class.getSimpleName();

    private JukeboxListener jukeboxListener = null;

    // Spotify integration
    private SpotifyAppRemote spotifyAppRemote;
    private Context roomContext;
    private SpotifyConnectionListener spotifyConnectionListener = null;
    private Subscription<PlayerState> playerStateSubscription = null;
    private String spotifyCurrentlyAddedSong = null;
    private boolean spotifyIsConnected = false;

    private boolean wasPaused = false;
    private boolean lock = false;
    private CallResult.ResultCallback<Empty> spotifyCallback = new CallResult.ResultCallback<Empty>() {
        @Override
        public void onResult(Empty empty) {
            lock = false;
        }
    };

    // Server jukebox
    // TODO: implement jukebox with different song apis
    // (i.e bit array indicating spotify/sc/google music/etc.)
    public ServerJukebox(Context roomActivity, JukeboxListener jukeboxListener) {
        super(jukeboxListener);
        // TODO: if spotify then ->
        this.roomContext = roomActivity;
        this.jukeboxListener = jukeboxListener;
        connectToSpotify(roomContext);
    }

    private class SpotifyConnectionListener implements Connector.ConnectionListener {


        private Subscription.EventCallback<PlayerState> spotifyEventCallback;
        private ErrorCallback spotifyErrorCallback;

        public SpotifyConnectionListener() {
            this.spotifyEventCallback = new Subscription.EventCallback<PlayerState>() {
                @Override
                public void onEvent(PlayerState playerState) {

                    // TODO: emit time change events to all clients
                    final int lastTimeElapsed = getTimeElapsed();
                    final int roundedTimeElapsed = Math.round(playerState.playbackPosition / 1000);
                    setTimeElapsed(roundedTimeElapsed);

                    String spotifyTrackURI = null;
                    String spotifyTrackID = null;
                    Song current = getCurrentlyPlaying();
                    if (playerState != null && playerState.track != null) {
                        spotifyTrackURI = playerState.track.uri;
                        if (spotifyTrackURI != null) {
                            spotifyTrackID = spotifyTrackURI.substring(spotifyTrackURI.lastIndexOf(":") + 1);
                        }
                    }

                    if (current != null && spotifyTrackURI != null &&
                        !spotifyTrackURI.equals(current.getString("uri"))) {
                        Song next = getNextSong();
                        if (next != null) {
                            if (spotifyTrackURI.equals(next.getString("uri"))) {
                                ServerJukebox.super.playSong(next);
                            }
                            else if (!lock) {
                                playSong(next);
                            }
                        }
                        // We have a current track, but nothing in the queue
                        // Show what Spotify is playing
                        else {
                            if (!spotifyTrackID.equals(spotifyCurrentlyAddedSong)) {
                                spotifyCurrentlyAddedSong = spotifyTrackID;
                                jukeboxListener.onSpotifyAddedSong(spotifyTrackID,
                                                                   roundedTimeElapsed);
                            }
                        }
                    }
                    // Following code handles the case where we have two of the same songs in a row
                    // which can cause the next song to never be played (because we see that
                    // spotify song == current and don't try to play the next song)
                    // If current == next song == spotify song && last timestamp > current timestamp
                    // Assume we should be playing the next song
                    // Might not be true in the case that someone is messing around with playback position
                    // in Spotify, but this is the easiest way to handle this edge-case while still using
                    // the Spotify queue and being able to force Turnip queue songs when queues disagree
                    else if (current != null && spotifyTrackURI != null &&
                            spotifyTrackURI.equals(current.getString("uri"))) {
                        Song next = getNextSong();
                        if (next != null && spotifyTrackURI.equals(next.getString("uri"))
                                         && lastTimeElapsed > roundedTimeElapsed) {
                            ServerJukebox.super.playSong(next);
                        }
                    }
                    // We don't have a current track
                    // But Spotify is playing something, just play Spotify track
                    else if (current == null && spotifyTrackURI != null &&
                        !spotifyTrackID.equals(spotifyCurrentlyAddedSong) &&
                        !playerState.isPaused) {
                        spotifyCurrentlyAddedSong = spotifyTrackID;
                        jukeboxListener.onSpotifyAddedSong(spotifyTrackID,
                                                           roundedTimeElapsed);
                    }

                    if (playerState.isPaused && !wasPaused) {
                        wasPaused = true;
                        Log.d(TAG, "pausing:");
                        pauseCurrent(roundedTimeElapsed);
                    } else if (!playerState.isPaused && wasPaused) {
                        wasPaused = false;
                        Log.d(TAG, "unpausing");
                        unpauseCurrent(roundedTimeElapsed);
                    }
                }
            };

            this.spotifyErrorCallback = new ErrorCallback() {
                @Override
                public void onError(Throwable throwable) {
                    // TODO: spotify remote error get player api handling
                    Log.d(TAG, throwable.toString());
                }
            };
        }

        @Override
        public void onConnected(SpotifyAppRemote remote) {
            Log.d(TAG, "succcessfully connected to spotify");
            spotifyAppRemote = remote;
            spotifyIsConnected = true;
            playerStateSubscription = spotifyAppRemote.getPlayerApi()
                                                      .subscribeToPlayerState();
            playerStateSubscription.setEventCallback(spotifyEventCallback)
                                   .setErrorCallback(spotifyErrorCallback);
        }

        @Override
        public void onFailure(Throwable throwable) {
            //TODO: spotify remote connection failure handling
            Log.d(TAG, "failed connection to spotify" + throwable.getMessage());
            spotifyIsConnected = false;
            spotifyAppRemote = null;
            playerStateSubscription = null;
            jukeboxListener.onSpotifyDisconnected();
        }
    }

    public void connectToSpotify(Context roomActivity) {
        if (spotifyAppRemote == null || !spotifyAppRemote.isConnected()) {
            Log.d(TAG, "re-initiating spotify connection");
            ConnectionParams connectionParams = new ConnectionParams.Builder(CLIENT_ID)
                                                                    .showAuthView(true)
                                                                    .setRedirectUri(APP_REDIRECT_URI)
                                                                    .build();
            SpotifyAppRemote.disconnect(spotifyAppRemote);

            if (spotifyConnectionListener == null) {
                spotifyConnectionListener = new SpotifyConnectionListener();
            }
            SpotifyAppRemote.connect(roomActivity,
                                     connectionParams,
                                     spotifyConnectionListener);
        }
    }

    @Override
    public void playSong(Song song) {
        // super.playSong(song);
        playSpotify(song.getString("uri"));
    }

    @Override
    public void enqueueSong(Song song) {
        super.enqueueSong(song);
        Log.d(TAG, "enqueuing as server");
        queueSpotify(song.getString("uri"));
    }

    public void playSongAddedBySpotify(Song song) {
        super.playSong(song);
    }

    public void playSpotify(String uri) {
        if (spotifyIsConnected) {
            lock = true;
            CallResult<Empty> result = spotifyAppRemote.getPlayerApi().play(uri);
            result.setResultCallback(spotifyCallback);
            result.setErrorCallback(new ErrorCallback() {
                @Override
                public void onError(Throwable throwable) {
                    Log.e(TAG, throwable.toString());
                    connectToSpotify(roomContext);
                }
            });
        }
    }

    public void queueSpotify(String uri) {
        if (spotifyIsConnected) {
            CallResult<Empty> result = spotifyAppRemote.getPlayerApi().queue(uri);
            result.setResultCallback(spotifyCallback);
            result.setErrorCallback(new ErrorCallback() {
                @Override
                public void onError(Throwable throwable) {
                    Log.e(TAG, throwable.toString());
                    connectToSpotify(roomContext);
                }
            });
        }
    }

    public void skipSpotify() {
        if (spotifyIsConnected) {
            CallResult<Empty> result = spotifyAppRemote.getPlayerApi().skipNext();
            result.setResultCallback(spotifyCallback);
            result.setErrorCallback(new ErrorCallback() {
                @Override
                public void onError(Throwable throwable) {
                    Log.e(TAG, throwable.toString());
                    connectToSpotify(roomContext);
                }
            });
        }
    }

    @Override
    public void playNextSong() {
        skipSpotify();
//        super.playNextSong();
//        Song next = getNextSong();
//        if (next != null) {
//            super.playSong(next);
//
//        }
    }

    @Override
    public void turnOff() {
        super.turnOff();
        SpotifyAppRemote.disconnect(spotifyAppRemote);
        spotifyIsConnected = false;
    }
}
