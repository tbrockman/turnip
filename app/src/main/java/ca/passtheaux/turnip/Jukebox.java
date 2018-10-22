package ca.passtheaux.turnip;

import java.util.ArrayList;
import java.util.Iterator;

public class Jukebox {
    // Songs
    private final ArrayList<Song> songQueue;
    private JukeboxListener jukeboxListener;
    private Song currentlyPlaying;

    public Jukebox(JukeboxListener jukeboxListener) {
        this.songQueue = new ArrayList<>();
        this.jukeboxListener = jukeboxListener;
    }

    public void enqueueSong(Song song) {
        songQueue.add(song);
    }

    public void playSong(Song song) {
        Iterator<Song> it = songQueue.iterator();
        while (it.hasNext()) {
            Song next = it.next();
            if (next.equals(song)) {
                jukeboxListener.onSongRemoved(next);
                songQueue.remove(next);
                break;
            }
        }
        currentlyPlaying = song;
        jukeboxListener.onSongPlaying(song);
    }

    public void playNextSong() {}

    public void turnOff() {
        songQueue.clear();
        jukeboxListener = null;
    }

    public Song getCurrentlyPlaying() {
        return this.currentlyPlaying;
    }

    public void setTimeElapsed(int timeElapsed) {
        if (this.currentlyPlaying != null) {
            this.currentlyPlaying.setTimeElapsed(timeElapsed);
        }
    }

    public Song getNextSong() {
        Song song = null;
        if (this.songQueue.size() > 0) {
            song = this.songQueue.get(0);
        }
        return song;
    }

    public ArrayList<Song> getSongQueue() { return this.songQueue; }

    public int getSongQueueLength() {
        return this.songQueue.size();
    }

    public interface JukeboxListener {
        public void onSongPlaying(Song song);
        public void onSongRemoved(Song song);
    }
}
