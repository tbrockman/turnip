package ca.turnip.turnip.controllers;

import android.os.Handler;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;

import ca.turnip.turnip.listeners.JukeboxListener;
import ca.turnip.turnip.models.Song;

public class Jukebox {

    private static final String TAG = Jukebox.class.getSimpleName();

    // Songs
    private final ArrayList<Song> songQueue;

    // Time
    private boolean isPlaying;
    private int timeElapsed;
    private JukeboxListener jukeboxListener;
    private final Handler songTimerHandler;
    private final Runnable songTimer =  new Runnable() {
        @Override
        public void run() {
            timeElapsed++;
            if (jukeboxListener != null) {
                jukeboxListener.onSongTick(timeElapsed);
            }
            songTimerHandler.postDelayed(this, 1000);
        }
    };

    private Song currentlyPlaying;

    public Jukebox(JukeboxListener jukeboxListener) {
        this.songQueue = new ArrayList<>();
        this.jukeboxListener = jukeboxListener;
        this.songTimerHandler = new Handler();
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

        if (song == null) { return; }

        Iterator<Song> it = songQueue.iterator();
        while (it.hasNext()) {
            Song next = it.next();
            if (next.equals(song)) {
                jukeboxListener.onSongRemoved(next);
                songQueue.remove(next);
                break;
            }
        }

        this.currentlyPlaying = song;
        this.currentlyPlaying.setLastPlayed(Calendar.getInstance().getTime());
        if (this.currentlyPlaying.has("timeElapsed")) {
            setTimeElapsed(Integer.parseInt(this.currentlyPlaying.getString("timeElapsed")));
        }
        else {
            setTimeElapsed(0);
        }
        jukeboxListener.onSongPlaying(this.currentlyPlaying);
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
        this.timeElapsed = timeElapsed;
        if (this.currentlyPlaying != null) {
            this.currentlyPlaying.setTimeElapsed(timeElapsed);
        }
    }

    public void pauseCurrent(int timeElapsed) {
        if (jukeboxListener != null) {
            jukeboxListener.onSongPaused(timeElapsed);
        }
        setTimeElapsed(timeElapsed);
        pauseTimer();

    }

    public void unpauseCurrent(int timeElapsed) {
        if (jukeboxListener != null) {
            jukeboxListener.onSongResumed(timeElapsed);
        }
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
