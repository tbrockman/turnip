package ca.turnip.turnip.listeners;

import ca.turnip.turnip.services.BackgroundService;

public interface RoomListListener {

    void onRoomLost(String endpointId);
    void onRoomFound(BackgroundService.Endpoint host);
}
