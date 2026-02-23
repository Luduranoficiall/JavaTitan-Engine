import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class ValidadorSeguranca {
    private static final String HMAC_ALG = "HmacSHA256";

    public static boolean validarAcesso(String token, Plano planoNecessario, JwtConfig config) {
        ValidationResult result = validarToken(token, config);
        if (!result.ok()) {
            LoggerSaaS.log("WARN", "[JAVA-AUTH] Token rejeitado: " + result.error());
            return false;
        }
        if (!planoNecessario.name().equalsIgnoreCase(result.plan())) {
            LoggerSaaS.log("WARN", "[JAVA-AUTH] Plano insuficiente: " + result.plan());
            return false;
        }
        return true;
    }

    private static ValidationResult validarToken(String token, JwtConfig config) {
        if (token == null || token.isBlank()) {
            return ValidationResult.erro("token vazio");
        }
        String[] partes = token.split("\\.");
        if (partes.length != 3) {
            return ValidationResult.erro("formato invalido");
        }

        String header64 = partes[0];
        String payload64 = partes[1];
        String assinatura = partes[2];

        String headerJson = decodeBase64Url(header64);
        String payloadJson = decodeBase64Url(payload64);
        if (headerJson == null || payloadJson == null) {
            return ValidationResult.erro("base64 invalido");
        }

        String alg = JsonUtils.readString(headerJson, "alg");
        if (alg == null || !"HS256".equalsIgnoreCase(alg)) {
            return ValidationResult.erro("alg nao suportado");
        }

        if (!validarAssinatura(header64, payload64, assinatura, config.secret())) {
            return ValidationResult.erro("assinatura invalida");
        }

        if (!validarClaims(payloadJson, config)) {
            return ValidationResult.erro("claims invalidas");
        }

        String plan = JsonUtils.readString(payloadJson, "plan");
        if (plan == null || plan.isBlank()) {
            return ValidationResult.erro("claim plan ausente");
        }

        return ValidationResult.ok(plan);
    }

    private static boolean validarClaims(String payloadJson, JwtConfig config) {
        if (config.requireExp()) {
            Long exp = JsonUtils.readOptionalLong(payloadJson, "exp");
            if (exp == null) {
                return false;
            }
            long now = Instant.now().getEpochSecond();
            if (exp <= now) {
                return false;
            }
        }

        if (config.issuer() != null) {
            String iss = JsonUtils.readString(payloadJson, "iss");
            if (iss == null || !iss.equals(config.issuer())) {
                return false;
            }
        }

        if (config.audience() != null) {
            String aud = JsonUtils.readString(payloadJson, "aud");
            if (aud == null || !aud.equals(config.audience())) {
                return false;
            }
        }

        return true;
    }

    private static boolean validarAssinatura(String header64, String payload64, String assinatura, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALG));
            String mensagem = header64 + "." + payload64;
            byte[] raw = mac.doFinal(mensagem.getBytes(StandardCharsets.UTF_8));
            String esperado = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
            return constantTimeEquals(esperado, assinatura);
        } catch (Exception ex) {
            return false;
        }
    }

    private static String decodeBase64Url(String base64) {
        try {
            return new String(Base64.getUrlDecoder().decode(padBase64(base64)), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String padBase64(String base64) {
        int padding = (4 - (base64.length() % 4)) % 4;
        return base64 + "=".repeat(padding);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private record ValidationResult(boolean ok, String plan, String error) {
        static ValidationResult ok(String plan) {
            return new ValidationResult(true, plan, null);
        }

        static ValidationResult erro(String error) {
            return new ValidationResult(false, null, error);
        }
    }
}
