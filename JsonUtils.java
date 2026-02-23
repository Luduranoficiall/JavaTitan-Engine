import java.math.BigDecimal;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JsonUtils {
    private JsonUtils() {}

    public static String readRequiredString(String json, String key) {
        String value = readString(json, key);
        if (value == null) {
            throw new IllegalArgumentException("Campo obrigatorio: " + key);
        }
        return value;
    }

    public static String readString(String json, String key) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public static BigDecimal readRequiredBigDecimal(String json, String key) {
        String raw = readNumber(json, key);
        if (raw == null) {
            throw new IllegalArgumentException("Campo obrigatorio: " + key);
        }
        return parseBigDecimal(raw, key);
    }

    public static BigDecimal readBigDecimal(String json, String key) {
        String raw = readNumber(json, key);
        if (raw == null) {
            return null;
        }
        return parseBigDecimal(raw, key);
    }

    public static Long readOptionalLong(String json, String key) {
        String raw = readNumber(json, key);
        if (raw == null) {
            return null;
        }
        try {
            return new BigDecimal(raw).longValueExact();
        } catch (ArithmeticException | NumberFormatException ex) {
            throw new IllegalArgumentException("Valor invalido para " + key + ": " + raw);
        }
    }

    public static String readNumber(String json, String key) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(key)
            + "\\\"\\s*:\\s*([-+]?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public static UUID readRequiredUuid(String json, String key) {
        String raw = readRequiredString(json, key);
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("UUID invalido para " + key + ": " + raw);
        }
    }

    public static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private static BigDecimal parseBigDecimal(String raw, String key) {
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Valor invalido para " + key + ": " + raw);
        }
    }
}
