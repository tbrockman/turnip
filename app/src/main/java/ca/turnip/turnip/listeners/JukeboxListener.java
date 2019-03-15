package ca.turnip.turnip.listeners;

import ca.turnip.turnip.models.Song;

public interface JukeboxListener {

    void onSongPlaying(Song song);
    void onSongRemoved(Song song);
    void onSongPaused(int timeElapsed);
    void onSongResumed(int timeElapsed);
    void onSongTick(int timeElapsed);
    void onSpotifyAddedSong(String id, int timeElapsed);
    void onSpotifyDisconnected();
}