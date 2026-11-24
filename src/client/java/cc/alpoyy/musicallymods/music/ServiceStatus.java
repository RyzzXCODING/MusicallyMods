package cc.alpoyy.musicallymods.music;

/** Honest, explicit integration status shown by the GUI instead of a generic placeholder. */
public record ServiceStatus(
        IntegrationLevel integration,
        PlaybackSupport playback,
        AuthRequirement auth,
        String summary,
        String detail
) {
    public enum IntegrationLevel {
        IMPLEMENTED,
        PREVIEW_ONLY,
        METADATA_ONLY,
        OPT_IN_EXTERNAL_BRIDGE,
        NOT_INTEGRATED
    }

    public enum PlaybackSupport {
        NATIVE,
        PREVIEW_ONLY,
        EXTERNAL_BRIDGE,
        EXTERNAL_PLAYER_ONLY,
        UNSUPPORTED
    }

    public enum AuthRequirement {
        NONE,
        USER_OAUTH,
        DEVELOPER_APP,
        USER_CONFIGURED_EXECUTABLE
    }

    public ServiceStatus {
        summary = summary == null ? "" : summary;
        detail = detail == null ? "" : detail;
    }

    /** One-line status used in compact layouts. */
    public String line() {
        return summary;
    }
}
