import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ValidadorSeguranca {
    private static final Pattern PLAN_PATTERN = Pattern.compile("\\\"plan\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    public static boolean validarAcesso(String token, Plano planoNecessario) {
        String planoNoToken = extrairPlano(token);
        if (planoNoToken == null) {
            return false;
        }
        return planoNoToken.equalsIgnoreCase(planoNecessario.name());
    }

    public static String extrairPlano(String token) {
        String payload = extrairPayload(token);
        if (payload == null) {
            return null;
        }
        Matcher matcher = PLAN_PATTERN.matcher(payload);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public static String extrairPayload(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String[] partes = token.split("\\.");
        if (partes.length < 2) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(padBase64(partes[1]));
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String padBase64(String base64) {
        int padding = (4 - (base64.length() % 4)) % 4;
        return base64 + "=".repeat(padding);
    }

    public static void main(String[] args) {
        String tokenRecebido = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyIjoiTHVjYXNfRHVyYW4iLCJwbGFuIjoiVklQIn0=.fake_signature";
        if (validarAcesso(tokenRecebido, Plano.VIP)) {
            System.out.println("ACESSO LIBERADO: Processando calculo de alta precisao...");
        } else {
            System.out.println("ACESSO NEGADO: Token invalido ou plano insuficiente.");
        }
    }
}
