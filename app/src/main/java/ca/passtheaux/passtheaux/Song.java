package ca.passtheaux.passtheaux;

import android.graphics.Bitmap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// Wrapper for our JSON songs
public abstract class Song {
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
    abstract String getString(String key);
    abstract String getSongType();
}
