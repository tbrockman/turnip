package ca.turnip.turnip;

interface JukeboxListener {

    void onSongPlaying(Song song);
    void onSongRemoved(Song song);
    void onSongPaused(int timeElapsed);
    void onSongResumed(int timeElapsed);
    void onSongTick(int timeElapsed);
    void onSpotifyAddedSong(String id, int timeElapsed);
    void onSpotifyDisconnected();
}