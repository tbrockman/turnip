package ca.turnip.turnip.models;

import android.graphics.Bitmap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;

// Wrapper for our JSON songs
public abstract class Song implements Comparable<Song> {

    public Bitmap albumArt;
    public JSONObject jsonSong;

    public Song(JSONObject jsonSong, String type) {
        try {
            jsonSong.put("songType", type);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        this.jsonSong = jsonSong;
    }

    public abstract ArrayList<String> getArtists();
    public abstract boolean has(String key);
    public abstract Date getLastPlayed();
    public abstract JSONArray getArray(String key);
    public abstract JSONObject get(String key);
    public abstract int compareTo(Song other);
    public abstract String getAlbumName();
    public abstract String getAlbumArtURL(String size) throws JSONException;
    public abstract String getArtistsAsString();
    public abstract String getSongType();
    public abstract String getSongTitle();
    public abstract String getString(String key);
    public abstract String getSongTypeName();
    public abstract String getExternalLink();
    public abstract void setTimeElapsed(int timeElapsed);
    public abstract void setLastPlayed(Date now);
}
