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

    private static final String TAG = Jukebox.class.getSimpleName();

    // Spotify integration
    private SpotifyAppRemote spotifyAppRemote;
    private boolean spotifyIsConnected = false;
    private boolean wasPaused = false;
    private CallResult.ResultCallback<Empty> spotifyCallback = new CallResult.ResultCallback<Empty>() {
        @Override
        public void onResult(Empty empty) {
        }
    };

    // Server jukebox
    // TODO: implement jukebox with different song apis
    // (i.e bit array indicating spotify/sc/google music/etc.)
    public ServerJukebox(Context roomActivity, JukeboxListener jukeboxListener) {
        super(jukeboxListener);
        ConnectionParams connectionParams = new ConnectionParams.Builder(CLIENT_ID)
                                                                .showAuthView(true)
                                                                .setRedirectUri(APP_REDIRECT_URI)
                                                                .build();

        // TODO: if spotify then ->
        SpotifyAppRemote.CONNECTOR.connect(roomActivity,
                                           connectionParams,
                                       new Connector.ConnectionListener() {

            @Override
            public void onConnected(SpotifyAppRemote remote) {
                spotifyAppRemote = remote;
                spotifyIsConnected = true;
                spotifyAppRemote.getPlayerApi()
                                .subscribeToPlayerState()
                                .setEventCallback(new Subscription.EventCallback<PlayerState>() {
                                    @Override
                                    public void onEvent(PlayerState playerState) {

                                        Song current = getCurrentlyPlaying();
                                        // TODO: if spotify starts playing another song
                                        // query it and display it as currently playing
                                        if (current != null &&
                                            !playerState.track
                                                        .uri
                                                        .equals(current.getString("uri"))) {
                                            Song next = getNextSong();
                                            ServerJukebox.super.playSong(next);
                                        }

                                        Log.i(TAG, String.valueOf(playerState.playbackPosition));
                                        if (playerState.isPaused && !wasPaused) {
                                            wasPaused = true;
                                            Log.i(TAG, "pausing:");
                                            pauseCurrent(Math.round(playerState.playbackPosition / 1000));
                                        } else if (!playerState.isPaused && wasPaused) {
                                            wasPaused = false;
                                            Log.i(TAG, "unpausing");
                                            unpauseCurrent(Math.round(playerState.playbackPosition / 1000));
                                        }
                                    }
                                })
                                .setErrorCallback(new ErrorCallback() {
                                    @Override
                                    public void onError(Throwable throwable) {
                                        // TODO: spotify remote error get player api handling
                                        Log.e(TAG, throwable.toString());
                                    }
                                });
            }

            @Override
            public void onFailure(Throwable throwable) {
                //TODO: spotify remote connection failure handling
                Log.i(TAG, "failed connection to spotify" + throwable.getMessage());
            }
        });
    }

    @Override
    public void playSong(Song song) {
        super.playSong(song);
        playSpotify(song.getString("uri"));
    }

    @Override
    public void enqueueSong(Song song) {
        super.enqueueSong(song);
        queueSpotify(song.getString("uri"));
    }

    public void playSpotify(String uri) {
        if (spotifyIsConnected) {
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
        super.playNextSong();
        Song next = getNextSong();
        if (next != null) {
            super.playSong(next);
            skipSpotify();
        }
    }

    @Override
    public void turnOff() {
        super.turnOff();
        SpotifyAppRemote.CONNECTOR.disconnect(spotifyAppRemote);
        spotifyIsConnected = false;
    }
}
