package ca.turnip.turnip.models;

import android.content.Context;
import android.text.TextUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import static ca.turnip.turnip.helpers.Utils.readStringFromFile;

public class RoomSessionHistory {

    public static String sessionHistoryFilename = "session_history";

    private Context context;
    private ArrayList<String> roomSessionNames;

    public RoomSessionHistory(Context context) {
        this.context = context;
        this.roomSessionNames = new ArrayList<>();
    }

    public void setRoomSessionNames(ArrayList<String> roomSessionNames) {
        this.roomSessionNames = roomSessionNames;
    }

    public void addRoomSession(String sessionName) {
        roomSessionNames.add(sessionName);
        writeToDisk();
    }

    public void removeRoomSession(String sessionName) {
        Iterator<String> iterator = roomSessionNames.iterator();

        while (iterator.hasNext()) {
            String next = iterator.next();

            if (next.equals(sessionName)) {
                context.deleteFile(next);
                iterator.remove();
                break;
            }
        }
    }

    public RoomSession getMostRecentRoomSession(Context ctx) {
        String filename = getMostRecentRoomSessionFilename();
        RoomSession recentSession = null;
        try {
            recentSession = RoomSession.fromDisk(ctx, filename);
        } catch(Exception e) {
            removeRoomSession(filename);
            e.printStackTrace();
        }
        return recentSession;
    }

    public String getMostRecentRoomSessionFilename() {
        if (roomSessionNames.size() > 0) {
            return roomSessionNames.get(roomSessionNames.size() - 1);
        }
        return null;
    }

    public static RoomSessionHistory fromDisk(Context ctx) {
        RoomSessionHistory history = new RoomSessionHistory(ctx);
        try {
            String stringList = readStringFromFile(ctx, sessionHistoryFilename);
            String[] split = stringList.split(",");
            ArrayList<String> sessionNames = new ArrayList(Arrays.asList(split));
            history.setRoomSessionNames(sessionNames);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return history;
    }

    public void writeToDisk() {
        try {
            FileOutputStream outputStream = context.openFileOutput(sessionHistoryFilename,
                                                                   context.MODE_PRIVATE);
            outputStream.write(TextUtils.join(",", roomSessionNames).getBytes());
            outputStream.close();
        } catch (Exception e) {
        }
    }
}