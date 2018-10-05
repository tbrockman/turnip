package ca.passtheaux.passtheaux;

import android.util.Log;

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
        Log.i("jukebox", String.valueOf(songQueue.size()));
    }

    public void playSong(Song song) {
        Iterator<Song> it = songQueue.iterator();
        while (it.hasNext()) {
            Song next = it.next();
            if (next.equals(song)) {
                it.remove();
                break;
            }
        }
        currentlyPlaying = song;
        jukeboxListener.onSongPlaying(song);
    }

    public void turnOff() {
        songQueue.clear();
    }

    public Song getCurrentlyPlaying() {
        return this.currentlyPlaying;
    }

    public Song getNextSong() {
        return this.songQueue.get(0);
    }

    public int getSongQueueLength() {
        return this.songQueue.size();
    }

    public interface JukeboxListener {
        public void onSongPlaying(Song song);
    }
}
