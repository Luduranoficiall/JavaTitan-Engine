package com.javatitan.engine;

import sun.security.tools.keytool.CertAndKeyGen;
import sun.security.x509.X500Name;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;

public class KeystoreGenerator {
    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);

        Files.createDirectories(options.outDir());

        KeyMaterial server = generate(options.serverDn(), options.keySize(), options.days());
        KeyMaterial client = options.mtls() ? generate(options.clientDn(), options.keySize(), options.days()) : null;

        String extension = options.type().equalsIgnoreCase("PKCS12") ? ".p12" : ".jks";
        Path serverKeystorePath = options.outDir().resolve("server-keystore" + extension);
        Path serverTruststorePath = options.outDir().resolve("server-truststore" + extension);
        Path clientKeystorePath = options.outDir().resolve("client-keystore" + extension);
        Path clientTruststorePath = options.outDir().resolve("client-truststore" + extension);

        createKeyStore(serverKeystorePath, options.type(), options.password(), options.serverAlias(), server.privateKey(), server.certificate());
        createTrustStore(serverTruststorePath, options.type(), options.password(), options.serverAlias(), server.certificate(), options.mtls() ? options.clientAlias() : null, options.mtls() ? client.certificate() : null);

        createTrustStore(clientTruststorePath, options.type(), options.password(), options.serverAlias(), server.certificate(), null, null);
        if (options.mtls()) {
            createKeyStore(clientKeystorePath, options.type(), options.password(), options.clientAlias(), client.privateKey(), client.certificate());
        }

        System.out.println("Keystores gerados em: " + options.outDir().toAbsolutePath());
        System.out.println("server-keystore: " + serverKeystorePath);
        System.out.println("server-truststore: " + serverTruststorePath);
        System.out.println("client-truststore: " + clientTruststorePath);
        if (options.mtls()) {
            System.out.println("client-keystore: " + clientKeystorePath);
        }
    }

    private static KeyMaterial generate(String dn, int keySize, int days) throws Exception {
        CertAndKeyGen keyGen = new CertAndKeyGen("RSA", "SHA256withRSA", null);
        keyGen.generate(keySize);
        PrivateKey privateKey = keyGen.getPrivateKey();
        X500Name x500Name = new X500Name(dn);
        long validitySeconds = Math.max(1L, (long) days * 24 * 3600);
        X509Certificate cert = keyGen.getSelfCertificate(x500Name, new Date(), validitySeconds);
        return new KeyMaterial(privateKey, cert);
    }

    private static void createKeyStore(Path path, String type, char[] password, String alias, PrivateKey key, Certificate cert) throws Exception {
        KeyStore ks = KeyStore.getInstance(type);
        ks.load(null, null);
        ks.setKeyEntry(alias, key, password, new Certificate[] { cert });
        try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
            ks.store(fos, password);
        }
    }

    private static void createTrustStore(Path path, String type, char[] password, String alias, Certificate cert, String extraAlias, Certificate extraCert) throws Exception {
        KeyStore ks = KeyStore.getInstance(type);
        ks.load(null, null);
        ks.setCertificateEntry(alias, cert);
        if (extraAlias != null && extraCert != null) {
            ks.setCertificateEntry(extraAlias, extraCert);
        }
        try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
            ks.store(fos, password);
        }
    }

    private record KeyMaterial(PrivateKey privateKey, X509Certificate certificate) {}

    private record Options(
        Path outDir,
        String type,
        int days,
        int keySize,
        boolean mtls,
        char[] password,
        String serverAlias,
        String clientAlias,
        String serverDn,
        String clientDn
    ) {
        static Options parse(String[] args) {
            Path outDir = Path.of("security");
            String type = "JKS";
            int days = 3650;
            int keySize = 2048;
            boolean mtls = false;
            String password = envOrDefault("JAVATITAN_KEYSTORE_PASSWORD", "changeit");
            String serverAlias = "javatitan-server";
            String clientAlias = "javatitan-client";
            String serverDn = "CN=JavaTitan-Server, OU=TCC, O=JavaTitan, L=Local, ST=BR, C=BR";
            String clientDn = "CN=JavaTitan-Client, OU=TCC, O=JavaTitan, L=Local, ST=BR, C=BR";

            for (String arg : args) {
                if (arg.startsWith("--out-dir=")) {
                    outDir = Path.of(arg.substring("--out-dir=".length()));
                } else if (arg.startsWith("--type=")) {
                    type = arg.substring("--type=".length());
                } else if (arg.startsWith("--days=")) {
                    days = parseInt("--days", arg.substring("--days=".length()));
                } else if (arg.startsWith("--key-size=")) {
                    keySize = parseInt("--key-size", arg.substring("--key-size=".length()));
                } else if (arg.equals("--mtls")) {
                    mtls = true;
                } else if (arg.startsWith("--password=")) {
                    password = arg.substring("--password=".length());
                } else if (arg.startsWith("--server-alias=")) {
                    serverAlias = arg.substring("--server-alias=".length());
                } else if (arg.startsWith("--client-alias=")) {
                    clientAlias = arg.substring("--client-alias=".length());
                } else if (arg.startsWith("--server-dn=")) {
                    serverDn = arg.substring("--server-dn=".length());
                } else if (arg.startsWith("--client-dn=")) {
                    clientDn = arg.substring("--client-dn=".length());
                } else if (arg.equals("--help")) {
                    printHelpAndExit();
                }
            }

            return new Options(outDir, type, days, keySize, mtls, password.toCharArray(), serverAlias, clientAlias, serverDn, clientDn);
        }

        private static int parseInt(String name, String value) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(name + " invalido: " + value);
            }
        }

        private static void printHelpAndExit() {
            System.out.println("KeystoreGenerator (Java puro)");
            System.out.println("Opcoes:");
            System.out.println("  --out-dir=DIR        Diretorio de saida (default security)");
            System.out.println("  --type=JKS|PKCS12    Tipo do keystore (default JKS)");
            System.out.println("  --days=N             Validade do certificado (default 3650)");
            System.out.println("  --key-size=2048      Tamanho da chave RSA (default 2048)");
            System.out.println("  --mtls               Gera client-keystore e truststores para mTLS");
            System.out.println("  --password=PASS      Senha dos stores (default changeit)");
            System.out.println("  --server-alias=ALIAS Alias do server (default javatitan-server)");
            System.out.println("  --client-alias=ALIAS Alias do client (default javatitan-client)");
            System.out.println("  --server-dn=DN       DN do server (default CN=JavaTitan-Server, OU=TCC, O=JavaTitan, L=Local, ST=BR, C=BR)");
            System.out.println("  --client-dn=DN       DN do client (default CN=JavaTitan-Client, OU=TCC, O=JavaTitan, L=Local, ST=BR, C=BR)");
            System.exit(0);
        }

        private static String envOrDefault(String name, String defaultValue) {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                return defaultValue;
            }
            return value.trim();
        }
    }
}
