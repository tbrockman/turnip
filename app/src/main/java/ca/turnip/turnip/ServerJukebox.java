package ca.turnip.turnip;

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

    // Spotify integration
    private SpotifyAppRemote spotifyAppRemote;
    private boolean spotifyIsConnected = false;
    private boolean wasPaused = false;
    private boolean lock = false;
    private String spotifyCurrentlyAddedSong = null;
    private CallResult.ResultCallback<Empty> spotifyCallback = new CallResult.ResultCallback<Empty>() {
        @Override
        public void onResult(Empty empty) {
            lock = false;
        }
    };

    // Server jukebox
    // TODO: implement jukebox with different song apis
    // (i.e bit array indicating spotify/sc/google music/etc.)
    public ServerJukebox(Context roomActivity, final JukeboxListener jukeboxListener) {
        super(jukeboxListener);
        // TODO: if spotify then ->
        connectToSpotify(roomActivity, jukeboxListener);
    }

    private class SpotifyConnectionListener implements Connector.ConnectionListener {

        private final JukeboxListener jukeboxListener;
        private Subscription.EventCallback<PlayerState> spotifyEventCallback;
        private ErrorCallback spotifyErrorCallback;

        public SpotifyConnectionListener(final JukeboxListener jukeboxListener) {
            this.jukeboxListener = jukeboxListener;
            this.spotifyEventCallback = new Subscription.EventCallback<PlayerState>() {
                @Override
                public void onEvent(PlayerState playerState) {

                    // TODO: emit time change events to all clients
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

                    // TODO: if spotify starts playing another song
                    // query it and display it as currently playing
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
                            // TODO: implement a lock to prevent calling this
                            // multiple times for the same song
                            if (!spotifyTrackID.equals(spotifyCurrentlyAddedSong)) {
                                Log.i(TAG, "could be playing spotifys song here");
                                spotifyCurrentlyAddedSong = spotifyTrackID;
                                jukeboxListener.onSpotifyAddedSong(spotifyTrackID,
                                                                   roundedTimeElapsed);
                            }
                        }
                    }

                    // We don't have a current track
                    // But Spotify is playing something
                    else if (current == null && spotifyTrackURI != null &&
                        !spotifyTrackID.equals(spotifyCurrentlyAddedSong) &&
                        !playerState.isPaused) {
                        Log.i(TAG, "current track is null, play spotify");
                        spotifyCurrentlyAddedSong = spotifyTrackID;
                        jukeboxListener.onSpotifyAddedSong(spotifyTrackID,
                                                           roundedTimeElapsed);
                    }

                    if (playerState.isPaused && !wasPaused) {
                        wasPaused = true;
                        Log.i(TAG, "pausing:");
                        pauseCurrent(roundedTimeElapsed);
                    } else if (!playerState.isPaused && wasPaused) {
                        wasPaused = false;
                        Log.i(TAG, "unpausing");
                        unpauseCurrent(roundedTimeElapsed);
                    }
                }
            };

            this.spotifyErrorCallback = new ErrorCallback() {
                @Override
                public void onError(Throwable throwable) {
                    // TODO: spotify remote error get player api handling
                    Log.e(TAG, throwable.toString());
                }
            };
        }

        @Override
        public void onConnected(SpotifyAppRemote remote) {
            Log.i(TAG, "succcessfully connected to spotify");
            spotifyAppRemote = remote;
            spotifyIsConnected = true;
            spotifyAppRemote.getPlayerApi()
                            .subscribeToPlayerState()
                            .setEventCallback(spotifyEventCallback)
                            .setErrorCallback(spotifyErrorCallback);
        }

        @Override
        public void onFailure(Throwable throwable) {
            //TODO: spotify remote connection failure handling
            Log.i(TAG, "failed connection to spotify" + throwable.getMessage());
            spotifyIsConnected = false;
            spotifyAppRemote = null;
        }
    }

    public void connectToSpotify(Context roomActivity, final JukeboxListener jukeboxListener) {
        if (spotifyAppRemote == null || !spotifyAppRemote.isConnected()) {
            Log.i(TAG, "re-initiating spotify connection");
            ConnectionParams connectionParams = new ConnectionParams.Builder(CLIENT_ID)
                                                                    .showAuthView(true)
                                                                    .setRedirectUri(APP_REDIRECT_URI)
                                                                    .build();

            SpotifyAppRemote.CONNECTOR.connect(roomActivity,
                    connectionParams,
                    new SpotifyConnectionListener(jukeboxListener));
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
        Log.i(TAG, "enqueuing as server");
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
        }
    }

    public void queueSpotify(String uri) {
        if (spotifyIsConnected) {
            CallResult<Empty> result = spotifyAppRemote.getPlayerApi().queue(uri);
            result.setResultCallback(spotifyCallback);
        }
    }

    public void skipSpotify() {
        if (spotifyIsConnected) {
            CallResult<Empty> result = spotifyAppRemote.getPlayerApi().skipNext();
            result.setResultCallback(spotifyCallback);
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
        SpotifyAppRemote.CONNECTOR.disconnect(spotifyAppRemote);
        spotifyIsConnected = false;
    }
}
