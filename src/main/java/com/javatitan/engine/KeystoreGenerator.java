package com.javatitan.engine;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class KeystoreGenerator {
    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        Files.createDirectories(options.outDir());

        String extension = options.type().equalsIgnoreCase("PKCS12") ? ".p12" : ".jks";
        Path serverKeystorePath = options.outDir().resolve("server-keystore" + extension);
        Path serverTruststorePath = options.outDir().resolve("server-truststore" + extension);
        Path clientKeystorePath = options.outDir().resolve("client-keystore" + extension);
        Path clientTruststorePath = options.outDir().resolve("client-truststore" + extension);
        Path serverCertPath = options.outDir().resolve("server-cert.pem");
        Path clientCertPath = options.outDir().resolve("client-cert.pem");

        deleteIfExists(serverKeystorePath);
        deleteIfExists(serverTruststorePath);
        deleteIfExists(clientKeystorePath);
        deleteIfExists(clientTruststorePath);
        deleteIfExists(serverCertPath);
        deleteIfExists(clientCertPath);

        generateKeypair(serverKeystorePath, options.type(), options.password(), options.serverAlias(), options.keySize(), options.days(), options.serverDn(), options.san());
        exportCert(serverKeystorePath, options.type(), options.password(), options.serverAlias(), serverCertPath);

        importCert(serverTruststorePath, options.type(), options.password(), options.serverAlias(), serverCertPath);
        importCert(clientTruststorePath, options.type(), options.password(), options.serverAlias(), serverCertPath);

        if (options.mtls()) {
            generateKeypair(clientKeystorePath, options.type(), options.password(), options.clientAlias(), options.keySize(), options.days(), options.clientDn(), options.san());
            exportCert(clientKeystorePath, options.type(), options.password(), options.clientAlias(), clientCertPath);
            importCert(serverTruststorePath, options.type(), options.password(), options.clientAlias(), clientCertPath);
        }

        System.out.println("Keystores gerados em: " + options.outDir().toAbsolutePath());
        System.out.println("server-keystore: " + serverKeystorePath);
        System.out.println("server-truststore: " + serverTruststorePath);
        System.out.println("client-truststore: " + clientTruststorePath);
        if (options.mtls()) {
            System.out.println("client-keystore: " + clientKeystorePath);
        }
    }

    private static void generateKeypair(Path keystore, String type, char[] password, String alias, int keySize, int days, String dn, String san) throws Exception {
        List<String> args = new ArrayList<>();
        args.add("-genkeypair");
        args.add("-alias");
        args.add(alias);
        args.add("-keyalg");
        args.add("RSA");
        args.add("-keysize");
        args.add(String.valueOf(keySize));
        args.add("-sigalg");
        args.add("SHA256withRSA");
        args.add("-validity");
        args.add(String.valueOf(days));
        args.add("-dname");
        args.add(dn);
        args.add("-keystore");
        args.add(keystore.toString());
        args.add("-storetype");
        args.add(type);
        args.add("-storepass");
        args.add(new String(password));
        args.add("-keypass");
        args.add(new String(password));
        args.add("-noprompt");
        if (san != null && !san.isBlank()) {
            args.add("-ext");
            args.add("SAN=" + san);
        }

        execKeytool(args);
    }

    private static void exportCert(Path keystore, String type, char[] password, String alias, Path certFile) throws Exception {
        List<String> args = List.of(
            "-exportcert",
            "-alias", alias,
            "-keystore", keystore.toString(),
            "-storetype", type,
            "-storepass", new String(password),
            "-rfc",
            "-file", certFile.toString()
        );
        execKeytool(args);
    }

    private static void importCert(Path truststore, String type, char[] password, String alias, Path certFile) throws Exception {
        List<String> args = List.of(
            "-importcert",
            "-noprompt",
            "-alias", alias,
            "-file", certFile.toString(),
            "-keystore", truststore.toString(),
            "-storetype", type,
            "-storepass", new String(password)
        );
        execKeytool(args);
    }

    private static void execKeytool(List<String> args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(findKeytool());
        command.addAll(args);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        String output = readAll(process.getInputStream());
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IllegalStateException("keytool falhou: " + output);
        }
    }

    private static String findKeytool() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            Path candidate = Path.of(javaHome, "bin", isWindows() ? "keytool.exe" : "keytool");
            if (Files.exists(candidate)) {
                return candidate.toString();
            }
        }
        return "keytool";
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win");
    }

    private static String readAll(InputStream input) throws Exception {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    private static void deleteIfExists(Path path) throws Exception {
        Files.deleteIfExists(path);
    }

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
        String clientDn,
        String san
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
            String san = envOrDefault("JAVATITAN_TLS_SAN", "DNS:localhost,IP:127.0.0.1");

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
                } else if (arg.startsWith("--san=")) {
                    san = arg.substring("--san=".length());
                } else if (arg.equals("--no-san")) {
                    san = null;
                } else if (arg.equals("--help")) {
                    printHelpAndExit();
                }
            }

            return new Options(outDir, type, days, keySize, mtls, password.toCharArray(), serverAlias, clientAlias, serverDn, clientDn, san);
        }

        private static int parseInt(String name, String value) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(name + " invalido: " + value);
            }
        }

        private static void printHelpAndExit() {
            System.out.println("KeystoreGenerator (Java, usando keytool)");
            System.out.println("Opcoes:");
            System.out.println("  --out-dir=DIR        Diretorio de saida (default security)");
            System.out.println("  --type=JKS|PKCS12    Tipo do keystore (default JKS)");
            System.out.println("  --days=N             Validade do certificado (default 3650)");
            System.out.println("  --key-size=2048      Tamanho da chave RSA (default 2048)");
            System.out.println("  --mtls               Gera client-keystore e truststores para mTLS");
            System.out.println("  --password=PASS      Senha dos stores (default changeit)");
            System.out.println("  --server-alias=ALIAS Alias do server (default javatitan-server)");
            System.out.println("  --client-alias=ALIAS Alias do client (default javatitan-client)");
            System.out.println("  --server-dn=DN       DN do server");
            System.out.println("  --client-dn=DN       DN do client");
            System.out.println("  --san=SAN            SubjectAltName (default DNS:localhost,IP:127.0.0.1)");
            System.out.println("  --no-san             Desabilita SAN");
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
