package ca.turnip.turnip.models;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

import ca.turnip.turnip.helpers.Utils;

public class SongHistory {

    private static final String TAG = SongHistory.class.getSimpleName();

    private Context context;
    private LinkedList<Song> songsPlayed;
    private static final String songHistoryFilename = "song_history";

    public SongHistory(Context context) {
        this.context = context;
        this.songsPlayed = readSongsPlayedFromDisk();
    }

    public void addSong(Song song) {
        String songUri = song.getString("uri");

        Iterator<Song> iterator = songsPlayed.iterator();

        while (iterator.hasNext()) {
            Song next = iterator.next();

            if (next.getString("uri").equals(songUri)) {
                iterator.remove();
            }
        }

        songsPlayed.push(song);

        while (songsPlayed.size() > getStorageLimit()) {
            songsPlayed.removeLast();
        }

        writeToDisk();
    }

    public ArrayList<Song> getSongHistoryList() {
        ArrayList<Song> songHistoryList = new ArrayList<>(songsPlayed);
        return songHistoryList;
    }

    public Integer getStorageLimit() {
        Integer limit;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        limit = Integer.valueOf(prefs.getString("save_queue_length", "50"));
        return limit;
    }

    public void writeToDisk() {
        try {
            FileOutputStream outputStream = context.openFileOutput(songHistoryFilename,
                                                                   context.MODE_PRIVATE);
            JSONObject json = new JSONObject();
            JSONArray jsonSongs = new JSONArray();

            Iterator<Song> iterator = songsPlayed.iterator();

            while (iterator.hasNext()) {
                Song next = iterator.next();
                jsonSongs.put(next.jsonSong);
            }

            json.put("songsPlayed", jsonSongs);
            outputStream.write(json.toString().getBytes());
            outputStream.close();
        } catch (Exception e) {
        }
    }

    public LinkedList<Song> readSongsPlayedFromDisk() {
        LinkedList<Song> fromDisk;

        try {
            String stringSongHistory = Utils.readStringFromFile(context, songHistoryFilename);
            fromDisk = parseJsonStringToLinkedList(stringSongHistory);
        } catch (IOException e) {
            fromDisk = new LinkedList<>();
        }
        return fromDisk;
    }

    public LinkedList<Song> parseJsonStringToLinkedList(String stringJson) {
        LinkedList<Song> parsedLinkedList = new LinkedList<>();
        try {
            JSONObject json = new JSONObject(stringJson);
            JSONArray jsonSongs = json.getJSONArray("songsPlayed");

            for (int i = 0; i < jsonSongs.length(); i++) {
                JSONObject jsonSong = jsonSongs.getJSONObject(i);
                Song song = null;

                if (jsonSong.getString("songType").equals("spotify")) {
                    song = new SpotifySong(jsonSong);
                }

                if (song != null) {
                    parsedLinkedList.offer(song);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return parsedLinkedList;
    }
}