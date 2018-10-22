package ca.passtheaux.turnip;

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

import static ca.passtheaux.turnip.Main.APP_REDIRECT_URI;
import static ca.passtheaux.turnip.Main.CLIENT_ID;

class ServerJukebox extends Jukebox {

    private static final String TAG = Jukebox.class.getSimpleName();

    // Spotify integration
    private SpotifyAppRemote spotifyAppRemote;
    private boolean spotifyIsConnected = false;
    private boolean lock = false;
    private CallResult.ResultCallback<Empty> playCallback = new CallResult.ResultCallback<Empty>() {
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
                                        // Check to see if Spotify automatically started playing another song
                                        // i.e, our current song finished playing. If so, override it
                                        if (current != null && !lock &&
                                            !playerState.track
                                                        .uri
                                                        .equals(current.getString("uri"))) {
                                            lock = true; // lock so that we wait to hear back
                                                         // whether the action we sent to spotify
                                                         // app remote finished
                                            playNextSong();
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
        if (spotifyIsConnected) {
            CallResult<Empty> result = spotifyAppRemote.getPlayerApi()
                                                       .play(song.getString("uri"));
            result.setResultCallback(playCallback);
        }
    }

    @Override
    public void playNextSong() {
        super.playNextSong();
        Song next = getNextSong();
        if (next != null) {
            playSong(next);
        }
    }

    @Override
    public void turnOff() {
        super.turnOff();
        SpotifyAppRemote.CONNECTOR.disconnect(spotifyAppRemote);
        spotifyIsConnected = false;
    }

}
