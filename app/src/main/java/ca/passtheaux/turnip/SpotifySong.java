package ca.passtheaux.turnip;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
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

    // TODO: serialize album art bitmap
    public void setAlbumArt(Bitmap bitmap) {
        albumArt = bitmap;

        try {
            String encodedImage = bitmapToString(bitmap);
            jsonSong.put("bitmap", encodedImage);
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
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

    private String bitmapToString(Bitmap bitmap) {
        String encodedImage;
        ByteArrayOutputStream byteArrayBitmapStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100,
                byteArrayBitmapStream);
        byte[] b = byteArrayBitmapStream.toByteArray();
        encodedImage = Base64.encodeToString(b, Base64.DEFAULT);
        return encodedImage;
    }

    public boolean hasAlbumArt() {
        return this.albumArt != null;
    }

    public Bitmap getAlbumArt() {

        if (this.albumArt == null) {
            byte[] decodedString = Base64.decode(getString("bitmap"), Base64.DEFAULT);
            Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString,
                                                         0,
                                                               decodedString.length);
            this.albumArt = decodedByte;
        }

        return this.albumArt;
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
