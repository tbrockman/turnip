package ca.passtheaux.passtheaux;

import android.content.Context;
import android.util.Log;

import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.protocol.client.ErrorCallback;
import com.spotify.protocol.client.Subscription;
import com.spotify.protocol.types.PlayerState;

import static ca.passtheaux.passtheaux.Main.APP_REDIRECT_URI;
import static ca.passtheaux.passtheaux.Main.CLIENT_ID;

class ServerJukebox extends Jukebox {

    private static final String TAG = Jukebox.class.getSimpleName();

    // Spotify integration
    private SpotifyAppRemote spotifyAppRemote;
    private boolean spotifyIsConnected = false;

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
                spotifyAppRemote.getPlayerApi().subscribeToPlayerState()
                        .setEventCallback(new Subscription.EventCallback<PlayerState>() {
                            @Override
                            public void onEvent(PlayerState playerState) {
                                // the Spotify App keeps you updated on PlayerState with this event
                                Log.i(TAG, playerState.playbackPosition + "/" + playerState.track.duration);
                                if (playerState.playbackPosition == playerState.track.duration) {
                                    playNextSong();
                                }
                            }
                        })
                        .setErrorCallback(new ErrorCallback() {
                            @Override
                            public void onError(Throwable throwable) {
                                // TODO: spotify remote error get player api handling
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
    public void enqueueSong(Song song) {
        super.enqueueSong(song);
        if (super.getCurrentlyPlaying() == null) {
            playSong(song);
        }
        else {
            Log.i(TAG, super.getCurrentlyPlaying().toString());
        }
    }

    @Override
    public void playSong(Song song) {
        super.playSong(song);
        if (spotifyIsConnected) {
            spotifyAppRemote.getPlayerApi().play(song.getString("uri"));
        }
    }

    public void playNextSong() {
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
