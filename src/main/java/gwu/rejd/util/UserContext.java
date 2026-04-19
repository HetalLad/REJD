package gwu.rejd.util;

public class UserContext {

    private UserContext() {}

    public static String getCurrentUser() {
        return System.getProperty("user.name");
    }
}
