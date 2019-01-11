package ca.turnip.turnip;

import android.graphics.Bitmap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

// Wrapper for our JSON songs
public abstract class Song {
    JSONObject jsonSong;
    Bitmap albumArt;

    protected Song(JSONObject jsonSong, String type) {
        try {
            jsonSong.put("songType", type);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        this.jsonSong = jsonSong;
    }

    abstract JSONObject get(String key);
    abstract String getString(String key);
    abstract boolean has(String key);
    abstract JSONArray getArray(String key);
    abstract void setTimeElapsed(int timeElapsed);
    abstract String getAlbumName();
    abstract String getAlbumArtURL(String size) throws JSONException;
    abstract ArrayList<String> getArtists();
    abstract String getArtistsAsString();
    abstract String getSongType();
    abstract String getSongTitle();
    abstract String getSongTypeName();
    abstract String getExternalLink();
}
