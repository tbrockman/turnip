package ca.passtheaux.passtheaux;

import android.graphics.Bitmap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;

// Wrapper for our JSON songs
public abstract class Song implements Serializable {
    JSONObject jsonSong;
    Bitmap albumArt;

    protected Song(JSONObject jsonSong, String type) {
        try {
            jsonSong.put("songType", "spotify");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        this.jsonSong = jsonSong;
    }

    abstract JSONObject get(String key);
    abstract JSONArray getArray(String key);
    abstract boolean hasAlbumArt();
    abstract void setAlbumArt(Bitmap bitmap);
    abstract Bitmap getAlbumArt();
    abstract ArrayList<String> getArtists();
    abstract String getString(String key);
    abstract String getSongType();
}
