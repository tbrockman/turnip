package ca.turnip.turnip.listeners;

public interface AuthenticationListener {
    public void onTokenSuccess();
    public void onTokenFailure();
}
