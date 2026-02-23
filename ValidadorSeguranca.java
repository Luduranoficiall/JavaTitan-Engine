import java.util.Base64;

public class ValidadorSeguranca {
    public static boolean validarAcesso(String token, String planoNecessario) {
        try {
            // Decodifica o payload do token (A parte do meio do JWT)
            String[] partes = token.split("\\.");
            if (partes.length < 2) return false;
            String payload = new String(Base64.getDecoder().decode(partes[1]));
            
            System.out.println("[JAVA-AUTH] Validando permissões para: " + payload);
            
            // Verifica se o plano no token condiz com o acesso (Simples, mas eficaz para a demo)
            return payload.contains("\"plan\":\"" + planoNecessario + "\"");
        } catch (Exception e) {
            return false;
        }
    }

    public static void main(String[] args) {
        String tokenRecebido = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyIjoiTHVjYXNfRHVyYW4iLCJwbGFuIjoiVklQIn0=.fake_signature";
        
        if (validarAcesso(tokenRecebido, "VIP")) {
            System.out.println("🔓 ACESSO LIBERADO: Processando cálculo de alta precisão...");
        } else {
            System.out.println("🚫 ACESSO NEGADO: Token inválido ou plano insuficiente.");
        }
    }
}