package au.com.transport.tapservice;

public class CommonUtils {

    public static String normalize(String value) {
        return value == null ? null : value.trim();
    }

    public static boolean isNumericPan(String pan) {
        if (pan == null || pan.isBlank()) {
            return false;
        }
        return pan.trim().matches("\\d+");
    }
}
