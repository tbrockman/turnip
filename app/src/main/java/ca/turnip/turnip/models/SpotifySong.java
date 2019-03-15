package ca.turnip.turnip.models;

import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class SpotifySong extends Song {

    private static final String TAG = SpotifySong.class.getSimpleName();
    DateFormat dateFormat = new SimpleDateFormat("yyyy-mm-dd hh:mm:ss");

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

    public int compareTo(Song other) {
        Date lastPlayed = getLastPlayed();
        Date otherLastPlayed = other.getLastPlayed();

        if (lastPlayed == otherLastPlayed) {
            return 0;
        }

        if (lastPlayed == null) {
            return 1;
        }

        if (otherLastPlayed == null) {
            return -1;
        }

        return otherLastPlayed.compareTo(lastPlayed); // descending order
    }

    @Override
    public void setTimeElapsed(int timeElapsed) {
        try {
            jsonSong.put("timeElapsed", String.valueOf(timeElapsed));
        } catch (JSONException e) {
            Log.e(TAG, "Error setting time elapsed on song: " + e.toString());
        }
    }

    @Override
    public Date getLastPlayed() {
        Date lastPlayed;
        try {
            String lastPlayedString = jsonSong.getString("lastPlayed");
            lastPlayed = dateFormat.parse(lastPlayedString);
        } catch (JSONException e) {
            lastPlayed = null;
        } catch (ParseException e) {
            lastPlayed = null;
        }
        return lastPlayed;
    }

    @Override
    public void setLastPlayed(Date now) {
        try {
            String stringDate = dateFormat.format(now);
            jsonSong.put("lastPlayed", stringDate);
        } catch (JSONException e) {
            Log.e(TAG, "Error setting time elapsed on song: " + e.toString());
        }
    }

    public String getAlbumName() {
        String albumName = null;
        JSONObject album = get("album");
        if (album != null) {
            try {
                albumName = album.getString("name");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return albumName;
    }

    public String getAlbumArtURL(String size) throws JSONException {
        String albumArtUrl = null;
        JSONObject imageInfoJSON = null;
        JSONObject album = this.get("album");
        JSONArray albumImages = album.getJSONArray("images");

        if (size.equals("small")) {
            if (albumImages.length() >= 1) {
                imageInfoJSON = albumImages.getJSONObject(albumImages.length()-1);
            }
        }
        else if (size.equals("medium")) {
            if (albumImages.length() >= 2) {
                imageInfoJSON = albumImages.getJSONObject(albumImages.length()-2);
            }
        }
        else if (size.equals("large")) {
            if (albumImages.length() >= 3) {
                imageInfoJSON = albumImages.getJSONObject(albumImages.length()-3);
            }
        }

        if (imageInfoJSON != null) {
            albumArtUrl = imageInfoJSON.getString("url");
        }
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

    public String getSongTypeName() {
        return "Spotify";
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

    public String getArtistsAsString() {
        return TextUtils.join(", ", getArtists());
    }

    public String getSongTitle() {
        return getString("name");
    }

    public String getExternalLink() {
        JSONObject externalUrls = get("external_urls");
        String external = null;
        try {
            if (externalUrls != null) {
                external = externalUrls.getString("spotify");
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return external;
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
