package com.sps.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Optional;

public class CommandLineInterface {

    public static void main(String[] args) throws Exception {
        Server server = Server.builder()
                .rootFile(new File(getEnvOpt("SPS_WEBSITE_ROOT_DIR").orElse(System.getProperty("user.dir"))))
                .port(getEnvOpt("SPS_PORT").map(Integer::parseInt).orElse(8000))
                .threads(getEnvOpt("SPS_THREADS").map(Integer::parseInt).orElse(100))
                .httpAuthEnabled(getEnvOpt("SPS_HTTP_AUTH_ENABLED").map(Boolean::valueOf).orElse(false))
                .httpAuthUser(getEnvOpt("SPS_HTTP_AUTH_USER").orElse(null))
                .httpAuthPassword(getEnvOpt("SPS_HTTP_AUTH_PASSWORD").orElse(null))
                .sslEnabled(getEnvOpt("SPS_SSL_ENABLED").map(Boolean::valueOf).orElse(false))
                .sslCertPassword(getEnvOpt("SPS_SSL_CERT_PASSWORD").orElse(null))
                .keystoreInputStream(getEnvOpt("SPS_SSL_CERT_FILE").map(CommandLineInterface::openStream).orElse(null))
                .build();
        server.run();
    }

    private static Optional<String> getEnvOpt(String name) {
        return Optional.ofNullable(System.getenv(name));
    }

    private static String getEnvRequired(String name) {
        return getEnvOpt(name).orElseThrow(() -> new RuntimeException(name + " is a required argument"));
    }

    private static FileInputStream openStream(String filename) {
        try {
            return new FileInputStream(filename);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
