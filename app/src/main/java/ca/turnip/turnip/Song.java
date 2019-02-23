package ca.turnip.turnip;

import android.graphics.Bitmap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;

// Wrapper for our JSON songs
public abstract class Song implements Comparable<Song> {

    Bitmap albumArt;
    JSONObject jsonSong;

    protected Song(JSONObject jsonSong, String type) {
        try {
            jsonSong.put("songType", type);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        this.jsonSong = jsonSong;
    }

    abstract ArrayList<String> getArtists();
    abstract boolean has(String key);
    abstract Date getLastPlayed();
    abstract JSONArray getArray(String key);
    abstract JSONObject get(String key);
    abstract public int compareTo(Song other);
    abstract String getAlbumName();
    abstract String getAlbumArtURL(String size) throws JSONException;
    abstract String getArtistsAsString();
    abstract String getSongType();
    abstract String getSongTitle();
    abstract String getString(String key);
    abstract String getSongTypeName();
    abstract String getExternalLink();
    abstract void setTimeElapsed(int timeElapsed);
    abstract void setLastPlayed(Date now);
}
