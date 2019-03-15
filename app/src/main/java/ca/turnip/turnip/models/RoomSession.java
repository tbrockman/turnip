package ca.turnip.turnip.models;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;

import static ca.turnip.turnip.helpers.Utils.readStringFromFile;

public class RoomSession {

    private ArrayList<Song> sessionQueue;
    private boolean sessionError = false;
    private Context context;
    private Date sessionDate;
    private Integer currentSessionQueueIndex;
    private SimpleDateFormat format = new SimpleDateFormat("EEE_d_MMM_yyyy_HH_mm_ss_Z");;
    private String roomName;

    public RoomSession(Context context, String roomName) {
        this.context = context;
        this.roomName = roomName;
        this.sessionDate = Calendar.getInstance().getTime();
        this.currentSessionQueueIndex = 0;
        this.sessionQueue = new ArrayList<>();
    }

    public RoomSession(JSONObject json) throws JSONException, ParseException {
        sessionError = json.getBoolean("error");
        sessionDate = format.parse(json.getString("date"));
        roomName = json.getString("roomName");
        currentSessionQueueIndex = json.getInt("queueIndex");
        sessionQueue = new ArrayList<>();

        JSONArray jsonQueue = json.getJSONArray("queue");

        for (int i = 0; i < jsonQueue.length(); i++) {
            JSONObject jsonSong = jsonQueue.getJSONObject(i);
            Song song = null;

            if (jsonSong.getString("songType").equals("spotify")) {
                song = new SpotifySong(jsonSong);
            }

            if (song != null) {
                sessionQueue.add(song);
            }
        }
    }

    public void addSong(Song song) {
        sessionQueue.add(song);
        writeToDisk();
    }

    public void playSong() {
        currentSessionQueueIndex += 1;
    }

    public void writeToDisk() {
        try {
            FileOutputStream outputStream = context.openFileOutput(getSessionFilename(),
                                                                   context.MODE_PRIVATE);
            JSONObject json = toJSON();
            outputStream.write(json.toString().getBytes());
            outputStream.close();
        } catch (Exception e) {
        }
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();
        JSONArray jsonSongs = new JSONArray();

        Iterator<Song> iterator = sessionQueue.iterator();

        while (iterator.hasNext()) {
            Song next = iterator.next();
            jsonSongs.put(next.jsonSong);
        }

        json.put("date", format.format(sessionDate));
        json.put("error", sessionError);
        json.put("roomName", roomName);
        json.put("queue", jsonSongs);
        json.put("queueIndex", currentSessionQueueIndex);

        return json;
    }

    public static RoomSession fromDisk(Context context, String filename) throws IOException,
                                                                                JSONException,
                                                                                ParseException {
        RoomSession session = null;

        String stringJSON = readStringFromFile(context, filename);
        JSONObject jsonSession = new JSONObject(stringJSON);
        session = new RoomSession(jsonSession);

        return session;
    }

    public String getSessionFilename() {
        String stringDate = format.format(sessionDate);
        return roomName + "_" + stringDate;
    }

    public ArrayList<Song> getSessionQueue() {
        return sessionQueue;
    }

    public Integer getCurrentSessionQueueIndex() {
        return currentSessionQueueIndex;
    }

    public void setError(boolean error) {
        sessionError = error;
        writeToDisk();
    }

    public boolean hasError() {
        return sessionError;
    }

    public boolean sessionDidFinish() {
        return currentSessionQueueIndex == sessionQueue.size();
    }

    @Override
    public String toString() {
        try {
            return toJSON().toString();
        } catch (JSONException e) {
            return "Failed to convert RoomSession to JSON string.";
        }
    }

}