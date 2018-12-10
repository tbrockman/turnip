package ca.turnip.turnip;

import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.Iterator;

public class Jukebox {

    private static final String TAG = Jukebox.class.getSimpleName();

    // Songs
    private final ArrayList<Song> songQueue;

    // Time
    private boolean isPlaying;
    private int timeElapsed;
    private JukeboxListener jukeboxListener;
    private final Handler songTimerHandler = new Handler();
    private final Runnable songTimer =  new Runnable() {
        @Override
        public void run() {
            timeElapsed++;
            jukeboxListener.onSongTick(timeElapsed);
            songTimerHandler.postDelayed(this, 1000);
        }
    };

    private Song currentlyPlaying;

    public Jukebox(JukeboxListener jukeboxListener) {
        this.songQueue = new ArrayList<>();
        this.jukeboxListener = jukeboxListener;
    }

    public Song getCurrentlyPlaying() {
        if (this.currentlyPlaying != null) {
            this.currentlyPlaying.setTimeElapsed(timeElapsed);
        }
        return this.currentlyPlaying;
    }

    public Song getNextSong() {
        Song song = null;
        if (this.songQueue.size() > 0) {
            song = this.songQueue.get(0);
        }
        return song;
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
        if (currentlyPlaying.has("timeElapsed")) {
            setTimeElapsed(Integer.parseInt(currentlyPlaying.getString("timeElapsed")));
        }
        else {
            setTimeElapsed(0);
        }
        jukeboxListener.onSongPlaying(currentlyPlaying);
        songTimerHandler.removeCallbacks(songTimer);
        resetTimer();
        startTimer();
    }

    public ArrayList<Song> getSongQueue() { return this.songQueue; }

    public int getSongQueueLength() {
        return this.songQueue.size();
    }

    public void playNextSong() {}

    public void turnOff() {
        songQueue.clear();
        songTimerHandler.removeCallbacks(songTimer);
        jukeboxListener = null;
    }

    public int getTimeElapsed() {
        return this.timeElapsed;
    }

    public void setTimeElapsed(int timeElapsed) {
        if (this.currentlyPlaying != null) {
            this.timeElapsed = timeElapsed;
            this.currentlyPlaying.setTimeElapsed(timeElapsed);
        }
    }

    public void pauseCurrent(int timeElapsed) {
        setTimeElapsed(timeElapsed);
        pauseTimer();

    }

    public void unpauseCurrent(int timeElapsed) {
        setTimeElapsed(timeElapsed);
        startTimer();
    }

    private void startTimer() {
        if (!isPlaying) {
            isPlaying = true;
            songTimerHandler.postDelayed(songTimer, 1000);
        }
    }

    private void pauseTimer() {
        isPlaying = false;
        songTimerHandler.removeCallbacks(songTimer);
    }

    private void resetTimer() { pauseTimer(); }

}
