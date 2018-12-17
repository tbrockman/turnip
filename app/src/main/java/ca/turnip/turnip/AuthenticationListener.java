package ca.turnip.turnip;

public interface AuthenticationListener {
    public void onTokenSuccess();
    public void onTokenFailure();
}
