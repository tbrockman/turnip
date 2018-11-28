package ca.turnip.turnip;

public interface RoomJukeboxListener extends JukeboxListener {

    void onSongAdded(Song song);
    void onDisconnect();
}
