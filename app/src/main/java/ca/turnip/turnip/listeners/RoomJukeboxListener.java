package ca.turnip.turnip.listeners;

import ca.turnip.turnip.models.RoomSession;
import ca.turnip.turnip.models.Song;

public interface RoomJukeboxListener extends JukeboxListener {

    void onSongAdded(Song song);
    void onDisconnect();
    void onSpotifyDisconnected();
    void onConnect();
    void onPastSessionError(RoomSession session);
}
