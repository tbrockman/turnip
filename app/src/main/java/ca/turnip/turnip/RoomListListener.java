package ca.turnip.turnip;

public interface RoomListListener {

    void onRoomLost(String endpointId);
    void onRoomFound(BackgroundService.Endpoint host);
}
