package cc.alpoyy.musicallymods.auth;

import java.util.concurrent.CompletableFuture;

/** Shared state/cancellation behavior; every provider keeps its own immutable view. */
abstract class BaseConnection {
    protected final String provider;
    private volatile PlatformConnection.View view;

    protected BaseConnection(String provider, PlatformConnection.State initial,
                             String detail, boolean playbackAvailable) {
        this.provider = provider;
        this.view = new PlatformConnection.View(initial, "", "", detail, false, playbackAvailable);
    }

    public final PlatformConnection.View view() {
        return view;
    }

    protected final void update(PlatformConnection.State state, String account, String error,
                                String detail, boolean hasSession) {
        view = new PlatformConnection.View(state, account, error, detail, hasSession,
                view.playbackAvailable());
    }

    protected final void update(PlatformConnection.State state, String account, String error,
                                String detail, boolean hasSession, boolean playbackAvailable) {
        view = new PlatformConnection.View(state, account, error, detail, hasSession, playbackAvailable);
    }

    /** Silent, user-independent session validation; each provider implements its own check. */
    public abstract CompletableFuture<Void> reconnect();

    public CompletableFuture<Void> disconnect() {
        AuthStore.clearSession(provider);
        update(PlatformConnection.State.DISCONNECTED, "", "", "Saved session removed from this device.", false);
        return CompletableFuture.completedFuture(null);
    }

    /** Called on client shutdown; subclasses cancel pending browser callbacks where applicable. */
    public void cancel() {
    }

    protected String missingConsent() {
        return "Local session storage is disabled. Confirm storage in the platform page before connecting; "
                + AuthStore.storageNote();
    }

    protected boolean consentGranted() {
        return AuthStore.get().storageConsent;
    }

    protected AuthStore.Session session() {
        return AuthStore.session(provider);
    }
}
