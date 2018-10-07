package ca.passtheaux.passtheaux;

import android.graphics.Bitmap;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

class SpotifySong extends Song {

    private static final String TAG = SpotifySong.class.getSimpleName();

    public SpotifySong(JSONObject jsonSong) {
        super(jsonSong, "spotify");
    }

    public JSONObject get(String key) {
        try {
            return jsonSong.getJSONObject(key);
        } catch (JSONException e) {
           return null;
        }
    }

    public void setAlbumArt(Bitmap bitmap) {
        albumArt = bitmap;
    }

    public boolean hasAlbumArt() {
        return this.albumArt != null;
    }

    public JSONArray getArray(String key) {
        try {
            return jsonSong.getJSONArray(key);
        } catch (JSONException e) {
            return null;
        }
    }

    public String getString(String key) {
        try {
            return jsonSong.getString(key);
        } catch (JSONException e) {
            return null;
        }
    }

    public String getSongType() {
        return "spotify";
    }

    public ArrayList<String> getArtists() {
        ArrayList<String> artists = new ArrayList<>();
        try {
            JSONArray jsonArtists = jsonSong.getJSONArray("artists");
            for (int i = 0; i < jsonArtists.length(); i++) {
                artists.add(jsonArtists.getJSONObject(i).getString("name"));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error converting JSON artists into JSON array");
        }

        return artists;
    }

    @Override
    public String toString() {
        return jsonSong.toString();
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == this) return true;
        if (o == null) return false;
        if (o.getClass() != getClass()) {
            return false;
        }
        return ((Song) o).getString("uri").equals(this.getString("uri"));
    }
}
