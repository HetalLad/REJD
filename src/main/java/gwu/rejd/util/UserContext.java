package gwu.rejd.util;

/**
 * Provides the current author name used for notes.
 *
 * <p>By default this falls back to {@code System.getProperty("user.name")} (the OS login).
 * The Eclipse plugin calls {@link #setCurrentUser(String)} after the Login dialog closes
 * so that the typed-in name is used instead.</p>
 */
public class UserContext {

    private static volatile String overrideUser = null;

    private UserContext() {}

    /**
     * Override the current user name.
     * Call this from the Eclipse Login dialog after the user clicks OK.
     */
    public static void setCurrentUser(String name) {
        overrideUser = (name != null && !name.isBlank()) ? name.trim() : null;
    }

    /** Returns the override name if set, otherwise the OS user name. */
    public static String getCurrentUser() {
        return overrideUser != null ? overrideUser : System.getProperty("user.name");
    }
}
