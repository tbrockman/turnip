package ca.turnip.turnip;

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

    public boolean has(String key) {
        return jsonSong.has(key);
    }

    public void setString(String key, String value) {
        try {
            jsonSong.put(key, value);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    void setTimeElapsed(int timeElapsed) {
        try {
            jsonSong.put("timeElapsed", String.valueOf(timeElapsed));
        } catch (JSONException e) {
            Log.e(TAG, "Error setting time elapsed on song: " + e.toString());
        }
    }

    public String getAlbumArtURL() throws JSONException {
        JSONObject album = this.get("album");
        JSONArray albumImages = album.getJSONArray("images");
        JSONObject imageInfoJSON = albumImages.getJSONObject(albumImages.length()-1);
        String albumArtUrl = imageInfoJSON.getString("url");
        return albumArtUrl;
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
